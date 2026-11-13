package com.buildai.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.builder.*
import com.buildai.app.data.local.*
import com.buildai.app.data.model.*
import com.buildai.app.data.storage.BuildAiDatabase
import com.buildai.app.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/** Development scenarios, not the held-out phone evaluation. No templates or controlled model answers. */
class LocalNativeBuilderTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private class MeasuredAdapter(app: BuildAiApplication) : ModelAdapter {
        val engine = LocalLlmEngine(app)
        override val supportsStructuredOutput = engine.supportsStructuredOutput
        override val resourceProfile = engine.resourceProfile
        val decoded = mutableListOf<Long>(); val prefills = mutableListOf<Long>()
        val attempts = mutableListOf<JSONObject>()
        val selectionGrammarEnabled = InstrumentationRegistry.getArguments().getString("selectionGrammarEnabled") != "false"
        override fun reply(modelFile: File, systemPrompt: String, history: List<Message>, maxTokens: Int, contextSize: Int,
            stopAt: String?, grammar: String?, onStatus: (String) -> Unit, onProgress: (InferenceProgress) -> Unit, priority: InferencePriority): Flow<String> {
            val output = AtomicLong(); val prompt = AtomicLong()
            val raw = StringBuilder()
            val input = JSONObject(history.single().content)
            val attempt = JSONObject().put("tool", input.optString("tool")).put("failureToRepair", input.optString("failureToRepair")).put("receivedFailedFeature", input.optString("failedFeature").takeIf { it != "null" }?.length ?: 0)
            val effectiveGrammar = grammar.takeUnless { input.optString("tool") == "inspectCapabilities" && !selectionGrammarEnabled }
            attempt.put("grammarEnabled", effectiveGrammar != null)
            attempts += attempt
            return engine.reply(modelFile, systemPrompt, history, maxTokens, contextSize, stopAt, effectiveGrammar, onStatus,
                onProgress = { event ->
                    if (event.stage == InferenceStage.DECODING) output.updateAndGet { maxOf(it, event.completed) }
                    if (event.stage == InferenceStage.PREFILL) prompt.updateAndGet { maxOf(it, event.completed) }
                    onProgress(event)
                }, priority = priority).onEach { raw.append(it) }.onCompletion {
                    decoded += output.get(); prefills += prompt.get(); attempt.put("output", raw.toString()).put("generatedTokens", output.get()).put("promptTokens", prompt.get())
                }
        }
        override suspend fun unload() { engine.unload() }
    }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream -> val bytes = ByteArray(262_144); while (true) { val count = stream.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun type(label: String, value: String) {
        val field = hasSetTextAction() and hasText(label)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(field); compose.onNode(field).performTextReplacement(value)
    }
    private suspend fun build(scenario: String, plan: NativePlan, repairAttempts: Int = 2, interaction: (AppSpec, String) -> Unit) {
        val arguments = InstrumentationRegistry.getArguments()
        val modelId = arguments.getString("nativeModelId") ?: "qwen-06"
        require(modelId in setOf("qwen-06", "qwen-17"))
        val catalog = checkNotNull(ModelCatalog.find(modelId))
        val fixture = File(app.filesDir, if (modelId == "qwen-06") "models/test.gguf" else "models/test-qwen-17.gguf")
        assumeTrue("Pinned GGUF fixture is required", fixture.isFile)
        assertEquals(catalog.sizeBytes, fixture.length()); assertEquals(catalog.sha256, hash(fixture))
        val windowSeconds = arguments.getString("nativeBuilderWindowSeconds")?.toLongOrNull() ?: 420L
        require(windowSeconds in 30..900)
        val project = Conversation(brief = ProjectBrief(version = 1, goal = plan.features.single().workflow, plan = plan, approvedVersion = 1)).also(app.container.projects::save)
        val adapter = MeasuredAdapter(app); val manager = NativeBuildJobs(app, app.container.projects, testAdapter = adapter, repairAttempts = repairAttempts)
        val input = JSONObject().put("brief", project.brief!!.json()).put("model", BuilderModel("local", catalog.name, fixture.absolutePath).json()).put("baseRevision", JSONObject.NULL)
        var id = ""; val started = android.os.SystemClock.elapsedRealtime()
        var result: com.buildai.app.data.storage.StoredAgentJob? = null
        var success = false
        try {
            instrumentation.runOnMainSync { id = manager.enqueue(project.id, input) }
            result = withTimeout(windowSeconds * 1000) { BuildAiDatabase.get(app).jobs().observe().first { rows -> rows.any { it.id == id && it.status !in listOf("RUNNING", "QUEUED") } }.first { it.id == id } }
            assertEquals(result.error, "COMPLETED", result.status)
            val spec = checkNotNull(NativeAppRepository(app).spec(project.id))
            assertNull(app.container.projects.generatedFile(project.id))
            ActivityScenario.launch<NativeRunnerActivity>(Intent(app, NativeRunnerActivity::class.java).putExtra("appId", project.id)).use {
                compose.waitUntil(15_000) { compose.onAllNodesWithText(plan.name).fetchSemanticsNodes().isNotEmpty() }
                interaction(spec, project.id)
            }
            success = true
        } finally {
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            val atDeadline = result ?: BuildAiDatabase.get(app).jobs().load(id)
            instrumentation.runOnMainSync { manager.cancel(project.id) }
            adapter.unload()
            val metrics = JSONObject().put("scenario", scenario).put("fixtureType", "Development; not held-out evaluation")
                .put("model", catalog.name).put("modelSha256", catalog.sha256).put("testWindowSeconds", windowSeconds)
                .put("apkSha256", hash(File(app.applicationInfo.sourceDir))).put("elapsedMsIncludingUiChecks", elapsed)
                .put("testApkSha256", hash(File(instrumentation.context.applicationInfo.sourceDir)))
                .put("successfulOutcome", success).put("jobStatus", atDeadline?.status ?: "test deadline or interruption").put("repairLimit", repairAttempts)
                .put("selectionGrammarEnabled", adapter.selectionGrammarEnabled)
                .put("approvedCapabilities", plan.features.single().composition?.json() ?: JSONObject.NULL)
                .put("error", atDeadline?.error ?: JSONObject.NULL).put("generatedTokensByCall", JSONArray(adapter.decoded))
                .put("promptTokensByCall", JSONArray(adapter.prefills)).put("physicalPhone", false)
            File(app.cacheDir, "native-builder-real-$scenario.json").writeText(metrics.toString(2))
            File(app.cacheDir, "native-builder-real-$scenario-output.json").writeText(JSONObject().put("fixtureType", "Controlled development prompt; real model output").put("attempts", JSONArray(adapter.attempts)).toString(2))
            app.container.projects.delete(project.id)
        }
    }
    @Test fun diagnosticRectangleWithoutRepairs() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use {
            val plan = NativePlan("Rectangle area", listOf(FeaturePlan("area", "Calculate area", "Enter width and height as decimal numbers. Display width multiplied by height as Area; no saved records.",
                listOf("Width 2.5 and height 4 shows Area 10", "Width 7 and height 3 shows Area 21"))), listOf("Calculator"), emptyList())
            build("rectangle-diagnostic", plan, repairAttempts = 0) { _, _ -> }
        }
    }
    @Test fun actualModelBuildsRectangleCalculatorWithChangedInputBehavior() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use {
            val plan = NativePlan("Rectangle area", listOf(FeaturePlan("area", "Calculate area", "Enter width and height as decimal numbers. Display width multiplied by height as Area; no saved records.",
                listOf("Width 2.5 and height 4 shows Area 10", "Width 7 and height 3 shows Area 21"))), listOf("Calculator"), emptyList())
            build("rectangle", plan) { spec, _ ->
                assertTrue(spec.entities.isEmpty())
                val inputs = spec.screens.flatMap { it.components }.filter { it.kind == ComponentKind.INPUT }
                val width = inputs.first { it.label.contains("width", true) }; val height = inputs.first { it.label.contains("height", true) }
                assertEquals(FieldKind.NUMBER, width.fieldKind); assertEquals(FieldKind.NUMBER, height.fieldKind)
                type(width.label, "6"); type(height.label, "8"); compose.onNodeWithText("48").assertExists()
                type(width.label, "1.5"); type(height.label, "2"); compose.onNodeWithText("3").assertExists()
            }
        }
    }
    @Test fun actualModelBuildsDiaryWithTypedSavedRecords() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use {
            val plan = NativePlan("Mood diary", listOf(FeaturePlan("diary", "Save a mood", "Enter Mood text and Rating number. Tap Save to persist both in a diary record. Show saved entries in a native list.",
                listOf("Enter Calm and rating 4, tap Save, one entry shows Calm and 4", "Enter Tired and rating 2, tap Save, one entry shows Tired and 2"))), listOf("Diary"), listOf("Mood and numeric rating"))
            build("diary", plan) { spec, id ->
                val controls = spec.screens.flatMap { it.components }
                val mood = controls.first { it.kind == ComponentKind.INPUT && it.label.contains("mood", true) }
                val rating = controls.first { it.kind == ComponentKind.INPUT && it.label.contains("rating", true) }
                type(mood.label, "Excited"); type(rating.label, "5")
                val save = controls.first { it.kind == ComponentKind.BUTTON }
                compose.onNodeWithText(save.label).performScrollTo().performClick()
                val list = controls.first { it.kind == ComponentKind.RECORD_LIST }
                val rows = NativeAppRepository(app).records(id).getValue(list.entityId); assertEquals(1, rows.size)
                val entity = spec.entities.first { it.id == list.entityId }
                val moodField = entity.fields.first { rows.single()[it.id]?.toString() == "Excited" }
                val ratingField = entity.fields.first { rows.single()[it.id]?.toString() == "5" }
                assertEquals(FieldKind.TEXT, moodField.kind); assertEquals(FieldKind.NUMBER, ratingField.kind)
                compose.onNodeWithText("${moodField.label}: Excited").performScrollTo().assertExists()
                compose.onNodeWithText("${ratingField.label}: 5").performScrollTo().assertExists()
            }
        }
    }
    @Test fun diagnosticModelConfiguresManuallyApprovedRectangleCapabilities() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use {
            val selected = CompositionSelection(setOf(ComponentKind.INPUT, ComponentKind.SUMMARY), setOf(ExpressionOp.MULTIPLY))
            val plan = NativePlan("Rectangle area", listOf(FeaturePlan("area", "Calculate area", "Enter width and height as decimal numbers. Display width multiplied by height as Area; no saved records.",
                listOf("Width 2.5 and height 4 shows Area 10", "Width 7 and height 3 shows Area 21"), selected)), listOf("Calculator"), emptyList())
            build("rectangle-approved-capabilities", plan) { spec, _ ->
                assertTrue(spec.entities.isEmpty())
                val inputs = spec.screens.flatMap { it.components }.filter { it.kind == ComponentKind.INPUT }
                val width = inputs.first { it.label.contains("width", true) }; val height = inputs.first { it.label.contains("height", true) }
                assertEquals(FieldKind.NUMBER, width.fieldKind); assertEquals(FieldKind.NUMBER, height.fieldKind)
                type(width.label, "6"); type(height.label, "8"); compose.onNodeWithText("48").assertExists()
                type(width.label, "1.5"); type(height.label, "2"); compose.onNodeWithText("3").assertExists()
            }
        }
    }
}
