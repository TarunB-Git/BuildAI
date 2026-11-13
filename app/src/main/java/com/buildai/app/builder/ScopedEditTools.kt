package com.buildai.app.builder

import com.buildai.app.runtime.*
import org.json.JSONArray
import org.json.JSONObject

data class EditScope(val screenId: String, val componentIds: Set<String>) {
    fun json() = JSONObject().put("screenId", screenId).put("componentIds", JSONArray(componentIds.toList()))
    companion object { fun read(j: JSONObject) = EditScope(j.getString("screenId"), j.strings("componentIds").toSet()) }
}
object ScopedEditTools {
    fun prepare(original: AppSpec, scope: EditScope, response: JSONObject): AppSpec {
        require(response.keys().asSequence().all { it in setOf("summary", "components", "removeComponents", "actions", "newFields", "entities", "checks") })
        val target = original.screens.first { it.id == scope.screenId }
        require(scope.componentIds.all { id -> target.components.any { it.id == id } })
        val allIds = original.screens.flatMap { it.components }.map { it.id }.toSet()
        val allowedActions = target.components.filter { it.id in scope.componentIds }.flatMap { listOf(it.actionId) + it.rowActions }.filter(String::isNotBlank).toSet()
        val controls = response.objects("components"); val removed = response.strings("removeComponents").toSet()
        require(controls.size + removed.size + response.objects("actions").size + response.objects("newFields").size <= 16) { "Keep each edit within sixteen scoped changes" }
        require(controls.all { it.getString("id") !in allIds || it.getString("id") in scope.componentIds }) { "An unrelated control was changed" }
        require(removed.all { it in scope.componentIds }) { "An unrelated control was removed" }
        val actions = response.objects("actions")
        require(actions.all { a -> original.actions.none { it.id == a.getString("id") } || a.getString("id") in allowedActions }) { "An unrelated action was changed" }
        val unselected = original.screens.flatMap { it.components }.filterNot { it.id in scope.componentIds }
        fun replace(before: List<JSONObject>, changes: List<JSONObject>): JSONArray {
            require(changes.map { it.getString("id") }.distinct().size == changes.size)
            val map = changes.associateBy { it.getString("id") }
            return JSONArray(before.map { map[it.getString("id")] ?: it } + changes.filter { next -> before.none { it.getString("id") == next.getString("id") } })
        }
        val j = original.json()
        val screens = j.getJSONArray("screens")
        val screen = (0 until screens.length()).map { screens.getJSONObject(it) }.first { it.getString("id") == scope.screenId }
        screen.put("components", replace(target.components.filterNot { it.id in removed }.map { it.json() }, controls))
        j.put("actions", replace(original.actions.map { it.json() }, actions))
        val entities = original.entities.map { it.json() }.toMutableList()
        response.objects("newFields").forEach { addition ->
            val entity = entities.first { it.getString("id") == addition.getString("entity") }
            val field = addition.getJSONObject("field")
            require(!field.optBoolean("required")) { "New fields must be optional for existing records" }
            val fields = entity.getJSONArray("fields")
            require((0 until fields.length()).none { fields.getJSONObject(it).getString("id") == field.getString("id") })
            fields.put(field)
        }
        val additions = response.objects("entities")
        require(additions.none { next -> original.entities.any { it.id == next.getString("id") } })
        j.put("entities", JSONArray(entities + additions))
        val result = AppSpec.parse(j.toString()); AppSpecValidator.additive(original, result)
        result.actions.forEach { action ->
            val before = original.actions.firstOrNull { it.id == action.id }
            if (before != null && before != action) require(unselected.none { it.actionId == action.id || action.id in it.rowActions }) {
                "This action is shared with other controls. Add a new action ID and connect only the selected controls to it."
            }
        }
        require(result != original) { "The proposal makes no change. Change the requested behavior before offering it for review." }
        return result
    }
    fun changedIds(original: AppSpec, next: AppSpec): List<String> {
        val before = original.screens.flatMap { it.components }.associateBy { it.id }
        val after = next.screens.flatMap { it.components }.associateBy { it.id }
        return (before.keys + after.keys).filter { before[it] != after[it] } +
            next.actions.filter { a -> original.actions.firstOrNull { it.id == a.id } != a }.map { "action:${it.id}" } +
            next.entities.flatMap { entity ->
                val prior = original.entities.firstOrNull { it.id == entity.id }
                if (prior == null) listOf("entity:${entity.id}") else entity.fields.filter { field -> prior.fields.none { it == field } }.map { "field:${entity.id}:${it.id}" }
            }
    }

    fun changeLabel(original: AppSpec, next: AppSpec, key: String): String {
        if (key.startsWith("action:")) {
            val id = key.removePrefix("action:")
            val action = next.actions.first { it.id == id }
            if (action.kind == ActionKind.NAVIGATE) {
                val destination = next.screens.first { it.id == action.target }.title
                return "${action.label.ifBlank { "Open screen" }} → $destination"
            }
            return action.label.ifBlank { action.kind.name.lowercase().replace('_', ' ') }
        }
        if (key.startsWith("entity:")) return "New saved collection: ${key.removePrefix("entity:")}"
        if (key.startsWith("field:")) {
            val (_, entityId, fieldId) = key.split(':')
            val field = next.entities.first { it.id == entityId }.fields.first { it.id == fieldId }
            return "New ${field.label} field in $entityId; existing records retained"
        }
        val control = next.screens.flatMap { it.components }.firstOrNull { it.id == key }
            ?: original.screens.flatMap { it.components }.first { it.id == key }
        val label = control.label.ifBlank { control.kind.name.lowercase().replace('_', ' ') }
        return if (next.screens.none { s -> s.components.any { it.id == key } }) "Remove $label; saved records retained" else label
    }
}
