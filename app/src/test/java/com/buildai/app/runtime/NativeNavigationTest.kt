package com.buildai.app.runtime

import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.TabSpec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeNavigationTest {
    private fun spec(): AppSpec = AppSpec("Connected notes", "home", listOf(
        ScreenSpec("home", "Notes", listOf(ComponentSpec("draft", ComponentKind.INPUT, "New note"),
            ComponentSpec("notes", ComponentKind.RECORD_LIST, "Notes", entityId = "notes", rowActions = listOf("open")))),
        ScreenSpec("detail", "Details", listOf(ComponentSpec("body", ComponentKind.INPUT, "Note"),
            ComponentSpec("review", ComponentKind.BUTTON, "Review", actionId = "review"))),
        ScreenSpec("confirm", "Confirmation", listOf(ComponentSpec("selected", ComponentKind.TEXT, expression = Expr(ExpressionOp.RECORD, "body")))),
        ScreenSpec("quiz", "Quiz", emptyList()),
        ScreenSpec("guarded", "Restricted", emptyList())
    ), listOf(EntitySpec("notes", listOf(FieldSpec("body", "Note")))), listOf(
        ActionSpec("open", ActionKind.NAVIGATE, target = "detail", values = mapOf("body" to Expr(ExpressionOp.RECORD, "body"))),
        ActionSpec("review", ActionKind.NAVIGATE, target = "confirm"),
        ActionSpec("guard", ActionKind.NAVIGATE, target = "guarded", condition = Expr(ExpressionOp.BOOLEAN, "true"))
    ))

    @Test fun globalNavigationExcludesContextualConditionalAndChainedScreens() {
        val app = spec().also(AppSpecValidator::validate)
        assertEquals(listOf("home", "quiz"), NativeNavigation.topLevelScreens(app).map { it.id })
        // More than five total screens does not require abandoning a two-item bottom bar.
        val extended = app.copy(screens = app.screens + ScreenSpec("last", "Last step", emptyList()),
            actions = app.actions + ActionSpec("last", ActionKind.NAVIGATE, target = "last", values = mapOf("body" to Expr.text("bound"))))
        assertEquals(2, NativeNavigation.topLevelScreens(extended).size)
    }

    @Test fun rowNavigationIsProtectedEvenWhenItHasNoBindings() {
        val app = spec().copy(actions = spec().actions.map { if (it.id == "open") it.copy(values = emptyMap()) else it })
        assertFalse(NativeNavigation.topLevelScreens(app).any { it.id == "detail" })
    }

    @Test fun backRestoresEachScreensInputsAndSelectedRecordAfterSerialization() {
        val app = spec()
        val state = JSONObject().put("inputs", JSONObject().put("draft", "Unsent draft"))
        NativeNavigation.enter(app, state, "detail")
        state.put("selectedRecord", JSONObject().put("id", "note-one").put("body", "Original"))
        state.getJSONObject("inputs").put("body", "Edited text")
        NativeNavigation.enter(app, state, "confirm")
        state.getJSONObject("inputs").put("body", "Other text")
        state.put("selectedRecord", JSONObject().put("id", "note-two"))
        val reopened = JSONObject(state.toString())
        assertTrue(NativeNavigation.back(app, reopened))
        assertEquals("detail", reopened.getString("screen"))
        assertEquals("Edited text", reopened.getJSONObject("inputs").getString("body"))
        assertEquals("note-one", reopened.getJSONObject("selectedRecord").getString("id"))
        assertTrue(NativeNavigation.back(app, reopened))
        assertEquals("home", reopened.getString("screen"))
        assertEquals("Unsent draft", reopened.getJSONObject("inputs").getString("draft"))
        assertFalse(reopened.has("selectedRecord"))
        assertFalse(NativeNavigation.back(app, reopened))
    }

    @Test fun oldAndRevisedRuntimesHaveASafeRouteHome() {
        val state = JSONObject().put("screen", "detail").put("selectedRecord", JSONObject().put("id", "old"))
        assertTrue(NativeNavigation.back(spec(), state))
        assertEquals("home", state.getString("screen")); assertFalse(state.has("selectedRecord"))
        NativeNavigation.enter(spec(), state, "detail")
        NativeNavigation.enter(spec(), state, "confirm")
        val revised = spec().copy(screens = spec().screens.filterNot { it.id == "detail" })
        assertTrue(NativeNavigation.back(revised, state))
        assertEquals("home", state.getString("screen"))
    }

    @Test fun historyIsBoundedWithoutDiscardingCurrentInputs() {
        val state = JSONObject().put("inputs", JSONObject().put("draft", "Keep this"))
        repeat(100) { NativeNavigation.enter(spec(), state, if (it % 2 == 0) "quiz" else "home") }
        assertTrue(state.getJSONArray("navigationHistory").length() <= 20)
        state.getJSONObject("inputs").put("large", "x".repeat(64_000))
        NativeNavigation.trimHistory(state)
        assertTrue(state.toString().length <= 65_536)
        assertEquals("Keep this", state.getJSONObject("inputs").getString("draft"))
    }

    @Test fun boundTabNamesFollowScreenIdsAcrossReorderingAndJsonRoundTrip() {
        val appearance = DesignSpec(templateId = "tabbed", tabs = listOf(TabSpec("My notes", "book", "home"), TabSpec("Practice", "school", "quiz")))
        val stored = DesignSpec.fromJson(appearance.toJson())
        assertEquals(appearance, stored)
        val reordered = spec().copy(screens = spec().screens.reversed())
        val tabs = NativeNavigation.tabs(reordered, stored).associateBy { it.screenId }
        assertEquals("My notes", tabs.getValue("home").name)
        assertEquals("Practice", tabs.getValue("quiz").name)
    }

    @Test fun legacyTabMigrationUsesOriginalScreenPositionsBeforeHidingDetails() {
        val appearance = DesignSpec(tabs = listOf(TabSpec("Library"), TabSpec("Edit"), TabSpec("Confirm"), TabSpec("Practice"), TabSpec("Private")))
        val tabs = NativeNavigation.tabs(spec(), appearance)
        assertEquals(listOf("Library", "Practice"), tabs.map { it.name })
        assertEquals(listOf("home", "quiz"), tabs.map { it.screenId })
    }

    @Test fun newNativeTabsUseActualScreenNamesAndRetainMoreThanFiveStableBindings() {
        assertEquals(listOf("Notes", "Quiz"), NativeNavigation.tabs(spec(), DesignSpec(tabs = emptyList())).map { it.name })
        val tabs = (1..12).map { TabSpec("Section $it", screenId = "section$it") }
        assertEquals(tabs, DesignSpec.fromJson(DesignSpec(tabs = tabs).toJson()).tabs)
    }
}
