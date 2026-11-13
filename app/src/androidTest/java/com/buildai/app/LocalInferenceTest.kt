package com.buildai.app

import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.api.GeminiRepository
import com.buildai.app.data.local.LocalLlmEngine
import com.buildai.app.data.model.Message
import com.buildai.app.data.model.MessageRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/** Real model checks require files/models/test.gguf in the debug app's private storage. */
class LocalInferenceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun fixture(): File = File(app.filesDir, "models/test.gguf").also {
        assumeTrue("A GGUF model must be installed for this test", it.isFile)
    }

    @Test fun malformedModelReportsAnErrorWithoutCrashing() = runBlocking {
        val file = File(app.cacheDir, "invalid-inference.gguf")
        file.writeBytes(byteArrayOf(71, 71, 85, 70, 3, 0, 0, 0) + ByteArray(128))
        val engine = LocalLlmEngine(app)
        try {
            try {
                withTimeout(10_000) {
                    engine.reply(file, "Be brief.", listOf(Message(role = MessageRole.USER, content = "Hello"))).toList()
                }
                fail("An empty GGUF must be rejected")
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("Could not load this GGUF"))
            }
        } finally {
            engine.unload()
            file.delete()
        }
    }

    @Test fun ggufModelStreamsACompleteShortReplyOnDevice() = runBlocking {
        val file = fixture()
        val engine = LocalLlmEngine(app)
        try {
            val chunks = withTimeout(120_000) {
                engine.reply(file, "Reply with the answer only.",
                    listOf(Message(role = MessageRole.USER, content = "What is 2 plus 2?")),
                    maxTokens = 32, contextSize = 1024).toList()
            }
            val reply = chunks.joinToString("").trim()
            assertTrue("The model did not answer the simple prompt: $reply", reply.contains("4"))
        } finally {
            engine.unload()
        }
    }

    @Test fun actualPlanningPromptThenLongHistoryCanReplyWithoutReloading() = runBlocking {
        val file = fixture()
        val engine = LocalLlmEngine(app)
        val system = GeminiRepository().systemPrompt
        try {
            val first = withTimeout(120_000) {
                engine.reply(file, system,
                    listOf(Message(role = MessageRole.USER, content = "Help me make a simple task list.")),
                    maxTokens = 64, contextSize = 2048).toList().joinToString("")
            }
            assertTrue(first.isNotBlank())
            val history = listOf(
                Message(role = MessageRole.USER, content = "An old request. ".repeat(1500)),
                Message(role = MessageRole.ASSISTANT, content = "An old answer."),
                Message(role = MessageRole.USER, content = "Help me make a simple task list."),
                Message(role = MessageRole.ASSISTANT, content = first),
                Message(role = MessageRole.USER, content = "Let me edit task names too.")
            )
            val stages = ConcurrentLinkedQueue<String>()
            val next = withTimeout(120_000) {
                engine.reply(file, system, history, maxTokens = 64, contextSize = 2048,
                    onStatus = { stages.add(it) }).toList().joinToString("")
            }
            assertTrue("The second reply failed", next.isNotBlank())
            assertFalse("The warm model was loaded again", stages.any { it.startsWith("Loading") })
        } finally {
            engine.unload()
        }
    }

    @Test fun stoppingDuringPromptProcessingAllowsTheNextReply() = runBlocking {
        val file = fixture()
        val engine = LocalLlmEngine(app)
        try {
            val reading = CompletableDeferred<Unit>()
            val reply = launch {
                engine.reply(file, "Reply briefly.",
                    listOf(Message(role = MessageRole.USER, content = "hello ".repeat(700))),
                    maxTokens = 256, contextSize = 2048,
                    onStatus = { if (it.startsWith("Reading")) reading.complete(Unit) }).toList()
            }
            withTimeout(120_000) { reading.await() }
            withTimeout(5_000) { reply.cancelAndJoin() }
            val next = withTimeout(30_000) {
                engine.reply(file, "Reply briefly.",
                    listOf(Message(role = MessageRole.USER, content = "Say hello.")),
                    maxTokens = 16, contextSize = 2048).toList().joinToString("")
            }
            assertTrue("Native cancellation left the runtime stuck", next.isNotBlank())
        } finally {
            engine.unload()
        }
    }
}
