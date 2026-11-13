package com.buildai.app

import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.api.GeneratedAppValidator
import com.buildai.app.data.model.*
import com.buildai.app.data.storage.ConversationRepository
import com.buildai.app.ui.components.GeneratedAppWebView
import com.buildai.app.ui.components.generatedAppOrigin
import com.buildai.app.viewmodel.ConversationViewModel
import com.buildai.app.viewmodel.DesignViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import android.webkit.WebViewClient

class ProjectRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private val repo = ConversationRepository(app)
    private val ids = mutableListOf<String>()
    private fun project(): Conversation = Conversation(title = "Same name").also { ids += it.id; repo.save(it) }
    @After fun cleanup() { ids.forEach(repo::delete) }
    private fun fixture() = instrumentation.context.assets.open("notes.html").bufferedReader().use { it.readText() }

    @Test fun readsLegacyProjectWithoutNewFields() {
        val legacy = JSONObject().put("id", UUID.randomUUID().toString()).put("createdAt", 1L).put("updatedAt", 1L)
        val project = Conversation.fromJson(legacy)
        assertNull(project.designSpec); assertNull(project.generatedFileName); assertNull(project.modelLastUsed)
    }
    @Test fun persistsDesignAndDoesNotLoseItWhenChatUpdates() {
        val project = project()
        val design = DesignSpec("tabbed", "forest", "music", listOf(TabSpec("Saved", "note")))
        repo.saveDesign(project.id, design)
        repo.update(project.id) { it.copy(phase = "CONFIRMED") }
        assertEquals(design, ConversationRepository(app).load(project.id)!!.designSpec)
        instrumentation.runOnMainSync { assertEquals(design, DesignViewModel(app, repo, project.id).spec.value) }
    }
    @Test fun sameNameProjectsDoNotOverwriteEachOtherAndDeleteRemovesOnlyOwnOutput() {
        val first = project(); val second = project()
        val firstFile = repo.saveGenerated(first.id, fixture()); val secondFile = repo.saveGenerated(second.id, fixture())
        assertNotEquals(firstFile.absolutePath, secondFile.absolutePath)
        val replacement = repo.saveGenerated(first.id, fixture().replace("Notes", "Updated"))
        assertFalse(firstFile.exists()); assertTrue(secondFile.exists()); assertTrue(replacement.exists())
        repo.delete(first.id)
        assertFalse(replacement.exists()); assertTrue(secondFile.exists())
    }
    @Test fun failedRequestAndProjectIdRecoverFromSavedState() {
        val project = project()
        repo.update(project.id) { it.copy(messages = listOf(Message(role = MessageRole.USER, content = "Remember my idea")), phase = "CLARIFYING") }
        instrumentation.runOnMainSync {
            val vm = ConversationViewModel(app, savedState = SavedStateHandle(mapOf("projectId" to project.id)))
            assertEquals(project.id, vm.projectId); assertTrue(vm.messages.value.last().failed)
            assertFalse(vm.isLoading.value)
            vm.leaveProject()
        }
    }
    @Test fun runtimeCheckAcceptsWorkingAppAndRejectsJavaScriptSyntaxError() = runBlocking {
        val validator = GeneratedAppValidator(app)
        validator.validate(fixture())
        val result = runCatching { validator.validate(fixture().replace("let notes=", "let notes = =")) }
        assertTrue(result.isFailure)
        val blank = """<!doctype html><html><head><script>window.ready=true;</script></head><body><div style="display:none">Hidden</div></body></html>"""
        assertTrue(runCatching { validator.validate(blank) }.isFailure)
    }
    @Test fun projectStorageIsIsolatedEvenWhenGeneratedAppsUseTheSameKey() {
        val first = project(); val second = project()
        assertNotEquals(generatedAppOrigin(first.id), generatedAppOrigin(second.id))
        lateinit var view: GeneratedAppWebView
        instrumentation.runOnMainSync { view = GeneratedAppWebView(app) }
        fun load(id: String) {
            val loaded = CountDownLatch(1)
            instrumentation.runOnMainSync {
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView, url: String) { loaded.countDown() }
                }
                view.loadApp(fixture(), id)
            }
            assertTrue(loaded.await(10, TimeUnit.SECONDS))
        }
        fun evaluate(js: String): String {
            val done = CountDownLatch(1); val result = AtomicReference<String>()
            instrumentation.runOnMainSync { view.evaluateJavascript(js) { result.set(it); done.countDown() } }
            assertTrue(done.await(5, TimeUnit.SECONDS)); return result.get()
        }
        try {
            load(first.id); evaluate("localStorage.setItem('shared-key','first')")
            load(second.id); assertEquals("null", evaluate("localStorage.getItem('shared-key')"))
            load(first.id); assertEquals("\"first\"", evaluate("localStorage.getItem('shared-key')"))
        } finally {
            instrumentation.runOnMainSync { view.destroy(); ids.forEach { android.webkit.WebStorage.getInstance().deleteOrigin(generatedAppOrigin(it).removeSuffix("/")) } }
        }
    }
}
