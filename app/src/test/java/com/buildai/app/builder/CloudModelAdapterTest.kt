package com.buildai.app.builder

import com.buildai.app.data.api.GeminiApi
import com.buildai.app.data.model.Message
import com.buildai.app.data.model.MessageRole
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CloudModelAdapterTest {
    private fun response() = MockResponse().setHeader("Content-Type", "text/event-stream").setBody("""data: {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"ok"}]}}]}

""")
    @Test fun disablingCloudOrClearingItsKeyBlocksSubsequentToolRequests() = runBlocking {
        val server = MockWebServer().also { it.start() }
        var enabled = true; var key: String? = "controlled-test-key"
        val adapter = CloudModelAdapter(GeminiApi(endpoint = server.url("/")), "controlled-model") {
            check(enabled) { "Cloud is disabled" }; checkNotNull(key) { "Key is missing" }
        }
        fun reply() = adapter.reply(File("unused"), "A controlled prompt", listOf(Message(role = MessageRole.USER, content = "Test")), 100, 2048)
        try {
            server.enqueue(response()); var result = ""; reply().collect { result += it }
            assertEquals("ok", result); assertEquals(1, server.requestCount)
            enabled = false
            assertTrue(runCatching { reply().collect() }.isFailure); assertEquals(1, server.requestCount)
            enabled = true; key = null
            assertTrue(runCatching { reply().collect() }.isFailure); assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun aReplyConstructedBeforeRevocationStillChecksConsentWhenCollected() = runBlocking {
        val server = MockWebServer().also { it.start() }
        var enabled = true
        val adapter = CloudModelAdapter(GeminiApi(endpoint = server.url("/")), "controlled-model") {
            check(enabled) { "Cloud is disabled" }; "controlled-test-key"
        }
        try {
            val pending = adapter.reply(File("unused"), "Prompt", listOf(Message(role = MessageRole.USER, content = "Test")), 100, 2048)
            enabled = false
            assertTrue(runCatching { pending.collect() }.isFailure); assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun queuedCloudRequestRechecksConsentAfterAcquiringItsSlot() = runBlocking<Unit> {
        val server = MockWebServer().also { it.start() }
        val enabled = AtomicBoolean(true); val resolutions = AtomicInteger()
        val adapter = CloudModelAdapter(GeminiApi(endpoint = server.url("/")), "controlled-model") {
            resolutions.incrementAndGet(); check(enabled.get()) { "Cloud is disabled" }; "controlled-test-key"
        }
        fun reply() = adapter.reply(File("unused"), "Prompt", listOf(Message(role = MessageRole.USER, content = "Test")), 100, 2048)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)); server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val first = launch { reply().collect() }; val second = launch { reply().collect() }
        try {
            withTimeout(5000) { while (server.requestCount < 2) delay(10) }
            val before = resolutions.get()
            val queued = async { runCatching { reply().collect() } }
            withTimeout(5000) { while (resolutions.get() <= before) delay(10) }
            assertEquals(2, server.requestCount)
            enabled.set(false); first.cancelAndJoin(); second.cancelAndJoin()
            assertTrue(withTimeout(5000) { queued.await() }.isFailure)
            assertEquals(2, server.requestCount)
        } finally { first.cancelAndJoin(); second.cancelAndJoin(); server.shutdown() }
    }

}
