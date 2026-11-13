package com.buildai.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.model.Conversation
import com.buildai.app.runtime.ManualExamples
import com.buildai.app.runtime.NativeAppRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs with real emulator animations enabled, rather than masking transition bugs. */
class NavigationMotionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BuildAiApplication
    private fun waitText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        UiTestWindows.awaitFocus(MainActivity::class.java)
    }

    @Test fun leavingLibraryDoesNotResizeItsOutgoingContent() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("New chat")
            val before = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
            try {
                compose.mainClock.autoAdvance = false
                compose.onNodeWithContentDescription("Settings").performClick()
                compose.mainClock.advanceTimeBy(64)
                compose.waitForIdle()
                val during = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
                assertEquals("The old library must keep its height during the exit fade", before.height, during.height, 1f)
                assertEquals(before.top, during.top, 1f)
            } finally { compose.mainClock.autoAdvance = true; compose.waitForIdle() }
            compose.onNodeWithText("AI & privacy").assertIsDisplayed()
        }
    }

    @Test fun previewScrollPositionSurvivesTabRoundTripAndSelectionPersists() {
        val project = Conversation(title = "Motion notes", titleOwned = true, workspaceTab = "preview").also(app.container.projects::save)
        val native = NativeAppRepository(app)
        native.publish(project.id, ManualExamples.notes(), null, "Motion fixture")
        repeat(12) { native.perform(project.id, "saveNote", mapOf("note" to "Record ${it + 1}", "tag" to "test")) }
        try {
            ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_PROJECT_ID, project.id)).use { scenario ->
                waitText("Current app · uses saved data")
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Note: Record 10"))
                compose.onNodeWithText("Note: Record 10").assertIsDisplayed()
                compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("Request a change").performClick(); waitText("What would you like to change?")
                compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("App details").performClick(); waitText("Current app")
                compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("Preview app").performClick(); waitText("Current app · uses saved data")
                compose.onNodeWithText("Note: Record 10").assertIsDisplayed()
                compose.waitUntil(5_000) { app.container.projects.load(project.id)?.workspaceTab == "preview" }
                scenario.recreate(); waitText("Current app · uses saved data")
                assertEquals("preview", app.container.projects.load(project.id)!!.workspaceTab)
            }
        } finally { app.container.projects.delete(project.id) }
    }
}
