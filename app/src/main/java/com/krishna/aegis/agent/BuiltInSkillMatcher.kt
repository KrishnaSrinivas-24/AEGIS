package com.krishna.aegis.agent

object BuiltInSkillMatcher {

    fun match(goal: String): List<Decision.Act>? {
        val g = goal.trim().lowercase()

        // 1. Home Navigation
        if (g in listOf("go home", "home", "take me home", "open home")) {
            return listOf(Decision.Act("go_home", emptyMap()))
        }

        // 2. Back Navigation
        if (g in listOf("go back", "back", "navigate back")) {
            return listOf(Decision.Act("go_back", emptyMap()))
        }

        // 3. Direct App Launch (e.g. "open settings", "launch calculator")
        val openAppRegex = Regex("^(?:open|launch|start)\\s+([a-zA-Z0-9\\s_-]+)$")
        val appMatch = openAppRegex.find(g)
        if (appMatch != null) {
            val appName = appMatch.groupValues[1].trim()
            if (appName.isNotEmpty() && !appName.contains("and") && !appName.contains("then")) {
                return listOf(Decision.Act("open_app", mapOf("name" to appName)))
            }
        }

        // 4. Read screen
        if (g in listOf("read screen", "what's on screen", "screen summary", "describe screen")) {
            return listOf(Decision.Act("read_screen_summary", emptyMap()))
        }

        return null
    }
}
