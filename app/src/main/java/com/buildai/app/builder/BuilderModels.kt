package com.buildai.app.builder

import android.app.Application
import com.buildai.app.data.api.GeminiApi
import com.buildai.app.data.local.*
import com.buildai.app.data.model.Message
import com.buildai.app.data.model.MessageRole
import com.buildai.app.data.storage.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Credentials are resolved in protected storage at execution time, never saved in job inputs. */
data class BuilderModel(val backend: String, val model: String, val path: String? = null, val provider: String = "gemini", val fallback: Boolean = true, val fallbackModels: List<String> = emptyList()) {
    fun json() = JSONObject().put("backend", backend).put("model", model).put("provider", provider).put("fallback", fallback).put("fallbackModels", JSONArray(fallbackModels)).also { path?.let { p -> it.put("path", p) } }
    companion object {
        fun read(j: JSONObject) = BuilderModel(j.getString("backend"), j.getString("model"), j.optString("path").takeIf(String::isNotBlank), j.optString("provider", "gemini"), j.optBoolean("fallback", true), j.optJSONArray("fallbackModels")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty())
        fun selected(app: Application): BuilderModel {
            val settings = SecureStorage(app)
            return if (settings.getChatBackend() == "local") {
                val file = LocalModelStore(app).selectedFile() ?: error("Choose a GGUF model in Settings first")
                BuilderModel("local", file.name, file.absolutePath)
            } else {
                check(settings.getApiKey() != null) { "Add your cloud AI key in Settings first" }
                BuilderModel("cloud", settings.getModel(), provider = settings.provider().id, fallback = settings.generationFallback(), fallbackModels = settings.fallbackModels())
            }
        }
    }
}
class BuilderModels(private val app: Application, private val api: GeminiApi = GeminiApi()) {
    fun adapter(snapshot: BuilderModel): ModelAdapter = when (snapshot.backend) {
        "local" -> LocalLlmEngine(app)
        "cloud" -> {
            val settings = SecureStorage(app)
            check(settings.getChatBackend() == "cloud") { "Cloud AI is disabled. Explicitly enable it before retrying connected work." }
            CloudModelAdapter(api, snapshot.model, provider = com.buildai.app.data.api.CloudProvider.read(snapshot.provider), fallback = snapshot.fallback, fallbackModels = snapshot.fallbackModels) {
                check(settings.getChatBackend() == "cloud") { "Paused: cloud AI is disabled. Enable it explicitly before retrying." }
                checkNotNull(settings.getApiKey(com.buildai.app.data.api.CloudProvider.read(snapshot.provider))) { "Paused: the cloud AI key is missing. Add it before retrying." }
            }
        }
        else -> error("Unsupported AI backend")
    }
}
class CloudModelAdapter(private val api: GeminiApi, private val model: String, private val provider: com.buildai.app.data.api.CloudProvider = com.buildai.app.data.api.CloudProvider.GEMINI, private val fallback: Boolean = true, private val fallbackModels: List<String> = emptyList(), private val keyProvider: () -> String) : ModelAdapter {
    override val supportsStructuredOutput = true
    override val resourceProfile = ModelResourceProfile(2, "Cloud AI; explicitly enabled")
    override fun reply(modelFile: File, systemPrompt: String, history: List<Message>, maxTokens: Int, contextSize: Int,
        stopAt: String?, grammar: String?, onStatus: (String) -> Unit, onProgress: (InferenceProgress) -> Unit, priority: InferencePriority): Flow<String> {
        val contents = JSONArray(history.map { m -> JSONObject().put("role", if (m.role == MessageRole.USER) "user" else "model")
            .put("parts", JSONArray().put(JSONObject().put("text", m.content))) })
        return flow {
            val key = keyProvider() // Resolve current consent and credentials for every collected tool call.
            onStatus("Waiting for enabled cloud AI")
            var characters = 0L
            val candidates = (listOf(model) + if (fallback) fallbackModels else emptyList()).distinct()
            var last: Exception? = null
            for (candidate in candidates) {
                try {
                    emitAll(com.buildai.app.data.api.CloudProviderApi(api).stream(provider, candidate, key, history, systemPrompt,
                        maxTokens = maxTokens, jsonMode = grammar != null, beforeRequest = {
                            check(keyProvider() == key) { "Paused: cloud credentials changed. Retry from the saved input." }
                        }).onEach { characters += it.length; onProgress(InferenceProgress(InferenceStage.DECODING, characters)) })
                    last = null; break
                } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (error: Exception) {
                    last = error
                    if (characters > 0 || error is com.buildai.app.data.api.GeminiApiException && error.status in listOf(401, 403)) throw error
                }
            }
            last?.let { throw it }
        }
    }

