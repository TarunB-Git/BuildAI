package com.buildai.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.builder.*
import com.buildai.app.data.api.OfflineStarterApp
import com.buildai.app.data.local.QuickPlan
import com.buildai.app.data.model.*
import com.buildai.app.ui.screens.NativePlanScreen
import com.buildai.app.ui.theme.BuildAITheme
import com.buildai.app.viewmodel.ConversationViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real navigation, persistence and large-text checks; no network or model is used. */
class WorkspaceUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private fun waitText(text: String) {
        compose.waitUntil(15_000) { runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
    }
    private fun snapshot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        runCatching { instrumentation.uiAutomation.waitForIdle(500, 5_000) }
        instrumentation.uiAutomation.takeScreenshot()?.let { image ->
            File(File(app.filesDir, "ui-review").apply { mkdirs() }, "redesign-$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun librarySearchDraftNavigationAndComposerPreserveWork() {
        val repo = app.container.projects
        val entry = repo.assistantEntry()
        val fixtures = mutableListOf<String>()
        try {
            repo.update(entry.id) { it.copy(draftText = "", assistantMode = AssistantMode.CREATE) }
            listOf("Study notes", "Daily routine", "Trip budget").forEach { title ->
                val plan = QuickPlan.fromIdea("A task checklist").copy(appName = title, description = when (title) {
                    "Study notes" -> "Keep your notes organized and easy to find."
                    "Daily routine" -> "A little more structure for your everyday."
                    else -> "Stay on top of spending, wherever you go."
                })
                val project = Conversation(title = title, phase = "CONFIRMED", projectPlan = plan, designSpec = DesignSpec())
                repo.save(project); fixtures += project.id
                repo.saveGenerated(project.id, OfflineStarterApp.build(plan, DesignSpec()))
            }
            val chat = Conversation(title = "A budget for my next trip", assistantMode = AssistantMode.CHAT,
                messages = listOf(Message(role = MessageRole.USER, content = "I need a travel budget."),
                    Message(role = MessageRole.ASSISTANT, content = "Let’s work it out together. Where are you going, and how many days will you be away?")))
            repo.save(chat); fixtures += chat.id
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitText("New chat")
                compose.onNode(hasText("Apps") and hasAnyAncestor(hasTestTag("root-Apps")), useUnmergedTree = true).performClick()
                snapshot("apps-entry")
                waitText("Find an app")
                snapshot("apps")
                compose.onNode(hasSetTextAction()).performTextInput("Trip budget")
                compose.onNodeWithText("Study notes").assertDoesNotExist()
                compose.onNode(hasText("Trip budget") and !hasSetTextAction()).assertExists()
                compose.onNodeWithContentDescription("Clear search").performClick()
                compose.onNode(hasText("Chat") and hasAnyAncestor(hasTestTag("root-Chat")), useUnmergedTree = true).performClick()
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(chat.title))
                compose.onNodeWithText(chat.title).performClick()
                waitText("I need a travel budget.")
                snapshot("chat")
                compose.onNode(hasSetTextAction()).performTextInput("An unsent answer")
                val reopenedTitle = checkNotNull(repo.load(chat.id)).title
                compose.onNodeWithContentDescription("Back").performClick()
                waitText("New chat"); waitText(reopenedTitle)
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(reopenedTitle))
                compose.onNode(hasText(reopenedTitle) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true).performClick()
                compose.onNode(hasSetTextAction()).assertTextContains("An unsent answer")
                scenario.recreate()
                compose.onNode(hasSetTextAction()).assertTextContains("An unsent answer")
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onNode(hasContentDescription("Activity", substring = true)).performClick()
                waitText("Your work")
                snapshot("activity")
                compose.onNodeWithContentDescription("Settings").performClick()
                waitText("Settings")
                snapshot("settings")
                compose.onNodeWithText("AI & privacy").performClick()
                compose.onNodeWithText("Local AI · selected").performClick()
                compose.onNodeWithText("Browse available models").assertHasClickAction()
            }
        } finally {
            fixtures.forEach(repo::delete)
            repo.update(entry.id) { it.copy(draftText = entry.draftText, assistantMode = entry.assistantMode) }
        }
    }
    @Test fun readablePlanEditorsAndBuildRemainReachableWithLargeText() {
        val repo = app.container.projects
        val plan = NativePlan("Trip budget", listOf(
            FeaturePlan("expenses", "Daily spending", "Add expenses and organize them by category.", listOf("A saved expense appears in the list")),
            FeaturePlan("split", "Split costs", "Split shared expenses between the people on your trip.", listOf("Each person sees their share")),
            FeaturePlan("remaining", "Remaining budget", "See how much you have left as you add expenses.", listOf("An expense reduces the remaining budget"))),
            listOf("Overview", "Expenses"), listOf("Expenses", "People"))
        val project = Conversation(title = plan.name, phase = "PLAN_READY", brief = ProjectBrief(version = 1,
            goal = "A simple way to keep your trip spending on track.", plan = plan))
        repo.save(project)
        lateinit var vm: ConversationViewModel
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    vm = ConversationViewModel(app); vm.loadConversation(project.id)
                    activity.setContent { BuildAITheme { NativePlanScreen(app, vm, {}, {}, {}) } }
                }
                waitText("Your app plan")
                compose.onNodeWithContentDescription("Edit feature Daily spending").performScrollTo().performClick()
                compose.onNode(hasSetTextAction() and hasText("What you will do")).performTextReplacement("Add expenses, including food and transport.")
                assertTrue(repo.load(project.id)!!.brief!!.planDraft!!.contains("including food and transport"))
                compose.onNodeWithContentDescription("Close feature editor").performScrollTo().performClick()
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
                    .perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
                snapshot("plan")
                scenario.onActivity { activity ->
                    activity.setContent { BuildAITheme {
                        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                            NativePlanScreen(app, vm, {}, {}, {})
                        }
                    } }
                }
                compose.onNodeWithText("Build").assertIsEnabled()
                snapshot("plan-large-text")
            }
        } finally { instrumentation.runOnMainSync { vm.leaveProject() }; repo.delete(project.id) }
    }
}
