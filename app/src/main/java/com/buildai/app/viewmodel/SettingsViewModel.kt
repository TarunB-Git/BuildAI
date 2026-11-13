package com.buildai.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.buildai.app.data.local.LocalModelStore
import com.buildai.app.data.local.LocalLlmEngine
import com.buildai.app.data.model.Message
import com.buildai.app.data.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import android.os.SystemClock
import com.buildai.app.data.local.ModelCatalog
import com.buildai.app.data.local.ModelDownloadWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import android.app.ActivityManager
import android.content.Context
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.AndroidViewModel
import com.buildai.app.data.storage.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class ModelDownloadStatus {
    data object Idle : ModelDownloadStatus()
    data class Running(val modelId: String, val bytes: Long, val total: Long) : ModelDownloadStatus()
    data class Failed(val message: String) : ModelDownloadStatus()
    data object Complete : ModelDownloadStatus()
}

sealed class LocalModelCheck {
    data object Idle : LocalModelCheck()
    data class Running(val status: String) : LocalModelCheck()
    data class Passed(val reply: String, val elapsedMs: Long) : LocalModelCheck()
    data class Failed(val message: String) : LocalModelCheck()
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = SecureStorage(app)
    private val modelStore = LocalModelStore(app)
    private val _modelCheck = MutableStateFlow<LocalModelCheck>(LocalModelCheck.Idle)
    val modelCheck: StateFlow<LocalModelCheck> = _modelCheck.asStateFlow()
    private var checkJob: Job? = null
    private val workManager = WorkManager.getInstance(app)
    private val downloadPrefs = app.getSharedPreferences("model_download", Context.MODE_PRIVATE)
    val downloadableModels = ModelCatalog.models
    val phoneRamGb: Int = ((app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .let { manager -> ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem }
        / 1_000_000_000.0).roundToInt()
    private val _downloadStatus = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.Idle)
    val downloadStatus: StateFlow<ModelDownloadStatus> = _downloadStatus.asStateFlow()

