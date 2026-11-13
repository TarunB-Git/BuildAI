package com.buildai.app.data.api

import com.buildai.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class CloudProviderApiTest {
    private val input = listOf(Message(role = MessageRole.USER, content = "Hello"))
    private fun event(text: String) = "data: $text\n\n"
    @Test fun eachProviderUsesItsOwnAuthenticationBodyAndStreamProtocol() = runBlocking {
        val streams = mapOf(
            CloudProvider.OPENAI to (event("""{"type":"response.output_text.delta","delta":"Hello"}""") + event("""{"type":"response.completed"}""")),
            CloudProvider.CLAUDE to (event("""{"type":"content_block_delta","delta":{"type":"text_delta","text":"Hello"}}""") + event("""{"type":"message_stop"}""")),
            CloudProvider.OPENROUTER to (event("""{"choices":[{"delta":{"content":"Hello"}}]}""") + event("[DONE]")))
        for ((provider, stream) in streams) {
            val server = MockWebServer().also { it.start() }
            try {
                server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(stream))
                var output = ""
                CloudProviderApi(testEndpoint = server.url("/")).stream(provider, "test-model", "fake-key", input, "Be concise").collect { output += it }
                assertEquals("Hello", output)
                val request = server.takeRequest(2, TimeUnit.SECONDS)!!; val body = JSONObject(request.body.readUtf8())
                assertEquals("test-model", body.getString("model")); assertTrue(body.getBoolean("stream"))
                if (provider == CloudProvider.CLAUDE) { assertEquals("fake-key", request.getHeader("x-api-key")); assertEquals("2023-06-01", request.getHeader("anthropic-version")) }
                else assertEquals("Bearer fake-key", request.getHeader("Authorization"))
                if (provider == CloudProvider.OPENAI) { assertFalse(body.getBoolean("store")); assertEquals("Hello", body.getJSONArray("input").getJSONObject(0).getString("content")) }
                else assertTrue(body.has("messages"))
            } finally { server.shutdown() }
        }
    }
    @Test fun disconnectedPartialReplyIsNotReportedAsComplete() = runBlocking {
        val server = MockWebServer().also { it.start() }
        try {
            server.enqueue(MockResponse().setBody(event("""{"type":"response.output_text.delta","delta":"Partial"}""")))
            var output = ""
            val result = runCatching { CloudProviderApi(testEndpoint = server.url("/")).stream(CloudProvider.OPENAI, "test", "fake", input, "Prompt").collect { output += it } }
            assertEquals("Partial", output); assertTrue(result.isFailure)
        } finally { server.shutdown() }
    }
    @Test fun cancellationClosesAWaitingHttpCall() = runBlocking {
        val server = MockWebServer().also { it.start() }
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = launch { CloudProviderApi(testEndpoint = server.url("/")).stream(CloudProvider.CLAUDE, "test", "fake", input, "Prompt").collect() }
            withTimeout(5000) { withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) } }
            withTimeout(5000) { job.cancelAndJoin() }
        } finally { server.shutdown() }
    }
    @Test fun fallbackOffSendsOnlyTheChosenGeminiModel() = runBlocking {
        val server = MockWebServer().also { it.start() }
        try {
            server.enqueue(MockResponse().setResponseCode(429))
            val result = GeminiRepository(GeminiApi(endpoint = server.url("/")), 0).sendMessage(input, "fake", "chosen", fallbackEnabled = false)
            assertTrue(result.isFailure); assertEquals(1, server.requestCount)
            assertTrue(server.takeRequest().path!!.contains("chosen"))
        } finally { server.shutdown() }
    }
}
