package com.buildai.app.data.api

import com.buildai.app.data.model.Message
import com.buildai.app.data.model.MessageRole
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class CloudProvider(val id: String, val title: String, val endpoint: String, val defaultModel: String, val keyUrl: String) {
    GEMINI("gemini", "Gemini", "https://generativelanguage.googleapis.com/", "gemma-4-31b-it", "https://aistudio.google.com/app/apikey"),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "openrouter/auto", "https://openrouter.ai/settings/keys"),
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1/responses", "gpt-5.5", "https://platform.openai.com/api-keys"),
    CLAUDE("claude", "Claude", "https://api.anthropic.com/v1/messages", "claude-sonnet-5-5", "https://platform.claude.com/settings/keys");
    companion object { fun read(id: String?) = entries.firstOrNull { it.id == id } ?: GEMINI }
}

/** Provider-specific protocols. Cancellation closes the HTTP call, including while awaiting a token. */
class CloudProviderApi(private val gemini: GeminiApi = GeminiApi(),
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).callTimeout(240, TimeUnit.SECONDS).build(),
    private val testEndpoint: HttpUrl? = null) {
    fun stream(provider: CloudProvider, model: String, key: String, messages: List<Message>, prompt: String,
        maxTokens: Int = 2048, jsonMode: Boolean = false, beforeRequest: () -> Unit = {}): Flow<String> {
        if (provider == CloudProvider.GEMINI) return gemini.stream(model, key, JSONArray(messages.map { m ->
            JSONObject().put("role", if (m.role == MessageRole.USER) "user" else "model").put("parts", JSONArray().put(JSONObject().put("text", m.content)))
        }), prompt, maxTokens = maxTokens, jsonMode = jsonMode, beforeRequest = beforeRequest)
        return flow {
            beforeRequest()
            val history = JSONArray(messages.map { JSONObject().put("role", if (it.role == MessageRole.USER) "user" else "assistant").put("content", it.content) })
            val body = JSONObject().put("model", model).put("stream", true)
            when (provider) {
                CloudProvider.OPENAI -> body.put("input", history).put("instructions", prompt).put("max_output_tokens", maxTokens).put("store", false)
                CloudProvider.CLAUDE -> body.put("messages", history).put("system", prompt).put("max_tokens", maxTokens)
                else -> { val input = JSONArray().put(JSONObject().put("role", "system").put("content", prompt)); for (i in 0 until history.length()) input.put(history.get(i)); body.put("messages", input).put("max_tokens", maxTokens) }
            }
            val request = Request.Builder().url(testEndpoint ?: provider.endpoint.toHttpUrl()).header("Accept", "text/event-stream")
            if (provider == CloudProvider.CLAUDE) request.header("x-api-key", key).header("anthropic-version", "2023-06-01")
            else request.header("Authorization", "Bearer $key")
            val call = client.newCall(request.post(body.toString().toRequestBody("application/json".toMediaType())).build())
            coroutineScope {
                val cancel = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { call.cancel() } }
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw GeminiApiException(response.code, "${provider.title} request failed (${response.code}). ${if (response.code in listOf(401, 403)) "Check your saved key and access." else "Check the model ID or retry."}")
                        val source = checkNotNull(response.body).source(); val event = StringBuilder(); var completed = false; var received = false
                        suspend fun consume() {
                            if (event.isEmpty()) return
                            val data = event.toString(); event.clear()
                            if (data == "[DONE]") { completed = true; return }
                            val json = JSONObject(data)
                            if (json.has("error") || json.optString("type") in listOf("error", "response.failed")) throw IOException("${provider.title} could not complete the reply. Retry or check the model and key.")
                            val delta = when (provider) {
                                CloudProvider.OPENAI -> if (json.optString("type") == "response.output_text.delta") json.optString("delta") else ""
                                CloudProvider.CLAUDE -> json.optJSONObject("delta")?.takeIf { it.optString("type") == "text_delta" }?.optString("text").orEmpty()
                                else -> json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                            }
                            if (delta.isNotEmpty()) { received = true; beforeRequest(); emit(delta) }
                            if (json.optString("type") in listOf("response.completed", "message_stop") || json.optJSONArray("choices")?.optJSONObject(0)?.optString("finish_reason")?.let { it.isNotBlank() && it != "null" } == true) completed = true
                            if (json.optString("type") == "response.incomplete") throw IOException("${provider.title} reached its output limit. Ask for a shorter response.")
                        }
                        while (!source.exhausted()) { currentCoroutineContext().ensureActive(); val line = source.readUtf8Line() ?: break
                            if (line.isEmpty()) { consume(); if (completed) break } else if (line.startsWith("data:")) { if (event.isNotEmpty()) event.append('\n'); event.append(line.removePrefix("data:").trimStart()) }
                        }
                        consume()
                        if (!received || !completed) throw IOException("${provider.title} reply was interrupted. Retry to continue.")
                    }
                } catch (error: IOException) { currentCoroutineContext().ensureActive(); throw error } finally { cancel.cancel() }
            }
        }.flowOn(Dispatchers.IO)
    }
}
