package com.buildai.app

import android.app.ActivityManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.api.*
import com.buildai.app.data.local.*
import com.buildai.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class InferenceLifetimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication

    @Test fun productiveLegacyBuildFinishesAfterTheOldDeadline() = runBlocking {
        // Controlled output exercises the actual orchestration and WebView validation.
        // It is a lifetime regression, never a claim about model-written app quality.
        val output = JSONObject().put("html", "<input id='a' type='number'><input id='b' type='number'><button id='sum'>Calculate</button><output id='result'>0</output>")
            .put("js", "document.getElementById('sum').onclick=()=>{document.getElementById('result').textContent=Number(document.getElementById('a').value)+Number(document.getElementById('b').value);};").toString()
        var unloaded = false
        val adapter = object : ModelAdapter {
            override val supportsStructuredOutput = true
            override val resourceProfile = ModelResourceProfile(1, "controlled fixture")
            override fun reply(modelFile: File, systemPrompt: String, history: List<Message>, maxTokens: Int, contextSize: Int,
                stopAt: String?, grammar: String?, onStatus: (String) -> Unit, onProgress: (InferenceProgress) -> Unit, priority: InferencePriority): Flow<String> = flow {
                val watchdog = InferenceWatchdog(SystemClock::elapsedRealtime)
                watchdog.record(InferenceProgress(InferenceStage.DECODING, 0))
                val chunks = output.chunked((output.length + 4) / 5)
                chunks.forEachIndexed { i, chunk ->
                    if (i > 0) delay(47_000)
                    val event = InferenceProgress(InferenceStage.DECODING, i + 1L)
                    watchdog.record(event); onProgress(event)
                    assertNull(watchdog.stalledStage())
                    emit(chunk)
                }
            }
            override suspend fun unload() { unloaded = true }
        }
        val plan = QuickPlan.fromIdea("A calculator that adds two numbers")
        val started = SystemClock.elapsedRealtime()
        val result = withTimeout(240_000) {
            LocalPwaGenerator(app, adapter).generate(File(app.cacheDir, "controlled-model"), plan, DesignSpec(), { _, _ -> }, GeneratedAppValidator(app)::validate)
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        println("CONTROLLED_BUILD_ELAPSED_MS=$elapsed")
        assertTrue("Build failed after productive progress: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue("Did not cross the original total deadline", elapsed > 180_000)
        assertTrue(unloaded)
    }

    @Test fun nativeProcessDeathInterruptsRequestWithoutKillingUi() = runBlocking {
        val fixture = File(app.filesDir, "models/test.gguf")
        assertTrue("A real GGUF fixture is required for this gate", fixture.isFile)
        val uiPid = android.os.Process.myPid()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val started = CompletableDeferred<Unit>()
            val reply = async {
                runCatching {
                    LocalLlmEngine(app).reply(fixture, "Answer briefly.",
                        listOf(Message(role = MessageRole.USER, content = "Summarize this context: " + "A useful personal tool. ".repeat(500))),
                        maxTokens = 128, contextSize = 4096,
                        onStatus = { if (it == "Loading local model…" || it == "Reading your message…") started.complete(Unit) }).collect()
                }
            }
            withTimeout(20_000) { started.await() }
            val processes = app.getSystemService(ActivityManager::class.java).runningAppProcesses
            val nativePid = checkNotNull(processes.firstOrNull { it.processName == app.packageName + ":inference" }).pid
            assertNotEquals(uiPid, nativePid)
            instrumentation.uiAutomation.executeShellCommand("run-as ${app.packageName} kill -9 $nativePid").use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).readBytes()
            }
            val result = withTimeout(15_000) { reply.await() }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("interrupted", true))
            assertEquals(uiPid, android.os.Process.myPid())
            scenario.onActivity { assertFalse(it.isFinishing) }
            // An explicit retry creates a fresh runtime; it never claims native-state resume.
            val recovered = withTimeout(120_000) {
                LocalLlmEngine(app).reply(fixture, "Answer only.", listOf(Message(role = MessageRole.USER, content = "2 plus 2?")),
                    maxTokens = 32, contextSize = 1024).toList().joinToString("")
            }
            assertTrue(recovered.contains("4"))
            LocalLlmEngine(app).unload()
        }
    }

    @Test fun nativeProgressIncludesProcessedPromptAndDecodedTokenCounters() = runBlocking {
        val fixture = File(app.filesDir, "models/test.gguf")
        assertTrue(fixture.isFile)
        val events = mutableListOf<InferenceProgress>()
        try {
            withTimeout(120_000) {
                LocalLlmEngine(app).reply(fixture, "Answer only.", listOf(Message(role = MessageRole.USER, content = "What is 2 plus 2?")),
                    maxTokens = 32, contextSize = 1024, onProgress = { events += it }).collect()
            }
            assertTrue(events.any { it.stage == InferenceStage.LOADING && it.completed > 0 })
            assertTrue(events.any { it.stage == InferenceStage.PREFILL && it.completed > 0 && it.completed <= it.total })
            assertTrue(events.any { it.stage == InferenceStage.DECODING && it.completed > 0 })
        } finally { LocalLlmEngine(app).unload() }
    }
}
