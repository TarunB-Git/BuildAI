package com.buildai.app.data.api

import com.buildai.app.data.model.Message
import com.buildai.app.data.model.MessageRole
import com.buildai.app.data.model.ProjectPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class GeminiRepository(internal val api: GeminiApi = GeminiApi(), private val fallbackDelayMs: Long = 300) {
    val systemPrompt = """
        You are BuildAI, a warm and friendly assistant who helps everyday people plan simple apps.

        Your entire job right now is to chat naturally with the person to understand their idea, then write a project plan. Respond exactly as a warm, curious friend would — not as a system following rules. Keep every reply short: one friendly sentence or question, nothing more.

        Never output bullet points, numbered lists, headings, or any kind of structured text in your replies. Never explain what you are about to do, what your goal is, or what constraints you are following. Just talk to the person.

        Ask one question at a time. After you have asked roughly 4 to 7 questions and genuinely understand what they want to build, who it is for, and what it needs to store or do — write the plan. Put nothing before the opening tag. Use this exact structure:

        <plan>
        {"appName":"A good name","description":"One or two plain sentences.","type":"app","features":["Feature one","Feature two","Feature three"],"screens":["Home","Second screen"],"targetUser":"Who this is for","complexity":"simple","estimatedBuildTime":"A few minutes"}
        </plan>
        Does this match what you had in mind? Say Go! to build it, or tell me what to change.

        These apps are self-contained mobile web pages. They can save data on the device and work offline. They cannot send scheduled notifications, access contacts, send messages directly, require accounts or cloud services, or run in the background. In-app reminders work only while the page is open. Only mention this if the person asks for something that will not work — tell them warmly and suggest a simpler alternative.

        Do not write the plan before asking at least 4 questions.
    """.trimIndent()

    // ── Response cleaner ─────────────────────────────────────────────────────
    // Gemma 4 sometimes outputs chain-of-thought as bullet lines. Strip ALL
    // bullet-prefixed lines from assistant responses — they are never valid in
    // casual conversation. Preserve <plan>…</plan> blocks untouched.

    private fun cleanResponse(raw: String): String {
        if (raw.contains("<plan>") && raw.contains("</plan>")) {
            val s = raw.indexOf("<plan>")
            val e = raw.indexOf("</plan>") + "</plan>".length
            return stripBullets(raw.substring(0, s)) +
                   raw.substring(s, e) +
                   stripBullets(raw.substring(e))
        }
        return stripBullets(raw)
    }

    private fun stripBullets(text: String): String =
        text.lines()
            .filterNot { line ->
                val t = line.trim()
                t.startsWith("* ") || t.startsWith("- ") || t.startsWith("• ") ||
                t == "*" || t == "-"
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()


    private val fallbackChain = listOf("gemma-4-31b-it", "gemma-4-26b-a4b-it", "gemini-2.5-flash-lite", "gemini-2.5-flash")

    suspend fun sendMessage(messages: List<Message>, apiKey: String, preferredModel: String, prompt: String = systemPrompt,
        onPartial: ((String) -> Unit)? = null, cleanPlanResponse: Boolean = true, provider: CloudProvider = CloudProvider.GEMINI, fallbackEnabled: Boolean = true, fallbackModels: List<String>? = null): Result<Pair<String, String>> {
        val contents = JSONArray(messages.map { message ->
            JSONObject().put("role", if (message.role == MessageRole.USER) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", message.content)))
        })
        var lastError: Exception = Exception("The AI service couldn't answer. Try again.")
        for ((index, model) in (listOf(preferredModel) + if (fallbackEnabled) fallbackModels ?: if (provider == CloudProvider.GEMINI) fallbackChain else emptyList() else emptyList()).distinct().withIndex()) {
            if (index > 0) delay(fallbackDelayMs * index)
            var partial = ""
            try {
                val raw = if (provider != CloudProvider.GEMINI) {
                    CloudProviderApi(api).stream(provider, model, apiKey, messages, prompt).collect { chunk -> partial += chunk; onPartial?.invoke(partial) }; partial
                } else if (onPartial == null) api.generate(model, apiKey, contents, prompt, 0.7, 2048)
                    else {
                        api.stream(model, apiKey, contents, prompt).collect { chunk -> partial += chunk; onPartial(partial) }
                        partial
                    }
                if (cleanPlanResponse && raw.contains("<plan>")) ProjectPlan.fromResponse(raw)
                val cleaned = if (cleanPlanResponse) cleanResponse(raw) else raw.trim()
                if (cleaned.isNotBlank()) return Result.success(cleaned to model)
                lastError = Exception("The AI returned an empty answer. Try again.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                lastError = e
                // An already visible answer must not be spliced into a different model's reply.
                if (partial.isNotBlank()) break
                if (e is GeminiApiException && e.status in listOf(401, 403)) break
            }
        }
        return Result.failure(lastError)
    }

    companion object { const val DEFAULT_MODEL = "gemma-4-31b-it" }
}
