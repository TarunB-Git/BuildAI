package com.buildai.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.jobs.LocalBuildJobs
import com.buildai.app.data.local.*
import com.buildai.app.data.model.*
import com.buildai.app.data.storage.BuildAiDatabase
import com.buildai.app.viewmodel.GenerationViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Controlled model output tests job lifetime/publication, not model capability. */
class LocalBuildLifetimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as BuildAiApplication
    private val repo get() = app.container.projects
    private val rows get() = BuildAiDatabase.get(app).jobs()
    private class ControlledAdapter : ModelAdapter {
        override val supportsStructuredOutput = true
        override val resourceProfile = ModelResourceProfile(1, "controlled fixture")
        val started = Channel<List<Message>>(Channel.UNLIMITED)
        val proceed = Channel<Unit>(Channel.UNLIMITED)
        val output = JSONObject().put("html", "<button id='hello'>Hello</button><output id='value'>Ready</output>")
            .put("js", "document.getElementById('hello').onclick=()=>{document.getElementById('value').textContent='Saved app';};").toString()
        override fun reply(modelFile: File, systemPrompt: String, history: List<Message>, maxTokens: Int, contextSize: Int,
            stopAt: String?, grammar: String?, onStatus: (String) -> Unit, onProgress: (InferenceProgress) -> Unit, priority: InferencePriority): Flow<String> = flow {
            emit(output.take(output.length / 2))
            started.send(history)
            proceed.receive()
            emit(output.drop(output.length / 2))
        }
        override suspend fun unload() {}
    }
    private fun project() = Conversation(projectPlan = com.buildai.app.data.local.QuickPlan.fromIdea("A greeting button").copy(appName = "Lifetime fixture", description = "Show a greeting",
        features = listOf("Greeting button"), screens = listOf("Home"), buildMode = BuildMode.CUSTOM), designSpec = DesignSpec())
    private suspend fun terminal(id: String) = withTimeout(30_000) {
        rows.observe().first { it.any { row -> row.id == id && row.status !in listOf("RUNNING", "QUEUED") } }.first { it.id == id }
    }
    private fun input(project: Conversation) = JSONObject().put("project", project.toJson()).put("modelPath", File(app.cacheDir, "controlled-model").absolutePath)
    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }

    @Test fun localBuildSurvivesViewModelDetachAndScreenOff() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            val project = project().also(repo::save)
            val adapter = ControlledAdapter()
            val manager = LocalBuildJobs(app, repo, adapter)
            var id = ""
            try {
                instrumentation.runOnMainSync { id = manager.enqueue(project.id, input(project)) }
                withTimeout(10_000) { adapter.started.receive() }
                lateinit var vm: GenerationViewModel
                withContext(Dispatchers.Main) {
                    vm = GenerationViewModel(app)
                    vm.loadProject(project.id)
                    vm.reset() // navigation detaches observation; manager still owns work
                }
                shell("input keyevent KEYCODE_SLEEP")
                adapter.proceed.send(Unit)
                assertEquals("COMPLETED", terminal(id).status)
                assertTrue(checkNotNull(repo.generatedFile(project.id)).readText().contains("Saved app"))
                assertEquals(project.projectPlan!!.toJson().toString(), repo.load(project.id)!!.projectPlan!!.toJson().toString())
            } finally {
                shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
                instrumentation.runOnMainSync { manager.cancel(project.id) }
                repo.delete(project.id)
            }
        }
    }

    @Test fun explicitCancellationPreservesWorkingAppAndRetryUsesSavedInput() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            val project = project().also(repo::save)
            val old = "<!DOCTYPE html><html><head></head><body><p>Previous working app</p></body></html>"
            repo.saveGenerated(project.id, old)
            val adapter = ControlledAdapter(); val manager = LocalBuildJobs(app, repo, adapter)
            var first = ""; var retry = ""
            try {
                instrumentation.runOnMainSync { first = manager.enqueue(project.id, input(project)) }
                val initialPrompt = withTimeout(10_000) { adapter.started.receive() }
                instrumentation.runOnMainSync { manager.cancel(project.id) }
                assertEquals("CANCELLED", terminal(first).status)
                assertEquals(old, repo.generatedFile(project.id)!!.readText())
                instrumentation.runOnMainSync { retry = manager.retry(first) }
                assertEquals(initialPrompt.map { it.content }, withTimeout(10_000) { adapter.started.receive() }.map { it.content })
                assertEquals(rows.load(first)!!.inputJson, rows.load(retry)!!.inputJson)
                adapter.proceed.send(Unit)
                assertEquals("COMPLETED", terminal(retry).status)
            } finally { instrumentation.runOnMainSync { manager.cancel(project.id) }; repo.delete(project.id) }
        }
    }

    @Test fun changedPlanRejectsPublicationAndKeepsPreviousApp() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            val project = project().also(repo::save)
            val old = "<!DOCTYPE html><html><head></head><body><p>Previous app</p></body></html>"
            repo.saveGenerated(project.id, old)
            val adapter = ControlledAdapter(); val manager = LocalBuildJobs(app, repo, adapter)
            var id = ""
            try {
                instrumentation.runOnMainSync { id = manager.enqueue(project.id, input(project)) }
                withTimeout(10_000) { adapter.started.receive() }
                repo.update(project.id) { it.copy(projectPlan = it.projectPlan!!.copy(features = listOf("Different greeting"))) }
                adapter.proceed.send(Unit)
                val job = terminal(id)
                assertEquals("FAILED", job.status)
                assertTrue(job.error!!.contains("Requirements changed"))
                assertTrue(job.checkpoint.contains("hello"))
                assertEquals(old, repo.generatedFile(project.id)!!.readText())
            } finally { instrumentation.runOnMainSync { manager.cancel(project.id) }; repo.delete(project.id) }
        }
    }
}
