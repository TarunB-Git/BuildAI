package com.buildai.app

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.api.GeneratedAppValidator
import com.buildai.app.data.api.LocalPwaGenerator
import com.buildai.app.data.local.QuickPlan
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.ui.components.GeneratedAppWebView
import com.buildai.app.ui.components.generatedAppOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LocalCustomBuildTest {
    @Test fun smallestModelWritesACustomCalculatorInsteadOfAList() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext
        val model = File(app.filesDir, "models/test.gguf")
        assumeTrue("A real GGUF is required", model.isFile)
        val plan = QuickPlan.fromIdea("A simple calculator that adds two numbers").copy(appName = "Pocket Sum",
            description = "Type two numbers and calculate their sum.", features = listOf("Two number inputs", "A Calculate button that adds them", "Display the sum"), screens = listOf("Calculator"))
        val html = LocalPwaGenerator(app).generate(model, plan, DesignSpec(), { _, _ -> }, GeneratedAppValidator(app)::validate, onDraft = { File(app.cacheDir, "custom-build-draft.json").writeText(it) }).getOrThrow()
        assertFalse("Custom builds returned the generic starter", html.contains("Offline starter app"))
        val id = UUID.randomUUID().toString()
        lateinit var view: GeneratedAppWebView
        val loaded = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view = GeneratedAppWebView(app)
            view.webViewClient = object : WebViewClient() { override fun onPageFinished(view: WebView, url: String) { loaded.countDown() } }
            view.loadApp(html, id, 0)
        }
        try {
            assertTrue(loaded.await(10, TimeUnit.SECONDS))
            val result = AtomicReference<String>(); val latch = CountDownLatch(1)
            instrumentation.runOnMainSync {
                view.evaluateJavascript("""(()=>{const fields=[...document.querySelectorAll('input')].filter(x=>x.type==='number'||x.type==='text');if(fields.length<2)return 'missing inputs';fields[0].value='2';fields[1].value='3';fields.forEach(x=>{x.dispatchEvent(new Event('input',{bubbles:true}));x.dispatchEvent(new Event('change',{bubbles:true}))});const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/calculate|add|sum/i.test(x.textContent||x.value));if(!b)return 'missing calculate button';b.click();return document.body.innerText})()""") { result.set(it); latch.countDown() }
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            val displayed = org.json.JSONTokener(result.get()).nextValue() as String
            assertTrue("The generated calculator could not add 2 + 3: $displayed", Regex("\\b5\\b").containsMatchIn(displayed))
        } finally { instrumentation.runOnMainSync { view.destroy(); android.webkit.WebStorage.getInstance().deleteOrigin(generatedAppOrigin(id).removeSuffix("/")) } }
    }
}
