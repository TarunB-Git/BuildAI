package com.buildai.app.data.jobs

import android.app.Application
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.buildai.app.data.api.GeminiRepository
import com.buildai.app.data.local.LocalLlmEngine
import com.buildai.app.data.local.LocalModelStore
import com.buildai.app.data.local.QuickPlan
import kotlinx.coroutines.flow.collect
import com.buildai.app.data.model.AppType
import com.buildai.app.data.model.Complexity
import com.buildai.app.data.model.Conversation
import com.buildai.app.data.model.Message
import com.buildai.app.data.model.MessageRole
import com.buildai.app.data.model.ProjectPlan
import com.buildai.app.data.storage.ConversationRepository
import com.buildai.app.data.storage.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.buildai.app.viewmodel.ConversationPhase

class ConversationSession(
    private val app: Application,
    private val gemini: GeminiRepository = GeminiRepository(),
    private val convRepo: ConversationRepository = ConversationRepository(app),
    private val secureStorage: SecureStorage = SecureStorage(app),
    private val requestScope: kotlinx.coroutines.CoroutineScope,
    initialId: String
) {

    private val _messages     = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading    = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error        = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _phase        = MutableStateFlow(ConversationPhase.IDEA_INPUT)
    val phase: StateFlow<ConversationPhase> = _phase.asStateFlow()

    private val _projectPlan  = MutableStateFlow<ProjectPlan?>(null)
    val projectPlan: StateFlow<ProjectPlan?> = _projectPlan.asStateFlow()

    /** Which model actually replied — may differ from preferred if fallback triggered */
    private val _activeModel  = MutableStateFlow<String?>(null)
    val activeModel: StateFlow<String?> = _activeModel.asStateFlow()


    private var currentId: String? = null
    private var requestJob: Job? = null
    private val modelStore = LocalModelStore(app)
    private val localEngine by lazy { LocalLlmEngine(app) }
    val projectId: String? get() = currentId
    private var lastUserText: String? = null

    private val jobs = com.buildai.app.data.storage.BuildAiDatabase.get(app).jobs()
    private var activeJobId: String? = null
    private val _draftText = MutableStateFlow("")
    val draftText = _draftText.asStateFlow()
    private val _queuedInputs = MutableStateFlow<List<String>>(emptyList())
    val queuedInputs = _queuedInputs.asStateFlow()
    private val _mode = MutableStateFlow(com.buildai.app.data.model.AssistantMode.CREATE)
    val mode = _mode.asStateFlow()
    private val _brief = MutableStateFlow<com.buildai.app.builder.ProjectBrief?>(null)
    val brief = _brief.asStateFlow()
    private var lastPartialSave = 0L
    private var partialMessageId: String? = null
    private var partialHasText = false
    init { loadConversation(initialId) }
    fun setDraftText(text: String) { _draftText.value = text; persist() }


    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private fun loadConversation(id: String) {
        val conv = convRepo.load(id) ?: return
        requestJob?.cancel(); requestJob = null
        _isLoading.value = false
        currentId          = id
        _draftText.value = conv.draftText
        _queuedInputs.value = conv.queuedInputs
        _mode.value = conv.assistantMode
        _brief.value = conv.brief
        _messages.value    = conv.messages
        _projectPlan.value = conv.projectPlan
        _phase.value       = runCatching { ConversationPhase.valueOf(conv.phase) }
                                 .getOrDefault(ConversationPhase.CLARIFYING)
        _activeModel.value = conv.modelLastUsed
        _error.value       = null
        lastUserText = conv.messages.lastOrNull { it.role == MessageRole.USER }?.content
        if (conv.replyStatus in listOf("RUNNING", "INTERRUPTED") || conv.messages.lastOrNull()?.role == MessageRole.USER) {
            val userId = conv.messages.lastOrNull { it.role == MessageRole.USER }?.id
            _messages.value = conv.messages.map { if (it.id == userId) it.copy(failed = true) else it }
            _error.value = "Your last request wasn't completed. Tap Retry to continue."
            persist()
        }
    }

    // ── Messaging ─────────────────────────────────────────────────────────────

    fun sendUserMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (_isLoading.value) {
            _queuedInputs.value += trimmed
            persist()
            return
        }
        if (_mode.value == com.buildai.app.data.model.AssistantMode.CREATE && _brief.value != null) {
            sendCreate(trimmed)
            return
        }

        // When a plan is waiting for confirmation, intercept keywords locally
        // so they never waste an API call or trigger system-prompt leakage.
        if (_mode.value == com.buildai.app.data.model.AssistantMode.CREATE && _phase.value == ConversationPhase.PLAN_READY) {
            if (isConfirmation(trimmed)) {
                confirmPlan()
                return
            }
            // Anything else → user wants to revise the plan
            revisePlan(trimmed)
            return
        }
        // A stopped or interrupted reply still belongs to the conversation when the
        // person adds a correction. Keep that original request in the next prompt.
        _messages.value.lastOrNull()?.takeIf { it.role == MessageRole.USER && it.failed }?.let { prior ->
            _messages.value = _messages.value.dropLast(1) + prior.copy(failed = false)
        }
        lastUserText = trimmed
        val userMsg = Message(role = MessageRole.USER, content = trimmed)
        _messages.value = _messages.value + userMsg
        if (_phase.value == ConversationPhase.IDEA_INPUT) _phase.value = ConversationPhase.CLARIFYING
        persist()
        val local = secureStorage.getChatBackend() == "local"
        val modelFile = if (local) modelStore.selectedFile() else null
        val apiKey = if (local) null else secureStorage.getApiKey()
        if ((local && modelFile == null) || (!local && apiKey == null)) {
            _messages.value = _messages.value.dropLast(1) + userMsg.copy(failed = true)
            _error.value = if (local) "Import a GGUF model in Settings first"
                else "Add your ${secureStorage.provider().title} key in Settings first"
            persist()
            return
        }

        _isLoading.value = true
        _error.value = null
        val requestProject = currentId
        val planRequested = trimmed == "Review my plan" && _messages.value.count { it.role == MessageRole.USER && !it.failed } >= 3
        val planningPrompt = if (_mode.value == com.buildai.app.data.model.AssistantMode.CHAT)
            "You are a helpful private chat assistant. Answer the user's message directly. Do not create an app or project plan."
            else if (planRequested) gemini.systemPrompt + "\nNow write the complete <plan> JSON from our conversation. Do not ask another question. Keep the features specific to this person's idea."
            else gemini.systemPrompt + "\nFor this turn, ask ONE short question about the user's workflow, data, or priorities. Do not output a plan, JSON, or app code yet."
        val existingPlan = _projectPlan.value?.let { "\nCurrent plan to revise: " + it.toJson() }.orEmpty()
        val inputHistory = _messages.value.filter { !it.failed }
        val jobId = java.util.UUID.randomUUID().toString()
        activeJobId = jobId
        jobs.save(com.buildai.app.data.storage.StoredAgentJob(jobId, checkNotNull(currentId), "CHAT", "RUNNING",
            org.json.JSONObject().put("history", org.json.JSONArray(inputHistory.map { it.toJson() }))
                .put("prompt", planningPrompt + existingPlan).put("backend", if (local) "local" else "cloud")
                .put("model", modelFile?.name ?: secureStorage.getModel()).toString(), "Waiting for a reply"))
        persist()
        try { ExecutionService.keepRunning(app) }
        catch (error: Exception) {
            _isLoading.value = false
            _error.value = "Android could not start background work. Keep BuildAI open and retry."
            jobs.load(jobId)?.let { jobs.save(it.copy(status = "FAILED", error = _error.value)) }
            persist()
            return
        }
        requestJob = requestScope.launch {
            try {
            if (local) {
                var evidenceAt = 0L
                var evidenceStage: com.buildai.app.data.local.InferenceStage? = null
                val pending = Message(role = MessageRole.ASSISTANT, content = "Loading local model…")
                partialMessageId = pending.id
                partialHasText = false
                var raw = ""
                try {
                    _messages.value = _messages.value + pending
                    localEngine.reply(checkNotNull(modelFile), planningPrompt + existingPlan,
                        inputHistory,
                        maxTokens = if (planRequested) 640 else 160,
                        onStatus = { status ->
                            requestScope.launch {
                                jobs.load(jobId)?.let { jobs.save(it.copy(stage = status, updatedAt = System.currentTimeMillis())) }
                                if (currentId == requestProject && raw.isEmpty() && _messages.value.lastOrNull()?.id == pending.id) {
                                    _messages.value = _messages.value.dropLast(1) + pending.copy(content = status)
                                }
                            }
                        }, onProgress = { event ->
                            requestScope.launch {
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (event.stage != evidenceStage || now - evidenceAt >= 500 || event.total > 0 && event.completed == event.total) {
                                    evidenceAt = now; evidenceStage = event.stage
                                    jobs.load(jobId)?.let { jobs.save(it.copy(progressJson = org.json.JSONObject()
                                        .put("stage", event.stage.name).put("completed", event.completed).put("total", event.total).toString())) }
                                }
                            }
                        }).collect { chunk ->
                        if (currentId != requestProject) return@collect
                        raw += chunk
                        partialHasText = raw.substringBefore("<plan>").isNotBlank()
                        val display = if (raw.contains("<plan>"))
                            raw.substringBefore("<plan>").ifBlank { "Writing your plan…" }
                        else raw
                        _messages.value = _messages.value.dropLast(1) + pending.copy(content = display)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastPartialSave >= 250) { persist(); lastPartialSave = now }
                    }
                    if (currentId == requestProject) {
                        if (raw.isBlank()) error("The local model ended without a reply. Try another model or a shorter message.")
                        _messages.value = _messages.value.dropLast(1)
                        _activeModel.value = "Local GGUF"
                        handleResponse(raw, "Local GGUF", allowPlan = planRequested && _mode.value == com.buildai.app.data.model.AssistantMode.CREATE)
                        if (planRequested && _mode.value == com.buildai.app.data.model.AssistantMode.CREATE && _phase.value != ConversationPhase.PLAN_READY) {
                            val idea = _messages.value.filter { it.role == MessageRole.USER && it.content != "Review my plan" }
                                .joinToString(". ") { it.content }
                            _projectPlan.value = QuickPlan.fromIdea(idea)
                            _phase.value = ConversationPhase.PLAN_READY
                            _messages.value += Message(role = MessageRole.ASSISTANT,
                                content = "I drafted this from your answers. Please edit the name, features and screens before choosing a design.")
                            persist("Local draft plan")
                        }
                        _isLoading.value = false
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (currentId == requestProject) {
                        if (!partialHasText) _messages.value = _messages.value.filterNot { it.id == pending.id }
                        _messages.value = _messages.value.map { if (it.id == userMsg.id) it.copy(failed = true) else it }
                        _isLoading.value = false
                        _error.value = e.message ?: "Local inference failed. Try a smaller GGUF model."
                        persist()
                    }
                }
            } else {
                val pending = Message(role = MessageRole.ASSISTANT, content = "Waiting for cloud AI…")
                partialMessageId = pending.id
                partialHasText = false
                _messages.value += pending
                gemini.sendMessage(
                    messages = inputHistory,
                    apiKey = checkNotNull(apiKey),
                    preferredModel = secureStorage.getModel(),
                    provider = secureStorage.provider(), fallbackEnabled = secureStorage.generationFallback(), fallbackModels = secureStorage.fallbackModels(),
                    prompt = planningPrompt + existingPlan,
                    cleanPlanResponse = _mode.value == com.buildai.app.data.model.AssistantMode.CREATE,
                    onPartial = { raw ->
                        partialHasText = raw.substringBefore("<plan>").isNotBlank()
                        val display = raw.substringBefore("<plan>").ifBlank { "Writing your plan…" }
                        _messages.value = _messages.value.map { if (it.id == pending.id) it.copy(content = display) else it }
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastPartialSave >= 250) { persist(); lastPartialSave = now }
                    }
                ).fold(
                    onSuccess = { (text, model) ->
                        if (currentId != requestProject) return@fold
                        _messages.value = _messages.value.filterNot { it.id == pending.id }
                        _activeModel.value = model
                        handleResponse(text, model, allowPlan = _mode.value == com.buildai.app.data.model.AssistantMode.CREATE)
                        _isLoading.value = false
                    },
                    onFailure = { e ->
                        if (e is CancellationException) throw e
                        if (currentId != requestProject) return@fold
                        _isLoading.value = false
                        _error.value = e.message
                        if (!partialHasText) _messages.value = _messages.value.filterNot { it.id == pending.id }
                        _messages.value = _messages.value.map { if (it.id == userMsg.id) it.copy(failed = true) else it }
                        persist()
                    }
                )
            }
            val errorText = _error.value.orEmpty()
            val status = when {
                _error.value == null -> "COMPLETED"
                errorText.contains("Paused:") -> "WAITING"
                errorText.contains("interrupted", true) || errorText.contains("progress", true) -> "INTERRUPTED"
                else -> "FAILED"
            }
            jobs.load(jobId)?.let { jobs.save(it.copy(status = status, error = _error.value, updatedAt = System.currentTimeMillis())) }
            } catch (cancelled: CancellationException) { throw cancelled }
            finally {
                if (activeJobId == jobId) {
                partialMessageId = null
                partialHasText = false
                _isLoading.value = false
                persist()
                if (_error.value == null && jobs.load(jobId)?.status == "COMPLETED" && _queuedInputs.value.isNotEmpty()) {
                    val next = _queuedInputs.value.first()
                    _queuedInputs.value = _queuedInputs.value.drop(1)
                    persist()
                    sendUserMessage(next)
                }
                }
            }
        }
    }

    fun stopReply() {
        if (!_isLoading.value) return
        val stoppedJob = requestJob
        requestJob = null
        if (!partialHasText) partialMessageId?.let { id -> _messages.value = _messages.value.filterNot { it.id == id } }
        _isLoading.value = false
        val userId = _messages.value.lastOrNull { it.role == MessageRole.USER }?.id
        _messages.value = _messages.value.map { if (it.id == userId) it.copy(failed = true) else it }
        activeJobId?.let { id -> jobs.load(id)?.let { jobs.save(it.copy(status = "CANCELLED", error = "Reply stopped", updatedAt = System.currentTimeMillis())) } }
        _error.value = "Reply stopped. You can retry or edit your next message."
        persist()
        stoppedJob?.cancel()
    }

    fun editQueued(index: Int, text: String) {
        if (index !in _queuedInputs.value.indices || text.isBlank()) return
        _queuedInputs.value = _queuedInputs.value.toMutableList().also { it[index] = text.trim() }; persist()
    }
    fun removeQueued(index: Int) {
        if (index !in _queuedInputs.value.indices) return
        _queuedInputs.value = _queuedInputs.value.filterIndexed { i, _ -> i != index }; persist()
    }
    fun steer(text: String) {
        if (text.isBlank()) return
        val previous = requestJob
        stopReply()
        // Invalidate the old finalizer before starting a new request; never let it consume the queue.
        activeJobId = null
        _error.value = null
        val userId = _messages.value.lastOrNull { it.role == MessageRole.USER }?.id
        _messages.value = _messages.value.map { if (it.id == userId) it.copy(failed = false) else it }
        _isLoading.value = true
        requestJob = requestScope.launch { previous?.join(); _isLoading.value = false; sendUserMessage(text) }
    }
    fun steerQueued(index: Int) {
        val text = _queuedInputs.value.getOrNull(index) ?: return
        removeQueued(index); steer(text)
    }

    fun retryLastMessage() {
        _messages.value = _messages.value.filterNot { it.failed }
        _error.value    = null
        lastUserText?.let { sendUserMessage(it) }
    }

    // ── Plan confirmation ─────────────────────────────────────────────────────

    fun confirmPlan() {
        if (_projectPlan.value == null || _isLoading.value) return
        _phase.value = ConversationPhase.CONFIRMED
        persist()
    }

    fun draftPlan() {
        if (_brief.value != null) {
            if (_isLoading.value || _brief.value!!.turns.isEmpty()) return
            sendCreate("Please prepare an editable plan now, with visible assumptions for undecided details.", true)
            return
        }
        if (_messages.value.none { it.role == MessageRole.USER && !it.failed }) return
        sendUserMessage("Review my plan")
    }

    fun editNativePlan(plan: com.buildai.app.builder.NativePlan) {
        check(!_isLoading.value) { "Stop the reply before changing its plan" }
        _brief.value = checkNotNull(_brief.value).editPlan(plan)
        persist()
    }
    fun editNativePlanDraft(text: String) {
        check(!_isLoading.value) { "Stop the reply before editing its plan" }
        _brief.value = checkNotNull(_brief.value).editDraft(text); persist()
    }
    fun acceptSuggestion(id: String, accepted: Boolean) {
        val suggestion = _brief.value?.suggestions?.firstOrNull { it.id == id } ?: return
        _brief.value = _brief.value!!.chooseSuggestion(id, accepted)
        persist()
        sendUserMessage(if (accepted) "Please add this suggestion to my plan: ${suggestion.title}. ${suggestion.benefit}"
            else "Please remove this optional suggestion from my plan: ${suggestion.title}")
    }
    fun applySuggestions(choices: Map<String, Boolean>) {
        check(!_isLoading.value) { "Wait for the reply before updating suggestions" }
        var next = _brief.value ?: return
        val changed = next.suggestions.filter { choices[it.id] != null && choices[it.id] != it.accepted }
        if (changed.isEmpty()) return
        changed.forEach { next = next.chooseSuggestion(it.id, checkNotNull(choices[it.id])) }
        _brief.value = next; persist()
        sendUserMessage(changed.joinToString("\n", "Update my plan with these optional choices:\n") {
            "${if (choices[it.id] == true) "Add" else "Remove"}: ${it.title}. ${it.benefit}"
        })
    }
    fun approveNativePlan(): com.buildai.app.builder.ProjectBrief {
        check(!_isLoading.value)
        val approved = checkNotNull(_brief.value).approve()
        _brief.value = approved; persist(); return approved
    }

    private fun sendCreate(text: String, requestPlan: Boolean = false) {
        val user = Message(role = MessageRole.USER, content = text)
        val input = runCatching { checkNotNull(_brief.value).addInput(user.id, text) }.getOrElse { _error.value = it.message; return }
        _brief.value = input
        _messages.value = _messages.value.map { if (it.failed) it.copy(failed = false) else it } + user
        lastUserText = text; _phase.value = ConversationPhase.CLARIFYING; _error.value = null
        persist()
        val model = runCatching { com.buildai.app.builder.BuilderModel.selected(app) }.getOrElse {
            _messages.value = _messages.value.map { if (it.id == user.id) it.copy(failed = true) else it }
            _error.value = it.message; persist(); return
        }
        val pending = Message(role = MessageRole.ASSISTANT, content = "Reading your requirements…")
        val jobId = java.util.UUID.randomUUID().toString(); activeJobId = jobId
        jobs.save(com.buildai.app.data.storage.StoredAgentJob(jobId, checkNotNull(currentId), "CHAT", "RUNNING",
            JSONObject().put("brief", input.json()).put("model", model.json()).put("requestPlan", requestPlan).toString(), "Reading requirements"))
        _isLoading.value = true; partialMessageId = pending.id; partialHasText = false
        _messages.value += pending; persist()
        try { ExecutionService.keepRunning(app) } catch (e: Exception) {
            _isLoading.value = false; _messages.value = _messages.value.filterNot { it.id == pending.id }
            _error.value = "Android could not start background work. Keep BuildAI open and retry."
            jobs.load(jobId)?.let { jobs.save(it.copy(status = "FAILED", error = _error.value)) }; persist(); return
        }
        requestJob = requestScope.launch {
            try {
                val adapter = com.buildai.app.builder.BuilderModels(app, gemini.api).adapter(model)
                var failure: String? = null
                var failedReply: String? = null
                val rejectedReplies = mutableListOf<String>()
                var output: com.buildai.app.builder.PlanningTurn? = null
                for (attempt in 0..2) {
                    var raw = ""; var savedAt = 0L
                    val prompt = com.buildai.app.builder.BuilderPrompts.planning +
                        (failure?.let { "\nRepair the structured reply. Validation failed: $it" } ?: "")
                    adapter.reply(model.path?.let { java.io.File(it) } ?: java.io.File(app.filesDir, "cloud-adapter"), prompt,
                        listOf(Message(role = MessageRole.USER, content = com.buildai.app.builder.BuilderPrompts.briefContext(input, requestPlan, failure, failedReply))),
                        maxTokens = 1600, contextSize = 4096, grammar = com.buildai.app.builder.PlanningGrammar.forBrief(input),
                        onStatus = { status -> requestScope.launch { jobs.load(jobId)?.takeIf { it.status == "RUNNING" }?.let { jobs.save(it.copy(stage = status)) } } },
                        onProgress = { event -> requestScope.launch {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - savedAt >= 500) {
                                savedAt = now
                                jobs.load(jobId)?.takeIf { it.status == "RUNNING" }?.let { jobs.save(it.copy(progressJson = JSONObject().put("stage", event.stage.name).put("completed", event.completed).put("total", event.total).toString())) }
                            }
                        } }).collect { chunk ->
                        raw += chunk; require(raw.length <= 65_536)
                        // Structured fragments are recovery evidence, not a user-facing plan.
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastPartialSave >= 500) {
                            jobs.load(jobId)?.let { jobs.save(it.copy(checkpoint = raw)) }; lastPartialSave = now
                        }
                    }
                    val parsed = runCatching { com.buildai.app.builder.BriefPlanner.apply(raw, input, requestPlan) }
                    output = parsed.getOrNull()
                    if (output != null) break
                    failure = parsed.exceptionOrNull()?.message ?: "Incomplete plan"
                    if (rejectedReplies.any { previous -> runCatching { com.buildai.app.builder.sameJson(com.buildai.app.builder.strictObject(previous), com.buildai.app.builder.strictObject(raw)) }.getOrDefault(previous.trim() == raw.trim()) }) {
                        failure = "The model repeated the same unsuccessful plan. $failure"
                        break
                    }
                    rejectedReplies += raw
                    failedReply = raw
                    jobs.load(jobId)?.let { jobs.save(it.copy(stage = if (attempt < 2) "Repairing the plan (${attempt + 1} of 2)" else "Plan checks failed", checkpoint = raw)) }
                }
                val response = output ?: error("The model could not finish a usable plan. Your idea, answers and appearance are saved. Change the model in Settings or revise the idea, then retry. Details: $failure")
                check(_brief.value?.version == input.version) { "Your requirements changed. Review the latest brief." }
                val completed = convRepo.completePlanningReply(checkNotNull(currentId), jobId, input.version, response.brief, pending.id,
                    Message(role = MessageRole.ASSISTANT, content = response.reply), if (model.backend == "local") "Local GGUF" else model.model)
                _messages.value = completed.messages
                _phase.value = ConversationPhase.valueOf(completed.phase)
                _activeModel.value = completed.modelLastUsed
                _brief.value = completed.brief
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                _error.value = e.message
                _messages.value = _messages.value.filterNot { it.id == pending.id }.map { if (it.id == user.id) it.copy(failed = true) else it }
                jobs.load(jobId)?.let { jobs.save(it.copy(status = if (e.message.orEmpty().contains("interrupted", true)) "INTERRUPTED" else "FAILED", error = e.message)) }
            } finally {
                if (activeJobId == jobId) {
                    partialMessageId = null; _isLoading.value = false; persist()
                    if (_error.value == null && _queuedInputs.value.isNotEmpty()) {
                        val next = _queuedInputs.value.first(); _queuedInputs.value = _queuedInputs.value.drop(1); persist(); sendUserMessage(next)
                    }
                }
            }
        }
    }

    /** Reopen the same project, keeping its design and working generated app. */
    fun resumePlanning() {
        if (_isLoading.value) stopReply()
        _phase.value = ConversationPhase.CLARIFYING
        _error.value = null
        persist()
    }

    fun chooseStarter(template: com.buildai.app.data.model.QuickTemplate) {
        val b = checkNotNull(_brief.value); val p = checkNotNull(b.plan)
        check(b.starterOption == template.name) { "The assistant has not recommended this starter" }
        _projectPlan.value = ProjectPlan(p.name, b.goal, AppType.APP, template.features, listOf("Home"), "You", Complexity.SIMPLE, "", com.buildai.app.data.model.BuildMode.TEMPLATE, template)
        _phase.value = ConversationPhase.PLAN_READY; persist()
    }

    fun updatePlan(plan: ProjectPlan) {
        require(plan.appName.isNotBlank() && plan.description.isNotBlank() && plan.features.isNotEmpty() && plan.screens.isNotEmpty())
        _projectPlan.value = plan
        persist()
    }

    fun revisePlan(feedback: String) {
        resumePlanning()
        sendUserMessage(feedback.ifBlank { "I'd like to change a few things about the plan." })
    }

    // ── Export ────────────────────────────────────────────────────────────────

    fun getExportText(): String =
        currentId?.let { convRepo.load(it) }
            ?.let { convRepo.buildExportText(it) } ?: ""

    // ── Misc ──────────────────────────────────────────────────────────────────

    fun clearError()        { _error.value = null }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun handleResponse(raw: String, modelUsed: String, allowPlan: Boolean = true) {
        // Chat may discuss XML or code containing plan tags. Only Create owns plans.
        val hasPlanBlock = raw.contains("<plan>") && raw.contains("</plan>")
        val display = if (_mode.value == com.buildai.app.data.model.AssistantMode.CHAT) raw else if (raw.contains("<plan>") && !hasPlanBlock && allowPlan) {
            "I could not finish the structured plan. Here is an editable draft from your answers."
        } else if (raw.contains("<plan>") && !allowPlan) {
            "What should you be able to do in this app, and what information should it remember?"
        } else if (hasPlanBlock) {
            raw.replace(Regex("<plan>[\\s\\S]*?</plan>"), "").trim()
                .ifBlank { "Here's your project plan! 🎉" }
        } else raw

        _messages.value = _messages.value +
            Message(role = MessageRole.ASSISTANT, content = display)

        if (hasPlanBlock && allowPlan) {
            val plan = tryParsePlan(raw)
            if (plan != null) {
                _projectPlan.value = plan
                _phase.value       = ConversationPhase.PLAN_READY
            }
        }
        persist(modelUsed)
    }

    private fun persist(modelUsed: String? = _activeModel.value) {
        val id = currentId ?: return
        convRepo.update(id) { base ->
            base.copy(
                messages      = _messages.value,
                projectPlan   = _projectPlan.value,
                phase         = _phase.value.name,
                updatedAt     = System.currentTimeMillis(),
                modelLastUsed = modelUsed,
                draftText = _draftText.value,
                queuedInputs = _queuedInputs.value,
                brief = _brief.value,
                replyStatus = if (_isLoading.value) "RUNNING" else if (_error.value != null) "INTERRUPTED" else "IDLE"
            ).withDerivedTitle()
        }
    }

    private fun isConfirmation(text: String): Boolean {
        val cleaned = text.lowercase().trim().trimEnd('!', '.', '?', ' ')
        return cleaned in setOf(
            "go", "yes", "ok", "okay", "looks good", "perfect", "great",
            "sure", "yep", "yeah", "sounds good", "alright", "build it",
            "do it", "let's go", "lets go", "good", "correct", "right",
            "exactly", "confirmed", "confirm", "proceed", "continue"
        )
    }

    private fun tryParsePlan(text: String): ProjectPlan? = runCatching { ProjectPlan.fromResponse(text) }.getOrNull()
}
