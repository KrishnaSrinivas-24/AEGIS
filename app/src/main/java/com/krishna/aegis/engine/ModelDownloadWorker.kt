package com.krishna.aegis.engine

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL)
            ?: return@withContext Result.failure(workDataOf("error" to "Missing download URL"))
        val targetFileName = inputData.getString(KEY_TARGET_FILENAME)
            ?: AegisModelConfig.DEFAULT_MODEL_NAME

        val targetFile = AegisModelConfig.getModelFile(applicationContext, targetFileName)
        val tempFile = File(targetFile.parentFile, "$targetFileName.download")

        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode !in 200..299) {
                ModelDownloadManager.fail("HTTP error: ${connection.responseCode}")
                return@withContext Result.failure(workDataOf("error" to "HTTP ${connection.responseCode}"))
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (isStopped) {
                            tempFile.delete()
                            return@withContext Result.failure()
                        }
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                        ModelDownloadManager.updateProgress(progress, downloadedBytes, totalBytes)
                        setProgress(workDataOf("progress" to progress))
                    }
                    output.flush()
                }
            }

            if (tempFile.renameTo(targetFile)) {
                ModelDownloadManager.complete()
                Result.success()
            } else {
                ModelDownloadManager.fail("Failed to rename temporary download file")
                Result.failure(workDataOf("error" to "File rename failed"))
            }
        } catch (e: Exception) {
            tempFile.delete()
            ModelDownloadManager.fail("Download failed: ${e.message}")
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }

    companion object {
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_TARGET_FILENAME = "target_filename"
    }
}
