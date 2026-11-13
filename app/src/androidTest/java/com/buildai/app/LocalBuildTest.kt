package com.buildai.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.buildai.app.data.api.GenerationWorker
import com.buildai.app.data.api.HtmlValidator
import com.buildai.app.data.local.QuickPlan
import com.buildai.app.data.model.BuildMode
import com.buildai.app.data.model.Conversation
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.QuickTemplate
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

class LocalBuildTest {
    @Test fun chosenTemplateBuildsOfflineWithoutASelectedModelOrKey() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BuildAiApplication
        val prefs = app.getSharedPreferences("local_model", Context.MODE_PRIVATE)
        val previous = prefs.getString("selected", null)
        val settings = app.container.settings
        val previousKey = settings.getApiKey()
        prefs.edit().remove("selected").commit()
        settings.clearApiKey()
        val id = UUID.randomUUID().toString()
        val plan = QuickPlan.fromIdea("A simple calculator").copy(buildMode = BuildMode.TEMPLATE, quickTemplate = QuickTemplate.CALCULATOR)
        val repo = app.container.projects
        repo.save(Conversation(id = id, title = "Local calculator", projectPlan = plan,
            phase = "CONFIRMED", designSpec = DesignSpec()))
        val work = WorkManager.getInstance(app)
        try {
            // A cloud preference must not introduce a network requirement for an explicit template.
            GenerationWorker.enqueue(app, id, "cloud")
            val result: WorkInfo = withTimeout(60_000) {
                var terminal: WorkInfo? = null
                while (terminal == null) {
                    val info = work.getWorkInfosForUniqueWork(GenerationWorker.workName(id)).get(10, TimeUnit.SECONDS).firstOrNull()
                    if (info != null && info.state.isFinished) terminal = info else delay(250)
                }
                checkNotNull(terminal)
            }
            assertEquals(NetworkType.NOT_REQUIRED, result.constraints.requiredNetworkType)
            assertEquals(result.outputData.getString(GenerationWorker.ERROR), WorkInfo.State.SUCCEEDED, result.state)
            val html = repo.generatedFile(id)?.readText()
            assertNotNull("No template output was saved", html)
            HtmlValidator.extractAndValidate(checkNotNull(html))
            assertTrue(html.contains("Calculate"))
            assertFalse(html.contains("Offline starter app"))
        } finally {
            work.cancelUniqueWork(GenerationWorker.workName(id)).result.get(10, TimeUnit.SECONDS)
            repo.delete(id)
            val editor = prefs.edit()
            if (previous == null) editor.remove("selected") else editor.putString("selected", previous)
            editor.commit()
            previousKey?.let(settings::saveApiKey) ?: settings.clearApiKey()
        }
    }
}
