package com.krishna.aegis.agent

import com.krishna.aegis.tools.ToolOutcome
import org.junit.Assert.*
import org.junit.Test

class SlidingWindowManagerTest {

    private fun createEntry(step: Int, tool: String, outcome: ToolOutcome, args: Map<String, Any?> = emptyMap()): HistoryEntry {
        return HistoryEntry(
            step = step,
            decision = Decision.Act(tool, args),
            outcome = outcome
        )
    }

    @Test
    fun testWindowBoundedTurnEviction() {
        val manager = SlidingWindowManager(maxRecentEntries = 5)

        for (i in 1..10) {
            manager.addEntry(createEntry(i, "scroll", ToolOutcome.Ok("Scrolled $i")))
        }

        val window = manager.getWindow()
        assertEquals("Window size must be capped at maxRecentEntries = 5", 5, window.size)
        assertEquals(6, window.first().step)
        assertEquals(10, window.last().step)

        val steps = window.map { it.step }
        assertEquals(listOf(6, 7, 8, 9, 10), steps)
    }

    @Test
    fun testDefaultMaxRecentEntriesIsNine() {
        val manager = SlidingWindowManager()

        for (i in 1..15) {
            manager.addEntry(createEntry(i, "tool_$i", ToolOutcome.Ok("Result $i")))
        }

        val window = manager.getWindow()
        assertEquals("Default window size must be 9", 9, window.size)
        assertEquals(7, window.first().step)
        assertEquals(15, window.last().step)
    }

    @Test
    fun testTransientErrorPrunedOnSubsequentSuccess() {
        val manager = SlidingWindowManager(maxRecentEntries = 10)

        // Step 1: Click failed with transient error
        manager.addEntry(createEntry(1, "click", ToolOutcome.TransientErr("Node not visible"), mapOf("target" to "Submit")))
        assertEquals(1, manager.getWindow().size)

        // Step 2: Scroll succeeded
        manager.addEntry(createEntry(2, "scroll", ToolOutcome.Ok("Scrolled forward")))
        assertEquals(2, manager.getWindow().size)

        // Step 3: Click succeeded
        manager.addEntry(createEntry(3, "click", ToolOutcome.Ok("Clicked Submit"), mapOf("target" to "Submit")))

        // Step 1 should have been pruned, leaving Step 2 and Step 3
        val window = manager.getWindow()
        assertEquals(2, window.size)
        assertEquals(2, window[0].step)
        assertEquals("scroll", window[0].decision.toolName)
        assertEquals(3, window[1].step)
        assertEquals("click", window[1].decision.toolName)
    }

    @Test
    fun testNonTransientErrorsAreNotPruned() {
        val manager = SlidingWindowManager(maxRecentEntries = 10)

        // Step 1: Logic error (e.g. invalid arguments)
        manager.addEntry(createEntry(1, "open_app", ToolOutcome.LogicErr("Missing 'name'")))
        // Step 2: Successful open_app
        manager.addEntry(createEntry(2, "open_app", ToolOutcome.Ok("Launched Settings")))

        val window = manager.getWindow()
        assertEquals("Logic errors must not be pruned on success", 2, window.size)
        assertTrue(window.any { it.outcome is ToolOutcome.LogicErr })
        assertTrue(window.any { it.outcome is ToolOutcome.Ok })
    }

    @Test
    fun testDifferentToolSuccessDoesNotPruneUnrelatedTransientError() {
        val manager = SlidingWindowManager(maxRecentEntries = 10)

        manager.addEntry(createEntry(1, "type_text", ToolOutcome.TransientErr("Input not focused")))
        manager.addEntry(createEntry(2, "click", ToolOutcome.Ok("Clicked button")))

        val window = manager.getWindow()
        assertEquals("Success on 'click' must not prune transient error on 'type_text'", 2, window.size)
    }

    @Test
    fun testGetAllSuccessfulActions() {
        val manager = SlidingWindowManager(maxRecentEntries = 10)

        manager.addEntry(createEntry(1, "go_home", ToolOutcome.Ok("Home")))
        manager.addEntry(createEntry(2, "click", ToolOutcome.TransientErr("Missed")))
        manager.addEntry(createEntry(3, "open_app", ToolOutcome.Ok("Launched"), mapOf("name" to "Clock")))
        manager.addEntry(createEntry(4, "save_fact", ToolOutcome.LogicErr("Missing key")))

        val successful = manager.getAllSuccessfulActions()
        assertEquals(2, successful.size)
        assertEquals("go_home", successful[0].toolName)
        assertEquals("open_app", successful[1].toolName)
    }

    @Test
    fun testClearEmptiesWindow() {
        val manager = SlidingWindowManager(maxRecentEntries = 5)
        manager.addEntry(createEntry(1, "go_home", ToolOutcome.Ok("Home")))
        manager.addEntry(createEntry(2, "go_back", ToolOutcome.Ok("Back")))
        assertEquals(2, manager.getWindow().size)

        manager.clear()
        assertTrue("Window must be empty after clear()", manager.getWindow().isEmpty())
        assertTrue("Successful actions must be empty after clear()", manager.getAllSuccessfulActions().isEmpty())
    }
}
