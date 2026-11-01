package com.krishna.aegis.tools

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadCalendarTool : AegisTool {
    override val name = "read_calendar"
    override val description = "Reads upcoming events, meetings, and schedules from the device's Google Calendar."
    override val parameterSchema = mapOf(
        "days_ahead" to "Number of days ahead to search (default 1)",
        "query" to "Optional search keyword to filter event titles"
    )

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val appContext = context.androidContext
        if (ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return ToolOutcome.AuthErr("READ_CALENDAR permission has not been granted by user.")
        }

        val daysAhead = (args["days_ahead"]?.toString()?.toIntOrNull() ?: 1).coerceIn(1, 30)
        val filterQuery = args["query"]?.toString()?.trim()?.lowercase()

        val now = System.currentTimeMillis()
        val endTime = now + (daysAhead * 24 * 60 * 60 * 1000L)

        val builder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now - (30 * 60 * 1000L)) // Include events started 30 mins ago
        ContentUris.appendId(builder, endTime)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION
        )

        val eventsArray = JSONArray()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        return try {
            val cursor: Cursor? = appContext.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.use {
                val titleCol = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginCol = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endCol = it.getColumnIndex(CalendarContract.Instances.END)
                val locCol = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val descCol = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)

                while (it.moveToNext()) {
                    val title = it.getString(titleCol) ?: "Untitled Event"
                    if (!filterQuery.isNullOrEmpty() && !title.lowercase().contains(filterQuery)) {
                        continue
                    }

                    val start = it.getLong(beginCol)
                    val end = it.getLong(endCol)
                    val location = it.getString(locCol) ?: ""
                    val description = it.getString(descCol) ?: ""

                    val obj = JSONObject().apply {
                        put("title", title)
                        put("start", dateFormat.format(Date(start)))
                        put("end", dateFormat.format(Date(end)))
                        if (location.isNotEmpty()) put("location", location)
                        if (description.isNotEmpty()) put("description", description)
                    }
                    eventsArray.put(obj)
                }
            }

            if (eventsArray.length() == 0) {
                ToolOutcome.Ok("No upcoming calendar events found for the next $daysAhead day(s).")
            } else {
                ToolOutcome.Ok(eventsArray.toString(2))
            }
        } catch (e: Exception) {
            ToolOutcome.TransientErr("Failed to query CalendarContract: ${e.message}")
        }
    }
}
