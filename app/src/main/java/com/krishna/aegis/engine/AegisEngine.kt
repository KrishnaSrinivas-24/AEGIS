package com.krishna.aegis.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.krishna.aegis.agent.PromptBuilder
import com.krishna.aegis.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

sealed class EngineTurnResult {
    data class Text(val content: String) : EngineTurnResult()
    data class ToolCall(val name: String, val arguments: Map<String, Any?>) : EngineTurnResult()
    data class Error(val message: String) : EngineTurnResult()
}

sealed class AgentResponse {
    data class Token(val text: String) : AgentResponse()
    data class ToolCallRequest(val toolName: String, val args: Map<String, Any?>) : AgentResponse()
    object Done : AgentResponse()
    data class Error(val reason: String, val isTransient: Boolean) : AgentResponse()
}

class AegisEngine(private val context: Context) : AutoCloseable {
    private val TAG = "AegisEngine"

    @Volatile
    private var isInitialized = false
    private var modelFile: File? = null

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun initialize(modelName: String = AegisModelConfig.DEFAULT_MODEL_NAME): Boolean = withContext(Dispatchers.IO) {
        val file = AegisModelConfig.getModelFile(context, modelName)
        if (!file.exists() || file.length() < 50_000_000L) {
            Log.w(TAG, "Model file not found or incomplete at ${file.absolutePath}. Waiting for download or adb push.")
            return@withContext false
        }
        modelFile = file

        return@withContext try {
            val cacheDir = context.cacheDir.absolutePath

            // Attempt GPU backend first, fallback to CPU on initialization error
            val activeEngine = try {
                Log.i(TAG, "Initializing LiteRT-LM Engine with Backend.GPU() on model: ${file.absolutePath}")
                val gpuConfig = EngineConfig(
                    modelPath = file.absolutePath,
                    backend = Backend.GPU(),
                    cacheDir = cacheDir
                )
                Engine(gpuConfig).also { it.initialize() }
            } catch (gpuError: Throwable) {
                Log.w(TAG, "Backend.GPU() failed (${gpuError.message}). Falling back to Backend.CPU()...", gpuError)
                val cpuConfig = EngineConfig(
                    modelPath = file.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = cacheDir
                )
                Engine(cpuConfig).also { it.initialize() }
            }

            this@AegisEngine.engine = activeEngine

            // Configure Conversation with native tools and automaticToolCalling = false
            setupConversation(activeEngine)

            isInitialized = true
            Log.i(TAG, "LiteRT-LM Engine & Conversation initialized successfully.")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize LiteRT-LM engine: ${e.message}", e)
            close()
            false
        }
    }

    private fun setupConversation(activeEngine: Engine) {
        conversation?.close()

        val toolProviders = ToolRegistry.getLiteRtToolProviders()
        val samplerConfig = SamplerConfig(
            topK = AegisModelConfig.TOP_K,
            topP = 0.95,
            temperature = AegisModelConfig.TEMPERATURE.toDouble(),
            seed = 0
        )
        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(PromptBuilder.buildSystemPrompt()),
            tools = toolProviders,
            samplerConfig = samplerConfig,
            automaticToolCalling = false
        )
        conversation = activeEngine.createConversation(conversationConfig)
    }

    fun resetConversation() {
        val activeEngine = engine
        if (activeEngine != null) {
            try {
                setupConversation(activeEngine)
                Log.i(TAG, "AegisEngine conversation reset for new turn sequence.")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting conversation: ${e.message}", e)
            }
        }
    }

    suspend fun sendUserPrompt(prompt: String): EngineTurnResult = withContext(Dispatchers.Default) {
        val conv = conversation
            ?: return@withContext EngineTurnResult.Error("Engine / Conversation is not initialized.")

        try {
            Log.i(TAG, "Sending user prompt to LiteRT-LM (length: ${prompt.length})")
            val message = conv.sendMessage(prompt)
            handleModelMessage(message)
        } catch (e: Throwable) {
            Log.e(TAG, "LiteRT-LM prompt inference failed: ${e.message}", e)
            EngineTurnResult.Error("Inference error: ${e.message}")
        }
    }

    suspend fun sendToolResult(toolName: String, result: String): EngineTurnResult = withContext(Dispatchers.Default) {
        val conv = conversation
            ?: return@withContext EngineTurnResult.Error("Engine / Conversation is not initialized.")

        try {
            Log.i(TAG, "Sending tool response back to LiteRT-LM: tool=$toolName, length=${result.length}")
            val toolResponseContent = Content.ToolResponse(name = toolName, response = result)
            val toolMessage = Message.tool(Contents.of(toolResponseContent))
            val message = conv.sendMessage(toolMessage)
            handleModelMessage(message)
        } catch (e: Throwable) {
            Log.e(TAG, "LiteRT-LM tool response failed: ${e.message}", e)
            EngineTurnResult.Error("Tool result processing error: ${e.message}")
        }
    }

    private fun handleModelMessage(message: Message): EngineTurnResult {
        val toolCalls = message.toolCalls
        if (!toolCalls.isNullOrEmpty()) {
            val firstCall = toolCalls[0]
            Log.i(TAG, "LiteRT-LM native ToolCall emitted: name=${firstCall.name}, args=${firstCall.arguments}")
            val argsMap = firstCall.arguments ?: emptyMap()
            return EngineTurnResult.ToolCall(
                name = firstCall.name,
                arguments = argsMap
            )
        }

        val textContents = message.contents?.contents
            ?.filterIsInstance<Content.Text>()
            ?.joinToString("\n") { it.text }

        val finalContent = if (!textContents.isNullOrBlank()) {
            textContents
        } else {
            message.toString()
        }

        // Backward compatibility fallback: check if response text contains raw JSON tool call
        val fallback = parseToolCall(finalContent)
        if (fallback is AgentResponse.ToolCallRequest) {
            Log.i(TAG, "Extracted fallback JSON ToolCall from text: ${fallback.toolName}")
            return EngineTurnResult.ToolCall(fallback.toolName, fallback.args)
        }

        return EngineTurnResult.Text(finalContent)
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.Default) {
        when (val result = sendUserPrompt(prompt)) {
            is EngineTurnResult.Text -> result.content
            is EngineTurnResult.ToolCall -> {
                val argsJson = JSONObject(result.arguments).toString()
                """{"tool": "${result.name}", "args": $argsJson}"""
            }
            is EngineTurnResult.Error -> """{"error": "${result.message}"}"""
        }
    }

    fun parseToolCall(responseJson: String): AgentResponse {
        return try {
            val cleanJson = extractJson(responseJson)
            val obj = JSONObject(cleanJson)
            val tool = obj.optString("tool", "")
            val argsObj = obj.optJSONObject("args")
            val argsMap = mutableMapOf<String, Any?>()
            if (argsObj != null) {
                val keys = argsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    argsMap[k] = argsObj.get(k)
                }
            }
            if (tool.isNotEmpty()) {
                AgentResponse.ToolCallRequest(tool, argsMap)
            } else {
                AgentResponse.Error("Empty tool name in response: $responseJson", isTransient = true)
            }
        } catch (e: Exception) {
            AgentResponse.Error("Failed to parse tool call JSON: ${e.message}", isTransient = true)
        }
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) {
            text.substring(start, end + 1)
        } else {
            text.trim()
        }
    }

    override fun close() {
        try {
            conversation?.close()
        } catch (_: Exception) {}
        conversation = null

        try {
            engine?.close()
        } catch (_: Exception) {}
        engine = null

        isInitialized = false
    }

    fun isReady(): Boolean = isInitialized && conversation != null
}
