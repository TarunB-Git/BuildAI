package com.buildai.app

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.api.GeneratedAppValidator
import com.buildai.app.data.api.QuickTemplateApp
import com.buildai.app.data.local.QuickPlan
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.QuickTemplate
import com.buildai.app.ui.components.GeneratedAppWebView
import com.buildai.app.ui.components.generatedAppOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class QuickTemplateFeatureTest {
    @Test fun distinctTemplatesCalculateTrackSpendAndKeepHabitHistory() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext
        val project = UUID.randomUUID().toString()
        lateinit var view: GeneratedAppWebView
        instrumentation.runOnMainSync { view = GeneratedAppWebView(app) }
        fun evaluate(code: String): String {
            val latch = CountDownLatch(1); val result = AtomicReference<String>()
            instrumentation.runOnMainSync { view.evaluateJavascript(code) { result.set(it); latch.countDown() } }
            assertTrue(latch.await(5, TimeUnit.SECONDS)); return result.get()
        }
        var token = 0
        fun load(kind: QuickTemplate) {
            val html = QuickTemplateApp.build(QuickPlan.fromIdea(kind.label).copy(quickTemplate = kind), DesignSpec())
            val latch = CountDownLatch(1)
            instrumentation.runOnMainSync {
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) { latch.countDown() }
                }
                view.loadApp(html, project, token++)
            }
            assertTrue(latch.await(10, TimeUnit.SECONDS))
        }
        try {
            QuickTemplate.entries.forEach { GeneratedAppValidator(app).validate(QuickTemplateApp.build(QuickPlan.fromIdea(it.label).copy(quickTemplate = it), DesignSpec())) }
            load(QuickTemplate.CALCULATOR)
            assertEquals("\"Result: 5\"", evaluate("document.getElementById('a').value='2';document.getElementById('b').value='3';document.getElementById('form').requestSubmit();document.getElementById('result').textContent"))
            assertEquals("\"Cannot divide by zero.\"", evaluate("document.getElementById('op').value='/';document.getElementById('b').value='0';document.getElementById('form').requestSubmit();document.getElementById('result').textContent"))
            load(QuickTemplate.EXPENSES)
            assertEquals("\"Total: 3.50\"", evaluate("document.getElementById('title').value='Coffee';document.getElementById('amount').value='3.50';document.getElementById('form').requestSubmit();document.getElementById('total').textContent"))
            load(QuickTemplate.EXPENSES)
            assertEquals("\"Total: 4.25\"", evaluate("document.querySelector('#items button').click();document.getElementById('amount').value='4.25';document.getElementById('form').requestSubmit();document.getElementById('total').textContent"))
            assertEquals("\"Total: 0.00\"", evaluate("document.querySelectorAll('#items button')[1].click();document.getElementById('total').textContent"))
            load(QuickTemplate.HABITS)
            assertEquals("1", evaluate("document.getElementById('title').value='Water plants';document.getElementById('form').requestSubmit();document.querySelector('#items button').click();JSON.parse(localStorage.getItem('buildai.template.HABITS.v1'))[0].days.length"))
            load(QuickTemplate.HABITS)
            assertTrue(evaluate("document.getElementById('history').textContent").contains("Water plants"))
            assertEquals("0", evaluate("document.querySelector('#items button').click();JSON.parse(localStorage.getItem('buildai.template.HABITS.v1'))[0].days.length"))
            load(QuickTemplate.TIMER)
            assertEquals("\"02:00\"", evaluate("document.getElementById('minutes').value='2';document.getElementById('reset').click();document.getElementById('start').click();document.getElementById('pause').click();document.getElementById('clock').textContent"))
        } finally { instrumentation.runOnMainSync { view.destroy(); android.webkit.WebStorage.getInstance().deleteOrigin(generatedAppOrigin(project).removeSuffix("/")) } }
    }
}
