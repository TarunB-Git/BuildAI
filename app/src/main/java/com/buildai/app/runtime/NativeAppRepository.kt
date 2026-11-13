package com.buildai.app.runtime

import android.content.Context
import com.buildai.app.data.storage.BuildAiDatabase
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class ActionConditionNotMet : IllegalArgumentException("This action is not available with the current inputs")

/** Definitions/revisions are separate from records; editing never replaces record storage. */
class NativeAppRepository(context: Context, private val database: BuildAiDatabase = BuildAiDatabase.get(context)) {
    val dao = database.nativeApps()
    fun spec(appId: String): AppSpec? = dao.head(appId)?.let { dao.revision(it.revisionId) }?.let { AppSpec.parse(it.specJson) }
    fun publish(appId: String, spec: AppSpec, expectedRevision: String?, summary: String,
        verification: JSONObject? = null, expectedBriefVersion: Int? = null): ProjectRevision {
        AppSpecValidator.validate(spec)
        val definition = spec.copy(screens = spec.screens.map { screen -> screen.copy(components = screen.components.map { c ->
            if (c.kind == ComponentKind.RECORD_LIST && c.displayFields.isEmpty())
                c.copy(displayFields = spec.entities.first { it.id == c.entityId }.fields.map { it.id }) else c
        }) })
        require(summary.isNotBlank() && summary.length <= 500)
        var published: ProjectRevision? = null
        database.runInTransaction {
            require(database.projects().load(appId) != null) { "The project is missing" }
            if (expectedBriefVersion != null) {
                val current = com.buildai.app.data.model.Conversation.fromJson(JSONObject(checkNotNull(database.projects().load(appId)).json)).brief
                check(current?.version == expectedBriefVersion && current.approvedVersion == expectedBriefVersion) {
                    "Your requirements changed during the build. The existing app is preserved; review the new plan."
                }
            }
            val head = dao.head(appId)
            check(head?.revisionId == expectedRevision) { "The app changed while this edit was prepared. Review the latest revision." }
            head?.let { AppSpecValidator.additive(AppSpec.parse(checkNotNull(dao.revision(it.revisionId)).specJson), spec) }
            val revision = ProjectRevision(UUID.randomUUID().toString(), appId, expectedRevision, definition.json().toString(), summary,
                (verification ?: JSONObject().put("structure", "passed").put("featureAcceptance", "not yet recorded")).toString(), System.currentTimeMillis())
            dao.addRevision(revision)
            dao.saveHead(NativeAppHead(appId, revision.id, head?.runtimeJson ?: "{}"))
            published = revision
        }
        return checkNotNull(published)
    }
    fun preview(appId: String, patch: AppPatch): AppSpec {
        check(dao.head(appId)?.revisionId == patch.baseRevisionId) { "This edit is stale. Review the latest app." }
        return patch.applyTo(checkNotNull(spec(appId)))
    }
    fun apply(appId: String, patch: AppPatch): ProjectRevision = publish(appId, preview(appId, patch), patch.baseRevisionId, patch.summary)
    fun undo(appId: String, targetRevision: String, expectedRevision: String): ProjectRevision {
        val target = checkNotNull(dao.revision(targetRevision)); require(target.appId == appId)
        val current = checkNotNull(spec(appId)); val previous = AppSpec.parse(target.specJson)
        // Restore controls/actions while retaining today's additive record schema/data.
        return publish(appId, previous.copy(entities = current.entities), expectedRevision, "Restore: ${target.summary}")
    }
    fun records(appId: String): Map<String, List<Map<String, Any>>> = dao.records(appId).groupBy { it.entityId }.mapValues { (_, rows) ->
        rows.map { row -> jsonValues(JSONObject(row.json)) + ("id" to row.id) }
    }
    fun runtime(appId: String): JSONObject = dao.head(appId)?.runtimeJson?.let(::JSONObject) ?: JSONObject()
    fun saveRuntime(appId: String, runtime: JSONObject) {
        NativeNavigation.trimHistory(runtime)
        require(runtime.toString().length <= 65_536) { "App input state exceeds supported size" }
        database.runInTransaction { dao.head(appId)?.let { dao.saveHead(it.copy(runtimeJson = runtime.toString())) } }
    }
    fun navigateTopLevel(appId: String, screenId: String): JSONObject {
        var result = JSONObject()
        database.runInTransaction {
            val spec = checkNotNull(spec(appId))
            require(NativeNavigation.topLevelScreens(spec).any { it.id == screenId }) {
                "Open this screen through its connected action so it receives the required context"
            }
            result = runtime(appId)
            if (result.optString("screen", spec.startScreen) != screenId) {
                NativeNavigation.enter(spec, result, screenId)
                result.remove("selectedRecord")
                result.remove("selectedEntity")
                result.put("uiVersion", result.optLong("uiVersion") + 1)
                saveRuntime(appId, result)
            }
        }
        return result
    }
    fun back(appId: String): JSONObject? {
        var result: JSONObject? = null
        database.runInTransaction {
            val next = runtime(appId)
            if (NativeNavigation.back(checkNotNull(spec(appId)), next)) {
                next.put("uiVersion", next.optLong("uiVersion") + 1)
                saveRuntime(appId, next)
                result = next
            }
        }
        return result
    }
    fun perform(appId: String, actionId: String, inputs: Map<String, String>, row: Map<String, Any> = emptyMap()): JSONObject {
        var result = JSONObject()
        database.runInTransaction {
            result = runtime(appId)
            val spec = checkNotNull(spec(appId)); val action = checkNotNull(spec.actions.firstOrNull { it.id == actionId }) { "The app changed. Open its latest screen and try again." }
            val currentRecords = records(appId)
            val selectedEntity = if (action.kind in setOf(ActionKind.UPDATE, ActionKind.DELETE)) action.target
                else result.optString("selectedEntity").takeIf { it.isNotBlank() }
            val snapshot = if (row.isNotEmpty()) row else result.optJSONObject("selectedRecord")?.let { saved -> saved.keys().asSequence().associateWith { saved.get(it) } }.orEmpty()
            val current = SelectedRecord.resolve(snapshot, currentRecords, selectedEntity)
                ?: SelectedRecord.resolve(snapshot, currentRecords)
            check(row.isEmpty() || current != null) { "The selected record is no longer available. Return to the list and select a saved item." }
            val ctx = ExpressionContext.forSpec(spec, inputs, current?.second.orEmpty(), currentRecords, current?.first)
            if (action.condition != null && Expressions.evaluate(action.condition, ctx) != true) throw ActionConditionNotMet()
            val values = action.values.mapValues { Expressions.evaluate(it.value, ctx) }
            val stateInputs = result.optJSONObject("inputs") ?: JSONObject()
            when (action.kind) {
                ActionKind.INSERT, ActionKind.UPDATE -> {
                    val entity = spec.entities.first { it.id == action.target }
                    val id = if (action.kind == ActionKind.INSERT) UUID.randomUUID().toString()
                        else Expressions.display(Expressions.evaluate(checkNotNull(action.recordId), ctx))
                    val before = dao.record(appId, entity.id, id)
                    if (action.kind == ActionKind.UPDATE) check(before != null && !before.deleted) { "The selected record is no longer available" }
                    if (action.kind == ActionKind.INSERT) require(dao.records(appId).size < 5000) { "This version supports 5,000 active records per app" }
                    val record = before?.json?.let(::JSONObject) ?: JSONObject()
                    values.forEach { (key, value) ->
                        val field = entity.fields.first { it.id == key }
                        val text = Expressions.display(value).let { if (field.kind == FieldKind.BOOLEAN && it.isBlank()) "false" else it }
                        require(text.length <= 4096)
                        val typed: Any = when (field.kind) {
                            FieldKind.TEXT -> text
                            FieldKind.NUMBER -> if (text.isBlank() && !field.required) "" else AppSpecValidator.decimal(text).toPlainString()
                            FieldKind.BOOLEAN -> text.toBooleanStrict()
                            FieldKind.DATE -> if (text.isBlank() && !field.required) "" else LocalDate.parse(text).toString()
                            FieldKind.REFERENCE -> {
                                if (text.isNotBlank()) require(dao.record(appId, field.referenceEntity, text)?.deleted == false) { "Choose an existing ${field.label} record" }
                                text
                            }
                        }
                        if (field.required) require(text.isNotBlank()) { "${field.label} is required" }
                        record.put(key, typed)
                    }
                    entity.fields.filter { it.required }.forEach { field -> require(record.has(field.id) && record.optString(field.id).isNotBlank()) { "${field.label} is required" } }
                    require(record.toString().length <= 16_384) { "This record exceeds the supported size" }
                    dao.saveRecord(AppRecord(appId, entity.id, id, record.toString(), createdAt = before?.createdAt ?: System.currentTimeMillis()))
                }
                ActionKind.DELETE -> {
                    val id = Expressions.display(Expressions.evaluate(checkNotNull(action.recordId), ctx))
                    require(spec.entities.none { entity -> entity.fields.any { field ->
                        field.kind == FieldKind.REFERENCE && field.referenceEntity == action.target && ctx.records[entity.id].orEmpty().any { linked ->
                            linked[field.id]?.toString() == id && !(entity.id == action.target && linked["id"]?.toString() == id)
                        }
                    } }) { "This record is used by another saved item. Remove those links before deleting it." }
                    val before = checkNotNull(dao.record(appId, action.target, id)); dao.saveRecord(before.copy(deleted = true))
                }
                ActionKind.NAVIGATE, ActionKind.ASSIGN -> {
                    if (action.kind == ActionKind.NAVIGATE) NativeNavigation.enter(spec, result, action.target)
                    values.forEach { (key, value) -> stateInputs.put(key, Expressions.display(value)) }
                    result.put("inputs", stateInputs)
                    if (current != null) result.put("selectedRecord", JSONObject(current.second)).put("selectedEntity", current.first)
                    if (action.kind == ActionKind.NAVIGATE && NativeNavigation.topLevelScreens(spec).any { it.id == action.target }) { result.remove("selectedRecord"); result.remove("selectedEntity") }
                }
                ActionKind.START_TIMER -> {
                    val seconds = AppSpecValidator.decimal(Expressions.display(Expressions.evaluate(checkNotNull(action.duration), ctx))).longValueExact()
                    require(seconds in 1..604_800) { "Timer duration must be between one second and seven days" }
                    val timers = result.optJSONObject("timers") ?: JSONObject()
                    timers.put(action.target, System.currentTimeMillis() + seconds * 1000); result.put("timers", timers)
                }
                ActionKind.STOP_TIMER -> result.optJSONObject("timers")?.remove(action.target)
            }
            // Refresh selection after writes, including deletion. Old snapshots never
            // drive the next action, even when a screen was left open during an update.
            result.optJSONObject("selectedRecord")?.let { snapshot ->
                val values = snapshot.keys().asSequence().associateWith { snapshot.get(it) }
                val fresh = SelectedRecord.resolve(values, records(appId), result.optString("selectedEntity").takeIf { it.isNotBlank() })
                if (fresh == null) { result.remove("selectedRecord"); result.remove("selectedEntity") }
                else result.put("selectedRecord", JSONObject(fresh.second)).put("selectedEntity", fresh.first)
            }
            NativeNavigation.trimHistory(result)
            require(result.toString().length <= 65_536) { "Save or clear some unsaved inputs before continuing" }
            result.put("uiVersion", result.optLong("uiVersion") + 1)
            val head = checkNotNull(dao.head(appId)); dao.saveHead(head.copy(runtimeJson = result.toString()))
        }
        return result
    }
    fun delete(appId: String) = database.runInTransaction { dao.deleteRecords(appId); dao.deleteRevisions(appId); dao.deleteHead(appId) }
    private fun jsonValues(j: JSONObject): Map<String, Any> = j.keys().asSequence().associateWith { j.get(it) }
}
