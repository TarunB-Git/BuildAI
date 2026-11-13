package com.buildai.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.api.GeminiApi
import com.buildai.app.data.model.AssistantMode
import com.buildai.app.data.storage.BuildAiDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Explicitly orchestrated in two processes by emulator-checks.sh. Never silently counted. */
class ProcessRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private val fixture get() = File(app.cacheDir, "process-recovery-fixture.json")
    @Test fun seedAnActualRunningReply() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recoveryPhase") == "seed")
        val server = MockWebServer().also { it.start() }
        val first = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Saved partial answer\"}]}}]}\n\n"
        val finish = "data: {\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\" completed\"}]}}]}\n\n"
        val response = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(first + finish)
            .throttleBody(first.toByteArray().size.toLong(), 60, TimeUnit.SECONDS)
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun peek() = MockResponse()
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = response
        }
        val container = AppContainer(app, GeminiApi(endpoint = server.url("/")))
        val settings = container.settings
        // Clean only this fixture's earlier failed run on its temporary test AVD.
        if (settings.getApiKey() == "controlled-recovery-key") { settings.clearApiKey(); settings.saveChatBackend("local") }
        val previousBackend = settings.getChatBackend()
        val keyWasPresent = settings.getApiKey() != null
        if (!keyWasPresent) settings.saveApiKey("controlled-recovery-key")
        settings.saveChatBackend("cloud")
        ActivityScenario.launch(MainActivity::class.java).use {
            lateinit var session: com.buildai.app.data.jobs.ConversationSession
            instrumentation.runOnMainSync {
                session = container.sessions.create(AssistantMode.CHAT)
                session.setDraftText("Draft retained across force-stop")
                session.sendUserMessage("A running request before force-stop")
            }
            withTimeout(15_000) { session.messages.first { list -> list.any { it.content == "Saved partial answer" } } }
            val id = checkNotNull(session.projectId)
            assertTrue(session.isLoading.value)
            withTimeout(5_000) {
                while (container.projects.load(id)!!.messages.none { it.content == "Saved partial answer" }) delay(20)
            }
            assertTrue(BuildAiDatabase.get(app).jobs().list().any { it.projectId == id && it.status == "RUNNING" })
            // Contains no credential, only test identity and restoration choices.
            fixture.writeText(JSONObject().put("projectId", id).put("backend", previousBackend)
                .put("keyWasPresent", keyWasPresent).toString())
        }
        // Host force-stops this process, including its actual HTTP call, before completion.
    }
    @Test fun coldRestartKeepsPartialDraftAndOffersExplicitRetry() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recoveryPhase") == "cold")
        val saved = JSONObject(fixture.readText())
        val id = saved.getString("projectId")
        val settings = app.container.settings
        try {
            assertTrue(BuildAiDatabase.get(app).jobs().list().any { it.projectId == id && it.status == "INTERRUPTED" })
            instrumentation.runOnMainSync {
                val session = checkNotNull(app.container.sessions.get(id))
                assertFalse(session.isLoading.value)
                assertEquals("Draft retained across force-stop", session.draftText.value)
                assertTrue(session.messages.value.any { it.content == "Saved partial answer" })
                assertTrue(session.messages.value.first().failed)
                assertTrue(session.error.value!!.contains("Retry"))
            }
        } finally {
            settings.saveChatBackend(saved.getString("backend"))
            if (!saved.getBoolean("keyWasPresent")) settings.clearApiKey()
            app.container.projects.delete(id)
            fixture.delete()
        }
    }
}
