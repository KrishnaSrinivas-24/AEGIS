package com.krishna.aegis.agent

import org.junit.Assert.*
import org.junit.Test

class BuiltInSkillMatcherTest {

    @Test
    fun testHomeNavigationMatches() {
        val variations = listOf("go home", "home", "take me home", "open home", "  GO HOME  ", "Home")
        for (v in variations) {
            val result = BuiltInSkillMatcher.match(v)
            assertNotNull("Prompt '$v' must match built-in home skill", result)
            assertEquals(1, result?.size)
            assertEquals("go_home", result?.first()?.toolName)
            assertEquals(emptyMap<String, Any?>(), result?.first()?.args)
        }
    }

    @Test
    fun testBackNavigationMatches() {
        val variations = listOf("go back", "back", "navigate back", "  Go Back  ", "BACK")
        for (v in variations) {
            val result = BuiltInSkillMatcher.match(v)
            assertNotNull("Prompt '$v' must match built-in back navigation", result)
            assertEquals(1, result?.size)
            assertEquals("go_back", result?.first()?.toolName)
            assertEquals(emptyMap<String, Any?>(), result?.first()?.args)
        }
    }

    @Test
    fun testReadScreenMatches() {
        val variations = listOf("read screen", "what's on screen", "screen summary", "describe screen", "Describe Screen")
        for (v in variations) {
            val result = BuiltInSkillMatcher.match(v)
            assertNotNull("Prompt '$v' must match built-in read screen", result)
            assertEquals(1, result?.size)
            assertEquals("read_screen_summary", result?.first()?.toolName)
            assertEquals(emptyMap<String, Any?>(), result?.first()?.args)
        }
    }

    @Test
    fun testDirectAppLaunchMatches() {
        val appTestCases = mapOf(
            "open settings" to "settings",
            "launch calculator" to "calculator",
            "start camera" to "camera",
            "open google chrome" to "google chrome",
            "launch whatsapp" to "whatsapp",
            "START YOUTUBE" to "youtube"
        )

        for ((prompt, expectedApp) in appTestCases) {
            val result = BuiltInSkillMatcher.match(prompt)
            assertNotNull("Prompt '$prompt' must match direct app launch", result)
            assertEquals(1, result?.size)
            val action = result?.first()
            assertEquals("open_app", action?.toolName)
            assertEquals(expectedApp, action?.args?.get("name"))
        }
    }

    @Test
    fun testCompoundAndComplexGoalsAreRejectedForFullPlanning() {
        val complexPrompts = listOf(
            "open settings and turn on wifi",
            "launch calculator then compute 45 times 2",
            "go home and open camera",
            "send a message to Mom saying hello",
            "book a cab to the airport",
            "delete the oldest email",
            "click on search button"
        )

        for (prompt in complexPrompts) {
            val result = BuiltInSkillMatcher.match(prompt)
            assertNull("Complex prompt '$prompt' must bypass fast-path matcher and return null for full planner", result)
        }
    }

    @Test
    fun testMalformedOrEmptyInputsReturnNull() {
        val malformed = listOf(
            "",
            "   ",
            "open",
            "launch",
            "start",
            "open ",
            "launch   "
        )

        for (prompt in malformed) {
            val result = BuiltInSkillMatcher.match(prompt)
            assertNull("Malformed/empty prompt '$prompt' must return null", result)
        }
    }
}
