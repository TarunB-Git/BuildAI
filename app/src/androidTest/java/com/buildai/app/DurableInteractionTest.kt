package com.buildai.app

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.api.GeminiApi
import com.buildai.app.data.jobs.ConversationSessions
import com.buildai.app.data.model.*
import com.buildai.app.data.storage.*
import com.buildai.app.viewmodel.ConversationViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class DurableInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private fun reply(text: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: " + JSONObject().put("candidates", JSONArray().put(
        JSONObject().put("finishReason", "STOP").put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))).toString() + "\n\n")

    @Test fun unsentAssistantEntryAndModeSurviveViewModelRecreation() {
        val before = app.container.projects.assistantEntry()
        val store = ViewModelStore()
        try {
            instrumentation.runOnMainSync {
                val first = com.buildai.app.viewmodel.HomeViewModel(app)
                store.put("first", first)
                first.setEntryText("Saved before choosing a model ☀")
                first.setAssistantMode(AssistantMode.CHAT)
                store.clear()
                val second = com.buildai.app.viewmodel.HomeViewModel(app)
                store.put("second", second)
                assertEquals("Saved before choosing a model ☀", second.entryText.value)
                assertEquals(AssistantMode.CHAT, second.assistantMode.value)
                assertFalse(app.container.projects.listAll().any { it.assistantEntryDraft })
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            app.container.projects.save(before)
        }
    }

    @Test fun replyAndDraftSurviveBackAndViewModelDestruction() = runBlocking {
        val server = MockWebServer().also { it.start() }
        val container = AppContainer(app, GeminiApi(endpoint = server.url("/")))
        val settings = container.settings
        val priorBackend = settings.getChatBackend(); val priorKey = settings.getApiKey()
        val store = ViewModelStore()
        var id: String? = null
        try {
            settings.saveChatBackend("cloud"); settings.saveApiKey("controlled-test-key")
            server.enqueue(reply("A saved reply after Back").setBodyDelay(3, TimeUnit.SECONDS))
            lateinit var first: ConversationViewModel
            lateinit var second: ConversationViewModel
            instrumentation.runOnMainSync {
                first = ConversationViewModel(app, container.conversation, container.projects, settings, sessions = container.sessions)
                store.put("first", first)
                first.startNewConversation(AssistantMode.CHAT)
                first.sendUserMessage("Remember the first request")
                id = first.projectId
                first.setDraftText("A draft I am still writing")
            }
            assertNotNull(server.takeRequest(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                first.leaveProject(); store.clear()
                second = ConversationViewModel(app, container.conversation, container.projects, settings, sessions = container.sessions)
                store.put("second", second)
                second.loadConversation(checkNotNull(id))
                assertTrue(second.isLoading.value)
                assertEquals("A draft I am still writing", second.draftText.value)
            }
            withTimeout(15_000) { second.isLoading.first { !it } }
            assertNull(second.error.value)
            assertTrue(container.projects.load(checkNotNull(id))!!.messages.any { it.content == "A saved reply after Back" })
            assertEquals("A draft I am still writing", container.projects.load(checkNotNull(id))!!.draftText)
            assertNull(second.projectPlan.value)
        } finally {
            instrumentation.runOnMainSync { store.clear(); id?.let(container.sessions::cancel) }
            id?.let(container.projects::delete)
            settings.saveChatBackend(priorBackend); priorKey?.let(settings::saveApiKey) ?: settings.clearApiKey()
            server.shutdown()
        }
    }

    @Test fun newInputQueuesWithoutChangingTheActiveRequest() = runBlocking {
        val server = MockWebServer().also { it.start() }
        val container = AppContainer(app, GeminiApi(endpoint = server.url("/")))
        val settings = container.settings
        val priorBackend = settings.getChatBackend(); val priorKey = settings.getApiKey()
        var id: String? = null
        lateinit var vm: ConversationViewModel
        try {
            settings.saveChatBackend("cloud"); settings.saveApiKey("controlled-test-key")
            server.enqueue(reply("First answer").setBodyDelay(2, TimeUnit.SECONDS)); server.enqueue(reply("Second answer"))
            instrumentation.runOnMainSync {
                vm = ConversationViewModel(app, container.conversation, container.projects, settings, sessions = container.sessions)
                vm.startNewConversation(AssistantMode.CHAT); vm.sendUserMessage("First input"); id = vm.projectId
            }
            val first = checkNotNull(server.takeRequest(10, TimeUnit.SECONDS)).body.readUtf8()
            instrumentation.runOnMainSync { vm.sendUserMessage("Second input") }
            assertEquals(listOf("Second input"), container.projects.load(checkNotNull(id))!!.queuedInputs)
            assertFalse(first.contains("Second input"))
            val second = checkNotNull(server.takeRequest(15, TimeUnit.SECONDS)).body.readUtf8()
            assertTrue(second.contains("First input")); assertTrue(second.contains("First answer")); assertTrue(second.contains("Second input"))
            withTimeout(15_000) { vm.messages.first { list -> list.any { it.content == "Second answer" } } }
            withTimeout(15_000) { BuildAiDatabase.get(app).jobs().observe().first { jobs ->
                jobs.count { it.projectId == id && it.status == "COMPLETED" } == 2
            } }
            assertEquals(emptyList<String>(), container.projects.load(checkNotNull(id))!!.queuedInputs)
            assertEquals(2, BuildAiDatabase.get(app).jobs().list().count { it.projectId == id && it.status == "COMPLETED" })
        } finally {
            instrumentation.runOnMainSync { vm.leaveProject(); id?.let(container.sessions::cancel) }
            id?.let(container.projects::delete)
            settings.saveChatBackend(priorBackend); priorKey?.let(settings::saveApiKey) ?: settings.clearApiKey()
            server.shutdown()
        }
    }

    @Test fun legacyImportKeepsOriginalAndDoesNotOverwriteNewerDatabaseState() {
        val id = UUID.randomUUID().toString()
        val original = File(app.filesDir, "conversations/$id.json")
        original.parentFile!!.mkdirs()
        val text = Conversation(id = id, title = "Original legacy project").toJson().toString()
        original.writeText(text)
        try {
            val repo = ConversationRepository(app)
            assertEquals(id, repo.load(id)!!.id)
            repo.update(id) { it.copy(title = "Updated in Room", draftText = "Retained draft") }
            assertEquals(text, original.readText())
            val reopened = ConversationRepository(app).load(id)!!
            assertEquals("Updated in Room", reopened.title); assertEquals("Retained draft", reopened.draftText)
        } finally { ConversationRepository(app).delete(id) }
    }

    @Test fun interruptedPartialReplyIsRetainedAndRetryable() {
        val id = UUID.randomUUID().toString()
        val repo = app.container.projects
        repo.save(Conversation(id = id, replyStatus = "RUNNING", messages = listOf(
            Message(role = MessageRole.USER, content = "An interrupted input"),
            Message(role = MessageRole.ASSISTANT, content = "Partial response")), draftText = "Saved next input"))
        try {
            val manager = ConversationSessions(app, app.container.conversation, repo, app.container.settings)
            instrumentation.runOnMainSync {
                val session = checkNotNull(manager.get(id))
                assertFalse(session.isLoading.value)
                assertTrue(session.messages.value.first().failed)
                assertEquals("Partial response", session.messages.value.last().content)
                assertEquals("Saved next input", session.draftText.value)
                assertTrue(session.error.value!!.contains("Retry"))
            }
        } finally { repo.delete(id) }
    }
}
