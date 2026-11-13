package com.buildai.app.data.api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.buildai.app.BuildAiApplication
import com.buildai.app.data.local.LocalModelStore
import com.buildai.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/** A project owns its job; leaving the screen does not cancel its build. */
class GenerationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    companion object {
        const val PROJECT_ID = "projectId"
        const val BACKEND = "backend"
        const val STEP = "step"
        const val PROGRESS = "progress"
        const val ERROR = "error"
        private const val CHANNEL = "buildai_generation"

        fun workName(projectId: String) = "generation-$projectId"

        fun enqueue(context: Context, projectId: String, backend: String = "cloud"): java.util.UUID {
            val builder = OneTimeWorkRequestBuilder<GenerationWorker>()
                .setInputData(workDataOf(PROJECT_ID to projectId, BACKEND to backend, "provider" to com.buildai.app.data.storage.SecureStorage(context).provider().id, "model" to com.buildai.app.data.storage.SecureStorage(context).getModel(), "fallback" to com.buildai.app.data.storage.SecureStorage(context).generationFallback(), "fallbackModels" to com.buildai.app.data.storage.SecureStorage(context).fallbackModels().joinToString(",")))
                .addTag("generation")
                .addTag(workName(projectId))
            if (backend != "local" && com.buildai.app.data.storage.ConversationRepository(context).load(projectId)?.projectPlan?.buildMode != com.buildai.app.data.model.BuildMode.TEMPLATE) builder.setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            val request = builder.build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(projectId), ExistingWorkPolicy.REPLACE, request
            )
            return request.id
        }
    }

    override suspend fun doWork(): Result {
        val projectId = inputData.getString(PROJECT_ID)
            ?: return Result.failure(workDataOf(ERROR to "Project missing"))
        val app = applicationContext as BuildAiApplication
        val project = withContext(Dispatchers.IO) { app.container.projects.load(projectId) }
            ?: return Result.failure(workDataOf(ERROR to "Project no longer exists"))
        val plan = project.projectPlan
            ?: return Result.failure(workDataOf(ERROR to "Confirm the project plan first"))
        val design = project.designSpec
            ?: return Result.failure(workDataOf(ERROR to "Choose a design first"))
        val settings = app.container.settings
        val backend = inputData.getString(BACKEND) ?: "cloud"
        val modelFile = if (backend == "local") LocalModelStore(applicationContext).selectedFile() else null
        if (plan.buildMode == com.buildai.app.data.model.BuildMode.CUSTOM && backend == "local" && modelFile == null) return Result.failure(workDataOf(ERROR to
            "The selected GGUF model is missing. Choose or download a model in Settings."))
        val provider = CloudProvider.read(inputData.getString("provider"))
        val key = if (backend == "local") null else settings.getApiKey(provider)
        if (plan.buildMode == com.buildai.app.data.model.BuildMode.CUSTOM && backend != "local" && key == null)
            return Result.failure(workDataOf(ERROR to "No API key — add it in Settings first"))

        setForeground(foregroundInfo(projectId))
        return try {
            val progress: (String, Float) -> Unit = { step, value ->
                if (!isStopped) setProgressAsync(workDataOf(STEP to step, PROGRESS to value))
            }
            val validator = GeneratedAppValidator(applicationContext)::validate
            val html = if (plan.buildMode == com.buildai.app.data.model.BuildMode.TEMPLATE) {
                progress("Building your chosen ${plan.quickTemplate.label.lowercase()} template…", 0.6f)
                val template = HtmlValidator.extractAndValidate(QuickTemplateApp.build(plan, design))
                validator(template)
                template
            } else if (backend == "local") {
                LocalPwaGenerator(applicationContext).generate(
                    checkNotNull(modelFile), plan, design, progress, validator,
                    previousHtml = withContext(Dispatchers.IO) { app.container.projects.generatedFile(projectId)?.readText() }).getOrThrow()
            } else {
                app.container.generator.generate(
                    plan, design, checkNotNull(key), inputData.getString("model") ?: settings.getModel(provider), progress, validator, provider, inputData.getBoolean("fallback", true), inputData.getString("fallbackModels")?.split(',')?.filter(String::isNotBlank)).getOrThrow()
            }
            currentCoroutineContext().ensureActive()
            val file = withContext(Dispatchers.IO) { app.container.projects.saveGenerated(projectId, html, expectedInput = project) }
            Result.success(workDataOf("fileName" to file.name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(workDataOf(ERROR to (e.message ?: "Couldn't build your app. Try again.")))
        }
    }

    private fun foregroundInfo(projectId: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "App builds", NotificationManager.IMPORTANCE_LOW))
        val launch = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(applicationContext, projectId.hashCode(), it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Building your app")
            .setContentText("You can return to BuildAI while this runs")
            .setOngoing(true)
            .apply { pending?.let(::setContentIntent) }
            .build()
        return ForegroundInfo(projectId.hashCode() and Int.MAX_VALUE, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
