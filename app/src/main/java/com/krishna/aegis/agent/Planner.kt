package com.krishna.aegis.agent

import android.content.Context
import com.krishna.aegis.data.AegisDatabase
import com.krishna.aegis.data.Skill
import com.krishna.aegis.engine.AegisEngine
import com.krishna.aegis.engine.AgentResponse
import org.json.JSONArray
import org.json.JSONObject

class Planner(
    private val context: Context,
    private val engine: AegisEngine
) {
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
        // Collect saved facts from database
        val facts = db.factDao().getAllFacts().joinToString("\n") { "${it.factKey}: ${it.factValue}" }

        val userPrompt = PromptBuilder.buildUserTurnPrompt(
            goal = goal,
            screenSummary = screenSummary,
            history = history,
            relevantFacts = facts.ifBlank { null }
        )

        val responseText = engine.generateResponse(userPrompt)
        val agentResponse = engine.parseToolCall(responseText)

        return when (agentResponse) {
            is AgentResponse.ToolCallRequest -> {
                if (agentResponse.toolName == "tasks") {
                    val summary = agentResponse.args["summary"]?.toString() ?: "Goal completed."
                    Decision.Finish(summary)
                } else {
                    Decision.Act(agentResponse.toolName, agentResponse.args)
                }
            }
            is AgentResponse.Error -> {
                // If model failed to generate valid JSON, fall back to read_screen_summary or finishing if stuck
                if (history.size > 15) {
                    Decision.Finish("Maximum turns reached without explicit completion.")
                } else {
                    Decision.Act("read_screen_summary", emptyMap())
                }
            }
            else -> Decision.Finish("Completed.")
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
