package com.buildai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.buildai.app.data.api.GenerationWorker
import com.buildai.app.data.local.LocalModelStore
import com.buildai.app.data.api.HtmlValidator
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.ProjectPlan
import com.buildai.app.data.storage.ConversationRepository
import com.buildai.app.data.storage.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class GenerationState {
    object Idle : GenerationState()
    data class Running(val step: String, val progress: Float) : GenerationState()
    data class Done(val html: String, val filePath: String, val projectId: String) : GenerationState()
    data class Failure(val message: String) : GenerationState()
}

class GenerationViewModel(
    app: Application,
    private val projects: ConversationRepository = ConversationRepository(app),
    private val settings: SecureStorage = SecureStorage(app),
    initialProjectId: String? = null
) : AndroidViewModel(app) {
    private val work = WorkManager.getInstance(app)
    private val _state = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val state = _state.asStateFlow()
    private var observation: Job? = null
    private var observedProjectId: String? = null
    private var activeRequestId: UUID? = null
    private val localBuilds = (app as com.buildai.app.BuildAiApplication).container.localBuilds
    private val jobs = com.buildai.app.data.storage.BuildAiDatabase.get(app).jobs()
    private var localRequestId: String? = null

    init { initialProjectId?.let { id -> viewModelScope.launch { loadProject(id) } } }

    /** Returns true if a saved app exists. Running work is exposed through [state]. */
    suspend fun loadProject(id: String): Boolean {
        reset()
        observedProjectId = id
        val info = withContext(Dispatchers.IO) {
            runCatching {
                work.getWorkInfosForUniqueWork(GenerationWorker.workName(id))
                    .get(5, TimeUnit.SECONDS)
            }.getOrDefault(emptyList())
        }
        val active = info.firstOrNull { !it.state.isFinished }
        val local = jobs.list().firstOrNull { it.projectId == id && it.kind == "BUILD" }
        _state.value = if (local?.status in listOf("QUEUED", "RUNNING", "WAITING", "INTERRUPTED", "FAILED")) stateForLocal(checkNotNull(local), id)
            else if (active != null) stateFor(active, id)
            else terminalState(info, id).takeIf { it is GenerationState.Failure } ?: readSaved(id) ?: GenerationState.Idle
        observe(id)
        return readSaved(id) != null
    }

    fun generate(projectId: String, plan: ProjectPlan, design: DesignSpec) {
        if (observedProjectId == projectId && _state.value is GenerationState.Running) return
        val backend = settings.getChatBackend()
        if (plan.buildMode == com.buildai.app.data.model.BuildMode.CUSTOM && backend == "local" && LocalModelStore(getApplication()).selectedFile() == null) {
            _state.value = GenerationState.Failure("Import or download a GGUF model in Settings first")
            return
        }
        if (plan.buildMode == com.buildai.app.data.model.BuildMode.CUSTOM && backend != "local" && settings.getApiKey() == null) {
            _state.value = GenerationState.Failure("No API key — add it in Settings first")
            return
        }
        if (plan.features.isEmpty()) {
            _state.value = GenerationState.Failure("Confirm the project plan first")
            return
        }
        projects.saveDesign(projectId, design)
        if (backend == "local") {
            localRequestId = localBuilds.enqueue(projectId)
            activeRequestId = null
        } else {
            localRequestId = null
            activeRequestId = GenerationWorker.enqueue(getApplication(), projectId, backend)
        }
        if (observedProjectId != projectId) {
            observation?.cancel()
            observedProjectId = projectId
            observe(projectId)
        }
        _state.value = GenerationState.Running("Waiting to start…", 0.05f)
    }

    /** Only explicit Cancel stops a build. reset() merely detaches this screen. */
    fun cancel() {
        observedProjectId?.let(localBuilds::cancel)
        activeRequestId?.let(work::cancelWorkById)
            ?: observedProjectId?.let { work.cancelUniqueWork(GenerationWorker.workName(it)) }
        activeRequestId = null
        localRequestId = null
        _state.value = GenerationState.Idle
    }

    fun reset() {
        observation?.cancel()
        observation = null
        observedProjectId = null
        activeRequestId = null
        localRequestId = null
        _state.value = GenerationState.Idle
    }

    private fun observe(id: String) {
        observation?.cancel()
        observation = viewModelScope.launch {
            combine(work.getWorkInfosForUniqueWorkFlow(GenerationWorker.workName(id)), jobs.observe()) { infos, stored -> infos to stored }
                .collectLatest { (infos, stored) ->
                if (observedProjectId != id) return@collectLatest
                val local = stored.firstOrNull { it.projectId == id && it.kind == "BUILD" && (localRequestId == null || it.id == localRequestId) }
                if (activeRequestId == null && local != null && (localRequestId != null || local.status in listOf("QUEUED", "RUNNING", "WAITING", "INTERRUPTED", "FAILED"))) {
                    _state.value = stateForLocal(local, id)
                    return@collectLatest
                }
                val current = activeRequestId?.let { requestId -> infos.firstOrNull { it.id == requestId } }
                if (activeRequestId != null && current == null) return@collectLatest
                val info = current ?: infos.firstOrNull { !it.state.isFinished }
                _state.value = if (info != null) stateFor(info, id)
                    else terminalState(infos, id).takeIf { it is GenerationState.Failure } ?: readSaved(id) ?: GenerationState.Idle
            }
        }
    }

    private fun stateForLocal(job: com.buildai.app.data.storage.StoredAgentJob, id: String): GenerationState = when (job.status) {
        "QUEUED", "RUNNING" -> GenerationState.Running(job.stage, 0f)
        "COMPLETED" -> readSaved(id) ?: GenerationState.Failure("The saved app is missing.")
        "FAILED", "WAITING", "INTERRUPTED" -> GenerationState.Failure(job.error ?: "Build interrupted. Retry from its saved input.")
        else -> readSaved(id) ?: GenerationState.Idle
    }

    private fun stateFor(info: WorkInfo, id: String): GenerationState = when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
            GenerationState.Running("Waiting to start…", 0.05f)
        WorkInfo.State.RUNNING -> GenerationState.Running(
            info.progress.getString(GenerationWorker.STEP) ?: "Building your app…",
            info.progress.getFloat(GenerationWorker.PROGRESS, 0.05f)
        )
        WorkInfo.State.SUCCEEDED -> readSaved(id)
            ?: GenerationState.Failure("Build finished, but the saved app is missing.")
        WorkInfo.State.FAILED -> GenerationState.Failure(
            info.outputData.getString(GenerationWorker.ERROR) ?: "Couldn't build your app. Try again."
        )
        WorkInfo.State.CANCELLED -> readSaved(id) ?: GenerationState.Idle
    }

    private fun terminalState(infos: List<WorkInfo>, id: String): GenerationState =
        infos.firstOrNull { it.state == WorkInfo.State.FAILED }?.let { stateFor(it, id) }
            ?: GenerationState.Idle

    fun savedOutput(id: String): GenerationState.Done? = readSaved(id)

    private fun readSaved(id: String): GenerationState.Done? = runCatching {
        val file = projects.generatedFile(id) ?: return null
        GenerationState.Done(HtmlValidator.extractAndValidate(file.readText()), file.absolutePath, id)
    }.getOrNull()
}
