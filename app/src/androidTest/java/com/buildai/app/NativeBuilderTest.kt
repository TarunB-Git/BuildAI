package com.buildai.app

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.buildai.app.builder.*
import com.buildai.app.data.local.*
import com.buildai.app.data.model.*
import com.buildai.app.data.storage.BuildAiDatabase
import com.buildai.app.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Controlled responses verify tool execution, publication and UI behavior, not model quality. */
class NativeBuilderTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private val rows get() = BuildAiDatabase.get(app).jobs()
    private val projects get() = app.container.projects
    private val apps = NativeAppRepository(app)
    private class Adapter(val outputs: List<String>, val block: Boolean = false) : ModelAdapter {
        override val supportsStructuredOutput = true
        override val resourceProfile = ModelResourceProfile(1, "Controlled response")
        var calls = 0
        val arguments = mutableListOf<JSONObject>()
        val started = Channel<Unit>(Channel.UNLIMITED); val proceed = Channel<Unit>(Channel.UNLIMITED)
        override fun reply(modelFile: File, systemPrompt: String, history: List<Message>, maxTokens: Int, contextSize: Int,
            stopAt: String?, grammar: String?, onStatus: (String) -> Unit, onProgress: (InferenceProgress) -> Unit, priority: InferencePriority) = flow {
            val response = outputs[calls.coerceAtMost(outputs.lastIndex)]; calls++
            arguments += JSONObject(history.single().content)
            started.send(Unit); if (block) proceed.receive()
            emit(response)
        }
        override suspend fun unload() = Unit
    }
    private val feature = FeaturePlan("convert", "Length conversion", "Enter centimetres and read inches", listOf("2.54 becomes 1", "25.4 becomes 10"), CompositionSelection(setOf(ComponentKind.INPUT, ComponentKind.SUMMARY), setOf(ExpressionOp.DIVIDE)))
    private fun project() = Conversation(brief = ProjectBrief(version = 1, goal = "Convert centimetres to inches",
        plan = NativePlan("Length helper", listOf(feature), listOf("Convert"), emptyList()), approvedVersion = 1)).also(projects::save)
    private fun input(project: Conversation) = JSONObject().put("brief", project.brief!!.json()).put("model", BuilderModel("local", "Controlled").json()).put("baseRevision", JSONObject.NULL)
    private fun fragment(): JSONObject {
        val controls = listOf(ComponentSpec("cm", ComponentKind.INPUT, "Centimetres", FieldKind.NUMBER),
            ComponentSpec("inches", ComponentKind.SUMMARY, "Inches", expression = Expr(ExpressionOp.DIVIDE, args = listOf(Expr.input("cm"), Expr.number("2.54")))))
        val checks = feature.checks.mapIndexed { index, name -> JSONObject().put("name", name).put("inputs", JSONObject().put("cm", if (index == 0) "2.54" else "25.4"))
            .put("assertions", JSONArray().put(JSONObject().put("component", "inches").put("equals", if (index == 0) "1" else "10"))) }
        return JSONObject().put("components", JSONArray(controls.map { it.json() })).put("checks", JSONArray(checks))
    }
    private suspend fun terminal(id: String) = withTimeout(30_000) { rows.observe().first { list -> list.any { it.id == id && it.status !in listOf("QUEUED", "RUNNING") } }.first { it.id == id } }
    private fun type(label: String, text: String) {
        val matcher = hasSetTextAction() and hasText(label)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher); compose.onNode(matcher).performTextReplacement(text)
    }
    @Test fun repairedNativeBuildPublishesActualConverterAndChangedInputsWork() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use {
            val project = project(); val adapter = Adapter(listOf("{broken", fragment().also { it.remove("checks") }.toString(), JSONObject().put("checks", fragment().getJSONArray("checks")).toString()))
            val manager = NativeBuildJobs(app, projects, testAdapter = adapter)
            try {
                var id = ""; instrumentation.runOnMainSync { id = manager.enqueue(project.id, input(project)) }
                val result = terminal(id); assertEquals(result.error, "COMPLETED", result.status); assertEquals(3, adapter.calls)
                assertEquals(1, apps.dao.history(project.id).size); assertEquals(1, JSONObject(result.checkpoint).getInt("completedFeatures"))
                assertNull(projects.generatedFile(project.id))
                ActivityScenario.launch<NativeRunnerActivity>(Intent(app, NativeRunnerActivity::class.java).putExtra("appId", project.id)).use { scenario ->
                    compose.waitUntil(15_000) { compose.onAllNodesWithText("Length helper").fetchSemanticsNodes().isNotEmpty() }
                    type("Centimetres", "7.62"); compose.onNodeWithText("3").assertExists()
                    type("Centimetres", "50.8"); compose.onNodeWithText("20").assertExists()
                    scenario.recreate(); compose.onNodeWithText("20").assertExists()
                }
            } finally { instrumentation.runOnMainSync { manager.cancel(project.id) }; projects.delete(project.id) }
        }
    }
    @Test fun changedRequirementsDuringBuildPreventPublicationAndRetainCheckpoint() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use {
            val project = project(); val adapter = Adapter(listOf(fragment().toString()), true)
            val manager = NativeBuildJobs(app, projects, testAdapter = adapter)
            try {
                var id = ""; instrumentation.runOnMainSync { id = manager.enqueue(project.id, input(project)) }
                withTimeout(10_000) { adapter.started.receive() }
                projects.update(project.id) { it.copy(brief = it.brief!!.addInput("new-input", "Use millimetres instead")) }
                adapter.proceed.send(Unit)
                val result = terminal(id); assertEquals("FAILED", result.status)
                assertTrue(result.error.orEmpty().contains("requirements changed")); assertNull(apps.dao.head(project.id)); assertTrue(result.checkpoint.isNotBlank())
            } finally { instrumentation.runOnMainSync { manager.cancel(project.id) }; projects.delete(project.id) }
        }
    }
    @Test fun failedCapabilitySelectionIsProvidedToTheRepairAndOnlyTheCheckedFeaturePublishes() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use {
            val project = project().let { original -> original.copy(brief = original.brief!!.copy(plan = original.brief.plan!!.copy(features = listOf(feature.copy(composition = null))))).also(projects::save) }
            val wrong = CapabilityDecisions.json(CompositionSelection(setOf(ComponentKind.INPUT, ComponentKind.SUMMARY), setOf(ExpressionOp.COUNT)))
            val right = CapabilityDecisions.json(checkNotNull(feature.composition))
            val adapter = Adapter(listOf(wrong.toString(), right.toString(), fragment().toString()))
            val manager = NativeBuildJobs(app, projects, testAdapter = adapter)
            try {
                var id = ""; instrumentation.runOnMainSync { id = manager.enqueue(project.id, input(project)) }
                val result = terminal(id); assertEquals(result.error, "COMPLETED", result.status)
                assertEquals(3, adapter.calls)
                assertEquals("inspectCapabilities", adapter.arguments[0].getString("tool"))
                assertTrue(adapter.arguments[1].getString("failedFeature").contains("COUNT"))
                assertTrue(adapter.arguments[1].getString("failureToRepair").contains("saved collections"))
                assertEquals(listOf("cm", "inches"), apps.spec(project.id)!!.screens.single().components.map { c -> c.id })
                assertEquals(1, apps.dao.history(project.id).size)
            } finally { instrumentation.runOnMainSync { manager.cancel(project.id) }; projects.delete(project.id) }
        }
    }
    @Test fun logMealActionGetsItsVisibleTriggerWithoutSpendingARepair() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use {
            val meal = FeaturePlan("meal", "Log meal", "Enter a meal name and tap Save to store it.", listOf("Lunch saves one meal named Lunch"))
            val brief = ProjectBrief(version = 1, goal = meal.workflow, plan = NativePlan("Meal log", listOf(meal), listOf("Meals"), listOf("Saved meal names")), approvedVersion = 1)
            val project = Conversation(brief = brief).also(projects::save)
            val save = ActionSpec("saveMeal", ActionKind.INSERT, "Save", "meals", mapOf("name" to Expr.input("mealName")))
            val fragment = JSONObject().put("components", JSONArray(listOf(
                ComponentSpec("mealName", ComponentKind.INPUT, "Meal").json(), ComponentSpec("save", ComponentKind.BUTTON, "Save", actionId = "saveMeal").json())))
                .put("entities", JSONArray().put(EntitySpec("meals", listOf(FieldSpec("name", "Meal", required = true))).json()))
                .put("actions", JSONArray().put(save.json()))
                .put("checks", JSONArray().put(JSONObject().put("name", meal.checks.single()).put("inputs", JSONObject().put("mealName", "Lunch"))
                    .put("steps", JSONArray().put(JSONObject().put("action", "saveMeal")))
                    .put("assertions", JSONArray().put(JSONObject().put("entity", "meals").put("count", 1).put("field", "name").put("equals", "Lunch")))))
            val adapter = Adapter(listOf("""{"use":["INPUT","INSERT","NEW_DATA"]}""", fragment.toString()))
            val manager = NativeBuildJobs(app, projects, testAdapter = adapter)
            try {
                var id = ""; instrumentation.runOnMainSync { id = manager.enqueue(project.id, input(project)) }
                val result = terminal(id); assertEquals(result.error, "COMPLETED", result.status); assertEquals(2, adapter.calls)
                assertTrue(adapter.arguments[1].getJSONObject("selection").getJSONArray("components").toString().contains("BUTTON"))
                apps.perform(project.id, "saveMeal", mapOf("mealName" to "Dinner"))
                assertEquals("Dinner", apps.records(project.id).getValue("meals").single()["name"])
            } finally { instrumentation.runOnMainSync { manager.cancel(project.id) }; projects.delete(project.id) }
        }
    }
    @Test fun malformedChecksReuseTheValidConfigurationAndRepeatedBadSelectionsStopEarly() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use {
            val project = project()
            val adapter = Adapter(listOf(fragment().also { it.remove("checks") }.toString(), "{broken", JSONObject().put("checks", fragment().getJSONArray("checks")).toString()))
            val manager = NativeBuildJobs(app, projects, testAdapter = adapter)
            try {
                var id = ""; instrumentation.runOnMainSync { id = manager.enqueue(project.id, input(project)) }
                val result = terminal(id); assertEquals(result.error, "COMPLETED", result.status)
                assertEquals(listOf("configureFeature", "proposeInteractionChecks", "proposeInteractionChecks"), adapter.arguments.map { it.getString("tool") })
                projects.update(project.id) { it.copy(brief = it.brief!!.copy(plan = it.brief.plan!!.copy(features = listOf(feature.copy(composition = null))))) }
                val invalid = """{"use":["INPUT","SUMMARY","COUNT"]}"""
                val repeating = Adapter(listOf(invalid, invalid))
                val repeatedManager = NativeBuildJobs(app, projects, testAdapter = repeating)
                var repeatId = ""; instrumentation.runOnMainSync { repeatId = repeatedManager.enqueue(project.id, input(project).put("baseRevision", apps.dao.head(project.id)!!.revisionId)) }
                val stopped = terminal(repeatId); assertEquals("FAILED", stopped.status); assertEquals(2, repeating.calls)
                assertTrue(stopped.error.orEmpty().contains("repeated the same")); assertEquals(1, apps.dao.history(project.id).size)
            } finally { instrumentation.runOnMainSync { manager.cancel(project.id) }; projects.delete(project.id) }
        }
    }
    @Test fun scopedProposalPreviewAndApplyKeepCurrentRecordsAndRejectStaleBase() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use {
            val project = Conversation(title = "Editable notes").also(projects::save)
            apps.publish(project.id, ManualExamples.notes(), null, "Manual notes")
            apps.perform(project.id, "saveNote", mapOf("note" to "Keep this record", "tag" to "biology"))
            val original = checkNotNull(apps.spec(project.id)); val list = original.screens[0].components.first { it.id == "notes" }
            val response = JSONObject().put("summary", "Add a separate topic search").put("components", JSONArray()
                .put(ComponentSpec("newSearch", ComponentKind.INPUT, "Find a note").json()).put(list.copy(expression = Expr.input("newSearch")).json()))
                .put("checks", JSONArray().put(JSONObject().put("name", "Biology search finds the saved biology note")
                    .put("inputs", JSONObject().put("note", "Photosynthesis").put("tag", "biology").put("newSearch", "biology"))
                    .put("steps", JSONArray().put(JSONObject().put("action", "saveNote")))
                    .put("assertions", JSONArray().put(JSONObject().put("component", "notes").put("count", 1)))))
            val unrelated = response.toString().let(::JSONObject).put("components", JSONArray().put(original.screens[1].components.single().copy(label = "Unrelated rename").json()))
            val adapter = Adapter(listOf(unrelated.toString(), response.toString())); val edits = NativeEditJobs(app, testAdapter = adapter)
            try {
                val before = apps.dao.allRecords(project.id); val base = checkNotNull(apps.dao.head(project.id)).revisionId
                var id = ""; instrumentation.runOnMainSync { id = edits.enqueue(project.id, "Add topic search", EditScope("home", setOf("notes"))) }
                val result = terminal(id); assertEquals(result.error, "REVIEW", result.status)
                assertEquals(2, adapter.calls)
                assertTrue(adapter.arguments[1].getString("failureToRepair").contains("unrelated control"))
                assertEquals("practice", adapter.arguments[1].getJSONObject("failedProposal").getJSONArray("components").getJSONObject(0).getString("id"))
                assertEquals(base, apps.dao.head(project.id)!!.revisionId); assertEquals(before, apps.dao.allRecords(project.id))
                ActivityScenario.launch<NativeRunnerActivity>(Intent(app, NativeRunnerActivity::class.java).putExtra("appId", project.id).putExtra("previewJob", id)).use {
                    compose.waitUntil(15_000) { compose.onAllNodesWithText("Preview: Study notes").fetchSemanticsNodes().isNotEmpty() }
                    type("Note", "Preview-only note"); type("Tag", "temporary")
                    compose.onNodeWithText("Save note").performScrollTo().performClick()
                    assertEquals(before, apps.dao.allRecords(project.id))
                }
                instrumentation.runOnMainSync { edits.apply(id) }
                assertEquals(before, apps.dao.allRecords(project.id)); assertEquals(original.actions, apps.spec(project.id)!!.actions)
                assertEquals(original.screens[1], apps.spec(project.id)!!.screens[1]); assertTrue(runCatching { edits.apply(id) }.isFailure)
                apps.undo(project.id, base, apps.dao.head(project.id)!!.revisionId); assertEquals(before, apps.dao.allRecords(project.id))
            } finally { instrumentation.runOnMainSync { edits.cancel(project.id) }; projects.delete(project.id) }
        }
    }
    @Test fun interactionProbesObserveAssignedValuesAndRejectUnknownStepInputs() {
        val spec = AppSpec("Assignment", "home", listOf(ScreenSpec("home", "Assign", listOf(
            ComponentSpec("number", ComponentKind.INPUT, "Number", FieldKind.NUMBER),
            ComponentSpec("result", ComponentKind.SUMMARY, "Result", expression = Expr.input("number")),
            ComponentSpec("set", ComponentKind.BUTTON, "Set", actionId = "assign")))),
            actions = listOf(ActionSpec("assign", ActionKind.ASSIGN, values = mapOf("number" to Expr.number("7")))))
        val feature = FeaturePlan("assign", "Set a value", "Set changes the number to seven", listOf("Set replaces 2 with 7"))
        fun check(expected: String, unknown: Boolean = false) = JSONObject().put("name", feature.checks.single())
            .put("inputs", JSONObject().put("number", "2"))
            .put("steps", JSONArray().put(JSONObject().put("action", "assign").also { if (unknown) it.put("inputs", JSONObject().put("invented", "99")) }))
            .put("assertions", JSONArray().put(JSONObject().put("component", "result").put("equals", expected)))
        FeatureTools.verify(app, spec, feature, listOf(check("7")))
        assertTrue(runCatching { FeatureTools.verify(app, spec, feature, listOf(check("2"))) }.isFailure)
        assertTrue(runCatching { FeatureTools.verify(app, spec, feature, listOf(check("7", true))) }.exceptionOrNull()?.message.orEmpty().contains("actual input IDs"))
    }
    @Test fun negativeActionProbesRequireRealConditionRejectionAndThenExerciseSuccessfulSave() {
        val controls = listOf(ComponentSpec("rating", ComponentKind.INPUT, "Rating", FieldKind.NUMBER),
            ComponentSpec("save", ComponentKind.BUTTON, "Save", actionId = "save_rating"),
            ComponentSpec("entries", ComponentKind.RECORD_LIST, "Ratings", entityId = "ratings"))
        val spec = AppSpec("Rating", "home", listOf(ScreenSpec("home", "Rating", controls)),
            entities = listOf(EntitySpec("ratings", listOf(FieldSpec("rating", "Rating", FieldKind.NUMBER, required = true)))),
            actions = listOf(ActionSpec("save_rating", ActionKind.INSERT, target = "ratings", values = mapOf("rating" to Expr.input("rating")),
                condition = Expr(ExpressionOp.GT, args = listOf(Expr.input("rating"), Expr.number("0"))))))
        val feature = FeaturePlan("rating", "Save positive ratings", "Save only a positive rating", listOf("Zero rejected; three saved"))
        fun check(rejected: String, expect: String = "conditionRejected") = JSONObject().put("name", feature.checks.single())
            .put("inputs", JSONObject().put("rating", rejected))
            .put("steps", JSONArray().put(JSONObject().put("action", "save_rating").put("expect", expect))
                .put(JSONObject().put("action", "save_rating").put("inputs", JSONObject().put("rating", "3"))))
            .put("assertions", JSONArray().put(JSONObject().put("component", "entries").put("count", 1))
                .put(JSONObject().put("entity", "ratings").put("field", "rating").put("index", 0).put("equals", "3")))
        val fragment = JSONObject().put("components", JSONArray(controls.map { it.json() })).put("checks", JSONArray().put(check("0")))
        FeatureTools.coverage(fragment)
        FeatureTools.verify(app, spec, feature, listOf(check("0")))
        assertTrue(runCatching { FeatureTools.verify(app, spec, feature, listOf(check("2"))) }.isFailure)
        assertTrue(runCatching { FeatureTools.verify(app, spec, feature, listOf(check("invalid"))) }.isFailure)
        assertTrue(runCatching { FeatureTools.verify(app, spec, feature, listOf(check("0", "anyError"))) }.isFailure)
    }

}
