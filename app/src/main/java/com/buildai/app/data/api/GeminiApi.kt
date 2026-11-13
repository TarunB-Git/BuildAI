package com.buildai.app.data.api

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import com.buildai.app.data.local.InferenceProgress
import com.buildai.app.data.local.InferenceStage
import com.buildai.app.data.local.InferenceWatchdog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GeminiApiException(val status: Int, message: String) : IOException(message)

/** The endpoint is injectable for tests; production always uses Google's HTTPS API. */
class GeminiApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(200, TimeUnit.SECONDS).build(),
    private val endpoint: HttpUrl = "https://generativelanguage.googleapis.com/".toHttpUrl()
) {
    companion object { private val cloudSlots = Semaphore(2) }
    /** Only answer text is productive; SSE comments/keepalives never reset the clock. */
    fun stream(
        model: String, apiKey: String, contents: JSONArray,
        systemPrompt: String? = null, temperature: Double = 0.7, maxTokens: Int = 2048, jsonMode: Boolean = false, beforeRequest: () -> Unit = {}
    ): Flow<String> = channelFlow {
        cloudSlots.acquire()
        try {
            beforeRequest() // Recheck policy after waiting for a cloud slot, before constructing any request.
            val body = requestBody(model, contents, systemPrompt, temperature, maxTokens)
            if (jsonMode) body.getJSONObject("generationConfig").put("responseMimeType", "application/json")
            val url = endpoint.newBuilder().addPathSegments("v1beta/models")
                .addPathSegment("$model:streamGenerateContent").addQueryParameter("alt", "sse").build()
            val call = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(url).header("x-goog-api-key", apiKey)
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build())
            coroutineScope {
                val watchdog = InferenceWatchdog(clock = { System.nanoTime() / 1_000_000 })
                watchdog.record(InferenceProgress(InferenceStage.PREFILL, 0))
                val closeOnCancel = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { call.cancel() }
                }
                val monitor = launch {
                    while (isActive) {
                        delay(1_000)
                        if (watchdog.stalledStage() != null)
                            throw IOException("Connected inference stopped making progress. Retry the interrupted reply.")
                    }
                }
                try {
                    withContext(Dispatchers.IO) {
                        call.awaitResponse().use { response ->
                            checkStatus(response)
                            val source = checkNotNull(response.body) { "The AI service returned no answer." }.source()
                            var event = StringBuilder()
                            var total = 0L
                            var finished = false
                            suspend fun consumeEvent() {
                                if (event.isEmpty()) return
                                val json = JSONObject(event.toString())
                                event = StringBuilder()
                                val candidate = json.optJSONArray("candidates")?.optJSONObject(0) ?: return
                                val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                                if (parts != null) for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)
                                    val text = if (part.optBoolean("thought", false)) "" else part.optString("text")
                                    if (text.isNotEmpty()) {
                                        total += text.length
                                        check(total <= 131_072) { "The AI response exceeded its safe size. Try a smaller request." }
                                        watchdog.record(InferenceProgress(InferenceStage.DECODING, total))
                                        send(text)
                                    }
                                }
                                val finish = candidate.optString("finishReason")
                                if (finish.isNotBlank()) { checkFinish(finish); finished = true }
                            }
                            while (!finished) {
                                currentCoroutineContext().ensureActive()
                                if (source.exhausted()) break
                                val line = source.readUtf8LineStrict(131_072)
                                if (line.isEmpty()) consumeEvent()
                                else if (line.startsWith("data:")) {
                                    if (event.isNotEmpty()) event.append('\n')
                                    event.append(line.removePrefix("data:").trimStart())
                                    check(event.length <= 131_072) { "The AI event exceeded its safe size." }
                                }
                            }
                            consumeEvent()
                            check(finished && total > 0) { "The AI stream ended before completing its answer. Retry the interrupted reply." }
                        }
                    }
                } catch (error: IOException) {
                    // Closing a blocked read is how Stop interrupts the socket. Preserve
                    // coroutine cancellation instead of reporting that close as a failure.
                    currentCoroutineContext().ensureActive()
                    throw error
                } finally { closeOnCancel.cancel(); monitor.cancel(); call.cancel() }
            }
        } finally { cloudSlots.release() }
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Couldn't reach the AI service. Check your connection and try again.", e))
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    private fun requestBody(model: String, contents: JSONArray, prompt: String?, temperature: Double, maxTokens: Int): JSONObject {
        val config = JSONObject().put("temperature", temperature).put("maxOutputTokens", maxTokens)
        val thinking = when {
            model.startsWith("gemma-4") -> JSONObject().put("thinkingLevel", "minimal")
            model.startsWith("gemini-2.5") -> JSONObject().put("thinkingBudget", 0)
            else -> null
        }
        thinking?.let { config.put("thinkingConfig", it) }
        return JSONObject().put("contents", contents).put("generationConfig", config).also { body ->
            prompt?.let { body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", it)))) }
        }
    }
    private fun checkFinish(finish: String) {
        check(finish.isBlank() || finish == "STOP") {
            if (finish == "MAX_TOKENS") "The AI response was cut short. Try a simpler app or another model."
            else "The AI service couldn't complete this answer. Try again."
        }
    }
    private fun checkStatus(response: Response) {
        if (!response.isSuccessful) throw GeminiApiException(response.code, when (response.code) {
            400 -> "The AI service couldn't accept this request. Check the selected model."
            401, 403 -> "The API key was rejected. Check Settings."
            404 -> "This model isn't available. Choose another in Settings."
            429 -> "The AI service limit was reached. Wait a moment and try again."
            else -> "The AI service is unavailable (${response.code}). Try again."
        })
    }
    suspend fun generate(
        model: String, apiKey: String, contents: JSONArray,
        systemPrompt: String? = null, temperature: Double = 0.3, maxTokens: Int = 8192
    ): String {
        cloudSlots.acquire()
        try {
        val config = JSONObject().put("temperature", temperature).put("maxOutputTokens", maxTokens)
        val thinking = when {
            model.startsWith("gemma-4") -> JSONObject().put("thinkingLevel", "minimal")
            model.startsWith("gemini-2.5") -> JSONObject().put("thinkingBudget", 0)
            else -> null
        }
        thinking?.let { config.put("thinkingConfig", it) }
        val body = JSONObject().put("contents", contents).put("generationConfig", config)
        systemPrompt?.let {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", it))))
        }
        val url = endpoint.newBuilder().addPathSegments("v1beta/models")
            .addPathSegment("$model:generateContent").build()
        val call = client.newCall(Request.Builder().url(url)
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(
                        IOException("Couldn't reach the AI service. Check your connection and try again.", e))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw GeminiApiException(it.code, when (it.code) {
                                400 -> "The AI service couldn't accept this request. Check the selected model."
                                401, 403 -> "The API key was rejected. Check Settings."
                                404 -> "This model isn't available. Choose another in Settings."
                                429 -> "The AI service limit was reached. Wait a moment and try again."
                                else -> "The AI service is unavailable (${it.code}). Try again."
                            })
                            val json = JSONObject(it.body?.string() ?: error("Empty AI response"))
                            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
                                ?: error("The AI service returned no answer. Try again.")
                            val finish = candidate.optString("finishReason")
                            check(finish.isBlank() || finish == "STOP") {
                                if (finish == "MAX_TOKENS") "The AI response was cut short. Try a simpler app or another model."
                                else "The AI service couldn't complete this answer. Try again."
                            }
                            val parts = candidate.getJSONObject("content").getJSONArray("parts")
                            (0 until parts.length()).map { parts.getJSONObject(it) }
                                .filterNot { it.optBoolean("thought", false) }
                                .joinToString("") { it.optString("text", "") }
                                .also { text -> check(text.isNotBlank()) { "The AI service returned an empty answer. Try again." } }
                        }
                    }
                    if (!continuation.isCancelled) result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }
        } finally { cloudSlots.release() }
    }
}
