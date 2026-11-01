package com.krishna.aegis.agent

import com.krishna.aegis.service.UINodeParser
import com.krishna.aegis.telemetry.AegisError
import com.krishna.aegis.telemetry.AegisEvent
import com.krishna.aegis.telemetry.EventLogger
import com.krishna.aegis.tools.ToolOutcome
import com.krishna.aegis.tools.ToolRegistry
import com.krishna.aegis.tools.ToolServiceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AgentOrchestrator(
    private val toolContext: ToolServiceContext,
    private val planner: Planner
) {
    private val windowManager = SlidingWindowManager(maxRecentEntries = 9)

    suspend fun executeGoal(goal: String): String = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        EventLogger.log(AegisEvent.GoalStarted(goal))
        windowManager.clear()

        // 1. Check Skill Memory Cache (Fast Replay Path)
        val cachedPlan = planner.checkCachedSkill(goal)
        if (cachedPlan != null && cachedPlan.isNotEmpty()) {
            EventLogger.log(AegisEvent.SkillMatch(goal, cachedPlan.size))
            val replaySuccess = executeCachedPlan(cachedPlan)
            if (replaySuccess) {
                val duration = System.currentTimeMillis() - startTime
                val summary = "Completed cached skill for \"$goal\" in ${duration}ms"
                EventLogger.log(AegisEvent.GoalCompleted(summary, duration))
                return@withContext summary
            }
            EventLogger.log(AegisEvent.ErrorEncountered(AegisError.TransientErr("Cached skill execution deviated from screen state. Falling back to live ReAct loop.")))
        }

        // 2. Live ReAct Loop (Up to 20 Turns)
        var currentStep = 1
        val maxSteps = 20

        while (currentStep <= maxSteps) {
            val root = toolContext.getRootInActiveWindow()
            val screenSummary = UINodeParser.summarizeScreen(root, toolContext.getActivePackageName())

            val decision = planner.planNextStep(
                goal = goal,
                screenSummary = screenSummary,
                history = windowManager.getWindow()
            )

            when (decision) {
                is Decision.Finish -> {
                    val duration = System.currentTimeMillis() - startTime
                    // Save successful actions to episodic skill memory
                    val successfulActions = windowManager.getAllSuccessfulActions()
                    if (successfulActions.isNotEmpty()) {
                        planner.saveLearnedSkill(goal, successfulActions)
                        EventLogger.log(AegisEvent.SkillLearned(goal, successfulActions.size))
                    }
                    EventLogger.log(AegisEvent.GoalCompleted(decision.summary, duration))
                    return@withContext decision.summary
                }

                is Decision.Act -> {
                    EventLogger.log(AegisEvent.PlanStep(currentStep, decision.toolName, decision.args))
                    val tool = ToolRegistry.getTool(decision.toolName)

                    if (tool == null) {
                        val outcome = ToolOutcome.LogicErr("Tool '${decision.toolName}' is not registered.")
                        windowManager.addEntry(HistoryEntry(currentStep, decision, outcome))
                        EventLogger.log(AegisEvent.ToolResult(decision.toolName, "UNKNOWN_TOOL"))
                    } else {
                        val outcome = try {
                            tool.execute(decision.args, toolContext)
                        } catch (e: Exception) {
                            ToolOutcome.FatalErr("Unhandled tool crash: ${e.message}")
                        }

                        windowManager.addEntry(HistoryEntry(currentStep, decision, outcome))
                        val outcomeDesc = when (outcome) {
                            is ToolOutcome.Ok -> outcome.result
                            is ToolOutcome.TransientErr -> "Transient: ${outcome.message}"
                            is ToolOutcome.LogicErr -> "Logic: ${outcome.message}"
                            is ToolOutcome.AuthErr -> "Auth Denied: ${outcome.message}"
                            is ToolOutcome.FatalErr -> "Fatal: ${outcome.message}"
                        }
                        EventLogger.log(AegisEvent.ToolResult(decision.toolName, outcomeDesc))

                        if (outcome is ToolOutcome.AuthErr) {
                            val duration = System.currentTimeMillis() - startTime
                            val summary = "Execution halted by safety gate: ${outcome.message}"
                            EventLogger.log(AegisEvent.GoalCompleted(summary, duration))
                            return@withContext summary
                        }
                    }
                }
            }

            delay(300)
            currentStep++
        }

        val duration = System.currentTimeMillis() - startTime
        val summary = "Reached 20-step limit without explicit completion."
        EventLogger.log(AegisEvent.GoalCompleted(summary, duration))
        return@withContext summary
    }

    private suspend fun executeCachedPlan(plan: List<Decision.Act>): Boolean {
        for ((idx, action) in plan.withIndex()) {
            val tool = ToolRegistry.getTool(action.toolName) ?: return false
            EventLogger.log(AegisEvent.PlanStep(idx + 1, action.toolName, action.args))
            val outcome = try {
                tool.execute(action.args, toolContext)
            } catch (e: Exception) {
                return false
            }
            if (outcome !is ToolOutcome.Ok) {
                return false
            }
        }
        return true
    }
}
