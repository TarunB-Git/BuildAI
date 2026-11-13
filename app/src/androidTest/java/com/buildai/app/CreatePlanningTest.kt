package com.buildai.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.builder.*
import com.buildai.app.data.api.GeminiApi
import com.buildai.app.data.model.*
import com.buildai.app.viewmodel.ConversationViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Controlled streaming planner responses establish state/UX contracts; live Gemini is manual. */
class CreatePlanningTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private fun response(json: JSONObject) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: " + JSONObject().put("candidates", JSONArray().put(
        JSONObject().put("finishReason", "STOP").put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", json.toString())))))).toString() + "\n\n")
    @Test fun planningCommitPreservesDraftAndQueueAndRejectsStaleOrStoppedWork() {
        val brief = ProjectBrief().addInput("u1", "A converter")
        val pending = Message(role = MessageRole.ASSISTANT, content = "Reading requirements")
        val project = Conversation(brief = brief, messages = listOf(Message(id = "u1", role = MessageRole.USER, content = "A converter"), pending),
            draftText = "Unsent text", queuedInputs = listOf("Use metres"), replyStatus = "RUNNING").also(app.container.projects::save)
        val jobs = com.buildai.app.data.storage.BuildAiDatabase.get(app).jobs()
        val jobId = java.util.UUID.randomUUID().toString()
        val job = com.buildai.app.data.storage.StoredAgentJob(jobId, project.id, "CHAT", "RUNNING", JSONObject().put("brief", brief.json()).toString(), "Planning")
        val plan = NativePlan("Converter", listOf(FeaturePlan("convert", "Convert", "Enter centimetres and read inches", listOf("2.54 gives 1"))), listOf("Convert"), emptyList())
        val next = brief.copy(plan = plan, planningPending = false)
        val reply = Message(role = MessageRole.ASSISTANT, content = "Review the plan")
        try {
            jobs.save(job.copy(status = "CANCELLED"))
            assertTrue(runCatching { app.container.projects.completePlanningReply(project.id, jobId, brief.version, next, pending.id, reply, "Controlled") }.isFailure)
            assertEquals(project, app.container.projects.load(project.id))
            jobs.save(job)
            assertTrue(runCatching { app.container.projects.completePlanningReply(project.id, jobId, brief.version + 1, next, pending.id, reply, "Controlled") }.isFailure)
            assertEquals("RUNNING", jobs.load(jobId)!!.status); assertEquals(project, app.container.projects.load(project.id))
            val completed = app.container.projects.completePlanningReply(project.id, jobId, brief.version, next, pending.id, reply, "Controlled")
            assertEquals(next, app.container.projects.load(project.id)!!.brief)
            assertEquals("IDLE", completed.replyStatus); assertEquals("Unsent text", completed.draftText); assertEquals(listOf("Use metres"), completed.queuedInputs)
            assertEquals(reply, completed.messages.last()); assertFalse(completed.messages.any { it.id == pending.id })
            val savedJob = jobs.load(jobId)!!; assertEquals("COMPLETED", savedJob.status)
            assertEquals(next, ProjectBrief.read(JSONObject(savedJob.checkpoint)))
        } finally { app.container.projects.delete(project.id) }
    }
    @Test fun questionsAreRememberedPlanAppearsWithoutPhraseAndQueuedCorrectionUsesSavedEvidence() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use {
            val server = MockWebServer().also { it.start() }; val container = AppContainer(app, GeminiApi(endpoint = server.url("/")))
            val settings = container.settings; val previousBackend = settings.getChatBackend(); val previousKey = settings.getApiKey()
            var projectId: String? = null
            lateinit var first: ConversationViewModel
            lateinit var reopened: ConversationViewModel
            try {
                settings.saveChatBackend("cloud"); settings.saveApiKey("controlled-test-key")
                var calls = 0
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        calls++
                        val body = JSONObject(request.body.readUtf8())
                        assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"))
                        val context = JSONObject(body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"))
                        val brief = ProjectBrief.read(context.getJSONObject("savedBrief"))
                        return if (calls == 1) response(JSONObject().put("reply", "Which units should it convert?").put("questions", JSONArray().put(JSONObject().put("id", "units").put("question", "Which units should it convert?")))).setBodyDelay(1, TimeUnit.SECONDS)
                        else {
                            val latest = brief.turns.last()
                            val units = if (latest.text.startsWith("Use metres")) "Metres" else "Centimetres"
                            val plan = NativePlan("Length converter", listOf(FeaturePlan("convert", "Convert length", "Enter $units and read inches", listOf("Changed input changes the result"))), listOf("Convert"), emptyList())
                            response(JSONObject().put("reply", "Your plan is ready to edit").put("answers", JSONArray().put(JSONObject().put("questionId", "units").put("messageId", latest.messageId).put("answer", "$units to inches")))
                                .put("plan", plan.json()).put("suggestions", JSONArray().put(JSONObject().put("id", "history").put("title", "Save recent conversions").put("benefit", "Reuse a result later"))))
                        }
                    }
                }
                instrumentation.runOnMainSync {
                    first = ConversationViewModel(app, container.conversation, container.projects, settings, sessions = container.sessions)
                    first.startNewConversation(AssistantMode.CREATE); first.sendUserMessage("I want a length converter. Ask me questions.")
                    projectId = first.projectId; first.setDraftText("Unsent correction")
                    first.leaveProject()
                    reopened = ConversationViewModel(app, container.conversation, container.projects, settings, sessions = container.sessions)
                    reopened.loadConversation(checkNotNull(projectId))
                }
                withTimeout(15_000) { reopened.isLoading.first { !it } }
                assertNull(reopened.error.value); assertEquals(1, reopened.brief.value!!.questions.size); assertEquals("Unsent correction", reopened.draftText.value)
                assertNull(reopened.brief.value!!.plan)
                instrumentation.runOnMainSync { reopened.sendUserMessage("Centimetres to inches") }
                withTimeout(15_000) { reopened.brief.first { it?.ready == true } }
                assertEquals("Centimetres to inches", reopened.brief.value!!.answers.single().answer)
                assertFalse(reopened.brief.value!!.suggestions.single().accepted)
                assertTrue(reopened.brief.value!!.questions.isEmpty()); assertEquals(2, calls)
                instrumentation.runOnMainSync { reopened.sendUserMessage("Use metres to inches instead") }
                withTimeout(15_000) { reopened.brief.first { it?.ready == true && it.answers.single().answer == "Metres to inches" } }
                assertEquals(3, reopened.brief.value!!.turns.size); assertEquals(3, calls)
                val cold = com.buildai.app.data.jobs.ConversationSessions(app, container.conversation, container.projects, settings).get(checkNotNull(projectId))!!
                assertEquals(reopened.brief.value, cold.brief.value)
                assertNull(reopened.projectPlan.value)
            } finally {
                instrumentation.runOnMainSync { projectId?.let(container.sessions::cancel); runCatching { first.leaveProject() } }
                projectId?.let(container.projects::delete)
                settings.saveChatBackend(previousBackend); previousKey?.let(settings::saveApiKey) ?: settings.clearApiKey()
                server.shutdown()
            }
        }
    }
}
