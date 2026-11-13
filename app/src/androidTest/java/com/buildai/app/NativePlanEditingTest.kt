package com.buildai.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.builder.*
import com.buildai.app.data.jobs.ConversationSessions
import com.buildai.app.data.model.Conversation
import com.buildai.app.ui.screens.NativePlanScreen
import com.buildai.app.ui.theme.BuildAITheme
import com.buildai.app.viewmodel.ConversationViewModel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Plan editing and approval only: does not generate an app or call a model. */
class NativePlanEditingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private fun type(label: String, text: String) {
        val field = hasSetTextAction() and hasText(label)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(field)
        compose.onNode(field).performTextReplacement(text)
    }
    @Test fun completePlanDraftSurvivesReopeningAndOneApprovalUsesTheEditedFields() {
        val plan = NativePlan("A tool", listOf(FeaturePlan("calculate", "Calculate", "Enter numbers", listOf("Changing an input changes the result"))), listOf("Tool"), emptyList())
        val project = Conversation(phase = "PLAN_READY", brief = ProjectBrief(version = 1, goal = "A personal tool", plan = plan)).also(app.container.projects::save)
        lateinit var vm: ConversationViewModel
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    vm = ConversationViewModel(app); vm.loadConversation(project.id)
                    activity.setContent { BuildAITheme { NativePlanScreen(app, vm, {}, {}, {}) } }
                }
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Your app plan").fetchSemanticsNodes().isNotEmpty() }
                type("App name", "Time helper")
                compose.onNodeWithContentDescription("Edit feature Calculate").performScrollTo().performClick()
                type("Feature name", "Track time")
                type("What you will do", "")
                compose.onNodeWithText("Build").assertIsNotEnabled()
                type("What you will do", "Enter hours and save the session")
                type("Checks — one per line", "Two hours is saved as 2")
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Edit screens & data") and hasClickAction())
                compose.onNodeWithText("Edit screens & data").performClick()
                type("Screens — one per line", "Sessions\nSummary")
                type("Saved data — one collection per line", "Session hours")
                val saved = JSONObject(checkNotNull(app.container.projects.load(project.id)!!.brief!!.planDraft))
                assertEquals("Track time", saved.getJSONArray("features").getJSONObject(0).getString("title"))
                assertEquals(2, saved.getJSONArray("screens").length())
                assertEquals("Session hours", saved.getJSONArray("data").getString(0))
                instrumentation.runOnMainSync {
                    vm.leaveProject()
                    val cold = ConversationSessions(app, app.container.conversation, app.container.projects, app.container.settings)
                    vm = ConversationViewModel(app, sessions = cold); vm.loadConversation(project.id)
                }
                scenario.recreate()
                scenario.onActivity { activity -> activity.setContent { BuildAITheme { NativePlanScreen(app, vm, {}, {}, {}) } } }
                compose.onAllNodesWithText("Build").assertCountEquals(1)
                compose.onNodeWithText("Build").assertIsEnabled()
                instrumentation.runOnMainSync {
                    val approved = vm.approveNativePlan()
                    assertEquals(approved.version, approved.approvedVersion)
                    assertEquals("Time helper", approved.plan!!.name)
                    assertEquals("Track time", approved.plan.features.single().title)
                    assertEquals("Enter hours and save the session", approved.plan.features.single().workflow)
                    assertEquals(listOf("Sessions", "Summary"), approved.plan.screens)
                    assertEquals(listOf("Session hours"), approved.plan.data)
                    assertNull(approved.planDraft)
                }
                assertTrue(com.buildai.app.runtime.NativeAppRepository(app).dao.history(project.id).isEmpty())
            }
        } finally {
            instrumentation.runOnMainSync { runCatching { vm.leaveProject() } }
            app.container.projects.delete(project.id)
        }
    }
}
