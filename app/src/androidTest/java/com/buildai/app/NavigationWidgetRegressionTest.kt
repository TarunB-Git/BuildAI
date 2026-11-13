package com.buildai.app

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.model.*
import com.buildai.app.runtime.*
import com.buildai.app.ui.screens.AppsScreen
import com.buildai.app.ui.theme.BuildAITheme
import com.buildai.app.viewmodel.HomeViewModel
import com.buildai.app.widgets.ProjectWidgetProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class NavigationWidgetRegressionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private val repo = NativeAppRepository(app)
    private fun connectedNotes() = AppSpec("Connected note fixture", "home", listOf(
        ScreenSpec("home", "Library", listOf(
            ComponentSpec("newNote", ComponentKind.INPUT, "New note"),
            ComponentSpec("notes", ComponentKind.RECORD_LIST, "Choose a note", entityId = "notes", rowActions = listOf("read")))),
        ScreenSpec("detail", "Details", listOf(
            ComponentSpec("body", ComponentKind.INPUT, "Edit note"),
            ComponentSpec("review", ComponentKind.BUTTON, "Review note", actionId = "review"))),
        ScreenSpec("review", "Review", listOf(ComponentSpec("selected", ComponentKind.TEXT,
            expression = Expr(ExpressionOp.CONCAT, args = listOf(Expr.text("Selected note: "), Expr(ExpressionOp.RECORD, "body"))))))
    ), listOf(EntitySpec("notes", listOf(FieldSpec("body", "Note", required = true)))), listOf(
        ActionSpec("save", ActionKind.INSERT, target = "notes", values = mapOf("body" to Expr.input("newNote"))),
        ActionSpec("read", ActionKind.NAVIGATE, "Read note", "detail", mapOf("body" to Expr(ExpressionOp.RECORD, "body"))),
        ActionSpec("review", ActionKind.NAVIGATE, "Review note", "review")
    ))
    private fun install(spec: AppSpec, design: DesignSpec? = null): String {
        val project = Conversation(title = spec.name, designSpec = design).also(app.container.projects::save)
        repo.publish(project.id, spec, null, "Navigation/widget regression fixture")
        return project.id
    }
    private fun waitText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun snapshot(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(app.cacheDir, "revision-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun recordActionsSupplyContextAndBackRestoresThePreviousDetailScreen() {
        val id = install(connectedNotes(), DesignSpec(templateId = "tabbed"))
        try {
            repo.perform(id, "save", mapOf("newNote" to "Original note"))
            ActivityScenario.launch<NativeRunnerActivity>(Intent(app, NativeRunnerActivity::class.java).putExtra("appId", id)).use { scenario ->
                waitText("Choose a note")
                compose.onNode(hasText("Details") and hasClickAction()).assertDoesNotExist()
                compose.onNode(hasText("Review") and hasClickAction()).assertDoesNotExist()
                compose.onNodeWithText("Read note").performScrollTo().performClick()
                waitText("Edit note")
                compose.onNode(hasSetTextAction() and hasText("Edit note")).assertTextContains("Original note").performTextReplacement("Unsent edit")
                compose.onNodeWithText("Review note").performScrollTo().performClick()
                waitText("Selected note: Original note")
                scenario.recreate()
                waitText("Selected note: Original note")
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                waitText("Edit note")
                compose.onNode(hasSetTextAction() and hasText("Edit note")).assertTextContains("Unsent edit")
                assertEquals("detail", repo.runtime(id).getString("screen"))
                snapshot("navigation-detail-back")
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                waitText("Choose a note")
                assertEquals("home", repo.runtime(id).getString("screen"))
                assertFalse(repo.runtime(id).has("selectedRecord"))
                snapshot("navigation-library")
            }
        } finally { app.container.projects.delete(id) }
    }

    @Test fun guardedScreensCannotBeOpenedThroughTheGlobalNavigationApi() {
        val spec = connectedNotes().copy(actions = connectedNotes().actions.map { action ->
            if (action.id == "read") action.copy(condition = Expr(ExpressionOp.BOOLEAN, "false")) else action
        })
        val id = install(spec)
        try {
            repo.perform(id, "save", mapOf("newNote" to "Private note"))
            val before = repo.runtime(id).toString()
            assertTrue(runCatching { repo.navigateTopLevel(id, "detail") }.isFailure)
            assertEquals(before, repo.runtime(id).toString())
            assertTrue(runCatching { repo.perform(id, "read", emptyMap(), repo.records(id).getValue("notes").single()) }.exceptionOrNull() is ActionConditionNotMet)
            assertEquals(before, repo.runtime(id).toString())
        } finally { app.container.projects.delete(id) }
    }

    @Test fun tabLabelsStillOpenTheirBoundScreensAfterASpecReorder() {
        val spec = ManualExamples.notes()
        val id = install(spec, DesignSpec(templateId = "tabbed", tabs = listOf(TabSpec("My library", "book", "home"), TabSpec("Practice", "school", "quiz"))))
        try {
            repo.publish(id, spec.copy(screens = spec.screens.reversed()), checkNotNull(repo.dao.head(id)).revisionId, "Reorder screens")
            ActivityScenario.launch<NativeRunnerActivity>(Intent(app, NativeRunnerActivity::class.java).putExtra("appId", id)).use {
                waitText("My library")
                compose.onNodeWithText("Practice").performClick()
                waitText("What is 2 + 2?")
                assertEquals("quiz", repo.runtime(id).getString("screen"))
                snapshot("navigation-stable-tabs")
            }
        } finally { app.container.projects.delete(id) }
    }

    @Test fun nativeAppsExposeWidgetAndShortcutActionsInTheLibrary() {
        val id = install(connectedNotes())
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent { BuildAITheme { AppsScreen(HomeViewModel(app), {}, {}, {}) } }
                }
                waitText("Find an app")
                compose.onNode(hasSetTextAction()).performTextReplacement("Connected note fixture")
                waitText("Connected note fixture")
                compose.onNodeWithContentDescription("Options for Connected note fixture").performClick()
                compose.onNodeWithText("Add home-screen widget").assertIsDisplayed().assertHasClickAction()
                compose.onNodeWithText("Add home-screen shortcut").assertIsDisplayed().assertHasClickAction()
                snapshot("apps-widget-menu")
            }
        } finally { app.container.projects.delete(id) }
    }

    @Test fun launcherWidgetsBindNativeAppsAndRefreshAppearanceAndTitles() {
        val id = install(connectedNotes())
        val other = install(ManualExamples.budget())
        val host = AppWidgetHost(app, 20261007)
        val manager = AppWidgetManager.getInstance(app)
        val prefs = app.getSharedPreferences("project_widgets", android.content.Context.MODE_PRIVATE)
        var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        try {
            // The fixture is the latest ready app; no HTML artifact exists for it.
            app.container.projects.update(id) { it.copy(updatedAt = System.currentTimeMillis() + 60_000) }
            instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            widgetId = host.allocateAppWidgetId()
            assertTrue(manager.bindAppWidgetIdIfAllowed(widgetId, ComponentName(app, ProjectWidgetProvider::class.java)))
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            ProjectWidgetProvider.update(app, manager, widgetId)
            assertEquals(id, prefs.getString("project_$widgetId", null))
            app.container.projects.update(other) { it.copy(updatedAt = System.currentTimeMillis() + 120_000) }
            ProjectWidgetProvider.update(app, manager, widgetId)
            assertEquals(id, prefs.getString("project_$widgetId", null))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    host.startListening()
                    activity.setContent { AndroidView(factory = { host.createView(it, widgetId, manager.getAppWidgetInfo(widgetId)) }) }
                }
                onView(withText("Connected note fixture")).check(matches(isDisplayed()))
                app.container.projects.update(id) { it.copy(title = "Renamed native widget", titleOwned = true) }
                ProjectWidgetProvider.refreshProject(app, id)
                instrumentation.waitForIdleSync()
                onView(withText("Renamed native widget")).check(matches(isDisplayed()))
                app.container.projects.saveDesign(id, DesignSpec(iconKey = "book"))
                instrumentation.waitForIdleSync()
                onView(withText(com.buildai.app.widgets.ProjectIcon.glyph("book"))).check(matches(isDisplayed()))
                snapshot("native-widget")
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            host.stopListening()
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) { host.deleteAppWidgetId(widgetId); prefs.edit().remove("project_$widgetId").apply() }
            app.container.projects.delete(id); app.container.projects.delete(other)
        }
    }
}
