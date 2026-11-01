package com.krishna.aegis.agent

import android.content.Context
import android.util.Log
import com.krishna.aegis.data.AegisDatabase
import com.krishna.aegis.data.Skill
import com.krishna.aegis.engine.AegisEngine
import com.krishna.aegis.engine.EngineTurnResult
import com.krishna.aegis.tools.ToolOutcome
import org.json.JSONArray
import org.json.JSONObject

class Planner(
    private val context: Context,
    private val engine: AegisEngine
) {
    private val TAG = "Planner"
    private val db = AegisDatabase.getInstance(context)

    suspend fun checkCachedSkill(goal: String): List<Decision.Act>? {
        val normalized = goal.trim().lowercase()

        // 1. Exact match in Room database
        val exactMatch = db.skillDao().findExactMatch(normalized)
        if (exactMatch != null) {
            return parseSkillPlan(exactMatch)
        }

        // 2. Fuzzy match in Room database
        val fuzzyMatch = db.skillDao().findBestFuzzyMatch(normalized)
        if (fuzzyMatch != null) {
            return parseSkillPlan(fuzzyMatch)
        }

        // 3. Fast-path built-in skills
        return BuiltInSkillMatcher.match(normalized)
    }

    suspend fun planNextStep(
        goal: String,
        screenSummary: String,
        history: List<HistoryEntry>
    ): Decision {
        val turnResult = if (history.isEmpty()) {
            // First turn of the goal: reset conversation & send user turn prompt
            engine.resetConversation()

            val facts = db.factDao().getAllFacts().joinToString("\n") { "${it.factKey}: ${it.factValue}" }
            val userPrompt = PromptBuilder.buildUserTurnPrompt(
                goal = goal,
                screenSummary = screenSummary,
                history = history,
                relevantFacts = facts.ifBlank { null }
            )
            engine.sendUserPrompt(userPrompt)
        } else {
            // Subsequent turns: feed previous tool execution result into LiteRT-LM conversation
            val lastEntry = history.last()
            val outcomeDesc = formatOutcome(lastEntry.outcome)
            val resultPayload = buildString {
                append(outcomeDesc)
                append("\n[Screen Update]: ")
                append(screenSummary)
            }
            engine.sendToolResult(lastEntry.decision.toolName, resultPayload)
        }

        return when (turnResult) {
            is EngineTurnResult.ToolCall -> {
                Log.i(TAG, "LiteRT-LM ToolCall turn: ${turnResult.name}(${turnResult.arguments})")
                if (turnResult.name == "tasks") {
                    val summary = turnResult.arguments["summary"]?.toString() ?: "Goal completed."
                    Decision.Finish(summary)
                } else {
                    Decision.Act(turnResult.name, turnResult.arguments)
                }
            }
            is EngineTurnResult.Text -> {
                Log.i(TAG, "LiteRT-LM Text turn: ${turnResult.content}")
                Decision.Finish(turnResult.content.ifBlank { "Completed." })
            }
            is EngineTurnResult.Error -> {
                Log.w(TAG, "LiteRT-LM Error turn: ${turnResult.message}")
                if (history.size > 15) {
                    Decision.Finish("Maximum turns reached without explicit completion: ${turnResult.message}")
                } else {
                    Decision.Act("read_screen_summary", emptyMap())
                }
            }
        }
    }

    private fun formatOutcome(outcome: ToolOutcome): String {
        return when (outcome) {
            is ToolOutcome.Ok -> "SUCCESS: ${outcome.result}"
            is ToolOutcome.TransientErr -> "RETRYABLE ERROR: ${outcome.message}"
            is ToolOutcome.LogicErr -> "LOGIC ERROR: ${outcome.message}"
            is ToolOutcome.AuthErr -> "AUTH REJECTED: ${outcome.message}"
            is ToolOutcome.FatalErr -> "FATAL ERROR: ${outcome.message}"
        }
    }

    private fun parseSkillPlan(skill: Skill): List<Decision.Act>? {
        return try {
            val jsonArray = JSONArray(skill.planJson)
            val actions = mutableListOf<Decision.Act>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val tool = obj.getString("tool")
                val argsObj = obj.optJSONObject("args")
                val args = mutableMapOf<String, Any?>()
                if (argsObj != null) {
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        args[k] = argsObj.get(k)
                    }
                }
                actions.add(Decision.Act(tool, args))
            }
            actions
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveLearnedSkill(goal: String, successfulActions: List<Decision.Act>) {
        if (successfulActions.isEmpty()) return
        try {
            val jsonArray = JSONArray()
            successfulActions.forEach { act ->
                val obj = JSONObject()
                obj.put("tool", act.toolName)
                val argsObj = JSONObject(act.args)
                obj.put("args", argsObj)
                jsonArray.put(obj)
            }
            val skill = Skill(
                goalPattern = goal.trim().lowercase(),
                planJson = jsonArray.toString(),
                successCount = 1
            )
            db.skillDao().insertSkill(skill)
        } catch (e: Exception) {
            // Ignore persistence errors
        }
    }
}
