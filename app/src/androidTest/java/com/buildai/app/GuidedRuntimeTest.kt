package com.buildai.app

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.buildai.app.builder.GuidedConfiguration
import com.buildai.app.data.model.Conversation
import com.buildai.app.runtime.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GuidedRuntimeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BuildAiApplication
    private fun type(label: String, value: String) {
        val field = hasSetTextAction() and hasText(label)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(field); compose.onNode(field).performTextReplacement(value)
    }
    @Test fun composedPrivateToolSavesUncheckedBooleanAndCommaDecimalsWithLiveCalculation() {
        val project = Conversation(title = "Guided materials").also(app.container.projects::save)
        val config = GuidedConfiguration("Materials", listOf(FieldSpec("material", "Material"), FieldSpec("width", "Width", FieldKind.NUMBER),
            FieldSpec("length", "Length", FieldKind.NUMBER), FieldSpec("complete", "Complete", FieldKind.BOOLEAN)),
            saveRecords = true, search = true, calculation = "MULTIPLY", left = "width", right = "length", resultLabel = "Area")
        val repo = NativeAppRepository(app)
        try {
            repo.publish(project.id, config.compose(), null, "Explicit guided configuration")
            ActivityScenario.launch<NativeRunnerActivity>(Intent(app, NativeRunnerActivity::class.java).putExtra("appId", project.id)).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Materials").fetchSemanticsNodes().isNotEmpty() }
                type("Material", "Timber"); type("Width", "2,5"); type("Length", ".5")
                compose.onNodeWithText("1.25").assertExists()
                compose.onNodeWithText("Save record").performScrollTo().performClick()
                val saved = repo.records(project.id).values.flatten().single()
                assertEquals(false, saved["complete"]); assertEquals("2.5", saved["width"]); assertEquals("0.5", saved["length"])
                assertEquals("Timber", saved["material"])
                scenario.recreate()
                type("Search records", "Timber")
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Material: Timber"))
                compose.onNodeWithText("Material: Timber").assertIsDisplayed()
            }
        } finally { app.container.projects.delete(project.id) }
    }
}
