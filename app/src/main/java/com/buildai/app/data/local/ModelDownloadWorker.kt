package com.buildai.app.data.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.buildai.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.net.URL

/** Downloads a pinned GGUF into private storage and verifies its full SHA-256 before selection. */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val WORK_NAME = "model-download"
        const val MODEL_ID = "modelId"
        const val BYTES = "bytes"
        const val TOTAL = "total"
        const val ERROR = "error"
        private const val CHANNEL = "buildai_model_downloads"

        fun enqueue(context: Context, modelId: String) {
            require(ModelCatalog.find(modelId) != null) { "Unknown model" }
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(MODEL_ID to modelId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            context.getSharedPreferences("model_download", Context.MODE_PRIVATE).edit()
                .putString("request_id", request.id.toString())
                .putString("model_id", modelId).apply()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val model = ModelCatalog.find(inputData.getString(MODEL_ID).orEmpty())
            ?: return@withContext Result.failure(workDataOf(ERROR to "Model no longer available."))
        val directory = File(applicationContext.filesDir, "models").apply { mkdirs() }
        directory.listFiles()?.filter { it.name.startsWith("download-") && it.name.endsWith(".partial") }
            ?.forEach(File::delete)
        val reserve = 128L * 1024 * 1024
        if (directory.usableSpace < model.sizeBytes + reserve) {
            return@withContext Result.failure(workDataOf(ERROR to
                "Not enough free storage. This model needs ${model.sizeBytes / 1_000_000_000.0} GB plus room for setup."))
        }
        val temp = File(directory, "download-${model.id}-$id.partial")
        try {
            setForeground(foregroundInfo(model.name))
            setProgress(workDataOf(MODEL_ID to model.id, BYTES to 0L, TOTAL to model.sizeBytes))
            var lastProgress = 0L
            VerifiedModelDownloader.download(URL(model.downloadUrl), temp, model.sizeBytes, model.sha256) { count ->
                if (isStopped) throw CancellationException("Download cancelled")
                check(directory.usableSpace > reserve) { "Storage filled during the download." }
                if (count - lastProgress >= 8L * 1024 * 1024 || count == model.sizeBytes) {
                    lastProgress = count
                    setProgress(workDataOf(MODEL_ID to model.id, BYTES to count, TOTAL to model.sizeBytes))
                }
            }
            DataInputStream(temp.inputStream()).use { input ->
                val header = ByteArray(8)
                input.readFully(header)
                check(header.copyOfRange(0, 4).contentEquals("GGUF".toByteArray())) {
                    "Downloaded file is not a GGUF model."
                }
                val version = (header[4].toInt() and 255) or ((header[5].toInt() and 255) shl 8)
                check(version in 2..3) { "Unsupported GGUF version." }
            }
            LocalModelStore(applicationContext).installVerifiedDownload(temp, model.name, model.id)
            Result.success(workDataOf(MODEL_ID to model.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(workDataOf(ERROR to (e.message ?: "Download failed. Please retry.")))
        } finally {
            temp.delete()
        }
    }

    private fun foregroundInfo(name: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Downloading $name")
            .setContentText("Verifying the model before use")
            .setOngoing(true)
            .build()
        return ForegroundInfo(93721, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
