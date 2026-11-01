package com.krishna.aegis.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.krishna.aegis.data.AegisDatabase
import com.krishna.aegis.data.BufferedNotification
import com.krishna.aegis.telemetry.AegisEvent
import com.krishna.aegis.telemetry.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AegisNotificationListenerService : NotificationListenerService() {
    private val TAG = "AegisNotificationListener"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        // Monitor key context applications (WhatsApp, Gmail, Messages, Slack, Telegram)
        if (pkg != "com.whatsapp" && pkg != "com.google.android.gm" && !pkg.contains("messaging")) {
            return
        }

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()

        val content = if (!bigText.isNullOrEmpty()) bigText else text
        if (content.isEmpty() && title.isEmpty()) return

        val appTitle = when (pkg) {
            "com.whatsapp" -> "WhatsApp"
            "com.google.android.gm" -> "Gmail"
            else -> pkg
        }

        serviceScope.launch {
            try {
                val db = AegisDatabase.getInstance(applicationContext)
                val notification = BufferedNotification(
                    packageName = pkg,
                    appTitle = appTitle,
                    sender = title,
                    content = content,
                    timestamp = sbn.postTime
                )
                db.notificationDao().insertNotification(notification)
                EventLogger.log(AegisEvent.VoiceState("Buffered $appTitle notification from '$title'"))
                Log.i(TAG, "Buffered notification from $appTitle: $title - ${content.take(40)}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist notification: ${e.message}")
            }
        }
    }
}
