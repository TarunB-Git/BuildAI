package com.buildai.app.builder

import android.app.Application
import com.buildai.app.data.jobs.ExecutionService
import com.buildai.app.data.local.*
import com.buildai.app.data.model.Message
import com.buildai.app.data.model.MessageRole
import com.buildai.app.data.storage.*
import com.buildai.app.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Prepares a checked scoped proposal. Applying it is an explicit review action. */
class NativeEditJobs(private val app: Application, private val models: BuilderModels = BuilderModels(app), private val testAdapter: ModelAdapter? = null) {
    private val jobs = BuildAiDatabase.get(app).jobs()
    private val apps = NativeAppRepository(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val active = mutableMapOf<String, Pair<String, Job>>()
    fun enqueue(id: String, request: String, target: EditScope): String {
        require(request.length in 1..4096); check(active[id]?.second?.isActive != true) { "An edit is already running" }
        val head = checkNotNull(apps.dao.head(id)); val original = checkNotNull(apps.spec(id))
        val model = if (testAdapter != null) BuilderModel("local", "Controlled test adapter") else BuilderModel.selected(app)
        val input = JSONObject().put("request", request).put("scope", target.json()).put("baseRevision", head.revisionId).put("model", model.json())
        val job = StoredAgentJob(UUID.randomUUID().toString(), id, "NATIVE_EDIT", "RUNNING", input.toString(), "Preparing a scoped edit")
        jobs.save(job)
        try { ExecutionService.keepRunning(app) } catch (e: Exception) { jobs.save(job.copy(status = "FAILED", error = "Android could not start background work. Keep BuildAI open and retry.")); return job.id }
        val task = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val adapter = testAdapter ?: models.adapter(model)
                var failure: String? = null; var failedProposal: String? = null; var proposal: JSONObject? = null
                for (attempt in 0..2) {
                    update(job.id) { it.copy(stage = if (attempt == 0) "Proposing the requested edit" else "Repairing the edit ($attempt of 2)") }
                    val selected = JSONObject().put("components", JSONArray(original.screens.flatMap { it.components }.filter { it.id in target.componentIds }.map { it.json() }))
                    val dependencies = FeatureTools.probeContext(original, selected).contracts
                    val actionIds = dependencies.actions.map { it.id }.toSet()
                    // Include other consumers so a shared action's effect is visible.
                    val relevant = dependencies.copy(screens = original.screens.map { screen -> screen.copy(components = screen.components.filter { c ->
                        dependencies.screens.flatMap { it.components }.any { it.id == c.id } || c.actionId in actionIds || c.rowActions.any { it in actionIds }
                    }) })
                    val context = JSONObject().put("tool", "proposeScopedEdit").put("request", request).put("scope", target.json())
                        .put("currentConfiguration", relevant.json()).put("readOnlyDependencies", JSONArray(relevant.screens.flatMap { it.components }.filterNot { it.id in target.componentIds }.map { it.id }))
                        .put("failureToRepair", failure ?: JSONObject.NULL)
                        .put("failedProposal", failedProposal?.let { runCatching<Any> { strictObject(it) }.getOrDefault(it) } ?: JSONObject.NULL).toString()
                    var raw = ""; var progressAt = 0L
                    adapter.reply(model.path?.let(::File) ?: File(app.filesDir, "cloud-adapter"), EDIT_PROMPT,
                        listOf(Message(role = MessageRole.USER, content = context)), maxTokens = 2400, contextSize = 4096,
                        grammar = FeatureGrammar.forEdit(original), priority = InferencePriority.BUILD,
                        onProgress = { event -> scope.launch {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - progressAt >= 500) { progressAt = now; update(job.id) { it.copy(progressJson = JSONObject().put("stage", event.stage.name).put("completed", event.completed).toString()) } }
                        } }).collect { raw += it; require(raw.length <= 65_536) }
                    val checked = runCatching {
                        val response = strictObject(raw)
                        response.optString("unsupported").takeIf(String::isNotBlank)?.let { error("Unsupported edit: $it") }
                        val candidate = ScopedEditTools.prepare(original, target, response)
                        require(response.getString("summary").length in 1..500)
                        val probes = response.objects("checks"); require(probes.size in 1..8)
                        val changedControls = candidate.screens.flatMap { it.components }.filter { c -> original.screens.flatMap { it.components }.none { it == c } }
                        FeatureTools.coverage(JSONObject().put("components", JSONArray(changedControls.map { it.json() })).put("checks", JSONArray(probes)))
                        val exercised = probes.flatMap { it.objects("steps") }.map { it.getString("action") }.toSet()
                        response.objects("actions").forEach { action -> require(action.getString("id") in exercised) { "Exercise the changed action in an interaction check" } }
                        val feature = FeaturePlan("edit", request.take(100), request, probes.map { it.getString("name") })
                        update(job.id) { it.copy(stage = "Checking the proposed change") }
                        val result = withContext(Dispatchers.IO) { FeatureTools.verify(app, candidate, feature, probes) }
                        JSONObject().put("definition", candidate.json()).put("summary", response.getString("summary"))
                            .put("changedIds", JSONArray(ScopedEditTools.changedIds(original, candidate))).put("verification", result)
                    }
                    proposal = checked.getOrNull()
                    if (proposal != null) break
                    checked.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    failure = checked.exceptionOrNull()?.message
                    failedProposal = raw.take(6000)
                }
                check(apps.dao.head(id)?.revisionId == head.revisionId) { "The app changed while this edit was prepared. Review its current revision." }
                val result = proposal ?: error("The edit failed after two repairs: $failure. Your app and records are preserved.")
                update(job.id) { it.copy(status = "REVIEW", stage = "Change ready to preview; saved data is untouched", checkpoint = result.toString()) }
            } catch (cancelled: CancellationException) { update(job.id) { it.copy(status = "CANCELLED", stage = "Edit stopped; app retained") }; throw cancelled }
            catch (e: Exception) { update(job.id) { it.copy(status = "FAILED", error = e.message, stage = "Working app preserved") } }
            finally { if (active[id]?.first == job.id) active.remove(id) }
        }
        active[id] = job.id to task; task.start()
        return job.id
    }
    fun apply(jobId: String) {
        val job = checkNotNull(jobs.load(jobId)); require(job.kind == "NATIVE_EDIT" && job.status == "REVIEW")
        val proposed = JSONObject(job.checkpoint); val input = JSONObject(job.inputJson)
        apps.publish(job.projectId, AppSpec.parse(proposed.getJSONObject("definition").toString()), input.getString("baseRevision"), proposed.getString("summary"),
            JSONObject().put("structure", "passed").put("edit", proposed.getJSONObject("verification")).put("physicalAcceptance", "pending"))
        update(jobId) { it.copy(status = "COMPLETED", stage = "Scoped revision applied; current records retained") }
    }
    fun discard(jobId: String) {
        val job = checkNotNull(jobs.load(jobId))
        require(job.kind == "NATIVE_EDIT" && job.status == "REVIEW")
        update(jobId) { it.copy(status = "CANCELLED", stage = "Proposal discarded; current app retained", checkpoint = "") }
    }
    fun cancel(id: String) { active[id]?.second?.cancel() }
    private fun update(id: String, change: (StoredAgentJob) -> StoredAgentJob) { jobs.load(id)?.let { jobs.save(change(it).copy(updatedAt = System.currentTimeMillis())) } }
    companion object {
        val EDIT_PROMPT = NativeBuildJobs.FEATURE_PROMPT + """

            This is a SCOPED EDIT, not a new feature fragment. Override the output shape with:
            {"summary":"plain description","components":[complete new or updated controls],"removeComponents":[selected IDs to hide],
            "actions":[complete new or updated actions],"newFields":[{"entity":"existing ID","field":{"id":"new ID","label":"Label","kind":"TEXT","required":false}}],"entities":[],"checks":[]}.
            You may update/remove ONLY the selected component IDs and their bound actions. Additions are allowed on the selected screen.
            readOnlyDependencies explain existing behavior; do not change those controls. If a selected action is shared, create a new action ID and rebind only the selected controls.
            Retain unrelated components/actions, IDs, record types and data. Do not regenerate the app.
            Checks use the same probe contracts above. Name each check after an observable result of the requested edit, with real example inputs.
            The person will review the summary, changed controls and checks before applying. Never claim physical UI testing.
        """.trimIndent()
    }
}
