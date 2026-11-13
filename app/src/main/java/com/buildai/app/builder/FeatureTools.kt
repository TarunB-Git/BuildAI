package com.buildai.app.builder

import android.content.Context
import androidx.room.Room
import com.buildai.app.data.model.Conversation
import com.buildai.app.data.storage.BuildAiDatabase
import com.buildai.app.data.storage.StoredProject
import com.buildai.app.runtime.*
import org.json.JSONArray
import org.json.JSONObject

/** An incremental contribution may add registered capabilities, never replace previous features. */
object FeatureTools {
    data class ProbeContext(val contracts: AppSpec, val inputIds: List<String>)
    /** Transitive configuration dependencies only; no saved user records enter a check prompt. */
    fun probeContext(spec: AppSpec, fragment: JSONObject): ProbeContext {
        val controls = spec.screens.flatMap { it.components }.associateBy { it.id }
        val actions = spec.actions.associateBy { it.id }
        val entities = spec.entities.associateBy { it.id }
        val controlIds = (fragment.objects("components") + fragment.objects("screens").flatMap { it.objects("components") }).map { it.getString("id") }.toMutableSet()
        val actionIds = mutableSetOf<String>(); val entityIds = mutableSetOf<String>()
        fun expression(e: Expr?) {
            if (e == null) return
            when (e.op) {
                ExpressionOp.INPUT -> controlIds += e.value
                ExpressionOp.COUNT, ExpressionOp.SUM -> entityIds += e.value.substringBefore('.')
                else -> Unit
            }
            e.args.forEach(::expression)
        }
        var before: Int
        do {
            before = controlIds.size + actionIds.size + entityIds.size
            controlIds.toList().forEach { id -> controls[id]?.let { c ->
                if (c.entityId.isNotBlank()) entityIds += c.entityId
                if (c.referenceEntity.isNotBlank()) entityIds += c.referenceEntity
                if (c.actionId.isNotBlank()) actionIds += c.actionId
                actionIds += c.rowActions; expression(c.expression); expression(c.filter)
            } }
            entityIds.toList().forEach { id -> entities[id]?.fields?.forEach { field ->
                if (field.referenceEntity.isNotBlank()) entityIds += field.referenceEntity
            } }
            // Scratch checks need a way to populate displayed collections, including parent records.
            actionIds += spec.actions.filter { it.kind == ActionKind.INSERT && it.target in entityIds }.map { it.id }
            actionIds.toList().forEach { id -> actions[id]?.let { action ->
                if (action.target in entities) entityIds += action.target
                if (action.kind in setOf(ActionKind.START_TIMER, ActionKind.STOP_TIMER) && action.target in controls) controlIds += action.target
                action.values.values.forEach(::expression); expression(action.duration); expression(action.recordId); expression(action.condition)
            } }
        } while (before != controlIds.size + actionIds.size + entityIds.size)
        val selected = spec.copy(screens = spec.screens.map { it.copy(components = it.components.filter { c -> c.id in controlIds }) },
            entities = spec.entities.filter { it.id in entityIds }, actions = spec.actions.filter { it.id in actionIds })
        val inputIds = selected.screens.flatMap { it.components }.filter { it.kind == ComponentKind.INPUT }.map { it.id }
        require(selected.json().toString().length <= 16_384) { "This feature needs too much existing configuration. Split it into a smaller feature; the app and records are retained." }
        return ProbeContext(selected, inputIds)
    }
    fun coverage(fragment: JSONObject) {
        val controls = fragment.objects("components") + fragment.objects("screens").flatMap { it.objects("components") }
        val checks = fragment.objects("checks")
        val steps = checks.flatMap { it.objects("steps") }
        val assertions = checks.flatMap { it.objects("assertions") }
        val suppliedInputs = (checks.mapNotNull { it.optJSONObject("inputs") } + steps.mapNotNull { it.optJSONObject("inputs") } + steps.mapNotNull { it.optJSONObject("inputRecords") })
            .flatMap { it.keys().asSequence().toList() }.toSet()
        controls.forEach { c -> when (ComponentKind.valueOf(c.getString("kind"))) {
            ComponentKind.INPUT -> require(c.getString("id") in suppliedInputs) { "An added input has no probe value" }
            ComponentKind.BUTTON -> require(steps.any { it.getString("action") == c.getString("actionId") }) { "An added button was not exercised" }
            ComponentKind.TEXT -> if (c.has("expression")) require(assertions.any { it.optString("component") == c.getString("id") }) { "An added calculated text output has no observable check" }
            ComponentKind.SUMMARY, ComponentKind.RECORD_LIST, ComponentKind.TIMER, ComponentKind.QUIZ ->
                require(assertions.any { it.optString("component") == c.getString("id") }) { "An added capability has no observable check" }
            else -> Unit
        } }
    }
    fun compose(current: AppSpec, fragment: JSONObject): AppSpec {
        require(fragment.keys().asSequence().all { it in setOf("screenId", "components", "screens", "entities", "actions", "checks") }) { "Return one feature fragment with screenId, components, screens, entities, actions and checks only" }
        require(listOf("components", "screens", "entities", "actions").any { fragment.objects(it).isNotEmpty() }) { "The feature is empty. Configure real controls and behavior for the requested workflow." }
        val j = current.json()
        fun merge(previous: JSONArray, additions: List<JSONObject>): JSONArray {
            val ids = (0 until previous.length()).map { previous.getJSONObject(it).getString("id") }.toSet()
            require(additions.map { it.getString("id") }.distinct().size == additions.size)
            require(additions.none { it.getString("id") in ids }) { "A feature attempted to overwrite existing IDs; use a scoped edit instead" }
            return JSONArray((0 until previous.length()).map { previous.getJSONObject(it) } + additions)
        }
        val screenId = fragment.optString("screenId", current.startScreen)
        val screens = j.getJSONArray("screens")
        val index = (0 until screens.length()).firstOrNull { screens.getJSONObject(it).getString("id") == screenId }
        require(index != null) { "The feature's destination screen is missing" }
        val screen = screens.getJSONObject(index)
        screen.put("components", merge(screen.getJSONArray("components"), fragment.objects("components")))
        j.put("screens", merge(screens, fragment.objects("screens")))
        j.put("entities", merge(j.getJSONArray("entities"), fragment.objects("entities")))
        j.put("actions", merge(j.getJSONArray("actions"), fragment.objects("actions")))
        return AppSpec.parse(j.toString())
    }

