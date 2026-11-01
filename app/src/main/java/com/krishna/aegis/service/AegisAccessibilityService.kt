package com.krishna.aegis.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.krishna.aegis.agent.AgentOrchestrator
import com.krishna.aegis.agent.Planner
import com.krishna.aegis.engine.AegisEngine
import com.krishna.aegis.telemetry.AegisEvent
import com.krishna.aegis.telemetry.EventLogger
import com.krishna.aegis.tools.ToolServiceContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException
import java.util.Locale

class AegisAccessibilityService : AccessibilityService(), ToolServiceContext, VoskRecognitionListener, RecognitionListener, TextToSpeech.OnInitListener {

    companion object {
        var instance: AegisAccessibilityService? = null
            private set
        val isRunning: Boolean get() = instance != null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val awarenessTracker = AccessibilityAwarenessTracker()

    private lateinit var aegisEngine: AegisEngine
    private lateinit var planner: Planner
    private lateinit var agentOrchestrator: AgentOrchestrator

    // Voice Stack
    private var voskModel: Model? = null
    private var voskSpeechService: SpeechService? = null
    private var androidSpeechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    override val androidContext: Context
        get() = this

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        EventLogger.log(AegisEvent.VoiceState("AEGIS Accessibility Service Connected"))

        // Initialize Engine & Orchestrator
        aegisEngine = AegisEngine(this)
        planner = Planner(this, aegisEngine)
        agentOrchestrator = AgentOrchestrator(this, planner)

        serviceScope.launch {
            aegisEngine.initialize()
        }

        // Initialize TTS
        textToSpeech = TextToSpeech(this, this)

        // Initialize SpeechRecognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            androidSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            androidSpeechRecognizer?.setRecognitionListener(this)
        }

        // Initialize Vosk Wake Word Model from assets
        initVoskModel()
    }

    private fun initVoskModel() {
        StorageService.unpack(this, "model-en-us", "model",
            { model ->
                voskModel = model
                startVoskListening()
            },
            { exception ->
                EventLogger.log(AegisEvent.VoiceState("Vosk unpack error: ${exception.message}"))
            }
        )
    }

    private fun startVoskListening() {
        val model = voskModel ?: return
        try {
            val rec = Recognizer(model, 16000.0f)
            voskSpeechService = SpeechService(rec, 16000.0f)
            voskSpeechService?.startListening(this)
            EventLogger.log(AegisEvent.VoiceState("Vosk listening for wake-word 'Gemma' (16kHz)"))
        } catch (e: IOException) {
            EventLogger.log(AegisEvent.VoiceState("Failed to start Vosk: ${e.message}"))
        }
    }

    private fun stopVoskListening() {
        voskSpeechService?.stop()
        voskSpeechService?.shutdown()
        voskSpeechService = null
    }

    // --- Vosk Wake-Word Listener ---
    override fun onPartialResult(hypothesis: String?) {
        val partial = JSONObject(hypothesis ?: "{}").optString("partial", "").lowercase()
        if (partial.contains("gemma")) {
            EventLogger.log(AegisEvent.VoiceState("Wake-word 'Gemma' recognized! Handoff to Android STT."))
            stopVoskListening()
            Toast.makeText(this, "AEGIS: Listening...", Toast.LENGTH_SHORT).show()
            startAndroidCommandCapture()
        }
    }

    override fun onResult(hypothesis: String?) {}
    override fun onFinalResult(hypothesis: String?) {}
    override fun onError(exception: java.lang.Exception?) {
        EventLogger.log(AegisEvent.VoiceState("Vosk error: ${exception?.message}"))
    }
    override fun onTimeout() {}

    // --- Android SpeechRecognizer Command Capture ---
    private fun startAndroidCommandCapture() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        androidSpeechRecognizer?.startListening(intent)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val command = matches?.firstOrNull()?.trim()

        if (!command.isNullOrEmpty()) {
            EventLogger.log(AegisEvent.VoiceState("User intent captured: \"$command\""))
            Toast.makeText(this, "Executing: $command", Toast.LENGTH_SHORT).show()

            serviceScope.launch {
                val summary = agentOrchestrator.executeGoal(command)
                speakAndReset(summary)
            }
        } else {
            startVoskListening()
        }
    }

    override fun onError(error: Int) {
        EventLogger.log(AegisEvent.VoiceState("SpeechRecognizer error code: $error"))
        startVoskListening()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    // --- TextToSpeech Feedback ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    // Once speaking is completed, restart wake-word listening loop
                    serviceScope.launch(Dispatchers.Main) {
                        startVoskListening()
                    }
                }
                override fun onError(utteranceId: String?) {
                    serviceScope.launch(Dispatchers.Main) {
                        startVoskListening()
                    }
                }
            })
            isTtsReady = true
        }
    }

    private fun speakAndReset(message: String) {
        if (isTtsReady && textToSpeech != null) {
            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_FINISH")
        } else {
            startVoskListening()
        }
    }

    // --- Accessibility Event Handling ---
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            awarenessTracker.onAccessibilityEvent(event)
        }
    }

    override fun onInterrupt() {
        stopVoskListening()
    }

    override fun onDestroy() {
        instance = null
        stopVoskListening()
        androidSpeechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    // --- ToolServiceContext Implementation ---
    override fun getRootInActiveWindow(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    override fun getActivePackageName(): String {
        return awarenessTracker.getCurrentPackage().ifEmpty {
            rootInActiveWindow?.packageName?.toString() ?: ""
        }
    }

    override suspend fun dispatchClick(node: AccessibilityNodeInfo): Boolean {
        return if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // Tap center coordinates of the node bounding box as fallback
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            GestureDispatcher.dispatchTap(this, rect.centerX().toFloat(), rect.centerY().toFloat())
        }
    }

    override suspend fun dispatchSetText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override suspend fun dispatchScroll(forward: Boolean): Boolean {
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val root = rootInActiveWindow
        val scrollableNode = findScrollableNode(root)
        return if (scrollableNode != null) {
            scrollableNode.performAction(action)
        } else {
            val metrics = resources.displayMetrics
            val cx = metrics.widthPixels / 2f
            val startY = if (forward) metrics.heightPixels * 0.75f else metrics.heightPixels * 0.25f
            val endY = if (forward) metrics.heightPixels * 0.25f else metrics.heightPixels * 0.75f
            GestureDispatcher.dispatchSwipe(this, cx, startY, cx, endY)
        }
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val found = findScrollableNode(root.getChild(i))
            if (found != null) return found
        }
        return null
    }

    override suspend fun dispatchGlobalBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override suspend fun dispatchGlobalHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override suspend fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun resolvePackageForApp(appName: String): String? {
        val lower = appName.trim().lowercase()
        val pm = packageManager
        val packages = pm.getInstalledApplications(0)

        // 1. Check exact package match
        if (packages.any { it.packageName.lowercase() == lower }) {
            return lower
        }

        // 2. Check application labels
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label == lower || label.contains(lower)) {
                return app.packageName
            }
        }

        // 3. Fallback well-known aliases
        return when (lower) {
            "settings" -> "com.android.settings"
            "chrome", "browser" -> "com.android.chrome"
            "calculator" -> "com.google.android.calculator"
            "camera" -> "com.google.android.GoogleCamera"
            "clock" -> "com.google.android.deskclock"
            "contacts" -> "com.google.android.contacts"
            "youtube" -> "com.google.android.youtube"
            "maps" -> "com.google.android.apps.maps"
            else -> null
        }
    }

    override suspend fun takeScreenCapture(): Bitmap? {
        return ScreenCapture.capture(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}
