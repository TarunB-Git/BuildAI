package com.buildai.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.api.OfflineStarterApp
import com.buildai.app.data.local.QuickPlan
import com.buildai.app.data.model.Conversation
import com.buildai.app.data.model.DesignSpec
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class AppEditingTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun builtAppCanEditRequirementsReturnToChatAndKeepPreviousOutput() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BuildAiApplication
        val id = UUID.randomUUID().toString()
        val plan = QuickPlan.fromIdea("A task checklist")
        val repo = app.container.projects
        repo.save(Conversation(id = id, projectPlan = plan, phase = "CONFIRMED", designSpec = DesignSpec()))
        val saved = repo.saveGenerated(id, OfflineStarterApp.build(plan, DesignSpec())).name
        fun waitText(text: String) {
            compose.waitUntil(20_000) { runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
            UiTestWindows.awaitFocus(if (text == "Close") com.buildai.app.runtime.LegacyRunnerActivity::class.java else MainActivity::class.java)
        }
        try {
            ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_PROJECT_ID, id)).use {
                compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Project options").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("App details").performClick(); waitText("Shape what your app will do")
                compose.onNode(hasSetTextAction() and hasText("App name")).performTextReplacement("Pocket Planner")
                compose.onNodeWithText("Appearance · optional").performScrollTo().performClick(); waitText("Appearance")
                compose.onNodeWithText("Forest").performScrollTo().performClick()
                compose.onNodeWithText("Save appearance").performScrollTo().performClick(); waitText("Shape what your app will do")
                assertEquals("Pocket Planner", repo.load(id)!!.projectPlan!!.appName)
                assertEquals("forest", repo.load(id)!!.designSpec!!.paletteId)
                assertEquals(saved, repo.load(id)!!.generatedFileName)
                compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("Conversation").performClick()
                compose.onNode(hasSetTextAction()).performTextInput("A revision for later")
                compose.onNodeWithContentDescription("Project options").performClick(); compose.onNodeWithText("Preview app").performClick(); waitText("Current web app · uses saved data")
                assertEquals(saved, repo.load(id)!!.generatedFileName)
                compose.onNodeWithContentDescription("Back").performClick(); waitText("Apps")
                it.recreate(); waitText("Apps")
                compose.onNodeWithContentDescription("Project options").assertDoesNotExist()
                compose.onNode(hasSetTextAction()).performTextReplacement("Pocket Planner")
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Pocket Planner"))
                compose.onNodeWithText("Open app").performClick(); waitText("Close")
                compose.onNodeWithText("Plan").assertDoesNotExist()
                assertEquals(saved, repo.load(id)!!.generatedFileName)
                compose.onNodeWithText("Close").performClick(); waitText("Apps")
                compose.onNode(hasContentDescription("Options for Pocket Planner")).performClick()
                compose.onNodeWithText("Delete app").performClick(); compose.onNodeWithText("Delete").performClick()
                compose.waitUntil(10_000) { repo.load(id) == null }
            }
        } finally { repo.delete(id) }
    }
}
