package com.buildai.app

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.local.LocalLlmEngine
import com.buildai.app.data.model.MessageRole
import com.buildai.app.viewmodel.ConversationPhase
import com.buildai.app.viewmodel.ConversationViewModel
import com.buildai.app.viewmodel.LocalModelCheck
import com.buildai.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Exercises the production Settings and conversation handlers with a real, selected GGUF. */
class LocalChatIntegrationTest {
    @Test fun selectedModelPassesSettingsCheckThenRepliesThroughNormalChat() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BuildAiApplication
        val fixture = File(app.filesDir, "models/test.gguf")
        assumeTrue("Install files/models/test.gguf to run real local chat", fixture.isFile)
        val selected = File(fixture.parentFile, "model-${UUID.randomUUID()}.gguf")
        val preferences = app.getSharedPreferences("local_model", Context.MODE_PRIVATE)
        val previousFile = preferences.getString("selected", null)
        val previousName = preferences.getString("display_name", null)
        val storage = app.container.settings
        val previousBackend = storage.getChatBackend()
        val store = ViewModelStore()
        var settings: SettingsViewModel? = null
        var chat: ConversationViewModel? = null
        var projectId: String? = null
        assertTrue(fixture.renameTo(selected))
        try {
            assertTrue(preferences.edit().putString("selected", selected.name)
                .putString("display_name", "Test Qwen3").commit())
            storage.saveChatBackend("local")
            instrumentation.runOnMainSync {
                settings = SettingsViewModel(app).also { store.put("settings", it) }
                chat = ConversationViewModel(app).also { store.put("conversation", it) }
                settings!!.testLocalModel()
            }
            val result = withTimeout(100_000) {
                settings!!.modelCheck.first { it is LocalModelCheck.Passed || it is LocalModelCheck.Failed }
            }
            assertTrue("The Settings check failed: $result", result is LocalModelCheck.Passed)
            assertTrue("The Settings check returned no reply", (result as LocalModelCheck.Passed).reply.isNotBlank())

            instrumentation.runOnMainSync {
                chat!!.sendUserMessage("Make a simple task checklist. Reply in one short sentence.")
                projectId = chat!!.projectId
            }
            withTimeout(120_000) { chat!!.isLoading.first { !it } }
            assertNull("Normal local chat failed", chat!!.error.value)
            assertEquals("Local GGUF", chat!!.activeModel.value)
            assertEquals("A first reply must not skip planning", ConversationPhase.CLARIFYING, chat!!.phase.value)
            assertNull(chat!!.projectPlan.value)
            instrumentation.runOnMainSync { chat!!.sendUserMessage("Let me add tasks, mark them done and edit names.") }
            withTimeout(120_000) { chat!!.isLoading.first { !it } }
            assertEquals(ConversationPhase.CLARIFYING, chat!!.phase.value)
            instrumentation.runOnMainSync { chat!!.draftPlan() }
            withTimeout(120_000) { chat!!.isLoading.first { !it } }
            instrumentation.runOnMainSync {
                assertNull(chat!!.error.value)
                assertEquals(ConversationPhase.PLAN_READY, chat!!.phase.value)
                assertNotNull(chat!!.projectPlan.value)
                chat!!.confirmPlan()
                chat!!.resumePlanning()
                assertEquals(ConversationPhase.CLARIFYING, chat!!.phase.value)
            }
            assertTrue(chat!!.messages.value.any { it.role == MessageRole.ASSISTANT && it.content.isNotBlank() })
            assertTrue("Successful chat left a failed message", chat!!.messages.value.none { it.failed })
            assertNotNull("The local conversation was not saved", app.container.projects.load(checkNotNull(projectId)))
        } finally {
            instrumentation.runOnMainSync {
                settings?.stopModelCheck()
                chat?.leaveProject()
                store.clear()
            }
            LocalLlmEngine(app).unload()
            projectId?.let { app.container.projects.delete(it) }
            storage.saveChatBackend(previousBackend)
            val editor = preferences.edit()
            if (previousFile == null) editor.remove("selected") else editor.putString("selected", previousFile)
            if (previousName == null) editor.remove("display_name") else editor.putString("display_name", previousName)
            assertTrue(editor.commit())
            assertTrue("Could not restore the GGUF fixture", selected.renameTo(fixture))
        }
    }
}
