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

    /**
     * Resolves the actual LiteRT-LM model file on device.
     * Searches in:
     * 1. Primary app models directory (`context.getExternalFilesDir(null)/models`)
     * 2. Any .litertlm file inside the models directory (> 50MB)
     * 3. Direct external storage paths (/sdcard/Android/data/... & /storage/emulated/0/...)
     * 4. Google AI Edge Gallery download cache directory
     */
    fun getModelFile(context: Context, modelName: String = DEFAULT_MODEL_NAME): File {
        // 1. Direct standard file in app models directory
        val standardDir = getModelDirectory(context)
        val directFile = File(standardDir, modelName)
        if (directFile.exists() && directFile.length() > 50_000_000L) {
            return directFile
        }

        // 2. Scan standard directory for any valid .litertlm file
        standardDir.listFiles()?.firstOrNull { it.name.endsWith(".litertlm") && it.length() > 50_000_000L }?.let {
            return it
        }

        // 3. Fallback explicit absolute paths
        val candidatePaths = listOf(
            "/sdcard/Android/data/${context.packageName}/files/models/$modelName",
            "/storage/emulated/0/Android/data/${context.packageName}/files/models/$modelName",
            "/sdcard/Android/data/${context.packageName}/files/models",
            "/storage/emulated/0/Android/data/${context.packageName}/files/models"
        )

        for (path in candidatePaths) {
            val f = File(path)
            if (f.isFile && f.exists() && f.length() > 50_000_000L) {
                return f
            } else if (f.isDirectory) {
                f.listFiles()?.firstOrNull { it.name.endsWith(".litertlm") && it.length() > 50_000_000L }?.let {
                    return it
                }
            }
        }

        // 4. Check Edge Gallery directory if user downloaded via Gallery app
        val galleryDir = File("/sdcard/Android/data/com.google.ai.edge.gallery/files")
        if (galleryDir.exists()) {
            findLitertFileRecursively(galleryDir)?.let { return it }
        }

        return directFile
    }

    private fun findLitertFileRecursively(dir: File): File? {
        if (!dir.exists() || !dir.canRead()) return null
        val files = dir.listFiles() ?: return null
        for (f in files) {
            if (f.isDirectory) {
                val found = findLitertFileRecursively(f)
                if (found != null) return found
            } else if (f.name.endsWith(".litertlm") && f.length() > 50_000_000L) {
                return f
            }
        }
        return null
    }

    fun isModelPresent(context: Context, modelName: String = DEFAULT_MODEL_NAME): Boolean {
        val file = getModelFile(context, modelName)
        return file.exists() && file.length() > 50_000_000L
    }
}
