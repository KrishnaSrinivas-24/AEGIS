package com.krishna.aegis.agent

import com.krishna.aegis.tools.ToolOutcome

class SlidingWindowManager(private val maxRecentEntries: Int = 9) {
    private val entries = mutableListOf<HistoryEntry>()

    fun addEntry(entry: HistoryEntry) {
        entries.add(entry)
        pruneTransientErrors(entry)
    }

    private fun pruneTransientErrors(latestEntry: HistoryEntry) {
        if (latestEntry.outcome is ToolOutcome.Ok) {
            val tool = latestEntry.decision.toolName
            // If the latest attempt succeeded, remove earlier failed transient attempts for this same tool
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val current = iterator.next()
                if (current != latestEntry && current.decision.toolName == tool && current.outcome is ToolOutcome.TransientErr) {
                    iterator.remove()
                }
            }
        }
    }

    fun getWindow(): List<HistoryEntry> {
        if (entries.size <= maxRecentEntries) return entries.toList()
        return entries.takeLast(maxRecentEntries)
    }

    fun getAllSuccessfulActions(): List<Decision.Act> {
        return entries
            .filter { it.outcome is ToolOutcome.Ok }
            .map { it.decision }
    }

    fun clear() {
        entries.clear()
    }
}
