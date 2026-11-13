package com.buildai.app

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.WindowCompat
import com.buildai.app.data.local.QuickPlan
import com.buildai.app.data.model.*
import com.buildai.app.data.storage.UiPreferences
import com.buildai.app.navigation.AppNavigation
import com.buildai.app.runtime.*
import com.buildai.app.ui.theme.*
import com.buildai.app.viewmodel.DesignViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real mobile journeys and screenshots, using only disposable fixtures. */
class MobileRedesignTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private val repo = app.container.projects
    private fun waitText(text: String, activity: Class<out android.app.Activity> = MainActivity::class.java) {
        compose.waitUntil(15_000) { runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        UiTestWindows.awaitFocus(activity, allowDialog = text in listOf("Unsaved key changes", "Use cloud AI?", "Delete this item?"))
    }
    private fun workspace(id: String) = Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_PROJECT_ID, id)
    private fun shot(name: String) {
        UiTestWindows.awaitFocus()
        compose.waitForIdle(); instrumentation.waitForIdleSync()
        runCatching { instrumentation.uiAutomation.waitForIdle(400, 3_000) }
        android.os.SystemClock.sleep(800) // Let Android window and render-thread fades finish before capture.
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap -> File(app.cacheDir, "workflow-ui-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    }
    @Test fun everyThemePersistsAndCoversLibraryWorkspaceAndNativeRunner() {
        val preferences = UiPreferences(app); val previous = preferences.selectedTheme()
        val project = Conversation(title = "Study notes", titleOwned = true).also(repo::save)
        NativeAppRepository(app).publish(project.id, ManualExamples.notes(), null, "Theme fixture")
        try {
            AppTheme.entries.forEach { theme ->
                preferences.selectTheme(theme)
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    waitText("New chat"); shot("${theme.name.lowercase()}-apps")
                    compose.onNodeWithContentDescription("Settings").performClick()
                    compose.onNodeWithText("Theme").performClick(); waitText("Make it feel like your space")
                    compose.onNodeWithText(theme.title).performClick()
                    scenario.recreate(); waitText("Make it feel like your space")
                    assertEquals(theme, UiPreferences(app).selectedTheme())
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    compose.onNodeWithText("Make it feel like your space").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val foreground = layouts.single().layoutInput.style.color.luminance()
                    val background = com.buildai.app.ui.theme.colorsFor(theme).background.luminance()
                    assertTrue("Theme headings must remain readable", (maxOf(foreground, background) + .05f) / (minOf(foreground, background) + .05f) >= 4.5f)
                    scenario.onActivity { activity -> assertEquals(!theme.dark, WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars) }
                    shot("${theme.name.lowercase()}-themes")
                }
                ActivityScenario.launch<MainActivity>(workspace(project.id)).use {
                    compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Project options").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("Request a change").performClick()
                    waitText("What would you like to change?"); shot("${theme.name.lowercase()}-chat")
                    compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("App details").performClick(); waitText("Current app"); shot("${theme.name.lowercase()}-plan")
                    compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("Preview app").performClick(); waitText("Current app · uses saved data"); shot("${theme.name.lowercase()}-preview")
                }
                ActivityScenario.launch<NativeRunnerActivity>(Intent(app, NativeRunnerActivity::class.java).putExtra("appId", project.id)).use {
                    waitText("Study notes", NativeRunnerActivity::class.java); shot("${theme.name.lowercase()}-runner")
                }
            }
        } finally { preferences.selectTheme(previous); repo.delete(project.id) }
    }
    @Test fun incompleteLegacyPlanAndSelectedTabSurviveRecreation() {
        val plan = QuickPlan.fromIdea("A task checklist")
        val project = Conversation(projectPlan = plan, phase = "CONFIRMED", workspaceTab = "plan").also(repo::save)
        try {
            ActivityScenario.launch<MainActivity>(workspace(project.id)).use { scenario ->
                waitText("Shape what your app will do")
                compose.onNode(hasSetTextAction() and hasText("App name")).performTextReplacement("")
                compose.onNodeWithText("Continue to appearance").assertIsNotEnabled()
                compose.waitUntil(5_000) { repo.load(project.id)?.legacyPlanDraft?.contains("\"appName\":\"\"") == true }
                compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("Conversation").performClick()
                compose.onNode(hasSetTextAction()).performTextInput("An unsent idea")
                compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("Requirements").performClick()
                scenario.recreate(); waitText("Shape what your app will do")
                compose.onNodeWithText("Continue to appearance").assertIsNotEnabled()
                compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("Conversation").performClick()
                compose.onNode(hasSetTextAction()).assertTextContains("An unsent idea")
                assertEquals(plan.appName, repo.load(project.id)!!.projectPlan!!.appName)
            }
        } finally { repo.delete(project.id) }
    }
    @Test fun appearanceDraftIsExplicitAndResetDoesNotApplyItself() {
        val project = Conversation(designSpec = DesignSpec()).also(repo::save)
        try {
            val vm = DesignViewModel(app)
            vm.bind(project.id, editDraft = true); vm.selectPalette("forest")
            assertEquals("forest", vm.spec.value.paletteId)
            assertNotEquals("forest", repo.load(project.id)!!.designSpec!!.paletteId)
            assertTrue(vm.applyDraft()); assertEquals("forest", repo.load(project.id)!!.designSpec!!.paletteId)
            vm.reset(); assertEquals("forest", repo.load(project.id)!!.designSpec!!.paletteId)
        } finally { repo.delete(project.id) }
    }
    @Test fun deletingRuntimeItemRequiresConfirmationAndCancelRetainsIt() {
        val project = Conversation().also(repo::save); val native = NativeAppRepository(app)
        native.publish(project.id, ManualExamples.notes(), null, "Delete fixture")
        native.perform(project.id, "saveNote", mapOf("note" to "Keep me", "tag" to "work"))
        try {
            ActivityScenario.launch<NativeRunnerActivity>(Intent(app, NativeRunnerActivity::class.java).putExtra("appId", project.id)).use {
                waitText("Study notes", NativeRunnerActivity::class.java)
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Delete note"))
                compose.onNodeWithText("Delete note").performClick(); waitText("Delete this item?", NativeRunnerActivity::class.java)
                compose.onNodeWithText("Cancel").performClick(); assertEquals(1, native.records(project.id)["notes"]!!.size)
                compose.onNodeWithText("Delete note").performClick(); compose.onNodeWithText("Delete").performClick()
                compose.waitUntil(5_000) { native.records(project.id)["notes"].orEmpty().isEmpty() }
            }
        } finally { repo.delete(project.id) }
    }
    @Test fun workspaceAndSettingsRemainReachableAtDoubleFontScale() {
        val project = Conversation(title = "Trip budget", titleOwned = true).also(repo::save)
        NativeAppRepository(app).publish(project.id, ManualExamples.budget(), null, "Large text fixture")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent { BuildAITheme(AppTheme.NIGHT_GRAY) {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                        AppNavigation(requestedProjectId = project.id)
                    }
                } } }
                waitText("What would you like to change?")
                compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("App details").performClick()
                waitText("Current app")
                compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("Preview app").performClick()
                waitText("Current app · uses saved data")
                shot("night-gray-double-text")
                compose.onNodeWithContentDescription("Project options").performClick()
                compose.onNodeWithText("Settings").performClick()
                compose.onNodeWithText("Theme").performScrollTo().performClick()
                compose.onNodeWithText("Night gray").performScrollTo().assertIsDisplayed()
                shot("night-gray-double-text-settings")
            }
        } finally { repo.delete(project.id) }
    }
    @Test fun proposedPreviewUsesTestStorageAndDiscardKeepsCurrentRevision() {
        val project = Conversation(title = "Study notes", titleOwned = true, workspaceTab = "preview").also(repo::save)
        val native = NativeAppRepository(app); val spec = ManualExamples.notes()
        val revision = native.publish(project.id, spec, null, "Preview fixture")
        native.perform(project.id, "saveNote", mapOf("note" to "Original record", "tag" to "keep"))
        val currentDefinition = checkNotNull(native.spec(project.id))
        val candidate = currentDefinition.copy(screens = currentDefinition.screens.map { screen -> screen.copy(components = screen.components.map { if (it.id == "save") it.copy(label = "Keep note") else it }) })
        val jobId = java.util.UUID.randomUUID().toString()
        val database = com.buildai.app.data.storage.BuildAiDatabase.get(app)
        database.jobs().save(com.buildai.app.data.storage.StoredAgentJob(jobId, project.id, "NATIVE_EDIT", "REVIEW",
            org.json.JSONObject().put("baseRevision", revision.id).toString(), "Ready to review",
            org.json.JSONObject().put("definition", candidate.json()).put("summary", "Rename the save button")
                .put("changedIds", org.json.JSONArray(listOf("save"))).put("verification", org.json.JSONObject().put("results", org.json.JSONArray())).toString()))
        try {
            ActivityScenario.launch<MainActivity>(workspace(project.id)).use {
                waitText("Proposed change · test data")
                val field = hasSetTextAction() and hasText("Note")
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(field)
                compose.onNode(field).performTextReplacement("Test record")
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot()).perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Keep note"))
                compose.onNodeWithText("Keep note").performClick()
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Note: Test record"))
                compose.onNodeWithText("Note: Test record").assertIsDisplayed()
                assertEquals(1, native.records(project.id)["notes"]!!.size)
                assertEquals("Original record", native.records(project.id)["notes"]!!.single()["body"])
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot()).perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
                shot("proposed-change")
                compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("Request a change").performClick(); waitText("Ready to review · not applied")
                compose.onNodeWithText("Discard proposal").performScrollTo().performClick()
                compose.waitUntil(5_000) { database.jobs().load(jobId)?.status == "CANCELLED" }
                assertEquals(revision.id, native.dao.head(project.id)!!.revisionId)
                assertEquals(currentDefinition, native.spec(project.id))
            }
        } finally { repo.delete(project.id) }
    }
    @Test fun settingsProviderChoiceIsExplicitAndUnsavedKeyCanBeDiscarded() {
        val settings = app.container.settings
        val previousBackend = settings.getChatBackend(); val previousProvider = settings.provider()
        val previousKey = settings.getApiKey(com.buildai.app.data.api.CloudProvider.GEMINI)
        try {
            settings.selectProvider(com.buildai.app.data.api.CloudProvider.GEMINI); settings.saveChatBackend("local")
            settings.saveApiKey("controlled-test-key")
            ActivityScenario.launch(MainActivity::class.java).use {
                waitText("New chat"); compose.onNodeWithContentDescription("Settings").performClick()
                compose.onNodeWithText("AI & privacy").performClick(); compose.onNodeWithText("Cloud AI").performClick()
                compose.onNode(hasSetTextAction() and hasText("Gemini API key")).performTextReplacement("unsaved-test-key")
                compose.onNodeWithContentDescription("Back").performClick(); waitText("Unsaved key changes")
                compose.onNodeWithText("Discard changes").performClick()
                assertEquals("controlled-test-key", settings.getApiKey()); assertEquals("local", settings.getChatBackend())
                compose.onNodeWithText("Cloud AI").performClick(); compose.onNodeWithText("Use Gemini").performScrollTo().performClick()
                assertEquals("cloud", settings.getChatBackend()); assertEquals(com.buildai.app.data.api.CloudProvider.GEMINI, settings.provider())
            }
        } finally { previousKey?.let { settings.saveApiKey(it, com.buildai.app.data.api.CloudProvider.GEMINI) } ?: settings.clearApiKey(com.buildai.app.data.api.CloudProvider.GEMINI); settings.selectProvider(previousProvider); settings.saveChatBackend(previousBackend) }
    }

    @Test fun manualAndOfflineStarterHaveDirectWorkingEntryPoints() {
        val initial = repo.listAll().map { it.id }.toSet(); val backend = app.container.settings.getChatBackend()
        val preferences = UiPreferences(app); val previousOffline = preferences.offlineToolsEnabled(); preferences.enableOfflineTools(true)
        try {
            app.container.settings.saveChatBackend("local")
            ActivityScenario.launch(MainActivity::class.java).use {
                waitText("New chat"); compose.onNode(hasText("Create") and hasAnyAncestor(hasTestTag("root-Create")), useUnmergedTree = true).performClick()
                compose.onNodeWithText("Use an offline starter").performScrollTo().performClick()
                compose.onNodeWithText("Calculator").performClick(); waitText("Shape what your app will do")
                compose.onNodeWithText("Continue to appearance").performClick(); waitText("Appearance"); compose.onNodeWithText("Save & build").performScrollTo().performClick(); waitText("Current web app · uses saved data")
                val starter = repo.listAll().first { it.id !in initial && !it.assistantEntryDraft }
                assertEquals(BuildMode.TEMPLATE, starter.projectPlan!!.buildMode)
                assertNotNull(repo.generatedFile(starter.id))
                compose.onNodeWithContentDescription("Back").performClick(); waitText("Apps")
                compose.onNode(hasText("Create") and hasAnyAncestor(hasTestTag("root-Create")), useUnmergedTree = true).performClick()
                waitText("What would you like your app to do?")
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Configure an offline tool"))
                compose.onNodeWithText("Configure an offline tool").performScrollTo().performClick(); waitText("Build manually")
                compose.onNodeWithText("Build app").assertIsNotEnabled()
                compose.onNode(hasSetTextAction() and hasText("App name")).performTextReplacement("Pocket list")
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot()).perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Text") and hasClickAction())
                compose.onNode(hasText("Text") and hasClickAction()).performClick()
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Save records locally"))
                compose.onNode(hasText("Save records locally") and hasClickAction()).performClick()
                compose.onNodeWithText("Build app").assertIsEnabled().performClick(); waitText("Close", NativeRunnerActivity::class.java)
                val manual = repo.listAll().first { it.id !in initial && it.id != starter.id && !it.assistantEntryDraft }
                assertEquals("Pocket list", manual.displayName)
                assertNotNull(NativeAppRepository(app).dao.head(manual.id))
                compose.onNodeWithText("Close").performClick(); waitText("What would you like to change?")
                shot("manual-workspace")
            }
        } finally {
            instrumentation.runOnMainSync { androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filter { it is NativeRunnerActivity || it is LegacyRunnerActivity }.forEach { it.finish() } }
            repo.listAll().filter { it.id !in initial && !it.assistantEntryDraft }.forEach { repo.delete(it.id) }
            app.container.settings.saveChatBackend(backend); preferences.enableOfflineTools(previousOffline)
        }
    }

}