    override suspend fun unload() = Unit
}
object BuilderPrompts {
    // Generic JSON syntax bounds output shape; semantic validation follows separately.
    val JSON_GRAMMAR = """
        root ::= object
        object ::= "{" ws (string ws ":" ws value (ws "," ws string ws ":" ws value)*)? ws "}"
        array ::= "[" ws (value (ws "," ws value)*)? ws "]"
        value ::= object | array | string | number | "true" | "false" | "null"
        string ::= "\"" char* "\""
        char ::= [^"\\\x7F\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4})
        number ::= "-"? ([0-9] | [1-9] [0-9]*) ("." [0-9]+)? ([eE] [+-]? [0-9]+)?
        ws ::= [ \t\n\r]*
    """.trimIndent()
    val planning = """
        You help a beginner describe a personal native phone app. Return only one JSON object.
        Tools available: inspectCapabilities, rememberAnswer, askQuestion, proposePlan, offerSuggestion.
        Supported now: native screens/text/forms, typed saved records, list search, decimal calculations/summaries,
        navigation, timers with saved deadlines, quizzes. Android scheduling/widgets/connections are a later integration step.
        No executable code, web sites, cross-app automation, generic template substitutions or invented capabilities.
        Identical user quotes may share equivalentMessageIds; those IDs refer to the same exact text. evidenceOrder preserves chronology. The final turn is the current input.
        Read saved user evidence and remembered answers. Ask at most TWO relevant unanswered questions; never repeat answered questions.
        Remember an answer as a short direct quote from the current user message, not an invented value or paraphrase.
        Honor a request to ask questions. Offer a few optional useful suggestions with a benefit; do not silently include them.
        When essential decisions are known, automatically propose a feature-specific plan. If an early plan is requested,
        state unresolved decisions as assumptions. Readiness depends on decisions, never a message count or special phrase.
        Shape: {"reply":"friendly concise reply","answers":[{"questionId":"previous question id","messageId":"current user message id","answer":"user's answer"}],
        "questions":[{"id":"stable_id","question":"relevant question","essential":true}],"assumptions":["visible assumption"],
        "suggestions":[{"id":"stable_id","title":"optional feature","benefit":"why helpful"}],
        "plan":{"name":"App name","features":[{"id":"stable_id","title":"Feature","workflow":"specific user workflow","checks":["observable result with example inputs"]}],
        "screens":["screen names"],"data":["what is saved"]},"unsupported":"reason ONLY if unsupported"}.
        Existing offline starter implementations: NOTES (write/edit/delete/search notes), TASKS (add/edit/check tasks), HABITS (habits with daily completion history), EXPENSES (amount/category entries, totals, edit/delete), CALCULATOR (two-number arithmetic), TIMER (duration, start/pause/reset).
        Compare all requested workflows with these implementations. ONLY if a starter covers every requested feature exactly, include "offlineStarter":"one of those IDs" as an optional alternative. Do not replace the custom plan or omit user requirements. Otherwise omit offlineStarter.
        Omit plan while essential answers are missing; omit unsupported for supported requests. Keep output compact.
    """.trimIndent()
    /** Lossless only: consolidate exact repeated quotes, never summarize or truncate unique requirements. */
    private fun evidenceContext(brief: ProjectBrief): JSONObject {
        val groups = brief.turns.withIndex().groupBy { it.value.text }.values.sortedBy { it.last().index }
        val turns = groups.map { group ->
            val current = group.last().value
            JSONObject().put("messageId", current.messageId).put("text", current.text).also { quote ->
                if (group.size > 1) quote.put("equivalentMessageIds", JSONArray(group.dropLast(1).map { it.value.messageId }))
            }
        }
        return brief.json().put("turns", JSONArray(turns)).also { context ->
            if (groups.size < brief.turns.size) context.put("evidenceOrder", JSONArray(brief.turns.map { it.messageId }))
        }
    }
    fun briefContext(brief: ProjectBrief, requestPlan: Boolean, failure: String? = null, failedReply: String? = null): String = JSONObject()
        .put("savedBrief", evidenceContext(brief)).put("requestPlanNow", requestPlan)
        .put("failureToRepair", failure ?: JSONObject.NULL)
        .put("failedReply", failedReply?.take(6000)?.let { runCatching<Any> { strictObject(it) }.getOrDefault(it) } ?: JSONObject.NULL).toString()
}
