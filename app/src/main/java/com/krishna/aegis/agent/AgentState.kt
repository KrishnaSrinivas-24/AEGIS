package com.krishna.aegis.agent

import com.krishna.aegis.tools.ToolOutcome

sealed class Decision {
    data class Act(val toolName: String, val args: Map<String, Any?>) : Decision()
    data class Finish(val summary: String) : Decision()
}

data class HistoryEntry(
    val step: Int,
    val decision: Decision.Act,
    val outcome: ToolOutcome,
    val timestamp: Long = System.currentTimeMillis()
)
