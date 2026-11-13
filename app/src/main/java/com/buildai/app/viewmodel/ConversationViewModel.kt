package com.buildai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.buildai.app.BuildAiApplication
import com.buildai.app.data.api.GeminiRepository
import com.buildai.app.data.jobs.ConversationSession
import com.buildai.app.data.jobs.ConversationSessions
import com.buildai.app.data.model.*
import com.buildai.app.data.storage.ConversationRepository
import com.buildai.app.data.storage.SecureStorage
import com.buildai.app.util.SpeechManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel

enum class ConversationPhase { IDEA_INPUT, CLARIFYING, PLAN_READY, CONFIRMED }

/** The screen owns observation and voice input; the application owns each request. */
class ConversationViewModel(
    app: Application,
    gemini: GeminiRepository = (app as BuildAiApplication).container.conversation,
    private val convRepo: ConversationRepository = (app as BuildAiApplication).container.projects,
    private val secureStorage: SecureStorage = (app as BuildAiApplication).container.settings,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val sessions: ConversationSessions = if (gemini === (app as BuildAiApplication).container.conversation && convRepo === app.container.projects)
        app.container.sessions else ConversationSessions(app, gemini, convRepo, secureStorage)
) : AndroidViewModel(app) {
    val speechManager = SpeechManager(app)
    val speechState = speechManager.speechState
    val operationLabel: String get() = if (secureStorage.getChatBackend() == "local") "Local AI" else "Cloud AI enabled"
    private var session: ConversationSession? = null
    private var observation: Job? = null
    val projectId: String? get() = session?.projectId
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _phase = MutableStateFlow(ConversationPhase.IDEA_INPUT)
    val phase = _phase.asStateFlow()
    private val _projectPlan = MutableStateFlow<ProjectPlan?>(null)
    val projectPlan = _projectPlan.asStateFlow()
    private val _activeModel = MutableStateFlow<String?>(null)
    val activeModel = _activeModel.asStateFlow()
    private val _draftText = MutableStateFlow("")
    val draftText = _draftText.asStateFlow()
    private val _queuedInputs = MutableStateFlow<List<String>>(emptyList())
    val queuedInputs = _queuedInputs.asStateFlow()
    private val _mode = MutableStateFlow(AssistantMode.CREATE)
    val mode = _mode.asStateFlow()
    private val _brief = MutableStateFlow<com.buildai.app.builder.ProjectBrief?>(null)
    val brief = _brief.asStateFlow()
    private val tabWrites = Channel<Pair<String, String>>(Channel.UNLIMITED)

    init {
        savedState.get<String>("projectId")?.let(::loadConversation)
        viewModelScope.launch(Dispatchers.IO) {
            for ((id, tab) in tabWrites) runCatching { convRepo.update(id) { it.copy(workspaceTab = tab) } }.onFailure { _error.value = "Could not save your place. Free some storage and try again." }
        }
    }

    /** Serialized off the UI thread so fast tab taps cannot save in the wrong order. */
    fun persistWorkspaceTab(id: String, tab: String) {
        require(tab in listOf("chat", "plan", "preview"))
        tabWrites.trySend(id to tab)
    }

    fun leaveProject() {
        speechManager.cancelListening()
        observation?.cancel(); observation = null
        session = null; savedState["projectId"] = null
        sync()
    }
    fun startNewConversation(mode: AssistantMode = AssistantMode.CREATE, nativePlanning: Boolean = true) = attach(sessions.create(mode, nativePlanning))
    fun loadConversation(id: String) { sessions.get(id)?.let(::attach) }
    /** Existing conversations keep their identity. New work starts through explicit navigation. */
    fun setMode(mode: AssistantMode) { if (session == null) startNewConversation(mode) }
    fun setDraftText(text: String) { session?.setDraftText(text); sync() }
    fun sendUserMessage(text: String) {
        if (session == null) startNewConversation(_mode.value)
        session?.sendUserMessage(text); sync()
    }
    fun stopReply() { session?.stopReply(); sync() }
    fun editQueued(index: Int, text: String) { session?.editQueued(index, text); sync() }
    fun removeQueued(index: Int) { session?.removeQueued(index); sync() }
    fun steer(text: String) { session?.steer(text); sync() }
    fun steerQueued(index: Int) { session?.steerQueued(index); sync() }
    fun retryLastMessage() { session?.retryLastMessage(); sync() }
    fun confirmPlan() { session?.confirmPlan(); sync() }
    fun draftPlan() { session?.draftPlan(); sync() }
    fun resumePlanning() { session?.resumePlanning(); sync() }
    fun currentProject() = projectId?.let(convRepo::load)
    fun saveLegacyPlanDraft(text: String) { projectId?.let { id -> convRepo.update(id) { base -> if (base.legacyPlanDraft == text) base else base.copy(legacyPlanDraft = text) } } }
    fun chooseStarter(template: QuickTemplate) { session?.chooseStarter(template); sync() }
    fun updatePlan(plan: ProjectPlan) { session?.updatePlan(plan); sync() }
    fun editNativePlan(plan: com.buildai.app.builder.NativePlan) { session?.editNativePlan(plan); sync() }
    fun editNativePlanDraft(text: String) { session?.editNativePlanDraft(text); sync() }
    fun applySuggestions(choices: Map<String, Boolean>) { session?.applySuggestions(choices); sync() }
    fun acceptSuggestion(id: String, accepted: Boolean) { session?.acceptSuggestion(id, accepted); sync() }
    fun approveNativePlan() = checkNotNull(session).approveNativePlan().also { sync() }
    fun revisePlan(text: String) { session?.revisePlan(text); sync() }
    fun clearError() { session?.clearError(); sync() }
    fun resetConversation() = startNewConversation()
    fun getExportText(): String = projectId?.let(convRepo::load)?.let(convRepo::buildExportText).orEmpty()

    private fun attach(next: ConversationSession) {
        leaveProject()
        session = next; savedState["projectId"] = next.projectId
        sync()
        observation = viewModelScope.launch {
            launch { next.messages.collect { sync() } }
            launch { next.isLoading.collect { sync() } }
            launch { next.error.collect { sync() } }
            launch { next.phase.collect { sync() } }
            launch { next.projectPlan.collect { sync() } }
            launch { next.activeModel.collect { sync() } }
            launch { next.draftText.collect { sync() } }
            launch { next.queuedInputs.collect { sync() } }
            launch { next.brief.collect { sync() } }
        }
    }
    private fun sync() {
        _messages.value = session?.messages?.value.orEmpty()
        _isLoading.value = session?.isLoading?.value ?: false
        _error.value = session?.error?.value
        _phase.value = session?.phase?.value ?: ConversationPhase.IDEA_INPUT
        _projectPlan.value = session?.projectPlan?.value
        _activeModel.value = session?.activeModel?.value
        _draftText.value = session?.draftText?.value.orEmpty()
        _queuedInputs.value = session?.queuedInputs?.value.orEmpty()
        _mode.value = session?.mode?.value ?: AssistantMode.CREATE
        _brief.value = session?.brief?.value
    }
    override fun onCleared() { observation?.cancel(); speechManager.destroy() }
}
