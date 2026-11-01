package com.krishna.aegis

import android.content.Context
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import com.krishna.aegis.agent.*
import com.krishna.aegis.tools.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BoundaryAndCornerCasesTest {

    private class MockToolServiceContext(
        override val androidContext: Context = object : android.content.ContextWrapper(null) {},
        private val activePackage: String = "com.android.settings",
        private val rootNode: AccessibilityNodeInfo? = null
    ) : ToolServiceContext {
        override fun getRootInActiveWindow(): AccessibilityNodeInfo? = rootNode
        override fun getActivePackageName(): String = activePackage
        override suspend fun dispatchClick(node: AccessibilityNodeInfo): Boolean = true
        override suspend fun dispatchSetText(node: AccessibilityNodeInfo, text: String): Boolean = true
        override suspend fun dispatchScroll(forward: Boolean): Boolean = true
        override suspend fun dispatchGlobalBack(): Boolean = true
        override suspend fun dispatchGlobalHome(): Boolean = true
        override suspend fun launchApp(packageName: String): Boolean = true
        override fun resolvePackageForApp(appName: String): String? {
            if (appName.isBlank()) return null
            return "com.example.${appName.lowercase()}"
        }
        override suspend fun takeScreenCapture(): Bitmap? = null
    }

    @Test
    fun testMissingRequiredArgumentsReturnLogicErr() = runBlocking {
        val mockContext = MockToolServiceContext()

        // 1. OpenAppTool missing 'name'
        val openAppResultEmpty = OpenAppTool().execute(emptyMap(), mockContext)
        assertTrue(openAppResultEmpty is ToolOutcome.LogicErr)
        val openAppResultBlank = OpenAppTool().execute(mapOf("name" to "   "), mockContext)
        assertTrue(openAppResultBlank is ToolOutcome.TransientErr || openAppResultBlank is ToolOutcome.LogicErr)

        // 2. ClickTool missing 'target'
        val clickResultEmpty = ClickTool().execute(emptyMap(), mockContext)
        assertTrue(clickResultEmpty is ToolOutcome.LogicErr)

        // 3. TypeTextTool missing 'text'
        val typeResultEmpty = TypeTextTool().execute(emptyMap(), mockContext)
        assertTrue(typeResultEmpty is ToolOutcome.LogicErr)

        // 4. SaveFactTool missing 'key' or 'value'
        val saveResultNoKey = SaveFactTool().execute(mapOf("value" to "123"), mockContext)
        assertTrue(saveResultNoKey is ToolOutcome.LogicErr)
        val saveResultNoValue = SaveFactTool().execute(mapOf("key" to "my_key"), mockContext)
        assertTrue(saveResultNoValue is ToolOutcome.LogicErr)

        // 5. RetrieveFactTool missing 'key'
        val retrieveResultNoKey = RetrieveFactTool().execute(emptyMap(), mockContext)
        assertTrue(retrieveResultNoKey is ToolOutcome.LogicErr)
    }

    @Test
    fun testTasksToolDefaultFallbackSummary() = runBlocking {
        val mockContext = MockToolServiceContext()
        val result = TasksTool().execute(emptyMap(), mockContext)
        assertTrue("TasksTool with empty args must succeed with default summary", result is ToolOutcome.Ok)
        assertEquals("Task completed successfully", (result as ToolOutcome.Ok).result)
    }

    @Test
    fun testPromptBuilderWithEmptyInputs() {
        val prompt = PromptBuilder.buildUserTurnPrompt(
            goal = "",
            screenSummary = "",
            history = emptyList(),
            relevantFacts = null
        )

        assertTrue(prompt.contains("USER GOAL: \"\""))
        assertTrue(prompt.contains("CURRENT SCREEN STATE:\n\n"))
        assertFalse(prompt.contains("SAVED USER FACTS"))
        assertFalse(prompt.contains("ACTION HISTORY"))
        assertTrue(prompt.endsWith("What is your next single tool action? (Output JSON only):"))
    }

    @Test
    fun testPromptBuilderWithWhitespaceOnlyFactsIsOmitted() {
        val prompt = PromptBuilder.buildUserTurnPrompt(
            goal = "Take a note",
            screenSummary = "Notes app",
            history = emptyList(),
            relevantFacts = "    \n   "
        )
        assertFalse("Whitespace-only facts must be omitted from prompt", prompt.contains("SAVED USER FACTS"))
    }

    @Test
    fun testPromptBuilderHandlesLargeHistorySequence() {
        val largeHistory = (1..50).map { step ->
            HistoryEntry(
                step = step,
                decision = Decision.Act("scroll", mapOf("direction" to "forward")),
                outcome = ToolOutcome.Ok("Scrolled to item $step")
            )
        }

        val prompt = PromptBuilder.buildUserTurnPrompt(
            goal = "Find item 50",
            screenSummary = "List view",
            history = largeHistory
        )

        assertTrue(prompt.contains("ACTION HISTORY (LAST STEPS):"))
        assertTrue(prompt.contains("- Step 1: scroll({direction=forward}) -> SUCCESS: Scrolled to item 1"))
        assertTrue(prompt.contains("- Step 50: scroll({direction=forward}) -> SUCCESS: Scrolled to item 50"))
    }

    @Test
    fun testRapidConsecutiveEventStreamInSlidingWindow() {
        val manager = SlidingWindowManager(maxRecentEntries = 8)

        // Simulate 100 rapid events with interleaved transient errors and recoveries
        for (i in 1..100) {
            val tool = if (i % 2 == 0) "click" else "scroll"
            val outcome = if (i % 5 == 0) {
                ToolOutcome.TransientErr("Transient UI glitch $i")
            } else {
                ToolOutcome.Ok("Success $i")
            }
            manager.addEntry(
                HistoryEntry(
                    step = i,
                    decision = Decision.Act(tool, mapOf("id" to i)),
                    outcome = outcome
                )
            )
        }

        val window = manager.getWindow()
        assertTrue("Window size must never exceed 8", window.size <= 8)
        assertTrue("Window must not be empty", window.isNotEmpty())

        // Verify ordering: steps should be strictly increasing
        for (idx in 0 until window.size - 1) {
            assertTrue("Steps must be chronologically ordered", window[idx].step < window[idx + 1].step)
        }
    }

    @Test
    fun testExtremePromptContentDoesNotBreakPromptBuilder() {
        val adversarialGoal = "Goal with \"quotes\", \nnew lines, \t tabs, and unicode: \uD83D\uDE80 \uD83E\uDDE0 {json: true}"
        val prompt = PromptBuilder.buildUserTurnPrompt(
            goal = adversarialGoal,
            screenSummary = "Summary with special chars: <xml></xml> & / \\ \" '",
            history = emptyList()
        )

        assertTrue(prompt.contains(adversarialGoal))
        assertTrue(prompt.contains("<xml></xml>"))
    }
}
