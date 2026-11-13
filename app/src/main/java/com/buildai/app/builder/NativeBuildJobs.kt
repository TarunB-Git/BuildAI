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

/** One orchestrator, bounded feature steps and repairs; screens only observe persisted jobs. */
class NativeBuildJobs(private val app: Application, private val projects: ConversationRepository,
    private val models: BuilderModels = BuilderModels(app), private val testAdapter: ModelAdapter? = null,
    private val repairAttempts: Int = 2) {
    init { require(repairAttempts in 0..2) }
    private val jobs = BuildAiDatabase.get(app).jobs()
    private val apps = NativeAppRepository(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val active = mutableMapOf<String, Pair<String, Job>>()
    fun enqueue(projectId: String, savedInput: JSONObject? = null, checkpoint: String = ""): String {
        active[projectId]?.takeIf { it.second.isActive }?.let { return it.first }
        val brief = projects.load(projectId)?.brief ?: error("A native plan is missing")
        check(brief.ready && brief.approvedVersion == brief.version) { "Review and approve the current plan first" }
        val input = savedInput ?: JSONObject().put("brief", brief.json()).put("model", BuilderModel.selected(app).json())
            .put("baseRevision", apps.dao.head(projectId)?.revisionId ?: JSONObject.NULL)
        val approved = ProjectBrief.read(input.getJSONObject("brief"))
        check(approved.version == brief.version && approved.approvedVersion == brief.approvedVersion) { "The saved build uses an older plan. Review the new plan first." }
        val job = StoredAgentJob(UUID.randomUUID().toString(), projectId, "NATIVE_BUILD", "QUEUED", input.toString(), "Waiting for AI", checkpoint = checkpoint)
        jobs.save(job)
        try { ExecutionService.keepRunning(app) } catch (e: Exception) {
            update(job.id) { it.copy(status = "FAILED", error = "Android could not start background work. Keep BuildAI open and retry.") }; return job.id
        }
        val task = scope.launch(start = CoroutineStart.LAZY) {
            try {
                update(job.id) { it.copy(status = "RUNNING", stage = "Reading the approved requirements") }
                val plan = checkNotNull(approved.plan)
                val model = BuilderModel.read(input.getJSONObject("model")); val adapter = testAdapter ?: models.adapter(model)
                val saved = checkpoint.takeIf(String::isNotBlank)?.let(::JSONObject)
                var definition = saved?.getJSONObject("definition")?.let { AppSpec.parse(it.toString()) }
                    ?: AppSpec(plan.name, "home", listOf(ScreenSpec("home", plan.screens.first(), emptyList())))
                val completed = saved?.optInt("completedFeatures") ?: 0
                require(completed in 0..plan.features.size)
                val fragments = saved?.objects("fragments")?.toMutableList() ?: mutableListOf()
                val verification = mutableListOf<JSONObject>()
                val selections = saved?.objects("selections")?.toMutableList() ?: mutableListOf()
                for ((index, feature) in plan.features.withIndex()) {
                    if (index < completed) continue
                    var failure: String? = null; var failedFeature: String? = null; var selection: CompositionSelection? = null; var checked: AppSpec? = null; var accepted: JSONObject? = null
                    var configured: Pair<AppSpec, JSONObject>? = null
                    val rejected = mutableListOf<Pair<String, String>>()
                    var step = "choose the parts for"
                    for (attempt in 0..repairAttempts) {
                        update(job.id) { it.copy(stage = if (attempt == 0) "Adding ${feature.title}" else "Repairing ${feature.title} ($attempt of $repairAttempts)") }
                        val contracts = JSONObject().put("name", definition.name).put("screens", JSONArray(definition.screens.map { JSONObject().put("id", it.id).put("title", it.title) }))
                            .put("existingControls", JSONArray(definition.screens.flatMap { it.components }.map { JSONObject().put("id", it.id).put("kind", it.kind.name).put("label", it.label) }))
                            .put("entities", JSONArray(definition.entities.map { it.json() })).put("existingActionIds", JSONArray(definition.actions.map { it.id }))
                        val context = JSONObject().put("goal", approved.goal).put("feature", feature.json()).put("existingContracts", contracts)
                            .put("approvedScreens", JSONArray(plan.screens)).put("approvedData", JSONArray(plan.data))
                            .put("decisions", JSONArray(approved.answers.map { "${it.question}: ${it.answer}" })).put("assumptions", JSONArray(approved.assumptions))
                            .put("failureToRepair", failure ?: JSONObject.NULL).put("failedFeature", failedFeature?.let { runCatching<Any> { strictObject(it) }.getOrDefault(it) } ?: JSONObject.NULL)
                            .put("tool", "configureFeature").put("currentTask", feature.workflow).toString()
                        suspend fun generate(prompt: String, arguments: String, grammar: String?, tokenLimit: Int, purpose: String): String {
                            var output = ""; var evidenceAt = 0L
                            adapter.reply(model.path?.let(::File) ?: File(app.filesDir, "cloud-adapter"), prompt,
                                listOf(Message(role = MessageRole.USER, content = arguments)), maxTokens = tokenLimit, contextSize = 4096,
                                grammar = grammar, priority = InferencePriority.BUILD,
                                onStatus = { stage -> scope.launch { update(job.id) { it.copy(stage = "${feature.title}: $purpose — $stage") } } },
                                onProgress = { event -> scope.launch {
                                    val now = android.os.SystemClock.elapsedRealtime()
                                    if (now - evidenceAt >= 500) {
                                        evidenceAt = now; update(job.id) { it.copy(progressJson = JSONObject().put("stage", event.stage.name).put("completed", event.completed).put("total", event.total).toString()) }
                                    }
                                } }).collect { output += it; require(output.length <= 65_536) }
                            // Repeating the same rejected answer is not a different repair.
                            check(rejected.none { (phase, text) -> phase == purpose && runCatching { sameJson(strictObject(text), strictObject(output)) }.getOrDefault(text.trim() == output.trim()) }) {
                                "The model repeated the same unsuccessful answer. Change the feature or model before retrying."
                            }
                            return output
                        }
                        var raw = ""
                        val result = runCatching {
                            val proposedSelection = selection ?: feature.composition ?: run {
                                step = "choose the parts for"
                                raw = generate(CapabilityDecisions.prompt + "\nRequested feature: " + feature.workflow + "\nRequired checks: " + feature.checks.joinToString("; "), JSONObject().put("tool", "inspectCapabilities").put("currentTask", feature.workflow).put("checks", JSONArray(feature.checks))
                                    .put("goal", approved.goal).put("decisions", JSONArray(approved.answers.map { "${it.question}: ${it.answer}" }))
                                    .put("approvedData", JSONArray(plan.data)).put("assumptions", JSONArray(approved.assumptions))
                                    .put("existingContracts", contracts).put("failureToRepair", failure ?: JSONObject.NULL)
                                    .put("failedFeature", failedFeature?.take(6000) ?: JSONObject.NULL).toString(),
                                    CapabilityDecisions.grammar, 128, "Selecting capabilities")
                                CapabilityDecisions.read(strictObject(raw))
                            }
                            val selected = proposedSelection.withDependencies().let { chosen ->
                                if (definition.entities.isEmpty() && plan.data.isNotEmpty() && chosen.actions.any { it in setOf(ActionKind.INSERT, ActionKind.UPDATE, ActionKind.DELETE) }) chosen.copy(records = true) else chosen
                            }
                            require(!selected.records || plan.data.isNotEmpty()) { "The plan does not request saved data. Remove NEW_DATA and record actions, or revise the plan to include the intended saved data." }
                            require(definition.entities.isNotEmpty() || selected.records || selected.expressions.none { it == ExpressionOp.COUNT || it == ExpressionOp.SUM }) { "COUNT and SUM read saved collections. For numeric inputs, select arithmetic operations instead." }
                            require(definition.entities.isNotEmpty() || selected.records || selected.actions.none { it in setOf(ActionKind.INSERT, ActionKind.UPDATE, ActionKind.DELETE) }) { "Record actions need a saved collection; select records or remove unneeded record actions." }
                            require(selected.actions.none { it == ActionKind.START_TIMER || it == ActionKind.STOP_TIMER } || ComponentKind.TIMER in selected.components ||
                                definition.screens.flatMap { it.components }.any { it.kind == ComponentKind.TIMER }) { "Timer actions need a timer control. Remove timer actions when no timer is requested." }
                            selection = selected
                            update(job.id) { it.copy(stage = "Selected ${selected.components.joinToString { c -> c.name.lowercase().replace('_', ' ') }} for ${feature.title}") }
                            val (candidate, fragment) = configured ?: run {
                                step = "configure"
                                raw = generate(selected.configurationPrompt(), JSONObject(context).put("selection", selected.json()).toString(),
                                    FeatureGrammar.forImplementation(definition, selected), 1200, "Configuring")
                                val fragment = strictObject(raw)
                                fragment.optString("unsupported").takeIf(String::isNotBlank)?.let { error("Unsupported feature: $it") }
                                val candidate = FeatureTools.compose(definition, fragment)
                                selected.validateImplementation(candidate, fragment)
                                (candidate to fragment).also { configured = it }
                            }
                            // A stronger adapter may already supply probes; otherwise use actual, validated IDs in a separate tool step.
                            if (!fragment.has("checks")) {
                                step = "prepare checks for"
                                update(job.id) { it.copy(stage = "Preparing checks for ${feature.title}") }
                                val relevant = FeatureTools.probeContext(candidate, fragment)
                                val arguments = JSONObject().put("tool", "proposeInteractionChecks").put("feature", feature.json())
                                    .put("configuredFeature", fragment).put("testContracts", relevant.contracts.json()).put("inputIds", JSONArray(relevant.inputIds))
                                    .put("currentTask", "Choose actual test inputs, bound actions and expected results for the approved checks.").toString()
                                raw = generate(PROBE_PROMPT, JSONObject(arguments).put("failureToRepair", failure ?: JSONObject.NULL)
                                    .put("failedChecks", failedFeature ?: JSONObject.NULL).toString(), FeatureGrammar.forProbes(relevant.contracts, feature, relevant.inputIds), 800, "Preparing checks")
                                fragment.put("checks", strictObject(raw).getJSONArray("checks"))
                            }
                            step = "verify"
                            FeatureTools.coverage(fragment)
                            update(job.id) { it.copy(stage = "Checking ${feature.title}") }
                            withContext(Dispatchers.IO) { FeatureTools.verify(app, candidate, feature, fragment.objects("checks")) }
                            candidate to fragment
                        }
                        if (result.isSuccess) { checked = result.getOrThrow().first; accepted = result.getOrThrow().second; break }
                        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                        failure = result.exceptionOrNull()?.message ?: "Feature checks failed"
                        failedFeature = raw.take(6000)
                        if (failure.contains("repeated the same unsuccessful answer")) break
                        val phase = when (step) { "choose the parts for" -> "Selecting capabilities"; "configure" -> "Configuring"; else -> "Preparing checks" }
                        if (raw.isNotBlank()) rejected += phase to raw
                        // Keep valid configuration after a malformed check response. A failing
                        // interaction, in contrast, must be allowed to repair the configuration.
                        if (step == "verify") configured = null
                        update(job.id) { it.copy(error = "${feature.title}: ${failure.take(1000)}") }
                        // Only completed, checked features become checkpoints. Never save a malformed fragment as resumable state.
                    }
                    definition = checked ?: error("Could not $step ${feature.title}. Your appearance, plan, previous app and checked features are saved. ${if (failure?.contains("repeated the same unsuccessful answer") == true) "The model repeated the same failed answer. Try a different model or revise this feature." else "Review this feature or change the model before retrying."}\nDetails: $failure")
                    fragments += checkNotNull(accepted)
                    selections += JSONObject().put("feature", feature.id).put("selection", checkNotNull(selection).json())
                    val next = JSONObject().put("definition", definition.json()).put("completedFeatures", index + 1).put("fragments", JSONArray(fragments)).put("selections", JSONArray(selections))
                    update(job.id) { it.copy(checkpoint = next.toString(), stage = "${feature.title} checked and saved", error = null,
                        progressJson = JSONObject().put("stage", "TOOL").put("completed", index + 1).put("total", plan.features.size).toString()) }
                    yield() // Interactive requests can win the next model step through the inference queue.
                }
                // Re-run every saved feature against the final definition; checkpoint claims are not trusted on retry.
                check(fragments.size == plan.features.size)
                for ((index, feature) in plan.features.withIndex()) {
                    update(job.id) { it.copy(stage = "Rechecking ${feature.title}") }
                    verification += withContext(Dispatchers.IO) { FeatureTools.verify(app, definition, feature, fragments[index].objects("checks")) }
                }
                currentCoroutineContext().ensureActive()
                val base = input.optString("baseRevision").takeIf { it.isNotBlank() && it != "null" }
                apps.publish(projectId, definition, base, "Build: ${plan.name}", JSONObject().put("structure", "passed")
                    .put("features", JSONArray(verification)).put("selectedCapabilities", JSONArray(selections)).put("approvedPlan", plan.json()).put("physicalAcceptance", "pending"), approved.version)
                update(job.id) { it.copy(status = "COMPLETED", stage = "Native app saved; feature probes passed", error = null) }
            } catch (cancelled: CancellationException) {
                update(job.id) { it.copy(status = "CANCELLED", stage = "Build stopped; previous app and checkpoint retained") }; throw cancelled
            } catch (e: Exception) {
                val text = e.message.orEmpty()
                update(job.id) { it.copy(status = when {
                    text.contains("Paused:") -> "WAITING"
                    text.contains("interrupted", true) || text.contains("progress", true) -> "INTERRUPTED"
                    else -> "FAILED"
                }, stage = "Previous app preserved", error = e.message) }
            } finally { if (active[projectId]?.first == job.id) active.remove(projectId) }
        }
        active[projectId] = job.id to task; task.start(); return job.id
    }
    fun cancel(projectId: String) { active[projectId]?.second?.cancel() }
    fun retry(id: String): String {
        val previous = checkNotNull(jobs.load(id)); require(previous.kind == "NATIVE_BUILD" && previous.status in listOf("FAILED", "INTERRUPTED", "WAITING", "CANCELLED"))
        val input = JSONObject(previous.inputJson)
        if (testAdapter == null) input.put("model", BuilderModel.selected(app).json())
        return enqueue(previous.projectId, input, previous.checkpoint)
    }
    private fun update(id: String, change: (StoredAgentJob) -> StoredAgentJob) {
        jobs.load(id)?.let { jobs.save(change(it).copy(updatedAt = System.currentTimeMillis())) }
    }
    companion object {
        val PROBE_PROMPT = """
            Configure interaction checks for this actual native feature. Return ONLY {"checks":[...]}.
            Use the exact approved names, in order. Every check starts with empty records.
            Each check has name, inputs (actual input ID to string test value), steps, assertions.
            Choose input values from the approved example. Never put field descriptions or types in inputs.
            A negative conditional-action check adds "expect":"conditionRejected" to its step and asserts unchanged saved data. Other errors never count as a passed rejection.
            Steps use {"action":"declared action ID"}. Use [] for a live calculation without buttons. Never invent an action.
            Assertions use component and equals for a calculated result; component and count for a visible list;
            entity, field, index and equals for a saved value; entity and count for saved row count; screen for navigation.
            Use only real IDs from configuredFeature and testContracts. Existing INSERT actions can populate empty collections for a search/filter check. Exercise every new input, button and observable capability.
            Check expected outputs from the approved examples, not just labels or self-reported success.
        """.trimIndent()
        val FEATURE_PROMPT = """
            Configure ONE requested feature using native capabilities. Return ONLY JSON.
            Root keys in order: screenId, components, screens, entities, actions, checks. Use [] for unneeded arrays.
            A NUMBER input: {"id":"chosen_id","kind":"INPUT","label":"Your label","fieldKind":"NUMBER"}.
            A live summary: {"id":"chosen_id","kind":"SUMMARY","label":"Your label","expression":EXPRESSION}.
            A button: {"id":"chosen_id","kind":"BUTTON","label":"Your label","actionId":"chosen_action_id"}.
            A saved list: {"id":"chosen_id","kind":"RECORD_LIST","label":"Your label","entityId":"chosen_entity_id","rowActions":[]}.
            Expressions read inputs as {"op":"INPUT","value":"actual_input_id"}; literals as {"op":"NUMBER","value":"literal"}.
            Binary calculations use {"op":"operation","args":[EXPRESSION,EXPRESSION]}. Start with input leaves; keep calculations shallow.
            An entity: {"id":"chosen_entity_id","fields":[{"id":"chosen_field_id","label":"Your label","kind":"TEXT","required":true}]}.
            A save action: {"id":"chosen_action_id","kind":"INSERT","label":"Your label","target":"actual_entity_id","values":{"actual_field_id":EXPRESSION}}.
            A check: {"name":"exact approved check text","inputs":{"actual_input_id":"test value"},"steps":[],"assertions":[]}.
            Steps call {"action":"actual_action_id"}. Assertions use {"component":"actual_summary_id","equals":"expected result"},
            {"component":"actual_list_id","count":1}, or {"entity":"actual_entity_id","field":"actual_field_id","index":0,"equals":"expected value"}.
            These are individual capability shapes, not an app. Replace every placeholder with the requested feature's identifiers and behavior.
            Use real, distinct IDs and labels for this person's feature. Never copy documentation placeholders. Do not write code or a template app.
            Use an existing screenId from existingContracts. Empty arrays are correct for unneeded screens/entities/actions.
            Inputs have fieldKind TEXT, NUMBER, BOOLEAN, DATE or REFERENCE. Buttons bind actionId. Text displays label or expression.
            SUMMARY evaluates its expression using LIVE INPUT values, not sample constants. Arithmetic has args: ADD, SUBTRACT, MULTIPLY, DIVIDE.
            INPUT expressions read a control ID. NUMBER, TEXT, BOOLEAN use literal strings. COUNT reads an entity ID; SUM reads entity.field.
            Record lists bind entityId and rowActions; their optional expression reads search text. Entities declare typed fields.
            INSERT writes values from input expressions. UPDATE/DELETE also need recordId from RECORD id. NAVIGATE targets a screen.
            START_TIMER targets the timer control ID and takes duration in seconds. STOP_TIMER targets that timer ID. QUIZ questions specify checked answers.
            Expressions EQ, GT, CONTAINS, AND and IF support conditions. Optional action condition must be true. Record filters evaluate against each row; sortField selects a stored field.
            TODAY is an ISO date. DATE_ADD(date,days), DATE_DIFF(end,start), BEFORE and AFTER use ISO dates.
            REFERENCE fields/inputs name referenceEntity and select a saved parent record. Probe steps can use inputRecords to select an inserted parent.
            Provide exactly one check for each approved check text, in order, with its EXACT text as name.
            Every check starts with EMPTY saved records. inputs supplies real input values; steps runs bound actions; assertions checks results.
            Each added input must be supplied; each added button must run; summaries/lists/timers/quizzes must be asserted.
            For calculations use the varied inputs and expected results in the approved checks. Keep output compact.
            Never replace previous controls/actions/entity IDs. Unsupported work returns only {"unsupported":"reason"}.
        """.trimIndent()
    }
}