    private val _installed = MutableStateFlow<List<com.buildai.app.data.local.InstalledModel>>(emptyList())
    val installed = _installed.asStateFlow()
    private val _discovered = MutableStateFlow<List<com.buildai.app.data.local.DiscoveredModel>>(emptyList())
    val discovered = _discovered.asStateFlow()
    private val _fallback = MutableStateFlow(storage.generationFallback())
    val fallback = _fallback.asStateFlow()
    private val _cloudProvider = MutableStateFlow(storage.provider())
    val cloudProvider = _cloudProvider.asStateFlow()
    private val _selectedLocal = MutableStateFlow(modelStore.selectedName())
    val selectedLocal = _selectedLocal.asStateFlow()
    fun selectedFileName() = modelStore.selectedName()
    fun refreshModels() { viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { _installed.value = modelStore.installed(); _discovered.value = modelStore.discovered(); _localModelName.value = modelStore.displayName(); _selectedLocal.value = modelStore.selectedName() } }
    fun selectLocalModel(name: String) { stopModelCheck(); modelStore.select(name); _localModelName.value = modelStore.displayName(); _selectedLocal.value = modelStore.selectedName() }
    fun removeModel(name: String) { stopModelCheck(); runCatching { modelStore.remove(name) }.onFailure { _modelError.value = it.message }; refreshModels() }
    fun setFallback(enabled: Boolean) { storage.setGenerationFallback(enabled); _fallback.value = enabled }
    private val draftKeys = mutableMapOf<com.buildai.app.data.api.CloudProvider, String>()
    private val draftModels = mutableMapOf<com.buildai.app.data.api.CloudProvider, String>()
    fun editProviderKey(provider: com.buildai.app.data.api.CloudProvider, key: String) { draftKeys[provider] = key }
    fun editProviderModel(provider: com.buildai.app.data.api.CloudProvider, model: String) { draftModels[provider] = model }
    fun providerDirty() = draftKeys.any { (p, key) -> key.trim() != providerKey(p) } || draftModels.any { (p, model) -> model.trim() != providerModel(p) }
    fun providerDraftError(): String? = com.buildai.app.data.api.CloudProvider.entries.firstNotNullOfOrNull { p ->
        if (p !in draftKeys && p !in draftModels) null
        else when {
            draftProviderKey(p).isBlank() -> "Enter a ${p.title} key, or discard the changes."
            draftProviderModel(p).isBlank() -> "Enter a model ID for ${p.title}."
            else -> null
        }
    }
    fun saveProviderDrafts() {
        require(providerDraftError() == null) { providerDraftError().orEmpty() }
        com.buildai.app.data.api.CloudProvider.entries.forEach { p -> if (p in draftKeys || p in draftModels) saveProvider(p, draftProviderKey(p), draftProviderModel(p)) }
    }
    fun discardProviderDrafts() { draftKeys.clear(); draftModels.clear() }
    fun draftProviderKey(provider: com.buildai.app.data.api.CloudProvider) = draftKeys[provider] ?: providerKey(provider)
    fun draftProviderModel(provider: com.buildai.app.data.api.CloudProvider) = draftModels[provider] ?: providerModel(provider)
    fun providerKey(provider: com.buildai.app.data.api.CloudProvider) = storage.getApiKey(provider).orEmpty()
    fun providerFallbacks(provider: com.buildai.app.data.api.CloudProvider) = storage.fallbackModels(provider).joinToString(", ")
    fun saveFallbacks(provider: com.buildai.app.data.api.CloudProvider, models: String) = storage.saveFallbackModels(provider, models)
    fun providerModel(provider: com.buildai.app.data.api.CloudProvider) = storage.getModel(provider)
    fun saveProvider(provider: com.buildai.app.data.api.CloudProvider, key: String, model: String) {
        require(key.isNotBlank()) { "Enter a ${provider.title} key." }; require(model.isNotBlank()) { "Enter a model ID for ${provider.title}." }
        storage.saveApiKey(key.trim(), provider); storage.saveModel(model.trim(), provider); draftKeys.remove(provider); draftModels.remove(provider)
        _saveSuccess.value = true; if (provider == storage.provider()) { _apiKey.value = key.trim(); _selectedModel.value = model.trim() }
    }
    fun useProvider(provider: com.buildai.app.data.api.CloudProvider) {
        saveProvider(provider, draftProviderKey(provider), draftProviderModel(provider))
        storage.selectProvider(provider); _cloudProvider.value = provider; _apiKey.value = storage.getApiKey().orEmpty(); _selectedModel.value = storage.getModel(); selectChatBackend("cloud")
    }
    fun clearProvider(provider: com.buildai.app.data.api.CloudProvider) {
        storage.clearApiKey(provider); draftKeys.remove(provider); draftModels.remove(provider)
        if (provider == storage.provider()) _apiKey.value = ""
    }

    private val _chatBackend = MutableStateFlow(storage.getChatBackend())
    val chatBackend: StateFlow<String> = _chatBackend.asStateFlow()
    private val _localModelName = MutableStateFlow(modelStore.displayName())
    val localModelName: StateFlow<String?> = _localModelName.asStateFlow()
    private val _modelImporting = MutableStateFlow(false)
    val modelImporting: StateFlow<Boolean> = _modelImporting.asStateFlow()
    private val _modelError = MutableStateFlow<String?>(null)
    val modelError: StateFlow<String?> = _modelError.asStateFlow()

