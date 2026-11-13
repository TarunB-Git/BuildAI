package com.buildai.app

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.buildai.app.data.model.Conversation
import com.buildai.app.data.storage.BuildAiDatabase
import com.buildai.app.runtime.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NativeRuntimeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private val repo = NativeAppRepository(app)
    private fun install(spec: AppSpec): String {
        val project = Conversation(title = spec.name).also(app.container.projects::save)
        repo.publish(project.id, spec, null, "Manually composed capability fixture")
        return project.id
    }
    private fun open(id: String) = ActivityScenario.launch<NativeRunnerActivity>(Intent(app, NativeRunnerActivity::class.java).putExtra("appId", id))
    private fun type(label: String, text: String) {
        val field = hasSetTextAction() and hasText(label)
        // Lazy items outside the viewport have no semantics node until scrolled into view.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(field)
        compose.onNode(field).performTextReplacement(text)
    }
    private fun click(label: String) { compose.onNodeWithText(label).performScrollTo().performClick() }
    private fun waitText(text: String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun remove(id: String) { repo.delete(id); app.container.projects.delete(id) }

    @Test fun budgetInputsChangeStoredTotalsAndAppReopensWithoutAi() {
        val id = install(ManualExamples.budget())
        try {
            open(id).use { scenario ->
                waitText("Trip budget")
                compose.onNodeWithText("Tap the preview to try it out").assertDoesNotExist()
                compose.onNodeWithText("Assistant").assertDoesNotExist()
                type("Budget", "100"); type("Expense", "Train"); type("Amount", "12.50"); click("Add expense")
                waitText("87.5")
                assertEquals("12.50", repo.records(id)["expenses"]!!.single()["amount"])
                type("Amount", "2.25"); click("Add expense"); waitText("85.25")
                assertEquals(2, repo.records(id)["expenses"]!!.size)
                scenario.recreate(); waitText("85.25")
                assertEquals("100", repo.runtime(id).getJSONObject("inputs").getString("budget"))
            }
        } finally { remove(id) }
    }
    @Test fun notesSearchTagsQuizAndColdReopenUseTheSameRuntime() {
        val id = install(ManualExamples.notes())
        try {
            open(id).use { scenario ->
                waitText("Study notes")
                type("Note", "Photosynthesis"); type("Tag", "biology"); click("Save note")
                compose.onNodeWithText("Note: Photosynthesis").performScrollTo().assertIsDisplayed()
                type("Search notes", "biology")
                compose.onNodeWithText("Note: Photosynthesis").performScrollTo().assertIsDisplayed()
                type("Search notes", "unrelated")
                waitText("No items match these filters.")
                click("Quiz"); waitText("What is 2 + 2?"); click("4"); waitText("Finished: 1 / 1 correct")
                scenario.recreate(); waitText("Finished: 1 / 1 correct")
                assertEquals(1, repo.records(id)["notes"]!!.size)
            }
        } finally { remove(id) }
    }
    @Test fun routineCompletionAndTimerDeadlineSurviveClosingTheRunner() {
        val id = install(ManualExamples.routine())
        try {
            open(id).use {
                waitText("Focus routine"); type("Routine step", "Read a chapter"); click("Add step")
                click("Complete step")
                assertEquals(true, repo.records(id)["steps"]!!.single()["complete"])
                type("Focus seconds", "60"); click("Start focus")
                assertTrue(repo.runtime(id).getJSONObject("timers").getLong("focus") > System.currentTimeMillis())
            }
            val deadline = repo.runtime(id).getJSONObject("timers").getLong("focus")
            open(id).use {
                waitText("Focus routine")
                assertEquals(deadline, repo.runtime(id).getJSONObject("timers").getLong("focus"))
                click("Stop focus")
                assertFalse(repo.runtime(id).getJSONObject("timers").has("focus"))
            }
        } finally { remove(id) }
    }
    @Test fun scopedEditsRejectStalePatchesAndUndoKeepsTodaysRecords() {
        val id = install(ManualExamples.notes())
        try {
            repo.perform(id, "saveNote", mapOf("note" to "Before edit", "tag" to "history"))
            val base = checkNotNull(repo.dao.head(id)).revisionId
            val spec = checkNotNull(repo.spec(id))
            val save = spec.screens[0].components.first { it.id == "save" }
            val patch = AppPatch(base, "Rename save and add an optional topic", listOf(
                PatchOperation.PutComponent("home", save.copy(label = "Keep note")),
                PatchOperation.AddField("notes", FieldSpec("topic", "Topic")),
                PatchOperation.PutComponent("home", ComponentSpec("topicInput", ComponentKind.INPUT, "Topic")),
                PatchOperation.PutAction(spec.actions.first { it.id == "saveNote" }.copy(values = spec.actions.first { it.id == "saveNote" }.values + ("topic" to Expr.input("topicInput")))),
                PatchOperation.PutComponent("home", spec.screens[0].components.first { it.id == "notes" }.copy(displayFields = listOf("body", "tag", "topic")))
            ))
            val changed = repo.apply(id, patch)
            assertTrue(runCatching { repo.apply(id, patch) }.isFailure)
            assertEquals(spec.actions.first { it.id == "deleteNote" }, repo.spec(id)!!.actions.first { it.id == "deleteNote" })
            assertEquals(spec.screens[1], repo.spec(id)!!.screens[1])
            open(id).use {
                waitText("Study notes")
                type("Note", "After edit"); type("Tag", "current"); type("Topic", "science"); click("Keep note")
                compose.onNodeWithText("Topic: science").performScrollTo().assertIsDisplayed()
            }
            val beforeUndo = repo.dao.allRecords(id)
            val restored = repo.undo(id, base, changed.id)
            assertEquals(beforeUndo, repo.dao.allRecords(id))
            assertEquals(2, repo.records(id)["notes"]!!.size)
            assertTrue(repo.spec(id)!!.entities.single().fields.any { it.id == "topic" })
            assertEquals(spec.screens, repo.spec(id)!!.screens)
            assertEquals(changed.id, restored.parentId)
            assertEquals(3, repo.dao.history(id).size)
            assertTrue(repo.dao.allRecords(id).any { it.json.contains("science") })
            open(id).use {
                waitText("Study notes"); compose.onNodeWithText("Save note").assertExists()
                compose.onNode(hasSetTextAction() and hasText("Topic")).assertDoesNotExist()
                compose.onNodeWithText("Topic: science").assertDoesNotExist()
            }
        } finally { remove(id) }
    }
}
