package com.buildai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.buildai.app.data.model.Conversation
import com.buildai.app.data.storage.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ConversationRepository(app)
    private val entry = repo.assistantEntry()
    private val _entryText = MutableStateFlow(entry.draftText)
    val entryText = _entryText.asStateFlow()
    private val _assistantMode = MutableStateFlow(entry.assistantMode)
    val assistantMode = _assistantMode.asStateFlow()
    val operationLabel: String get() = if ((getApplication<Application>() as com.buildai.app.BuildAiApplication).container.settings.getChatBackend() == "local") "Local AI" else "Cloud AI enabled"
    fun setEntryText(text: String) {
        _entryText.value = text
        repo.update(entry.id) { it.copy(draftText = text) }
    }
    fun setAssistantMode(mode: com.buildai.app.data.model.AssistantMode) {
        _assistantMode.value = mode
        repo.update(entry.id) { it.copy(assistantMode = mode) }
    }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()
    private val _nativeAppIds = MutableStateFlow<Set<String>>(emptySet())
    val nativeAppIds = _nativeAppIds.asStateFlow()

    private val _buildingIds = MutableStateFlow<Set<String>>(emptySet())
    val buildingIds: StateFlow<Set<String>> = _buildingIds.asStateFlow()

    init {
        viewModelScope.launch {
            com.buildai.app.data.storage.BuildAiDatabase.get(app).projects().observe()
                .map { rows -> rows.map { Conversation.fromJson(JSONObject(it.json)) }
                    .filterNot { it.assistantEntryDraft }.sortedByDescending { it.updatedAt } }
                .flowOn(Dispatchers.Default)
                .collectLatest { _conversations.value = it }
        }
        viewModelScope.launch {
            com.buildai.app.data.storage.BuildAiDatabase.get(app).nativeApps().observeHeads().collectLatest { heads ->
                _nativeAppIds.value = heads.map { it.appId }.toSet()
            }
        }
        viewModelScope.launch {
            combine(androidx.work.WorkManager.getInstance(app).getWorkInfosByTagFlow("generation"),
                com.buildai.app.data.storage.BuildAiDatabase.get(app).jobs().observe()) { infos, jobs -> infos to jobs }
                .collectLatest { (infos, jobs) ->
                    _buildingIds.value = infos.filter { !it.state.isFinished }
                        .mapNotNull { work -> work.tags.firstOrNull { it.startsWith("generation-") }
                            ?.removePrefix("generation-") }.toSet() + jobs.filter { it.kind in listOf("BUILD", "NATIVE_BUILD", "NATIVE_EDIT") && it.status in listOf("QUEUED", "RUNNING") }.map { it.projectId }
                }
        }
    }

    fun togglePin(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.update(id) { it.copy(pinnedAt = if (it.pinnedAt == 0L) System.currentTimeMillis() else 0L) } }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _conversations.value = repo.listAll()
        }
    }

    fun delete(id: String) {
        (getApplication<Application>() as com.buildai.app.BuildAiApplication).container.sessions.cancel(id)
        (getApplication<Application>() as com.buildai.app.BuildAiApplication).container.localBuilds.cancel(id)
        (getApplication<Application>() as com.buildai.app.BuildAiApplication).container.nativeBuilds.cancel(id)
        (getApplication<Application>() as com.buildai.app.BuildAiApplication).container.nativeEdits.cancel(id)
        androidx.work.WorkManager.getInstance(getApplication())
            .cancelUniqueWork(com.buildai.app.data.api.GenerationWorker.workName(id))
        repo.delete(id)
        com.buildai.app.widgets.ProjectWidgetProvider.refreshProject(getApplication(), id)
        android.webkit.WebStorage.getInstance().deleteOrigin(com.buildai.app.ui.components.generatedAppOrigin(id).removeSuffix("/"))
        refresh()
    }
}
