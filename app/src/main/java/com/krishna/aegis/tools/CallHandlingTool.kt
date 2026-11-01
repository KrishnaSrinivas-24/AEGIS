package com.krishna.aegis.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallHandlingTool : AegisTool {
    override val name = "call_handling"
    override val description = "Handles phone calls: places direct calls, opens the dialer with a pre-filled number, or reads recent call history."
    override val parameterSchema = mapOf(
        "action" to "Action to perform: 'call' (direct call), 'dial' (open dialer), or 'recent_calls' (inspect recent call history)",
        "phoneNumber" to "Phone number to call or dial (required for 'call' and 'dial')",
        "limit" to "Maximum number of recent calls to return (defaults to 5, used for 'recent_calls')"
    )

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val action = args["action"]?.toString()?.lowercase()?.trim() ?: "dial"
        val phoneNumber = args["phoneNumber"]?.toString()?.trim() ?: ""
        val limit = (args["limit"] as? Number)?.toInt() ?: 5

        val appContext = context.androidContext

        return when (action) {
            "call" -> {
                if (phoneNumber.isEmpty()) {
                    return ToolOutcome.LogicErr("Missing 'phoneNumber' argument for action 'call'.")
                }
                makePhoneCall(appContext, phoneNumber)
            }
            "dial" -> {
                if (phoneNumber.isEmpty()) {
                    return ToolOutcome.LogicErr("Missing 'phoneNumber' argument for action 'dial'.")
                }
                openDialer(appContext, phoneNumber)
            }
            "recent_calls" -> {
                readRecentCalls(appContext, limit)
            }
            else -> {
                ToolOutcome.LogicErr("Unknown call action: '$action'. Use 'call', 'dial', or 'recent_calls'.")
            }
        }
    }

    private fun makePhoneCall(context: Context, phoneNumber: String): ToolOutcome {
        val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            val intent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            if (hasCallPermission) {
                ToolOutcome.Ok("Initiated direct phone call to $cleanNumber")
            } else {
                ToolOutcome.Ok("Opened phone dialer with number $cleanNumber (CALL_PHONE permission not yet granted; dialer activated)")
            }
        } catch (e: Exception) {
            ToolOutcome.TransientErr("Failed to execute phone call: ${e.localizedMessage}")
        }
    }

    private fun openDialer(context: Context, phoneNumber: String): ToolOutcome {
        val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolOutcome.Ok("Opened dialer pre-populated with $cleanNumber")
        } catch (e: Exception) {
            ToolOutcome.TransientErr("Failed to open dialer: ${e.localizedMessage}")
        }
    }

    private fun readRecentCalls(context: Context, limit: Int): ToolOutcome {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return ToolOutcome.TransientErr(
                "Permission READ_CALL_LOG not granted. Grant via: adb shell pm grant ${context.packageName} android.permission.READ_CALL_LOG"
            )
        }

        val calls = mutableListOf<String>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val number = if (numberIdx >= 0) it.getString(numberIdx) ?: "Unknown" else "Unknown"
                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                    val typeCode = if (typeIdx >= 0) it.getInt(typeIdx) else 0
                    val dateMillis = if (dateIdx >= 0) it.getLong(dateIdx) else 0L
                    val durationSec = if (durIdx >= 0) it.getLong(durIdx) else 0L

                    val typeStr = when (typeCode) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        CallLog.Calls.REJECTED_TYPE -> "Rejected"
                        else -> "Call"
                    }

                    val dateStr = if (dateMillis > 0) dateFormat.format(Date(dateMillis)) else "Unknown time"
                    val callerDisplay = if (name.isNotEmpty()) "$name ($number)" else number

                    calls.add("[$typeStr] $callerDisplay at $dateStr (Duration: ${durationSec}s)")
                    count++
                }
            }
        } catch (e: Exception) {
            return ToolOutcome.TransientErr("Failed to query CallLog: ${e.localizedMessage}")
        }

        return if (calls.isEmpty()) {
            ToolOutcome.Ok("Call history is empty.")
        } else {
            ToolOutcome.Ok("Recent Call History (${calls.size} calls):\n" + calls.joinToString("\n"))
        }
    }
}
