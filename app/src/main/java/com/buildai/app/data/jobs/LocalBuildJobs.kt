package com.buildai.app.data.jobs

import android.app.Application
import com.buildai.app.data.api.*
import com.buildai.app.data.local.LocalModelStore
import com.buildai.app.data.model.*
import com.buildai.app.data.storage.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID

/** Local build workflow is user-started service work, outside WorkManager quotas. */
class LocalBuildJobs(private val app: Application, private val projects: ConversationRepository,
    private val adapter: com.buildai.app.data.local.ModelAdapter? = null) {
    private val jobs = BuildAiDatabase.get(app).jobs()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val active = mutableMapOf<String, Job>()
    fun enqueue(projectId: String, savedInput: JSONObject? = null): String {
        active[projectId]?.takeIf { it.isActive }?.let {
            return checkNotNull(jobs.list().firstOrNull { job -> job.projectId == projectId && job.kind == "BUILD" && job.status in listOf("QUEUED", "RUNNING") }).id
        }
        val project = savedInput?.getJSONObject("project")?.let(Conversation::fromJson)
            ?: projects.load(projectId) ?: error("Project no longer exists")
        val input = savedInput ?: JSONObject().put("project", project.toJson()).also { data ->
            LocalModelStore(app).selectedFile()?.let { data.put("modelPath", it.absolutePath) }
        }
        val job = StoredAgentJob(UUID.randomUUID().toString(), projectId, "BUILD", "QUEUED", input.toString(), "Waiting for local AI")
        jobs.save(job)
        try { ExecutionService.keepRunning(app) }
        catch (error: Exception) {
            jobs.save(job.copy(status = "FAILED", error = "Android could not start background work. Keep BuildAI open and retry."))
            return job.id
        }
        active[projectId] = scope.launch {
            try {
                jobs.save(job.copy(status = "RUNNING", stage = "Reading the approved plan"))
                val plan = checkNotNull(project.projectPlan) { "Confirm the plan first" }
                val design = checkNotNull(project.designSpec) { "Choose a design first" }
                val validate = GeneratedAppValidator(app)::validate
                var evidenceAt = 0L
                var evidenceStage: com.buildai.app.data.local.InferenceStage? = null
                val html = if (plan.buildMode == BuildMode.TEMPLATE) {
                    HtmlValidator.extractAndValidate(QuickTemplateApp.build(plan, design)).also { validate(it) }
                } else {
                    val model = input.optString("modelPath").takeIf(String::isNotBlank)?.let { java.io.File(it) }
                        ?: error("Choose a GGUF model in Settings first")
                    (adapter?.let { LocalPwaGenerator(app, it) } ?: LocalPwaGenerator(app)).generate(model, plan, design,
                        onProgress = { stage, _ -> update(job.id) { it.copy(stage = stage) } }, validate = validate,
                        previousHtml = projects.generatedFile(projectId)?.readText(),
                        onDraft = { draft -> update(job.id) { it.copy(checkpoint = draft) } },
                        onProgressEvidence = { event ->
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (event.stage != evidenceStage || now - evidenceAt >= 500 || event.total > 0 && event.completed == event.total) {
                                evidenceAt = now; evidenceStage = event.stage
                                update(job.id) { it.copy(progressJson = JSONObject().put("stage", event.stage.name)
                                    .put("completed", event.completed).put("total", event.total).toString()) }
                            }
                        }).getOrThrow()
                }
                currentCoroutineContext().ensureActive()
                // A saved draft is not proof of a valid app; publication follows checks.
                projects.saveGenerated(projectId, html, expectedInput = project)
                update(job.id) { it.copy(status = "COMPLETED", stage = "App opening checks passed", error = null) }
            } catch (cancelled: CancellationException) {
                update(job.id) { it.copy(status = "CANCELLED", stage = "Build stopped") }
                throw cancelled
            } catch (error: Exception) {
                val interrupted = error.message.orEmpty().contains("interrupted", true) || error.message.orEmpty().contains("progress", true)
                update(job.id) { it.copy(status = if (error.message.orEmpty().contains("Paused:")) "WAITING" else if (interrupted) "INTERRUPTED" else "FAILED", error = error.message, stage = "Previous app preserved") }
            } finally { active.remove(projectId) }
        }
        return job.id
    }
    fun cancel(projectId: String) { active[projectId]?.cancel() }
    fun retry(id: String): String {
        val original = jobs.load(id) ?: error("Saved build is missing")
        require(original.kind == "BUILD" && original.status in listOf("FAILED", "WAITING", "INTERRUPTED", "CANCELLED"))
        return enqueue(original.projectId, JSONObject(original.inputJson))
    }
    private fun update(id: String, change: (StoredAgentJob) -> StoredAgentJob) {
        jobs.load(id)?.let { jobs.save(change(it).copy(updatedAt = System.currentTimeMillis())) }
    }
}
