package com.buildai.app.data.api

import com.buildai.app.data.model.Message
import com.buildai.app.data.model.MessageRole
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

fun answer(text: String, finish: String = "STOP") = MockResponse().setBody(JSONObject().put("candidates", JSONArray().put(
    JSONObject().put("finishReason", finish).put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))).toString())

class GeminiApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: GeminiApi
    @Before fun setup() { server = MockWebServer(); server.start(); api = GeminiApi(endpoint = server.url("/")) }
    @After fun teardown() { server.shutdown() }
    private fun contents() = JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "Hello"))))

    @Test fun gemmaUsesThinkingLevelAndKeyHeader() = runBlocking {
        server.enqueue(answer("Hello"))
        assertEquals("Hello", api.generate("gemma-4-31b-it", "secret-test-key", contents()))
        val request = server.takeRequest()
        assertEquals("secret-test-key", request.getHeader("x-goog-api-key"))
        assertFalse(request.path!!.contains("secret-test-key"))
        val thinking = JSONObject(request.body.readUtf8()).getJSONObject("generationConfig").getJSONObject("thinkingConfig")
        assertEquals("minimal", thinking.getString("thinkingLevel")); assertFalse(thinking.has("thinkingBudget"))
    }
    @Test fun excludesThoughtPartsAndReadsAllTextParts() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"private reasoning","thought":true},{"text":"Hello "},{"text":"friend"}]}}]}"""))
        assertEquals("Hello friend", api.generate("gemini-2.5-flash-lite", "key", contents()))
    }
    @Test fun rejectsTokenLimitInsteadOfAcceptingPartialOutput() = runBlocking {
        server.enqueue(answer("<html>", "MAX_TOKENS"))
        val result = runCatching { api.generate("gemini-2.5-flash-lite", "key", contents()) }
        assertTrue(result.isFailure); assertTrue(result.exceptionOrNull()!!.message!!.contains("cut short"))
    }
    @Test fun authFailureDoesNotTryEveryModel() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        val result = GeminiRepository(api, 0).sendMessage(listOf(Message(role = MessageRole.USER, content = "Hi")), "bad-key", "gemma-4-31b-it")
        assertTrue(result.isFailure); assertEquals(1, server.requestCount)
    }
    @Test fun cancellationStopsAnInFlightHttpRequest() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async { api.generate("gemini-2.5-flash-lite", "key", contents()) }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })
        withTimeout(2_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
    }
    @Test fun malformedPlanFallsBackInsteadOfDisplayingBrokenJson() = runBlocking {
        server.enqueue(answer("<plan>{}</plan>"))
        server.enqueue(answer("<plan>{\"appName\":\"Notes\",\"description\":\"Save notes\",\"features\":[\"Add notes\"],\"screens\":[\"Home\"]}</plan>"))
        val result = GeminiRepository(api, 0).sendMessage(listOf(Message(role = MessageRole.USER, content = "Notes")), "key", "gemma-4-31b-it")
        assertTrue(result.isSuccess); assertEquals("gemma-4-26b-a4b-it", result.getOrThrow().second)
    }
    @Test fun ordinaryChatPreservesPlanTagsInCodeWithoutParsingOrRetryingThem() = runBlocking {
        val explanation = "XML example: <plan>{not an app plan}</plan>"
        server.enqueue(answer(explanation))
        val result = GeminiRepository(api, 0).sendMessage(listOf(Message(role = MessageRole.USER, content = "Explain this XML")),
            "fake", "chosen", cleanPlanResponse = false, fallbackEnabled = false)
        assertEquals(explanation, result.getOrThrow().first); assertEquals(1, server.requestCount)
    }

    @Test fun streamingReadsChunksExcludesThoughtsAndUsesHeader() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            ": keepalive\n\ndata: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"private\",\"thought\":true},{\"text\":\"Hello \"}]}}]}\n\n" +
            "data: {\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"friend\"}]}}]}\n\n"))
        assertEquals(listOf("Hello ", "friend"), api.stream("gemini-2.5-flash-lite", "stream-secret", contents()).toList())
        val request = server.takeRequest()
        assertEquals("/v1beta/models/gemini-2.5-flash-lite:streamGenerateContent?alt=sse", request.path)
        assertEquals("stream-secret", request.getHeader("x-goog-api-key"))
        assertFalse(request.path!!.contains("stream-secret"))
    }

    @Test fun prematureStreamRetainsPartialAndDoesNotSpliceFallback() = runBlocking {
        server.enqueue(MockResponse().setBody("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Saved partial\"}]}}]}\n\n"))
        val partial = mutableListOf<String>()
        val result = GeminiRepository(api, 0).sendMessage(listOf(Message(role = MessageRole.USER, content = "Hi")), "key", "gemini-2.5-flash-lite", onPartial = partial::add)
        assertTrue(result.isFailure)
        assertEquals(listOf("Saved partial"), partial)
        assertEquals(1, server.requestCount)
    }

    @Test fun streamingCancellationClosesWaitingConnection() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch { api.stream("gemini-2.5-flash-lite", "key", contents()).toList() }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })
        withTimeout(2_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
    }

    @Test fun firstStreamChunkArrivesBeforeTheResponseFinishes() = runBlocking {
        val first = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Early text\"}]}}]}\n\n"
        val last = "data: {\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\" done\"}]}}]}\n\n"
        val response = MockResponse().setBody(first + last).throttleBody(first.toByteArray().size.toLong(), 3, TimeUnit.SECONDS)
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            // QueueDispatcher's throttle also delays reading the incoming request.
            override fun peek() = MockResponse()
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = response
        }
        val arrived = CompletableDeferred<String>()
        val job = launch { api.stream("gemini-2.5-flash-lite", "key", contents()).collect { arrived.complete(it) } }
        try {
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
            assertEquals("Early text", withTimeout(2_000) { arrived.await() })
            assertTrue(job.isActive)
        } finally { withTimeout(2_000) { job.cancelAndJoin() } }
    }

}
