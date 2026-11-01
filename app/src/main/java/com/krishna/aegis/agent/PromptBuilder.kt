package com.krishna.aegis.agent

import com.krishna.aegis.tools.ToolRegistry

object PromptBuilder {

    fun buildSystemPrompt(): String {
        return """
You are AEGIS (Autonomous Edge-Guided Interface Sentinel), an on-device Android OS automation agent.
Your mission is to achieve the user's goal by observing the active screen and choosing the next best action.

GUIDELINES:
1. Examine the CURRENT SCREEN state carefully.
2. Choose exactly ONE action per turn.
3. Respond ONLY with a valid JSON object matching this schema:
   {"tool": "<tool_name>", "args": {<argument_key>: <argument_value>}}
4. When the goal is completed, call the `tasks` tool:
   {"tool": "tasks", "args": {"summary": "<brief summary of what was completed>"}}
5. Never output markdown codeblocks, explanations, or commentary. Output pure JSON only.

${ToolRegistry.getToolDeclarationsPrompt()}
""".trimIndent()
    }

    fun buildUserTurnPrompt(
        goal: String,
        screenSummary: String,
        history: List<HistoryEntry>,
        relevantFacts: String? = null
    ): String {
        return buildString {
            append("USER GOAL: \"$goal\"\n\n")

            if (!relevantFacts.isNullOrBlank()) {
                append("SAVED USER FACTS / PREFERENCES:\n")
                append(relevantFacts)
                append("\n\n")
            }

            append("CURRENT SCREEN STATE:\n")
            append(screenSummary)
            append("\n\n")

            if (history.isNotEmpty()) {
                append("ACTION HISTORY (LAST STEPS):\n")
                history.forEach { entry ->
                    append("- Step ${entry.step}: ${entry.decision.toolName}(${entry.decision.args}) -> ${formatOutcome(entry.outcome)}\n")
                }
                append("\n")
            }

            append("What is your next single tool action? (Output JSON only):")
        }
    }

    private fun formatOutcome(outcome: com.krishna.aegis.tools.ToolOutcome): String {
        return when (outcome) {
            is com.krishna.aegis.tools.ToolOutcome.Ok -> "SUCCESS: ${outcome.result}"
            is com.krishna.aegis.tools.ToolOutcome.TransientErr -> "RETRYABLE ERROR: ${outcome.message}"
            is com.krishna.aegis.tools.ToolOutcome.LogicErr -> "LOGIC ERROR: ${outcome.message}"
            is com.krishna.aegis.tools.ToolOutcome.AuthErr -> "AUTH REJECTED: ${outcome.message}"
            is com.krishna.aegis.tools.ToolOutcome.FatalErr -> "FATAL ERROR: ${outcome.message}"
        }
    }
}
