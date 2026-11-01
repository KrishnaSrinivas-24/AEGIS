package com.krishna.aegis.engine

import android.content.Context
import java.io.File

object AegisModelConfig {
    const val MODEL_E2B_NAME = "gemma-4-E2B-it.litertlm"
    const val MODEL_E4B_NAME = "gemma-4-E4B-it.litertlm"

    // Default to E2B for compatibility with 6-8GB RAM devices, fallback or switchable to E4B
    const val DEFAULT_MODEL_NAME = MODEL_E2B_NAME

    const val MAX_NUM_TOKENS = 1024
    const val TEMPERATURE = 0.2f
    const val TOP_K = 40

    fun getModelDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelFile(context: Context, modelName: String = DEFAULT_MODEL_NAME): File {
        return File(getModelDirectory(context), modelName)
    }

    fun isModelPresent(context: Context, modelName: String = DEFAULT_MODEL_NAME): Boolean {
        val file = getModelFile(context, modelName)
        return file.exists() && file.length() > 100_000_000L // Ensure file has substantial size
    }
}
