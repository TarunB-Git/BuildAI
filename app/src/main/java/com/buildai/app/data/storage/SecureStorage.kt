package com.buildai.app.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "buildai_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ── API key ──────────────────────────────────────────────────────────────

    fun provider() = com.buildai.app.data.api.CloudProvider.read(prefs.getString("cloud_provider", "gemini"))
    fun selectProvider(provider: com.buildai.app.data.api.CloudProvider) { prefs.edit().putString("cloud_provider", provider.id).apply() }
    private fun keyName(provider: com.buildai.app.data.api.CloudProvider) = if (provider.id == "gemini") KEY_API else "${provider.id}_api_key"
    private fun modelName(provider: com.buildai.app.data.api.CloudProvider) = if (provider.id == "gemini") KEY_MODEL else "${provider.id}_model"
    fun saveApiKey(key: String, provider: com.buildai.app.data.api.CloudProvider = provider()) = prefs.edit().putString(keyName(provider), key.trim()).apply()
    fun getApiKey(provider: com.buildai.app.data.api.CloudProvider = provider()): String? = prefs.getString(keyName(provider), null)?.takeIf { it.isNotBlank() }
    fun clearApiKey(provider: com.buildai.app.data.api.CloudProvider = provider()) = prefs.edit().remove(keyName(provider)).apply()
    fun hasApiKey(provider: com.buildai.app.data.api.CloudProvider = provider()): Boolean = getApiKey(provider) != null
    fun saveModel(model: String, provider: com.buildai.app.data.api.CloudProvider = provider()) = prefs.edit().putString(modelName(provider), model.trim()).apply()
    fun getModel(provider: com.buildai.app.data.api.CloudProvider = provider()): String = prefs.getString(modelName(provider), provider.defaultModel) ?: provider.defaultModel
    fun fallbackModels(provider: com.buildai.app.data.api.CloudProvider = provider()): List<String> = prefs.getString("${provider.id}_fallback_models", when(provider) {
        com.buildai.app.data.api.CloudProvider.GEMINI -> "gemini-2.5-flash-lite,gemini-2.5-flash,gemma-4-26b-a4b-it"
        com.buildai.app.data.api.CloudProvider.OPENAI -> "gpt-5.5"
        com.buildai.app.data.api.CloudProvider.CLAUDE -> "claude-haiku-4-5"
        else -> ""
    }).orEmpty().split(',').map(String::trim).filter(String::isNotBlank).distinct().take(8)
    fun saveFallbackModels(provider: com.buildai.app.data.api.CloudProvider, models: String) { prefs.edit().putString("${provider.id}_fallback_models", models.take(1000)).apply() }
    fun generationFallback() = prefs.getBoolean("generation_fallback", true)
    fun setGenerationFallback(on: Boolean) { prefs.edit().putBoolean("generation_fallback", on).apply() }

    fun saveChatBackend(backend: String) {
        require(backend == "cloud" || backend == "local")
        prefs.edit().putString(KEY_CHAT_BACKEND, backend).apply()
    }
    fun getChatBackend(): String = prefs.getString(KEY_CHAT_BACKEND, "local") ?: "local"

    companion object {
        private const val KEY_CHAT_BACKEND = "chat_backend"
        private const val KEY_API   = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"
        // Keep the saved preference compatible with existing installations.
        const val DEFAULT_MODEL = "gemma-4-31b-it"
    }
}
