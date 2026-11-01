package com.krishna.aegis.tools

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadRecordingsTool : AegisTool {
    override val name = "read_recordings"
    override val description = "Scans and inspects local voice recordings, call recordings, and audio memos on the device."
    override val parameterSchema = mapOf(
        "query" to "Optional keyword or date to filter recording filenames",
        "limit" to "Maximum number of recordings to return (defaults to 10)"
    )

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val query = args["query"]?.toString()?.lowercase()?.trim() ?: ""
        val limit = (args["limit"] as? Number)?.toInt() ?: 10

        val appContext = context.androidContext
        val results = mutableListOf<String>()

        // 1. Try MediaStore Audio Query
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED
            )

            val cursor = appContext.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )

            cursor?.use {
                val nameIdx = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val durIdx = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeIdx = it.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val dateIdx = it.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

                while (it.moveToNext() && results.size < limit) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                    val durationMs = if (durIdx >= 0) it.getLong(durIdx) else 0L
                    val sizeBytes = if (sizeIdx >= 0) it.getLong(sizeIdx) else 0L
                    val dateSec = if (dateIdx >= 0) it.getLong(dateIdx) else 0L

                    // Check if recording or matches query
                    val isAudioMemo = name.contains("Record", ignoreCase = true) ||
                            name.contains("Voice", ignoreCase = true) ||
                            name.contains("Call", ignoreCase = true) ||
                            name.endsWith(".m4a") || name.endsWith(".wav") || name.endsWith(".3gp")

                    if (isAudioMemo && (query.isEmpty() || name.lowercase().contains(query))) {
                        val durationStr = if (durationMs > 0) "${durationMs / 1000}s" else "N/A"
                        val sizeMb = String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
                        val dateStr = if (dateSec > 0) dateFormat.format(Date(dateSec * 1000)) else "Unknown"
                        results.add("🎤 $name ($sizeMb, Duration: $durationStr, Modified: $dateStr)")
                    }
                }
            }
        } catch (e: Exception) {
            // MediaStore might require permission or fail; proceed to filesystem fallback
        }

        // 2. Filesystem check for well-known recording folders
        val knownDirs = listOf(
            File(Environment.getExternalStorageDirectory(), "Recordings"),
            File(Environment.getExternalStorageDirectory(), "Voice Recorder"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), ""),
            File(Environment.getExternalStorageDirectory(), "Sounds")
        )

        val audioExtensions = setOf("m4a", "mp3", "wav", "3gp", "aac", "amr")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        for (dir in knownDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    if (results.size >= limit) break
                    val ext = file.extension.lowercase()
                    if (audioExtensions.contains(ext)) {
                        if (query.isEmpty() || file.name.lowercase().contains(query)) {
                            val sizeMb = String.format(Locale.US, "%.1f MB", file.length() / (1024.0 * 1024.0))
                            val dateStr = dateFormat.format(Date(file.lastModified()))
                            val item = "🎤 [Storage] ${file.name} ($sizeMb, Path: ${file.absolutePath}, Modified: $dateStr)"
                            if (!results.any { it.contains(file.name) }) {
                                results.add(item)
                            }
                        }
                    }
                }
            }
        }

        return if (results.isEmpty()) {
            ToolOutcome.Ok("No voice recordings found matching filter '$query'.")
        } else {
            ToolOutcome.Ok("Discovered Recordings (${results.size} files):\n" + results.joinToString("\n"))
        }
    }
}
