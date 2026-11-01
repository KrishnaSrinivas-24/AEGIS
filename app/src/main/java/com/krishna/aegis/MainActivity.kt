package com.krishna.aegis

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.krishna.aegis.engine.AegisModelConfig
import com.krishna.aegis.service.AegisAccessibilityService
import com.krishna.aegis.service.AegisSentinelService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start background keep-alive sentinel service
        try {
            AegisSentinelService.start(this)
        } catch (_: Exception) {}

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0D0F12),
                    surface = Color(0xFF161920),
                    primary = Color(0xFF00E5FF),
                    onPrimary = Color.Black,
                    onSurface = Color(0xFFE2E8F0)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AegisDashboardScreen(
                        onOpenAccessibilitySettings = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            startActivity(intent)
                        },
                        onOpenOverlaySettings = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            }
                        },
                        onOpenNotificationListenerSettings = {
                            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AegisDashboardScreen(
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasCalendarPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }

    var isAccessibilityEnabled by remember {
        mutableStateOf(checkAccessibilityServiceEnabled(context))
    }

    var isModelInstalled by remember {
        mutableStateOf(AegisModelConfig.isModelPresent(context))
    }

    var detectedModelName by remember {
        val f = AegisModelConfig.getModelFile(context)
        mutableStateOf(if (f.exists()) f.name else AegisModelConfig.DEFAULT_MODEL_NAME)
    }

    var modelSizeMb by remember {
        val f = AegisModelConfig.getModelFile(context)
        mutableStateOf(if (f.exists()) "${f.length() / (1024 * 1024)} MB" else "Not Found")
    }

    fun refreshAllStatus() {
        hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasCalendarPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        isAccessibilityEnabled = checkAccessibilityServiceEnabled(context)
        isModelInstalled = AegisModelConfig.isModelPresent(context)
        val f = AegisModelConfig.getModelFile(context)
        detectedModelName = if (f.exists()) f.name else AegisModelConfig.DEFAULT_MODEL_NAME
        modelSizeMb = if (f.exists()) "${f.length() / (1024 * 1024)} MB" else "Not Found"
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasMicPermission = result[Manifest.permission.RECORD_AUDIO] ?: hasMicPermission
        hasCalendarPermission = result[Manifest.permission.READ_CALENDAR] ?: hasCalendarPermission
        refreshAllStatus()
    }

    // Refresh status automatically whenever user returns to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAllStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "A.E.G.I.S.",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Text(
                text = "Autonomous Edge-Guided Interface Sentinel",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8)
            )
        }

        HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

        // Status Card 1: Accessibility Service
        StatusPanel(
            title = "OS Accessibility Core",
            subtitle = if (isAccessibilityEnabled) "Active & Inspecting UI Tree" else "Disabled — Required for autonomous actions",
            isReady = isAccessibilityEnabled,
            actionLabel = if (isAccessibilityEnabled) "Active" else "Enable",
            onAction = {
                if (!isAccessibilityEnabled) onOpenAccessibilitySettings() else refreshAllStatus()
            }
        )

        // Status Card 2: Audio & Wake-Word
        StatusPanel(
            title = "Offline Wake-Word (Vosk 16kHz)",
            subtitle = if (hasMicPermission) "Continuous listener armed for 'Gemma'" else "Microphone permission required",
            isReady = hasMicPermission,
            actionLabel = if (hasMicPermission) "Armed" else "Grant Mic",
            onAction = {
                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionsLauncher.launch(perms.toTypedArray())
            }
        )

        // Status Card 3: Gemma 4 Brain (LiteRT-LM)
        StatusPanel(
            title = "Gemma 4 LiteRT-LM ($detectedModelName)",
            subtitle = if (isModelInstalled) "Ready on GPU ($modelSizeMb)" else "Push model via ADB: /sdcard/Android/data/.../files/models/",
            isReady = isModelInstalled,
            actionLabel = if (isModelInstalled) "Ready ($modelSizeMb)" else "Rescan",
            onAction = { refreshAllStatus() }
        )

        // Status Card 4: Floating Sentinel HUD (Over Other Apps)
        StatusPanel(
            title = "Floating Sentinel HUD",
            subtitle = if (hasOverlayPermission) "Active over other apps (Stark HUD Overlay)" else "Permission required to draw over apps",
            isReady = hasOverlayPermission,
            actionLabel = if (hasOverlayPermission) "Active" else "Enable HUD",
            onAction = {
                if (!hasOverlayPermission) onOpenOverlaySettings() else AegisSentinelService.start(context)
            }
        )

        // Status Card 5: Google Calendar & Context
        StatusPanel(
            title = "Calendar & Messages Context",
            subtitle = if (hasCalendarPermission) "Connected: CalendarContract & Message Buffers" else "Grant Calendar permission for agenda context",
            isReady = hasCalendarPermission,
            actionLabel = if (hasCalendarPermission) "Connected" else "Grant Perm",
            onAction = {
                permissionsLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
            }
        )

        // System Maintenance / Cache Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val coroutineScope = rememberCoroutineScope()
            OutlinedButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            com.krishna.aegis.data.AegisDatabase.getInstance(context).skillDao().clearAllSkills()
                        } catch (_: Exception) {}
                    }
                    android.widget.Toast.makeText(context, "Skill Cache Cleared", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
            ) {
                Text("Clear Cache", color = Color(0xFFEF4444), fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = { onOpenNotificationListenerSettings() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
            ) {
                Text("Notification Access", color = Color(0xFF00E5FF), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Telemetry info panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF11141A), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "OPERATIONAL PROTOCOL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "• Say \"Gemma\" or tap the floating Stark HUD to activate.\n• Context tools: \"What's on my calendar today?\", \"Read recent WhatsApp messages\", \"Save to Notes\".\n• Background keep-alive service maintains continuous OS sentinel status.",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun StatusPanel(
    title: String,
    subtitle: String,
    isReady: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isReady) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isReady) Color(0xFF10B981) else Color(0xFFEF4444),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Button(
                onClick = onAction,
                enabled = true,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReady) Color(0xFF10B981) else Color(0xFF00E5FF),
                    disabledContainerColor = Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }
        }
    }
}

private fun checkAccessibilityServiceEnabled(context: Context): Boolean {
    if (AegisAccessibilityService.isRunning) return true

    try {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        if (enabledServices.contains(context.packageName, ignoreCase = true)) {
            return true
        }
    } catch (_: Exception) {}

    return try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        enabledServices.any {
            it.id.contains(context.packageName, ignoreCase = true) ||
            it.resolveInfo?.serviceInfo?.packageName.equals(context.packageName, ignoreCase = true)
        }
    } catch (_: Exception) {
        false
    }
}