    /** Scratch Room storage exercises production actions; no user's records or state are touched. */
    fun verify(context: Context, spec: AppSpec, feature: FeaturePlan, checks: List<JSONObject>): JSONObject {
        require(checks.size == feature.checks.size) { "Provide one independent probe per approved acceptance check" }
        val db = Room.inMemoryDatabaseBuilder(context, BuildAiDatabase::class.java).allowMainThreadQueries().build()
        try {
            val project = Conversation(title = "Verification scratch")
            db.projects().save(StoredProject(project.id, project.toJson().toString()))
            val repo = NativeAppRepository(context, db)
            repo.publish(project.id, spec, null, "Isolated verification")
            val results = checks.mapIndexed { index, check ->
                require(check.getString("name") == feature.checks[index]) { "Probe must identify the approved check" }
                db.nativeApps().deleteRecords(project.id); repo.saveRuntime(project.id, JSONObject())
                var inputs = check.optJSONObject("inputs")?.stringMap().orEmpty()
                val validInputs = spec.screens.flatMap { it.components }.filter { it.kind == ComponentKind.INPUT }.map { it.id }.toSet()
                fun validateInputs() {
                    require(inputs.size <= 80 && inputs.values.all { it.length <= 4096 })
                    require(inputs.keys.all { it in validInputs }) { "Probe inputs must map actual input IDs to test values, without field descriptions or invented IDs" }
                }
                validateInputs()
                val steps = check.objects("steps"); require(steps.size <= 24)
                steps.forEach { step ->
                    inputs = inputs + step.optJSONObject("inputs")?.stringMap().orEmpty()
                    step.optJSONObject("inputRecords")?.let { bindings -> bindings.keys().asSequence().forEach { key ->
                        val selector = bindings.getJSONObject(key)
                        val record = repo.records(project.id)[selector.getString("entity")].orEmpty().getOrNull(selector.optInt("index")) ?: error("A relationship probe selected a missing record")
                        inputs = inputs + (key to record.getValue("id").toString())
                    } }
                    validateInputs()
                    val selected = step.optJSONObject("record")
                    val row = if (selected == null) emptyMap() else repo.records(project.id)[selected.getString("entity")].orEmpty()
                        .getOrNull(selected.optInt("index")) ?: error("Probe selected a missing record")
                    val actionId = step.getString("action")
                    require(spec.actions.any { it.id == actionId }) { "Probe references missing action $actionId. Use a declared action ID, or no steps for a live calculation." }
                    repo.saveRuntime(project.id, repo.runtime(project.id).put("inputs", JSONObject(inputs)))
                    val expectation = if (step.has("expect")) step.getString("expect") else null
                    require(expectation == null || expectation == "conditionRejected") { "Unknown action-check expectation" }
                    if (expectation == "conditionRejected") {
                        require(spec.actions.first { it.id == actionId }.condition != null) { "Only a conditional action can have a rejection check" }
                        val beforeRecords = repo.dao.allRecords(project.id)
                        val beforeRuntime = repo.runtime(project.id).toString()
                        val failure = runCatching { repo.perform(project.id, actionId, inputs, row) }.exceptionOrNull()
                        require(failure is ActionConditionNotMet) { "Expected the action's condition to reject these inputs" }
                        require(repo.dao.allRecords(project.id) == beforeRecords && repo.runtime(project.id).toString() == beforeRuntime) { "Rejected action changed saved data or runtime" }
                    } else {
                        val after = repo.perform(project.id, actionId, inputs, row)
                        inputs = after.optJSONObject("inputs")?.stringMap() ?: inputs
                    }
                }
                val assertions = check.objects("assertions"); require(assertions.size in 1..16) { "A probe needs an observable assertion" }
                val records = repo.records(project.id)
                val ctx = ExpressionContext.forSpec(spec, inputs, records = records)
                assertions.forEach { assertion ->
                    when {
                        assertion.has("component") -> {
                            val componentId = assertion.getString("component")
                            val component = spec.screens.flatMap { it.components }.firstOrNull { it.id == componentId }
                                ?: error("Probe references missing component $componentId. Create the real input and output controls, and assert their IDs.")
                            when (component.kind) {
                                ComponentKind.TEXT, ComponentKind.SUMMARY -> {
                                    val actual = component.expression?.let { Expressions.display(Expressions.evaluate(it, ctx)) } ?: component.label
                                    require(actual == assertion.getString("equals")) { "${feature.title}: expected ${assertion.getString("equals")}, got $actual" }
                                }
                                ComponentKind.RECORD_LIST -> {
                                    val visible = RecordQueries.visible(spec, component, ctx)
                                    require(visible.size == assertion.getInt("count")) { "${feature.title}: search/list count differs" }
                                }
                                ComponentKind.TIMER -> require((repo.runtime(project.id).optJSONObject("timers")?.optLong(component.id) ?: 0L) > System.currentTimeMillis()) { "The timer did not start" }
                                ComponentKind.QUIZ -> {
                                    val q = component.questions.first { it.id == assertion.getString("question") }
                                    require(q.options[q.answer] == assertion.getString("equals")) { "Quiz answer differs from the approved check" }
                                }
                                else -> error("This component has no supported probe assertion")
                            }
                        }
                        assertion.has("entity") -> {
                            val rows = records[assertion.getString("entity")].orEmpty()
                            if (assertion.has("count")) require(rows.size == assertion.getInt("count")) { "${feature.title}: saved record count differs" }
                            if (assertion.has("field")) require(rows.getOrNull(assertion.optInt("index"))?.get(assertion.getString("field"))?.let(Expressions::display) == assertion.getString("equals")) { "${feature.title}: saved value differs" }
                            require(assertion.has("count") || assertion.has("field"))
                        }
                        assertion.has("screen") -> require(repo.runtime(project.id).optString("screen", spec.startScreen) == assertion.getString("screen")) { "Navigation did not reach the expected screen" }
                        else -> error("Unknown probe assertion")
                    }
                }
                JSONObject().put("check", feature.checks[index]).put("result", "passed").put("steps", steps.size).put("assertions", assertions.size)
            }
            return JSONObject().put("feature", feature.id).put("results", JSONArray(results)).put("method", "Production actions and expressions in isolated Room storage; UI coverage is a separate gate")
        } finally { db.close() }
    }
    private fun JSONObject.stringMap() = keys().asSequence().associateWith { getString(it) }
}
