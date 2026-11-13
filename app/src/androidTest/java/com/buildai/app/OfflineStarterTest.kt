package com.buildai.app

import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.api.GeneratedAppValidator
import com.buildai.app.data.api.HtmlValidator
import com.buildai.app.data.api.OfflineStarterApp
import com.buildai.app.data.model.Complexity
import com.buildai.app.data.model.Conversation
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.ProjectPlan
import com.buildai.app.data.model.AppType
import com.buildai.app.data.storage.WidgetSummaryStore
import com.buildai.app.ui.components.GeneratedAppWebView
import com.buildai.app.ui.components.generatedAppOrigin
import com.buildai.app.widgets.ProjectHomeActions
import com.buildai.app.widgets.ProjectWidgetProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OfflineStarterTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private fun plan() = ProjectPlan("Local Notes", "Keep my notes private.", AppType.APP,
        listOf("Add and edit notes"), listOf("Home", "History"), "Me", Complexity.SIMPLE, "A moment")

    @Test fun starterAddsEditsDeletesAndPersistsOffline() = runBlocking {
        val html = HtmlValidator.extractAndValidate(OfflineStarterApp.build(plan(), DesignSpec()))
        GeneratedAppValidator(app).validate(html)
        val projectId = UUID.randomUUID().toString()
        lateinit var view: GeneratedAppWebView
        instrumentation.runOnMainSync {
            view = GeneratedAppWebView(app)
            view.webChromeClient = object : WebChromeClient() {
                override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                    result.confirm(); return true
                }
            }
        }
        fun load(token: Int) {
            val latch = CountDownLatch(1)
            instrumentation.runOnMainSync {
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) { latch.countDown() }
                }
                view.loadApp(html, projectId, token)
            }
            assertTrue("Starter did not load", latch.await(10, TimeUnit.SECONDS))
        }
        fun evaluate(js: String): String {
            val latch = CountDownLatch(1); val output = AtomicReference<String>()
            instrumentation.runOnMainSync { view.evaluateJavascript(js) { output.set(it); latch.countDown() } }
            assertTrue("JavaScript did not answer", latch.await(5, TimeUnit.SECONDS))
            return output.get()
        }
        fun waitSummary(total: Int, done: Int) {
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            while (android.os.SystemClock.uptimeMillis() < deadline) {
                if (WidgetSummaryStore.read(app, projectId) == WidgetSummaryStore.Summary(total, done)) return
                android.os.SystemClock.sleep(25)
            }
            assertEquals(WidgetSummaryStore.Summary(total, done), WidgetSummaryStore.read(app, projectId))
        }
        try {
            load(0)
            waitSummary(0, 0)
            assertEquals("\"No items yet — add your first one!\"", evaluate("document.querySelector('#items p').textContent"))
            assertEquals("\"Milk\"", evaluate("document.querySelector('#title').value='Milk';document.querySelector('#details').value='2 bottles';document.querySelector('#item-form').requestSubmit();document.querySelector('.item-title').textContent"))
            waitSummary(1, 0)
            load(1)
            assertEquals("\"Milk\"", evaluate("document.querySelector('.item-title').textContent"))
            assertEquals("\"Bread\"", evaluate("document.querySelector('.actions button').click();document.querySelector('#title').value='Bread';document.querySelector('#item-form').requestSubmit();document.querySelector('.item-title').textContent"))
            assertEquals("true", evaluate("document.querySelector('.check').click();JSON.parse(localStorage.getItem('buildai.offline-starter.v1'))[0].done"))
            waitSummary(1, 1)
            val otherProject = UUID.randomUUID().toString()
            evaluate("window.BuildAIWidget.updateSummary('{\"total\":99,\"done\":99}','$otherProject');true")
            assertNull(WidgetSummaryStore.read(app, otherProject))
            assertEquals("\"History\"", evaluate("document.querySelectorAll('#sections button')[1].click();document.querySelector('#section-title').textContent"))
            assertEquals("\"No items yet — add your first one!\"", evaluate("document.querySelector('#items p').textContent"))
            evaluate("document.querySelectorAll('#sections button')[0].click()")
            assertEquals("\"No items yet — add your first one!\"", evaluate("document.querySelector('.actions .danger').click();document.querySelector('#items p').textContent"))
            waitSummary(0, 0)
        } finally {
            instrumentation.runOnMainSync {
                view.destroy()
                WidgetSummaryStore.delete(app, projectId)
                android.webkit.WebStorage.getInstance().deleteOrigin(generatedAppOrigin(projectId).removeSuffix("/"))
            }
        }
    }

    @Test fun savedAppOpensFromHomeScreenProjectIntent() {
        val id = UUID.randomUUID().toString()
        val repo = app.container.projects
        val html = OfflineStarterApp.build(plan(), DesignSpec())
        repo.save(Conversation(id = id, title = "Local Notes", projectPlan = plan(),
            phase = "CONFIRMED", designSpec = DesignSpec()))
        repo.saveGenerated(id, html)
        try {
            val providers = android.appwidget.AppWidgetManager.getInstance(app).installedProviders
            assertTrue(providers.any { it.provider.className == ProjectWidgetProvider::class.java.name })
            ActivityScenario.launch<MainActivity>(ProjectHomeActions.projectIntent(app, id)).use {
                compose.waitUntil(15_000) {
                    runCatching { compose.onNodeWithText("Tap the preview to try it out").fetchSemanticsNode(); true }.getOrDefault(false)
                }
                compose.onNodeWithText("Tap the preview to try it out").assertExists()
            }
        } finally { repo.delete(id) }
    }
}
