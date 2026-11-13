package com.buildai.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.model.*
import com.buildai.app.data.storage.UiPreferences
import com.buildai.app.data.local.LocalModelStore
import com.buildai.app.data.api.*
import com.buildai.app.runtime.*
import com.buildai.app.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class RevisionUxTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private val repo = app.container.projects
    @Test fun providerDraftActivationAndRemovalAgreeWithProtectedStorage() {
        val storage = app.container.settings
        val provider = CloudProvider.GEMINI
        val previousKey = storage.getApiKey(provider); val previousModel = storage.getModel(provider)
        val previousProvider = storage.provider(); val previousBackend = storage.getChatBackend()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            instrumentation.runOnMainSync {
                val vm = androidx.lifecycle.ViewModelProvider(store, androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app))[com.buildai.app.viewmodel.SettingsViewModel::class.java]
                vm.saveProvider(provider, "dummy-original", "model-a")
                vm.editProviderModel(provider, "model-b"); vm.useProvider(provider)
                assertEquals("model-b", storage.getModel(provider))
                vm.editProviderKey(provider, "dummy-hidden-draft"); vm.clearProvider(provider)
                assertFalse(vm.providerDirty()); vm.saveProviderDrafts(); assertNull(storage.getApiKey(provider))
                assertEquals("", vm.draftProviderKey(provider))
                vm.saveProvider(provider, "dummy-current", "model-b"); vm.editProviderModel(provider, "")
                assertNotNull(vm.providerDraftError()); assertTrue(runCatching { vm.saveProviderDrafts() }.isFailure)
                assertEquals("model-b", storage.getModel(provider))
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            if (previousKey == null) storage.clearApiKey(provider) else storage.saveApiKey(previousKey, provider)
            storage.saveModel(previousModel, provider); storage.selectProvider(previousProvider); storage.saveChatBackend(previousBackend)
        }
    }
    @Test fun slashContainingProjectTitlesExportUsingSafeIdentityAndCurrentRequirements() {
        val project = Conversation(title = "Trip / budget", brief = com.buildai.app.builder.ProjectBrief(goal = "Keep this travel budget"))
        val file = File(app.cacheDir, "BuildAI_${project.id}.txt")
        try {
            val intent = repo.buildShareIntent(project)
            assertTrue(file.isFile); assertTrue(file.readText().contains("Trip / budget")); assertTrue(file.readText().contains("Keep this travel budget"))
            assertEquals("BuildAI — Trip / budget", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        } finally { file.delete() }
    }
    private fun waitText(text: String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }; UiTestWindows.awaitFocus(MainActivity::class.java) }
    private fun root(text: String) { compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("root-$text")), useUnmergedTree = true).performClick() }
    private fun shot(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync()
        runCatching { instrumentation.uiAutomation.waitForIdle(500, 3_000) }
        android.os.SystemClock.sleep(800) // Let Android window and render-thread fades finish before capture.
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap -> File(app.cacheDir, "revision-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    }
    @Test fun draftsAndFinishedAppsHaveSeparateHomesAndPinsPersist() {
        val draft = Conversation(title = "Draft fixture", titleOwned = true).also(repo::save)
        val finished = Conversation(title = "Finished fixture", titleOwned = true).also(repo::save)
        val native = NativeAppRepository(app); native.publish(finished.id, ManualExamples.notes(), null, "Fixture")
        val preferences = UiPreferences(app); val previous = preferences.offlineToolsEnabled(); preferences.enableOfflineTools(false)
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitText("New chat"); root("Create"); waitText("What would you like your app to do?")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(draft.displayName))
            compose.onNodeWithText(draft.displayName).assertIsDisplayed(); compose.onNodeWithText(finished.displayName).assertDoesNotExist()
            compose.onNodeWithText("Configure an offline tool").assertDoesNotExist(); compose.onNodeWithText("Use an offline starter").assertDoesNotExist(); shot("create-drafts")
            root("Apps"); waitText("Apps")
            compose.onNodeWithText(draft.displayName).assertDoesNotExist()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(finished.displayName)); compose.onNodeWithContentDescription("Options for ${finished.displayName}").performClick()
            compose.onNodeWithText("Pin to top").performClick(); compose.waitUntil(5_000) { repo.load(finished.id)!!.pinnedAt > 0 }
            scenario.recreate(); waitText("Apps"); compose.onNodeWithContentDescription("Pinned app").assertExists(); shot("pinned-apps")
        } } finally { repo.delete(draft.id); repo.delete(finished.id); preferences.enableOfflineTools(previous) }
    }
    @Test fun providerAccordionsAreExclusiveAndFallbackCanBeTurnedOff() {
        val settings = app.container.settings; val original = settings.generationFallback()
        try { ActivityScenario.launch(MainActivity::class.java).use {
            waitText("New chat"); compose.onNodeWithContentDescription("Settings").performClick(); compose.onNodeWithText("AI & privacy").performClick(); compose.onNodeWithText("Cloud AI").performClick()
            compose.onNode(hasSetTextAction() and hasText("Gemini API key")).assertExists()
            compose.onNodeWithText("OpenAI", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText("OpenAI API key")).assertExists()
            compose.onNode(hasSetTextAction() and hasText("Gemini API key")).assertDoesNotExist()
            compose.onNodeWithText("Generation fallback").performScrollTo()
            compose.onNode(isToggleable()).performClick(); assertEquals(!original, settings.generationFallback()); shot("cloud-providers")
        } } finally { settings.setGenerationFallback(original) }
    }
    @Test fun htmlIsReadableAndCopyableWithoutBlockingKeyboard() {
        val preferences = UiPreferences(app); val original = preferences.selectedTheme(); preferences.selectTheme(AppTheme.SEA_BLUE)
        val html = "<!DOCTYPE html>\n<html>\n<body>Hello</body>\n</html>"
        val chat = Conversation(title = "HTML basics", titleOwned = true, assistantMode = AssistantMode.CHAT, messages = listOf(
            Message(role = MessageRole.USER, content = "Build a simple page"), Message(role = MessageRole.ASSISTANT, content = "## A simple page\n**Save** this as index.html.\n\n```html\n$html\n```"))).also(repo::save)
        fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText().trim() } }
        val previousKeyboard = shell("settings get secure show_ime_with_hard_keyboard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        try { ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_PROJECT_ID, chat.id)).use { scenario ->
            waitText("Copy code"); compose.onNodeWithText("Copy code").performClick()
            instrumentation.runOnMainSync { val clipboard = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; assertEquals(html, clipboard.primaryClip!!.getItemAt(0).text.toString()) }
            compose.onNodeWithText("```html", substring = true).assertDoesNotExist()
            compose.onNode(hasSetTextAction()).performClick().performTextInput("Make the heading blue")
            compose.waitUntil(10_000) { var visible = false; scenario.onActivity { visible = it.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true }; visible }
            val imeShownAt = android.os.SystemClock.uptimeMillis()
            compose.waitUntil(5_000) {
                var settled = false
                scenario.onActivity {
                    val insets = it.window.decorView.rootWindowInsets
                    settled = insets?.isVisible(android.view.WindowInsets.Type.ime()) == true &&
                        insets.getInsets(android.view.WindowInsets.Type.ime()).bottom > 200 &&
                        android.os.SystemClock.uptimeMillis() - imeShownAt > 1_000
                }
                settled
            }
            compose.waitForIdle(); shot("chat-keyboard")
            val input = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
            val content = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
            assertTrue("A one-line input must stay compact", input.height < content.height / 2)
        } } finally { shell(if (previousKeyboard == "null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $previousKeyboard"); repo.delete(chat.id); preferences.selectTheme(original) }
    }
    @Test fun importingKeepsEarlierModelsAndRecordsUserProvenance() = runBlocking {
        val store = LocalModelStore(app); val previous = store.selectedName(); val initial = store.installed().map { it.fileName }.toSet()
        val fixture = File(app.cacheDir, "inventory-fixture.gguf").apply { writeBytes("GGUF".toByteArray() + byteArrayOf(3,0,0,0) + ByteArray(2048)) }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", fixture)
            store.import(uri); val first = store.selectedName()!!; store.import(uri); val second = store.selectedName()!!
            assertNotEquals(first, second); val installed = store.installed(); assertTrue(installed.any { it.fileName == first }); assertTrue(installed.any { it.fileName == second })
            assertEquals("User", installed.first { it.fileName == second }.source)
            store.select(first); assertEquals(first, store.selectedName()); store.remove(second); assertEquals(first, store.selectedName())
        } finally { store.installed().filter { it.fileName !in initial }.forEach { store.remove(it.fileName) }; previous?.let(store::select); fixture.delete() }
    }
    @Test fun largerModelInventoryOpensSearchableScreenAndReturnsToLocalSetup() {
        val directory = File(app.filesDir, "models").apply { mkdirs() }
        val prefix = "catalogue-fixture-${java.util.UUID.randomUUID()}"
        val fixtures = (1..11).map { index -> File(directory, "$prefix-$index.gguf").apply { writeBytes("GGUF".toByteArray() + byteArrayOf(3,0,0,0) + ByteArray(2048)) } }
        try { ActivityScenario.launch(MainActivity::class.java).use {
            waitText("New chat"); compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("AI & privacy").performClick()
            compose.onNode(hasText("Runs on your phone", substring = true)).performClick()
            waitText("Browse available models"); compose.onNodeWithText("Browse available models").performScrollTo().performClick()
            waitText("Find a model"); compose.onNode(hasSetTextAction()).performTextInput("$prefix-11")
            compose.onNodeWithText("$prefix-11.gguf").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("$prefix-1.gguf").assertDoesNotExist()
            compose.onNodeWithContentDescription("Back").performClick(); waitText("Browse available models")
        } } finally { fixtures.forEach { it.delete() } }
    }
}
