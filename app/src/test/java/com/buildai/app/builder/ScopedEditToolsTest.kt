package com.buildai.app.builder

import com.buildai.app.runtime.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ScopedEditToolsTest {
    @Test fun searchPatchPreservesSaveDeleteQuizAndRecordSchema() {
        val original = ManualExamples.notes()
        val list = original.screens[0].components.first { it.id == "notes" }
        val response = JSONObject().put("components", org.json.JSONArray().put(ComponentSpec("query", ComponentKind.INPUT, "New search").json()).put(list.copy(expression = Expr.input("query")).json()))
        val next = ScopedEditTools.prepare(original, EditScope("home", setOf("notes")), response)
        assertEquals(original.actions, next.actions); assertEquals(original.entities, next.entities)
        assertEquals(original.screens[1], next.screens[1])
        assertEquals(setOf("notes", "query"), ScopedEditTools.changedIds(original, next).toSet())
    }
    @Test fun unrelatedControlsAndActionsCannotBeReplaced() {
        val original = ManualExamples.notes()
        val target = EditScope("home", setOf("notes"))
        val button = original.screens[0].components.first { it.id == "save" }
        assertTrue(runCatching { ScopedEditTools.prepare(original, target, JSONObject().put("components", org.json.JSONArray().put(button.copy(label = "Wrong").json()))) }.isFailure)
        val unrelated = original.actions.first { it.id == "saveNote" }
        assertTrue(runCatching { ScopedEditTools.prepare(original, target, JSONObject().put("actions", org.json.JSONArray().put(unrelated.copy(label = "Wrong").json()))) }.isFailure)
        assertTrue(runCatching { ScopedEditTools.prepare(original, target, JSONObject()) }.exceptionOrNull()?.message.orEmpty().contains("no change"))
    }
    @Test fun sharedActionsCannotLeakBeyondScopeAndFieldAdditionsAppearInReview() {
        val shared = ActionSpec("open", ActionKind.NAVIGATE, "Open", "other")
        val first = ComponentSpec("first", ComponentKind.BUTTON, "Open", actionId = "open")
        val second = first.copy(id = "second")
        val original = AppSpec("Shared routes", "home", listOf(ScreenSpec("home", "Home", listOf(first)), ScreenSpec("other", "Other", listOf(second))), actions = listOf(shared))
        val scope = EditScope("home", setOf("first"))
        val sharedChange = JSONObject().put("actions", org.json.JSONArray().put(shared.copy(target = "home").json()))
        assertTrue(runCatching { ScopedEditTools.prepare(original, scope, sharedChange) }.exceptionOrNull()?.message.orEmpty().contains("shared"))
        val isolated = JSONObject().put("actions", org.json.JSONArray().put(shared.copy(id = "localOpen", target = "home").json()))
            .put("components", org.json.JSONArray().put(first.copy(actionId = "localOpen").json()))
        val next = ScopedEditTools.prepare(original, scope, isolated)
        assertEquals(second, next.screens[1].components.single())
        assertTrue(next.actions.contains(shared))
        assertEquals("Open → Home", ScopedEditTools.changeLabel(original, next, "action:localOpen"))
        val notes = ManualExamples.notes()
        val fields = JSONObject("""{"newFields":[{"entity":"notes","field":{"id":"category","label":"Category","kind":"TEXT","required":false}}]}""")
        val withCategory = ScopedEditTools.prepare(notes, EditScope("home", setOf("notes")), fields)
        assertEquals(listOf("field:notes:category"), ScopedEditTools.changedIds(notes, withCategory))
        assertTrue(ScopedEditTools.changeLabel(notes, withCategory, "field:notes:category").contains("existing records retained"))
    }
    @Test fun incrementalFeatureCannotOverwritePriorControlsAndProbesMustExerciseAddedButtons() {
        val base = AppSpec("Converter", "home", listOf(ScreenSpec("home", "Home", listOf(ComponentSpec("distance", ComponentKind.INPUT, "Distance", FieldKind.NUMBER)))))
        assertTrue(runCatching { FeatureTools.compose(base, JSONObject().put("components", org.json.JSONArray().put(ComponentSpec("distance", ComponentKind.INPUT, "Changed").json()))) }.isFailure)
        val untested = JSONObject().put("components", org.json.JSONArray().put(ComponentSpec("save", ComponentKind.BUTTON, "Save", actionId = "saveRecord").json()))
        assertTrue(runCatching { FeatureTools.coverage(untested) }.isFailure)
    }
    @Test fun searchCheckContextIncludesEarlierSaveInputsButExcludesUnrelatedQuiz() {
        val original = ManualExamples.notes()
        val additions = JSONObject().put("components", org.json.JSONArray()
            .put(ComponentSpec("newQuery", ComponentKind.INPUT, "Search saved notes").json())
            .put(ComponentSpec("matches", ComponentKind.RECORD_LIST, "Matches", entityId = "notes", expression = Expr.input("newQuery")).json()))
        val candidate = FeatureTools.compose(original, additions)
        val context = FeatureTools.probeContext(candidate, additions)
        assertEquals(setOf("note", "tag", "newQuery"), context.inputIds.toSet())
        assertTrue(context.contracts.actions.any { it.id == "saveNote" })
        assertFalse(context.contracts.screens.flatMap { it.components }.any { it.kind == ComponentKind.QUIZ })
        assertEquals(original.entities, context.contracts.entities)
    }

}
