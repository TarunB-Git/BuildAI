package com.buildai.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.model.Conversation
import com.buildai.app.runtime.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Manually composed capability contracts; no model/template fallback and no phone benchmark. */
class NativeRelationshipTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BuildAiApplication
    private val repo = NativeAppRepository(app)
    private fun spec() = AppSpec("Linked sessions", "home", listOf(ScreenSpec("home", "Sessions", listOf(
        ComponentSpec("name", ComponentKind.INPUT, "Session"),
        ComponentSpec("score", ComponentKind.INPUT, "Score", FieldKind.NUMBER),
        ComponentSpec("date", ComponentKind.INPUT, "Date", FieldKind.DATE),
        ComponentSpec("group", ComponentKind.INPUT, "Group", FieldKind.REFERENCE, referenceEntity = "groups"),
        ComponentSpec("save", ComponentKind.BUTTON, "Save session", actionId = "save"),
        ComponentSpec("days", ComponentKind.SUMMARY, "Days since October 1", expression = Expr(ExpressionOp.DATE_DIFF, args = listOf(Expr.input("date"), Expr.text("2026-10-01")))),
        ComponentSpec("sessions", ComponentKind.RECORD_LIST, "Saved sessions", entityId = "sessions", sortField = "score", displayFields = listOf("name", "score", "date", "group"))
    ))), listOf(
        EntitySpec("groups", listOf(FieldSpec("name", "Group name", required = true))),
        EntitySpec("sessions", listOf(FieldSpec("name", "Session", required = true), FieldSpec("score", "Score", FieldKind.NUMBER, true),
            FieldSpec("date", "Date", FieldKind.DATE, true), FieldSpec("group", "Group", FieldKind.REFERENCE, true, "groups")))
    ), listOf(
        ActionSpec("addGroup", ActionKind.INSERT, "Add group", "groups", mapOf("name" to Expr.input("name"))),
        ActionSpec("save", ActionKind.INSERT, "Save session", "sessions", mapOf("name" to Expr.input("name"), "score" to Expr.input("score"), "date" to Expr.input("date"), "group" to Expr.input("group")),
            condition = Expr(ExpressionOp.GT, args = listOf(Expr.input("score"), Expr.number("0")))),
        ActionSpec("deleteGroup", ActionKind.DELETE, "Delete group", "groups", recordId = Expr(ExpressionOp.RECORD, "id")),
        ActionSpec("deleteSession", ActionKind.DELETE, "Delete session", "sessions", recordId = Expr(ExpressionOp.RECORD, "id"))
    ))
    private fun type(label: String, text: String) {
        val field = hasSetTextAction() and hasText(label)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(field); compose.onNode(field).performTextReplacement(text)
    }
    @Test fun pickerDateCalculationAndConditionalSavePersistTypedLinkedRecords() {
        val project = Conversation(title = "Linked sessions").also(app.container.projects::save)
        try {
            repo.publish(project.id, spec(), null, "Manual capability check")
            repo.perform(project.id, "addGroup", mapOf("name" to "Class A"))
            val parent = repo.records(project.id).getValue("groups").single().getValue("id").toString()
            ActivityScenario.launch<NativeRunnerActivity>(Intent(app, NativeRunnerActivity::class.java).putExtra("appId", project.id)).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Linked sessions").fetchSemanticsNodes().isNotEmpty() }
                type("Session", "Reading"); type("Date", "2026-10-04"); type("Score", "0")
                compose.onNodeWithText("Group: Choose a record").performScrollTo().performClick()
                compose.onNode(hasText("Class A") and hasClickAction() and !hasSetTextAction()).performClick()
                compose.onNodeWithText("Save session").performScrollTo().performClick()
                assertTrue(repo.records(project.id)["sessions"].orEmpty().isEmpty())
                compose.onNodeWithText("This action is not available with the current inputs").assertExists()
                compose.onNodeWithText("OK").performClick()
                type("Score", "2,5"); compose.onNodeWithText("Save session").performScrollTo().performClick()
                val record = repo.records(project.id).getValue("sessions").single()
                assertEquals("2.5", record["score"]); assertEquals("2026-10-04", record["date"]); assertEquals(parent, record["group"])
                compose.onNodeWithText("3").performScrollTo().assertExists()
                scenario.recreate()
                compose.onNodeWithText("Session: Reading").performScrollTo().assertExists()
                assertEquals(record, repo.records(project.id).getValue("sessions").single())
            }
        } finally { app.container.projects.delete(project.id) }
    }
    @Test fun foreignReferencesInvalidDatesAndFailedConditionsCannotPartiallyWriteRecords() {
        val projects = List(2) { Conversation(title = "Reference isolation").also(app.container.projects::save) }
        try {
            projects.forEach { repo.publish(it.id, spec(), null, "Manual capability check"); repo.perform(it.id, "addGroup", mapOf("name" to it.id)) }
            val id = projects[0].id
            val own = repo.records(id).getValue("groups").single().getValue("id").toString()
            val foreign = repo.records(projects[1].id).getValue("groups").single().getValue("id").toString()
            val inputs = mapOf("name" to "Retain this", "score" to "3", "date" to "2026-10-04", "group" to own)
            for (invalid in listOf(inputs + ("group" to foreign), inputs + ("date" to "2025-02-29"), inputs + ("score" to "0"))) {
                val before = repo.dao.allRecords(id); val runtime = repo.runtime(id).toString()
                assertTrue(runCatching { repo.perform(id, "save", invalid) }.isFailure)
                assertEquals(before, repo.dao.allRecords(id)); assertEquals(runtime, repo.runtime(id).toString())
            }
            repo.perform(id, "save", inputs)
            assertEquals(1, repo.records(id).getValue("sessions").size)
            assertTrue(repo.records(projects[1].id)["sessions"].orEmpty().isEmpty())
        } finally { projects.forEach { app.container.projects.delete(it.id) } }
    }
    @Test fun deletingAReferencedParentIsBlockedUntilItsChildIsArchived() {
        val project = Conversation(title = "Reference deletion").also(app.container.projects::save)
        try {
            repo.publish(project.id, spec(), null, "Manual capability check")
            repo.perform(project.id, "addGroup", mapOf("name" to "Class A"))
            val parent = repo.records(project.id).getValue("groups").single()
            repo.perform(project.id, "save", mapOf("name" to "Reading", "score" to "3", "date" to "2026-10-04", "group" to parent.getValue("id").toString()))
            val child = repo.records(project.id).getValue("sessions").single()
            val before = repo.dao.allRecords(project.id)
            assertTrue(runCatching { repo.perform(project.id, "deleteGroup", emptyMap(), parent) }.exceptionOrNull()?.message.orEmpty().contains("used by another saved item"))
            assertEquals(before, repo.dao.allRecords(project.id))
            repo.perform(project.id, "deleteSession", emptyMap(), child)
            repo.perform(project.id, "deleteGroup", emptyMap(), parent)
            assertTrue(repo.records(project.id).values.flatten().isEmpty())
            val archived = repo.dao.allRecords(project.id)
            assertEquals(2, archived.size); assertTrue(archived.all { it.deleted })
        } finally { app.container.projects.delete(project.id) }
    }
    @Test fun repeatedDetailActionsResolveCurrentRecordValuesAndDeletionClearsSelection() {
        val project = Conversation(title = "Current counter").also(app.container.projects::save)
        val recordId = Expr(ExpressionOp.RECORD, "id")
        val counter = AppSpec("Counter", "home", listOf(
            ScreenSpec("home", "Counters", listOf(ComponentSpec("rows", ComponentKind.RECORD_LIST, "Counters", entityId = "counters", rowActions = listOf("open")))),
            ScreenSpec("detail", "Counter", listOf(ComponentSpec("increment", ComponentKind.BUTTON, "Increment", actionId = "increment"),
                ComponentSpec("delete", ComponentKind.BUTTON, "Delete", actionId = "delete")))) ,
            listOf(EntitySpec("counters", listOf(FieldSpec("amount", "Amount", FieldKind.NUMBER, true)))),
            listOf(ActionSpec("new", ActionKind.INSERT, target = "counters", values = mapOf("amount" to Expr.number("0"))),
                ActionSpec("open", ActionKind.NAVIGATE, target = "detail"),
                ActionSpec("increment", ActionKind.UPDATE, target = "counters", recordId = recordId, values = mapOf("amount" to Expr(ExpressionOp.ADD, args = listOf(Expr(ExpressionOp.RECORD, "amount"), Expr.number("1"))))),
                ActionSpec("delete", ActionKind.DELETE, target = "counters", recordId = recordId)))
        try {
            repo.publish(project.id, counter, null, "Counter regression")
            repo.perform(project.id, "new", emptyMap())
            val staleRow = repo.records(project.id).getValue("counters").single()
            repo.perform(project.id, "open", emptyMap(), staleRow)
            repeat(2) { repo.perform(project.id, "increment", emptyMap(), staleRow) }
            assertEquals("2", repo.records(project.id).getValue("counters").single()["amount"])
            assertEquals("2", repo.runtime(project.id).getJSONObject("selectedRecord").getString("amount"))
            repo.perform(project.id, "delete", emptyMap())
            assertFalse(repo.runtime(project.id).has("selectedRecord")); assertTrue(repo.records(project.id).values.flatten().isEmpty())
        } finally { app.container.projects.delete(project.id) }
    }
}
