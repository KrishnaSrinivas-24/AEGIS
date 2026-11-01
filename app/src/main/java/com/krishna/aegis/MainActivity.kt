package com.krishna.aegis

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.krishna.aegis.engine.AegisModelConfig
import com.krishna.aegis.service.AegisAccessibilityService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AegisDashboardScreen(onOpenAccessibilitySettings: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isAccessibilityEnabled by remember {
        mutableStateOf(checkAccessibilityServiceEnabled(context))
    }

    var isModelInstalled by remember {
        mutableStateOf(AegisModelConfig.isModelPresent(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
    }

    // Refresh status when returning to foreground
    DisposableEffect(Unit) {
        val listener = {
            isAccessibilityEnabled = checkAccessibilityServiceEnabled(context)
            isModelInstalled = AegisModelConfig.isModelPresent(context)
        }
        listener()
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(top = 16.dp)) {
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
            actionLabel = if (isAccessibilityEnabled) "Configured" else "Enable Service",
            onAction = onOpenAccessibilitySettings
        )

        // Status Card 2: Audio & Wake-Word
        StatusPanel(
            title = "Offline Wake-Word (Vosk 16kHz)",
            subtitle = if (hasMicPermission) "Continuous listener armed for 'Gemma'" else "Microphone permission required",
            isReady = hasMicPermission,
            actionLabel = if (hasMicPermission) "Granted" else "Grant Mic",
            onAction = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        )

        // Status Card 3: Gemma 4 Brain (LiteRT-LM)
        val modelFile = AegisModelConfig.getModelFile(context)
        val modelSizeMb = if (modelFile.exists()) (modelFile.length() / (1024 * 1024)).toString() + " MB" else "Not Found"
        StatusPanel(
            title = "Gemma 4 LiteRT-LM (${AegisModelConfig.DEFAULT_MODEL_NAME})",
            subtitle = if (isModelInstalled) "Ready on GPU ($modelSizeMb)" else "Push model via ADB: /sdcard/Android/data/.../files/models/",
            isReady = isModelInstalled,
            actionLabel = if (isModelInstalled) "Installed" else "Check Path",
            onAction = { isModelInstalled = AegisModelConfig.isModelPresent(context) }
        )

        Spacer(modifier = Modifier.weight(1f))

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
                    text = "• Say \"Gemma\" to wake the sentinel.\n• State your task (e.g. \"Open Settings and turn on Wi-Fi\").\n• Sensitive operations trigger biometric fingerprint verification.\n• Successful workflows are cached in Room DB for sub-second replay.",
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
                .padding(16.dp),
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
                enabled = !isReady,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    disabledContainerColor = Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isReady) Color(0xFF94A3B8) else Color.Black
                )
            }
        }
    }
}

private fun checkAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    val expectedId = "${context.packageName}/${AegisAccessibilityService::class.java.canonicalName}"
    return enabledServices.any { it.id.equals(expectedId, ignoreCase = true) }
}
