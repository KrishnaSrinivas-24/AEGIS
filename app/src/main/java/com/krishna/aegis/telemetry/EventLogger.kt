package com.krishna.aegis.telemetry

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

sealed class AegisEvent {
    data class VoiceState(val state: String) : AegisEvent()
    data class GoalStarted(val goal: String) : AegisEvent()
    data class PlanStep(val step: Int, val tool: String, val args: Map<String, Any?>) : AegisEvent()
    data class ToolResult(val tool: String, val outcome: String) : AegisEvent()
    data class BiometricIntercept(val reason: String, val approved: Boolean) : AegisEvent()
    data class SkillMatch(val goal: String, val stepsCount: Int) : AegisEvent()
    data class SkillLearned(val goal: String, val stepsCount: Int) : AegisEvent()
    data class GoalCompleted(val summary: String, val durationMs: Long) : AegisEvent()
    data class ErrorEncountered(val error: AegisError) : AegisEvent()
}

object EventLogger {
    private const val TAG = "AEGISEventLogger"
    private val channel = Channel<AegisEvent>(Channel.BUFFERED)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            for (event in channel) {
                render(event)
            }
        }
    }

    fun log(event: AegisEvent) {
        channel.trySend(event)
    }

    private fun render(event: AegisEvent) {
        when (event) {
            is AegisEvent.VoiceState -> Log.i(TAG, "[VOICE] ${event.state}")
            is AegisEvent.GoalStarted -> Log.i(TAG, ">>> GOAL STARTED: \"${event.goal}\"")
            is AegisEvent.PlanStep -> Log.i(TAG, "[STEP ${event.step}] Tool: ${event.tool} | Args: ${event.args}")
            is AegisEvent.ToolResult -> Log.i(TAG, "[RESULT] Tool: ${event.tool} -> ${event.outcome}")
            is AegisEvent.BiometricIntercept -> Log.w(TAG, "[SECURITY] Biometric HITL Intercept: ${event.reason} -> Approved: ${event.approved}")
            is AegisEvent.SkillMatch -> Log.i(TAG, "[SKILL] Cache Hit! Replaying ${event.stepsCount} steps for: \"${event.goal}\"")
            is AegisEvent.SkillLearned -> Log.i(TAG, "[SKILL] Learned new skill (${event.stepsCount} steps) for: \"${event.goal}\"")
            is AegisEvent.GoalCompleted -> Log.i(TAG, "<<< GOAL COMPLETED in ${event.durationMs}ms: ${event.summary}")
            is AegisEvent.ErrorEncountered -> {
                if (event.error.isRecoverable) {
                    Log.w(TAG, "[WARN] Transient error: ${event.error.message}")
                } else {
                    Log.e(TAG, "[ERROR] Unrecoverable error: ${event.error.message}", (event.error as? AegisError.FatalErr)?.cause)
                }
            }
        }
    }
}
