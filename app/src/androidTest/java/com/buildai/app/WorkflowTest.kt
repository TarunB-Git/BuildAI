package com.buildai.app

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.widget.EditText
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.*
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.api.GeminiApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hamcrest.Matchers.containsString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val evidence = object : org.junit.rules.TestWatcher() {
        override fun failed(error: Throwable, description: org.junit.runner.Description) {
            val base = java.io.File(app.cacheDir, "workflow-${description.methodName}")
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(base.path + ".png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            instrumentation.uiAutomation.executeShellCommand("dumpsys window windows").use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { input -> java.io.File(base.path + ".txt").writeBytes(input.readBytes()) }
            }
        }
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var server: MockWebServer
    private val idea = "A simple notes app ${java.util.UUID.randomUUID().toString().take(8)}"
    private var initialIds = emptySet<String>()
    private var previousApiKey: String? = null
    private var previousBackend: String = "local"
    private fun reply(text: String) = MockResponse().setBody(JSONObject().put("candidates", JSONArray().put(
        JSONObject().put("finishReason", "STOP").put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))).toString())
    private fun streamed(text: String) = reply(text).let { response ->
        response.setHeader("Content-Type", "text/event-stream").setBody("data: " + checkNotNull(response.getBody()).readUtf8() + "\n\n")
    }
    private fun plan(history: Boolean = false) = """<plan>{"appName":"Notes","description":"Save notes on your device.","type":"app","features":["Add, edit and delete notes"],"screens":["Home"${if (history) ",\"History\"" else ""}],"targetUser":"Me","complexity":"simple","estimatedBuildTime":"A few minutes"}</plan>Does this match?"""
    private fun waitText(text: String) { compose.waitUntil(20_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
            .getOrDefault(false)
    }; compose.mainClock.advanceTimeBy(300); compose.waitForIdle(); UiTestWindows.awaitFocus(MainActivity::class.java) }
    private fun send(text: String) { val field = compose.onNode(hasSetTextAction()); field.performTextInput(text); field.performImeAction() }
    private fun waitForNativeText(text: String) {
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root?.findAccessibilityNodeInfosByText(text)?.isNotEmpty() == true) return
            android.os.SystemClock.sleep(30)
        }
        error("Timed out waiting for the active window: $text")
    }

    private fun waitForPreviewWindow() {
        onView(androidx.test.espresso.matcher.ViewMatchers.isRoot()).perform(closeSoftKeyboard())
        val deadline = android.os.SystemClock.uptimeMillis() + 20_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync {
                ready = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .any { it.window.decorView.hasWindowFocus() && !it.window.decorView.isLayoutRequested }
            }
            if (ready) return
            android.os.SystemClock.sleep(30)
        }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(app.cacheDir, "workflow-failure.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        error("The preview window did not gain focus")
    }

    private fun clickDialogButton(id: String) {
        fun find(view: android.view.View): android.webkit.WebView? {
            if (view is android.webkit.WebView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        scenario.onActivity { activity ->
            val view = checkNotNull(find(activity.window.decorView))
            view.evaluateJavascript("setTimeout(() => document.getElementById('$id').click(), 0)", null)
        }
        waitForNativeText(if (id.startsWith("edit")) "Edit note" else "Delete note?")
    }
    private fun fixture() = instrumentation.context.assets.open("notes.html").bufferedReader().use { it.readText() }

    @Before fun setup() {
        initialIds = app.container.projects.listAll().map { it.id }.toSet()
        previousApiKey = app.container.settings.getApiKey()
        previousBackend = app.container.settings.getChatBackend()
        server = MockWebServer(); server.start()
        val testContainer = AppContainer(app, GeminiApi(endpoint = server.url("/")))
        instrumentation.runOnMainSync { app.useTestContainer(testContainer) }
        app.container.settings.saveApiKey("instrumentation-test-key")
        app.container.settings.saveChatBackend("cloud")
        Intents.init()
        Intents.intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(ActivityResult(Activity.RESULT_CANCELED, null))
        Intents.intending(hasAction(Intent.ACTION_VIEW)).respondWith(ActivityResult(Activity.RESULT_CANCELED, null))
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).toList()
                .filter { it is com.buildai.app.runtime.LegacyRunnerActivity || it is com.buildai.app.runtime.NativeRunnerActivity }
                .forEach { it.finish() }
        }
        if (::scenario.isInitialized) scenario.close()
        previousApiKey?.let { app.container.settings.saveApiKey(it) } ?: app.container.settings.clearApiKey()
        app.container.settings.saveChatBackend(previousBackend)
        if (InstrumentationRegistry.getArguments().getString("preserveFixture") != "true")
            app.container.projects.listAll().filterNot { it.id in initialIds }.forEach {
                androidx.work.WorkManager.getInstance(app).cancelUniqueWork(
                    com.buildai.app.data.api.GenerationWorker.workName(it.id)).result.get(5, java.util.concurrent.TimeUnit.SECONDS)
                app.container.projects.delete(it.id)
            }
        instrumentation.runOnMainSync { app.useTestContainer(AppContainer(app)) }
        Intents.release(); server.shutdown()
    }

    private fun seededPlan(id: String = java.util.UUID.randomUUID().toString()): String {
        app.container.projects.save(com.buildai.app.data.model.Conversation(id = id, title = idea, titleOwned = true,
            projectPlan = com.buildai.app.data.model.ProjectPlan.fromResponse(plan(true)), phase = "CONFIRMED", designSpec = com.buildai.app.data.model.DesignSpec(), workspaceTab = "plan"))
        return id
    }
    private fun launchProject(id: String) { scenario = ActivityScenario.launch(Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_PROJECT_ID, id)) }
    private fun build() { waitText("Shape what your app will do"); compose.onNodeWithText("Continue to appearance").assertIsEnabled().performClick(); waitText("Appearance"); compose.onNodeWithText("Save & build").performScrollTo().performClick() }
    private fun projectMenu(text: String) { compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText(text).performClick() }
    private fun startChat(text: String) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNode(hasText("Chat") and hasAnyAncestor(hasTestTag("root-Chat")), useUnmergedTree = true).performClick()
        compose.onNodeWithText("New chat").performClick(); send(text)
    }
    @Test fun requirementsAppearanceGenerationInteractionsExportAndColdReopen() {
        val id = seededPlan()
        server.enqueue(reply(fixture())); server.enqueue(reply(fixture()))
        launchProject(id); waitText("Shape what your app will do")
        compose.onNodeWithText("Appearance · optional").performScrollTo().performClick(); waitText("Appearance")
        compose.onNodeWithText("Tabbed").performScrollTo().performClick()
        compose.onNodeWithText("Forest").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Music").performScrollTo().performClick()
        compose.onNodeWithText("Save appearance").performScrollTo().performClick()
        build(); waitText("Current web app · uses saved data"); waitForPreviewWindow()
        val saved = checkNotNull(app.container.projects.load(id))
        assertEquals("forest", saved.designSpec!!.paletteId); assertEquals("music", saved.designSpec.iconKey)
        onWebView().withElement(findElement(Locator.ID, "add")).perform(webClick())
        onWebView().withElement(findElement(Locator.ID, "error")).check(webMatches(getText(), containsString("Enter a note")))
        onWebView().withElement(findElement(Locator.ID, "name")).perform(webKeys("Milk"))
        onWebView().withElement(findElement(Locator.ID, "add")).perform(webClick())
        clickDialogButton("edit-0")
        onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(replaceText("Bread"))
        onView(withText("OK")).inRoot(isDialog()).perform(click())
        waitForPreviewWindow()
        onWebView().withElement(findElement(Locator.ID, "note-0")).check(webMatches(getText(), containsString("Bread")))
        onWebView().withElement(findElement(Locator.ID, "history")).perform(webClick())
        onWebView().withElement(findElement(Locator.ID, "count")).check(webMatches(getText(), containsString("1 notes")))
        onWebView().withElement(findElement(Locator.ID, "home")).perform(webClick())
        clickDialogButton("delete-0")
        onView(withText("Cancel")).inRoot(isDialog()).perform(click())
        waitForPreviewWindow()
        onWebView().withElement(findElement(Locator.ID, "note-0")).check(webMatches(getText(), containsString("Bread")))
        clickDialogButton("delete-0")
        onView(withText("OK")).inRoot(isDialog()).perform(click())
        waitForPreviewWindow()
        onWebView().withElement(findElement(Locator.ID, "items")).check(webMatches(getText(), containsString("No notes yet")))
        onWebView().withElement(findElement(Locator.ID, "name")).perform(webKeys("Persistent note"))
        onWebView().withElement(findElement(Locator.ID, "add")).perform(webClick())
        projectMenu("Share HTML file")
        Intents.intended(hasAction(Intent.ACTION_CHOOSER))
        val chooser = Intents.getIntents().last { it.action == Intent.ACTION_CHOOSER }
        @Suppress("DEPRECATION") val share = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION") val stream = share.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
        assertTrue(app.contentResolver.openInputStream(stream)!!.bufferedReader().use { it.readText() }.contains("<!DOCTYPE html>"))
        projectMenu("Open in browser")
        assertEquals("text/html", Intents.getIntents().last { it.action == Intent.ACTION_VIEW }.type)
        scenario.recreate(); waitText("Current web app · uses saved data")
        onWebView().withElement(findElement(Locator.ID, "note-0")).check(webMatches(getText(), containsString("Persistent note")))
        server.enqueue(MockResponse().setResponseCode(401))
        compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("App details").performClick(); build(); waitText("Work needs attention")
        assertEquals(saved.generatedFileName, app.container.projects.load(id)!!.generatedFileName)
        server.enqueue(reply(fixture())); server.enqueue(reply(fixture()))
        compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("App details").performClick(); build()
        compose.waitUntil(30_000) { app.container.projects.load(id)!!.generatedFileName != saved.generatedFileName }
        waitText("Current web app · uses saved data")
        onWebView().withElement(findElement(Locator.ID, "note-0")).check(webMatches(getText(), containsString("Persistent note")))
        scenario.close()
        launchProject(id); waitText("Current web app · uses saved data")
        onWebView().withElement(findElement(Locator.ID, "note-0")).check(webMatches(getText(), containsString("Persistent note")))
    }
    @Test fun draftRemainsEditableWhileReplyRuns() {
        server.enqueue(streamed("First response").setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS))
        server.enqueue(streamed("Updated response"))
        startChat(idea)
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithContentDescription("Stop reply").fetchSemanticsNode() }
                .isSuccess
        }
        val draft = "Also keep a history of notes"
        compose.onNode(hasSetTextAction()).performTextInput(draft)
        compose.onNode(hasSetTextAction()).assertTextContains(draft)
        compose.onNodeWithContentDescription("Stop reply").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains(draft)
        compose.onNodeWithContentDescription("Send").performClick()
        waitText("Updated response")
        assertEquals(2, server.requestCount)
        server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
        val revised = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
        assertNotNull(revised)
        val revisedBody = revised!!.body.readUtf8()
        assertTrue(revisedBody.contains(idea))
        assertTrue(revisedBody.contains(draft))
    }

    @Test fun failedPlanningRequestSurvivesRestartAndRetries() {
        server.enqueue(MockResponse().setResponseCode(403))
        startChat(idea)
        waitText("Retry")
        val project = app.container.projects.listAll().filterNot { it.id in initialIds }.single()
        assertTrue(project.messages.last().failed)
        scenario.close(); scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNode(hasText("Chat") and hasAnyAncestor(hasTestTag("root-Chat")), useUnmergedTree = true).performClick()
        compose.onNodeWithText(idea).performScrollTo().performClick()
        waitText("Retry")
        server.enqueue(streamed("Who is this for?"))
        compose.onAllNodesWithText("Retry").onFirst().performClick()
        waitText("Who is this for?")
        assertFalse(app.container.projects.load(project.id)!!.messages.any { it.failed })
        assertEquals(2, server.requestCount)
    }

    @Test fun queuedRequestCanBeEditedAndSteerCancelsPreviousReply() {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        startChat(idea)
        assertNotNull(server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS))
        send("Add a footer")
        compose.onNodeWithText("1 queued").performClick()
        compose.onNode(hasSetTextAction() and hasText("Request 1")).performTextReplacement("Make the heading blue")
        compose.onNodeWithText("Save").performClick()
        val id = app.container.projects.listAll().first { it.id !in initialIds && it.assistantMode == com.buildai.app.data.model.AssistantMode.CHAT }.id
        compose.waitUntil(5_000) { app.container.projects.load(id)?.queuedInputs == listOf("Make the heading blue") }
        server.enqueue(streamed("Updated answer"))
        compose.onNodeWithText("Send now & steer").performClick(); waitText("Updated answer")
        val stored = app.container.projects.load(id)!!
        assertTrue(stored.queuedInputs.isEmpty()); assertEquals(2, server.requestCount)
        val body = JSONObject(server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8()).getJSONArray("contents").toString()
        assertTrue(body.contains(idea)); assertTrue(body.contains("Make the heading blue"))
    }

    @Test fun cancelledGenerationCanRetryWithoutSavingIncompleteOutput() {
        val id = seededPlan()
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        launchProject(id); waitText("Shape what your app will do"); build(); waitText("Stop build")
        assertNotNull(server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS))
        compose.onNodeWithText("Stop build").performClick(); waitText("Your app will appear here")
        assertNull(app.container.projects.generatedFile(id))
        server.enqueue(reply(fixture())); server.enqueue(reply(fixture()))
        compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("Requirements").performClick(); build(); waitText("Current web app · uses saved data")
        assertNotNull(app.container.projects.generatedFile(id))
    }
    @Test fun buildContinuesAfterReturningHome() {
        val id = seededPlan()
        server.enqueue(reply(fixture()).setBodyDelay(2, java.util.concurrent.TimeUnit.SECONDS))
        server.enqueue(reply(fixture()).setBodyDelay(2, java.util.concurrent.TimeUnit.SECONDS))
        launchProject(id); waitText("Shape what your app will do"); build()
        waitText("Work continues in background")
        compose.onNodeWithContentDescription("Back").performClick(); waitText("Apps")
        compose.waitUntil(30_000) { app.container.projects.generatedFile(id) != null }
        scenario.close(); launchProject(id); waitText("Current web app · uses saved data")
        assertEquals(2, server.requestCount)
    }

    @Test fun separateProjectsCanRunAtTheSameTime() {
        val ids = List(2) { java.util.UUID.randomUUID().toString() }
        ids.forEach { id ->
            app.container.projects.save(com.buildai.app.data.model.Conversation(
                id = id, title = "$idea $id",
                projectPlan = com.buildai.app.data.model.ProjectPlan.fromResponse(plan()),
                phase = "CONFIRMED", designSpec = com.buildai.app.data.model.DesignSpec()
            ))
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        }
        ids.forEach { com.buildai.app.data.api.GenerationWorker.enqueue(app, it) }
        repeat(2) { assertNotNull(server.takeRequest(20, java.util.concurrent.TimeUnit.SECONDS)) }
        val work = androidx.work.WorkManager.getInstance(app)
        ids.forEach { id ->
            val info = work.getWorkInfosForUniqueWork(com.buildai.app.data.api.GenerationWorker.workName(id))
                .get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(info.any { it.state == androidx.work.WorkInfo.State.RUNNING })
        }
        work.cancelUniqueWork(com.buildai.app.data.api.GenerationWorker.workName(ids[0]))
        val other = work.getWorkInfosForUniqueWork(com.buildai.app.data.api.GenerationWorker.workName(ids[1]))
            .get(5, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(other.any { it.state == androidx.work.WorkInfo.State.RUNNING })
        work.cancelUniqueWork(com.buildai.app.data.api.GenerationWorker.workName(ids[1]))
    }

}
