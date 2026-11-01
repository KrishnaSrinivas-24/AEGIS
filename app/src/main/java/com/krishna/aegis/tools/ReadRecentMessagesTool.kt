package com.krishna.aegis.tools

import com.krishna.aegis.data.AegisDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadRecentMessagesTool : AegisTool {
    override val name = "read_recent_messages"
    override val description = "Retrieves recent buffered WhatsApp messages and Gmail snippets captured by the background sentinel."
    override val parameterSchema = mapOf(
        "app" to "Filter by application: 'whatsapp', 'gmail', or 'all' (default: 'all')",
        "query" to "Optional search keyword to match sender or message content",
        "limit" to "Maximum number of messages to return (default 5)"
    )

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val app = args["app"]?.toString()?.trim()?.lowercase() ?: "all"
        val query = args["query"]?.toString()?.trim()
        val limit = (args["limit"]?.toString()?.toIntOrNull() ?: 5).coerceIn(1, 20)

        val db = AegisDatabase.getInstance(context.androidContext)
        val notifications = when {
            !query.isNullOrEmpty() -> db.notificationDao().searchNotifications(query, limit)
            app == "whatsapp" -> db.notificationDao().getRecentByPackage("com.whatsapp", limit)
            app == "gmail" -> db.notificationDao().getRecentByPackage("com.google.android.gm", limit)
            else -> db.notificationDao().getLatest(limit)
        }

        if (notifications.isEmpty()) {
            return ToolOutcome.Ok("No recent buffered messages found matching criteria.")
        }

        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val array = JSONArray()
        for (item in notifications) {
            array.put(
                JSONObject().apply {
                    put("app", item.appTitle)
                    put("sender", item.sender)
                    put("content", item.content)
                    put("time", dateFormat.format(Date(item.timestamp)))
                }
            )
        }
        return ToolOutcome.Ok(array.toString(2))
    }
}
