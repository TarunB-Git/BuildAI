package com.buildai.app.runtime

import com.buildai.app.data.model.DesignDefaults
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.TabSpec
import org.json.JSONArray
import org.json.JSONObject

/** Global navigation must not skip the actions that establish a detail screen's context. */
object NativeNavigation {
    private const val HISTORY = "navigationHistory"
    private const val MAX_HISTORY = 20

    private fun Expr.readsRecord(): Boolean = op == ExpressionOp.RECORD || args.any { it.readsRecord() }
    private fun ActionSpec.readsRecord(): Boolean = values.values.any { it.readsRecord() } ||
        recordId?.readsRecord() == true || condition?.readsRecord() == true || duration?.readsRecord() == true

    fun topLevelScreens(spec: AppSpec): List<ScreenSpec> {
        val rowActions = spec.screens.flatMap { it.components }.flatMap { it.rowActions }.toSet()
        val protected = spec.actions.filter { it.kind == ActionKind.NAVIGATE &&
            (it.values.isNotEmpty() || it.condition != null || it.id in rowActions) }.map { it.target }.toMutableSet()
        spec.screens.filter { screen -> screen.components.any { c ->
            // Record-list expressions operate on each row, not on the screen's selected record.
            (c.kind != ComponentKind.RECORD_LIST && c.expression?.readsRecord() == true) ||
                (c.kind == ComponentKind.BUTTON && spec.actions.any { it.id == c.actionId && it.readsRecord() })
        } }.mapTo(protected) { it.id }
        // A contextual screen can lead to another contextual step even without new bindings.
        var changed: Boolean
        do {
            val prior = protected.size
            spec.screens.filter { it.id in protected && it.id != spec.startScreen }.forEach { screen ->
                screen.components.filter { it.kind == ComponentKind.BUTTON }.forEach { c ->
                    spec.actions.firstOrNull { it.id == c.actionId && it.kind == ActionKind.NAVIGATE }?.let { protected += it.target }
                }
            }
            changed = protected.size != prior
        } while (changed)
        return spec.screens.filter { it.id == spec.startScreen || it.id !in protected }
    }

    fun tabs(spec: AppSpec, appearance: DesignSpec): List<TabSpec> = topLevelScreens(spec).map { screen ->
        val bound = appearance.tabs.firstOrNull { it.screenId == screen.id }
        // Older native designs indexed tabs against all screens, including detail screens.
        val legacy = appearance.tabs.getOrNull(spec.screens.indexOf(screen))?.takeIf { it.screenId.isBlank() }
        (bound ?: legacy ?: TabSpec(screen.title, DesignDefaults.defaultTabIcon(screen.title, spec.screens.indexOf(screen))))
            .copy(screenId = screen.id)
    }

    fun enter(spec: AppSpec, state: JSONObject, target: String) {
        require(spec.screens.any { it.id == target }) { "The destination screen is missing" }
        val current = spec.screens.firstOrNull { it.id == state.optString("screen", spec.startScreen) }
            ?: spec.screens.first { it.id == spec.startScreen }
        val snapshot = JSONObject().put("screen", current.id)
        state.optJSONObject("selectedRecord")?.let { snapshot.put("selectedRecord", JSONObject(it.toString())) }
        state.optString("selectedEntity").takeIf { it.isNotBlank() }?.let { snapshot.put("selectedEntity", it) }
        val inputs = state.optJSONObject("inputs") ?: JSONObject()
        val savedInputs = JSONObject()
        current.components.filter { it.kind == ComponentKind.INPUT }.forEach { c ->
            if (inputs.has(c.id)) savedInputs.put(c.id, inputs.get(c.id))
        }
        snapshot.put("inputs", savedInputs)
        val history = state.optJSONArray(HISTORY) ?: JSONArray()
        history.put(snapshot)
        state.put(HISTORY, history).put("screen", target)
        trimHistory(state)
    }

    fun back(spec: AppSpec, state: JSONObject): Boolean {
        val history = state.optJSONArray(HISTORY) ?: JSONArray()
        while (history.length() > 0) {
            val snapshot = history.optJSONObject(history.length() - 1)
            history.remove(history.length() - 1)
            val screen = spec.screens.firstOrNull { it.id == snapshot?.optString("screen") } ?: continue
            state.put(HISTORY, history).put("screen", screen.id)
            state.remove("selectedRecord")
            state.remove("selectedEntity")
            snapshot?.optJSONObject("selectedRecord")?.let { state.put("selectedRecord", JSONObject(it.toString())) }
            snapshot?.optString("selectedEntity")?.takeIf { it.isNotBlank() }?.let { state.put("selectedEntity", it) }
            val inputs = state.optJSONObject("inputs") ?: JSONObject()
            val saved = snapshot?.optJSONObject("inputs") ?: JSONObject()
            screen.components.filter { it.kind == ComponentKind.INPUT }.forEach { c ->
                if (saved.has(c.id)) inputs.put(c.id, saved.get(c.id)) else inputs.remove(c.id)
            }
            state.put("inputs", inputs)
            return true
        }
        state.remove(HISTORY)
        // Old saved runtimes have no stack. Give them a safe route home.
        if (state.optString("screen", spec.startScreen) != spec.startScreen) {
            state.put("screen", spec.startScreen).remove("selectedRecord")
            state.remove("selectedEntity")
            return true
        }
        return false
    }

    fun trimHistory(state: JSONObject) {
        val history = state.optJSONArray(HISTORY) ?: return
        while (history.length() > 0 && (history.length() > MAX_HISTORY ||
                history.toString().length > 32_768 || state.toString().length > 65_536)) history.remove(0)
    }
}
