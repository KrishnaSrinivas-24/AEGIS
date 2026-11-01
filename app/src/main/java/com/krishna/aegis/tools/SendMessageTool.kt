package com.krishna.aegis.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.net.URLEncoder

class SendMessageTool : AegisTool {
    override val name = "send_message"
    override val description = "Sends a message to a recipient via WhatsApp or SMS. Supports phone numbers and automatic text dispatch."
    override val parameterSchema = mapOf(
        "platform" to "Messaging platform ('whatsapp', 'sms', or 'auto'). Defaults to 'whatsapp'",
        "recipient" to "Phone number with country code (e.g. '+919876543210') or recipient name",
        "message" to "Text content of the message to send"
    )

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val message = args["message"]?.toString()?.trim()
            ?: return ToolOutcome.LogicErr("Missing required argument: 'message'")
        val recipient = args["recipient"]?.toString()?.trim() ?: ""
        val platform = args["platform"]?.toString()?.lowercase()?.trim() ?: "whatsapp"

        val appContext = context.androidContext
        val pm = appContext.packageManager

        // Clean recipient numbers
        val cleanDigits = recipient.filter { it.isDigit() }

        return when {
            platform == "whatsapp" || (platform == "auto" && isPackageInstalled(pm, "com.whatsapp")) -> {
                sendWhatsApp(appContext, cleanDigits, recipient, message)
            }
            platform == "sms" || platform == "auto" -> {
                sendSms(appContext, cleanDigits.ifEmpty { recipient }, message)
            }
            else -> {
                ToolOutcome.LogicErr("Unsupported messaging platform: '$platform'. Use 'whatsapp' or 'sms'.")
            }
        }
    }

    private fun sendWhatsApp(context: Context, cleanPhone: String, rawRecipient: String, message: String): ToolOutcome {
        val encodedMessage = try {
            URLEncoder.encode(message, "UTF-8")
        } catch (e: Exception) {
            message
        }

        val intent = if (cleanPhone.length >= 7) {
            // Direct chat open with pre-filled message
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage")
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            // General share to WhatsApp contact
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return try {
            context.startActivity(intent)
            val destination = if (cleanPhone.isNotEmpty()) cleanPhone else rawRecipient.ifEmpty { "WhatsApp" }
            ToolOutcome.Ok("Opened WhatsApp conversation for $destination with pre-filled message: \"$message\"")
        } catch (e: Exception) {
            ToolOutcome.TransientErr("Failed to launch WhatsApp: ${e.localizedMessage}")
        }
    }

    private fun sendSms(context: Context, recipient: String, message: String): ToolOutcome {
        val uri = if (recipient.isNotEmpty()) {
            Uri.parse("smsto:$recipient")
        } else {
            Uri.parse("smsto:")
        }

        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            ToolOutcome.Ok("Opened SMS composer for recipient '$recipient' with message: \"$message\"")
        } catch (e: Exception) {
            ToolOutcome.TransientErr("Failed to launch SMS composer: ${e.localizedMessage}")
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