    private val _apiKey        = MutableStateFlow(storage.getApiKey() ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(storage.getModel())
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _saveSuccess   = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    init {
        refreshModels()
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME).collectLatest { infos ->
                val requestId = downloadPrefs.getString("request_id", null)
                val info = infos.firstOrNull { it.id.toString() == requestId } ?: return@collectLatest
                _downloadStatus.value = when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                        ModelDownloadStatus.Running(downloadPrefs.getString("model_id", null).orEmpty(), 0, 0)
                    WorkInfo.State.RUNNING -> ModelDownloadStatus.Running(
                        downloadPrefs.getString("model_id", null).orEmpty(),
                        info.progress.getLong(ModelDownloadWorker.BYTES, 0),
                        info.progress.getLong(ModelDownloadWorker.TOTAL, 0))
                    WorkInfo.State.SUCCEEDED -> {
                        _localModelName.value = modelStore.displayName()
                        refreshModels()
                        ModelDownloadStatus.Complete
                    }
                    WorkInfo.State.FAILED -> ModelDownloadStatus.Failed(
                        info.outputData.getString(ModelDownloadWorker.ERROR) ?: "Download failed. Please retry.")
                    WorkInfo.State.CANCELLED -> ModelDownloadStatus.Idle
                }
            }
        }
    }

    fun downloadModel(modelId: String) {
        stopModelCheck()
        _modelError.value = null
        _downloadStatus.value = ModelDownloadStatus.Running(modelId, 0, 0)
        ModelDownloadWorker.enqueue(getApplication(), modelId)
    }
    fun cancelModelDownload() { workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME) }

    fun testLocalModel() {
        if (checkJob?.isActive == true) return
        val file = modelStore.selectedFile() ?: return
        _modelCheck.value = LocalModelCheck.Running("Starting model check…")
        checkJob = viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val runningJob = coroutineContext[Job]
            var engine: LocalLlmEngine? = null
            try {
                val local = LocalLlmEngine(getApplication())
                engine = local
                val output = StringBuilder()
                withTimeout(90_000) {
                    local.reply(file, "Answer in one short sentence.",
                        listOf(Message(role = MessageRole.USER, content = "What is 2 plus 2?")),
                        maxTokens = 32, contextSize = 1024,
                        onStatus = { if (runningJob?.isActive == true) _modelCheck.value = LocalModelCheck.Running(it) }
                    ).collect { output.append(it) }
                }
                _modelCheck.value = LocalModelCheck.Passed(output.toString().trim().take(180),
                    SystemClock.elapsedRealtime() - started)
            } catch (error: TimeoutCancellationException) {
                _modelCheck.value = LocalModelCheck.Failed("Model check timed out. Try a smaller model; the CPU request has been stopped.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _modelCheck.value = LocalModelCheck.Failed(error.message ?: "The local model could not reply.")
            } finally {
                engine?.unload()
            }
        }
    }

    fun stopModelCheck() {
        checkJob?.cancel()
        _modelCheck.value = LocalModelCheck.Idle
    }


    // Quotas and availability depend on the Google account, so do not hard-code promises.
    val availableModels = listOf(
        "gemma-4-31b-it"        to "Gemma 4 31B",
        "gemma-4-26b-a4b-it"   to "Gemma 4 26B MoE",
        "gemini-2.5-flash-lite" to "Gemini 2.5 Flash-Lite",
        "gemini-2.5-flash"      to "Gemini 2.5 Flash"
    )

    fun selectChatBackend(backend: String) {
        storage.saveChatBackend(backend)
        _chatBackend.value = backend
    }
    fun importModel(uri: Uri) {
        stopModelCheck()
        viewModelScope.launch {
            _modelImporting.value = true
            _modelError.value = null
            try {
                _localModelName.value = modelStore.import(uri)
                refreshModels()
                downloadPrefs.edit().remove("request_id").apply()
                _downloadStatus.value = ModelDownloadStatus.Idle
            } catch (e: Exception) {
                _modelError.value = e.message ?: "Could not import this GGUF model."
            } finally {
                _modelImporting.value = false
            }
        }
    }
    fun removeLocalModel() {
        stopModelCheck()
        modelStore.remove()
        downloadPrefs.edit().remove("request_id").apply()
        _downloadStatus.value = ModelDownloadStatus.Idle
        _localModelName.value = null
        refreshModels()
    }

    fun updateApiKey(key: String)  { _apiKey.value = key }
    fun selectModel(model: String) { _selectedModel.value = model; storage.saveModel(model) }
    fun saveSettings()             { storage.saveApiKey(_apiKey.value); _saveSuccess.value = true }
    fun clearSaveSuccess()         { _saveSuccess.value = false }
    fun clearApiKey()              { clearProvider(storage.provider()) }
    fun hasUnsavedKey() = _apiKey.value.trim() != storage.getApiKey().orEmpty()
    fun discardKeyChanges() { _apiKey.value = storage.getApiKey().orEmpty() }
    fun hasApiKey(): Boolean       = storage.hasApiKey()
}
