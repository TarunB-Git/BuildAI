package com.buildai.app.data.api

import com.buildai.app.data.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PwaGeneratorTest {
    private lateinit var server: MockWebServer
    private lateinit var generator: PwaGenerator
    private val plan = ProjectPlan("Notes", "Save notes", AppType.APP, listOf("Add notes"), listOf("Home"), "Everyone", Complexity.SIMPLE, "A few minutes")
    private val design = DesignSpec("tabbed", "forest", "music", listOf(TabSpec("Saved", "note")))
    private fun html(marker: String) = "<!DOCTYPE html><html><head></head><body><button>$marker</button><script>let items=[];</script></body></html>"
    @Before fun setup() { server = MockWebServer(); server.start(); generator = PwaGenerator(GeminiApi(endpoint = server.url("/"))) }
    @After fun teardown() { server.shutdown() }
    private suspend fun generate(validate: suspend (String) -> Unit = {}) = generator.generate(plan, design, "key", "gemini-2.5-flash-lite", { _, _ -> }, validate)

    @Test fun reviewsWithSuccessfulFallbackAndIncludesSelectedIcons() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503)); server.enqueue(answer(html("original"))); server.enqueue(answer(html("reviewed")))
        assertTrue(generate().getOrThrow().contains("reviewed"))
        val first = server.takeRequest(); val working = server.takeRequest(); val review = server.takeRequest()
        assertNotEquals(first.path, working.path); assertEquals(working.path, review.path)
        val prompt = JSONObject(working.body.readUtf8()).getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(prompt.contains("App icon: music")); assertTrue(prompt.contains("Saved (icon: note)"))
    }
    @Test fun invalidReviewKeepsCompleteOriginal() = runBlocking {
        server.enqueue(answer(html("original"))); server.enqueue(answer("<!DOCTYPE html><html>cut off"))
        assertEquals(html("original"), generate().getOrThrow())
    }
    @Test fun runtimeFailureInReviewFallsBackToValidOriginal() = runBlocking {
        server.enqueue(answer(html("original"))); server.enqueue(answer(html("broken")))
        assertEquals(html("original"), generate { require(!it.contains("broken")) }.getOrThrow())
    }
    @Test fun runtimeFailureInBothVersionsFailsGeneration() = runBlocking {
        server.enqueue(answer(html("original"))); server.enqueue(answer(html("reviewed")))
        assertTrue(generate { error("JavaScript failed") }.isFailure)
    }
    @Test fun invalidKeyStopsGenerationWithoutReviewOrFallback() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(generate().isFailure); assertEquals(1, server.requestCount)
    }
}
