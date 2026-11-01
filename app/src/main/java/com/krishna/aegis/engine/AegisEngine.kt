package com.krishna.aegis.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

sealed class AgentResponse {
    data class Token(val text: String) : AgentResponse()
    data class ToolCallRequest(val toolName: String, val args: Map<String, Any?>) : AgentResponse()
    object Done : AgentResponse()
    data class Error(val reason: String, val isTransient: Boolean) : AgentResponse()
}

class AegisEngine(private val context: Context) {
    private val TAG = "AegisEngine"
    private var isInitialized = false
    private var modelFile: File? = null

    suspend fun initialize(modelName: String = AegisModelConfig.DEFAULT_MODEL_NAME): Boolean = withContext(Dispatchers.IO) {
        val file = AegisModelConfig.getModelFile(context, modelName)
        if (!file.exists()) {
            Log.w(TAG, "Model file not found at ${file.absolutePath}. Waiting for download or adb push.")
            return@withContext false
        }
        modelFile = file

        try {
            val engineConfigClass = try {
                Class.forName("com.google.ai.edge.litert.lm.EngineConfig")
            } catch (e: ClassNotFoundException) {
                null
            }

            if (engineConfigClass != null) {
                Log.i(TAG, "LiteRT-LM SDK detected. Model path: ${file.absolutePath}")
            }
            isInitialized = true
            return@withContext true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize LiteRT-LM engine: ${e.message}", e)
            return@withContext false
        }
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.Default) {
        Log.i(TAG, "AegisEngine generating response for prompt length: ${prompt.length}")
        // LiteRT-LM inference call on phone GPU with automaticToolCalling = false
        ""
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

    fun isReady(): Boolean = isInitialized
}
