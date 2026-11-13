package com.buildai.app.builder

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

data class RequirementTurn(val messageId: String, val text: String)
data class Clarification(val id: String, val question: String, val essential: Boolean = true)
data class RememberedAnswer(val questionId: String, val question: String, val answer: String, val messageId: String)
data class FeatureSuggestion(val id: String, val title: String, val benefit: String, val accepted: Boolean = false)
data class FeaturePlan(val id: String, val title: String, val workflow: String, val checks: List<String>, val composition: CompositionSelection? = null) {
    fun json() = JSONObject().put("id", id).put("title", title).put("workflow", workflow).put("checks", JSONArray(checks)).also { j -> composition?.let { j.put("composition", it.json()) } }
}
data class NativePlan(val name: String, val features: List<FeaturePlan>, val screens: List<String>, val data: List<String>) {
    fun json() = JSONObject().put("name", name).put("features", JSONArray(features.map { it.json() }))
        .put("screens", JSONArray(screens)).put("data", JSONArray(data))
    fun validate() {
        require(name.isNotBlank() && name.length <= 100) { "Give the app a name of up to 100 characters" }
        require(features.size in 1..12) { "Use one to twelve focused features in this plan" }
        require(screens.size in 1..12 && screens.all { it.isNotBlank() && it.length <= 100 }) { "Give each screen a short name" }
        require(data.size <= 40 && data.all { it.isNotBlank() && it.length <= 500 }) { "Describe each saved collection briefly" }
        require(features.map { it.id }.distinct().size == features.size) { "Each feature needs its own stable identifier" }
        features.forEach { it.composition?.validate(); require(it.id.matches(Regex("[a-zA-Z][a-zA-Z0-9_-]{0,63}")) && it.title.length in 1..100
            && it.title.isNotBlank() && it.workflow.isNotBlank() && it.workflow.length <= 2000 && it.checks.size in 1..8 && it.checks.all { check -> check.isNotBlank() && check.length <= 1000 }) { "Each feature needs a name, a focused workflow, and one to eight observable checks" } }
    }
    companion object {
        fun read(j: JSONObject) = NativePlan(j.getString("name"), j.objects("features").map {
            FeaturePlan(it.getString("id"), it.getString("title"), it.getString("workflow"), it.strings("checks"), it.optJSONObject("composition")?.let(CompositionSelection::read))
        }, j.strings("screens"), j.strings("data")).also { it.validate() }
    }
}

/** User evidence is immutable; a model can propose decisions but cannot erase what was said. */
data class ProjectBrief(
    val version: Int = 0, val goal: String = "", val turns: List<RequirementTurn> = emptyList(),
    val answers: List<RememberedAnswer> = emptyList(), val questions: List<Clarification> = emptyList(),
    val assumptions: List<String> = emptyList(), val suggestions: List<FeatureSuggestion> = emptyList(),
    val plan: NativePlan? = null, val unsupported: String? = null, val approvedVersion: Int? = null,
    val planDraft: String? = null, val planningPending: Boolean = false, val starterOption: String? = null
) {
    val ready: Boolean get() = plan != null && questions.none { it.essential } && unsupported == null && !planningPending
    fun addInput(messageId: String, text: String): ProjectBrief {
        require(text.length in 1..4096) { "Please keep each Create message within 4,096 characters" }
        require(turns.size < 64) { "This brief has 64 saved decisions. Start a new project for a separate idea." }
        return copy(version = version + 1, goal = goal.ifBlank { text }, turns = turns + RequirementTurn(messageId, text), approvedVersion = null, planningPending = true, starterOption = null)
    }
    fun chooseSuggestion(id: String, accepted: Boolean): ProjectBrief = copy(version = version + 1,
        suggestions = suggestions.map { if (it.id == id) it.copy(accepted = accepted) else it }, approvedVersion = null)
    fun editPlan(next: NativePlan): ProjectBrief { next.validate(); return copy(version = version + 1, plan = next, approvedVersion = null, starterOption = null) }
    fun editDraft(text: String): ProjectBrief {
        require(text.length <= 32_768)
        JSONObject(text) // Drafts may have incomplete fields but must remain valid JSON.
        return copy(version = version + 1, planDraft = text, approvedVersion = null, starterOption = null)
    }
    fun approve(): ProjectBrief {
        check(ready) { "Resolve the essential questions or review visible assumptions first" }
        val edited = planDraft?.let { NativePlan.read(JSONObject(it)) } ?: checkNotNull(plan)
        return copy(plan = edited, planDraft = null, approvedVersion = version)
    }
    fun json() = JSONObject().put("starterOption", starterOption ?: JSONObject.NULL).put("version", version).put("goal", goal)
        .put("turns", JSONArray(turns.map { JSONObject().put("messageId", it.messageId).put("text", it.text) }))
        .put("answers", JSONArray(answers.map { JSONObject().put("questionId", it.questionId).put("question", it.question).put("answer", it.answer).put("messageId", it.messageId) }))
        .put("questions", JSONArray(questions.map { JSONObject().put("id", it.id).put("question", it.question).put("essential", it.essential) }))
        .put("assumptions", JSONArray(assumptions)).put("suggestions", JSONArray(suggestions.map {
            JSONObject().put("id", it.id).put("title", it.title).put("benefit", it.benefit).put("accepted", it.accepted)
        })).put("planningPending", planningPending).also { j -> plan?.let { j.put("plan", it.json()) }; unsupported?.let { j.put("unsupported", it) }; approvedVersion?.let { j.put("approvedVersion", it) }; planDraft?.let { j.put("planDraft", it) } }
    companion object {
        fun read(j: JSONObject) = ProjectBrief(j.optInt("version"), j.optString("goal"),
            j.objects("turns").map { RequirementTurn(it.getString("messageId"), it.getString("text")) },
            j.objects("answers").map { RememberedAnswer(it.getString("questionId"), it.getString("question"), it.getString("answer"), it.getString("messageId")) },
            j.objects("questions").map { Clarification(it.getString("id"), it.getString("question"), it.optBoolean("essential", true)) },
            j.strings("assumptions"), j.objects("suggestions").map { FeatureSuggestion(it.getString("id"), it.getString("title"), it.getString("benefit"), it.optBoolean("accepted")) },
            j.optJSONObject("plan")?.let(NativePlan::read), j.optString("unsupported").takeIf { it.isNotBlank() },
            if (j.has("approvedVersion")) j.getInt("approvedVersion") else null, j.optString("planDraft").takeIf(String::isNotBlank), j.optBoolean("planningPending"), j.optString("starterOption").takeIf { it.isNotBlank() && it != "null" })
    }
}

data class PlanningTurn(val reply: String, val brief: ProjectBrief)
object BriefPlanner {
    fun apply(raw: String, before: ProjectBrief, requestPlan: Boolean = false): PlanningTurn {
        val j = strictObject(raw)
        val questions = j.objects("questions").map { Clarification(it.getString("id"), it.getString("question"), it.optBoolean("essential", true)) }
        require(questions.size <= 3 && questions.all { it.id.length in 1..64 && it.question.length in 1..500 })
        val answers = before.answers.toMutableList()
        j.objects("answers").forEach { a ->
            val id = a.getString("questionId"); val source = a.getString("messageId")
            val question = before.questions.firstOrNull { it.id == id }?.question ?: before.answers.firstOrNull { it.questionId == id }?.question
                ?: error("The answer refers to a question that was not asked")
            require(before.turns.any { it.messageId == source } && source == before.turns.lastOrNull()?.messageId) { "An answer needs current user evidence" }
            val answer = a.getString("answer"); require(answer.length in 1..2000)
            require(before.turns.last().text.contains(answer, ignoreCase = true)) { "Remember the user's actual words, rather than inventing or paraphrasing an answer" }
            answers.removeAll { it.questionId == id }; answers += RememberedAnswer(id, question, answer, source)
        }
        val unanswered = (before.questions + questions).distinctBy { it.id }.filterNot { q -> answers.any { it.questionId == q.id || normalized(it.question) == normalized(q.question) } }
        val askedForQuestions = before.turns.lastOrNull()?.text?.lowercase()?.let { Regex("ask(?: me)?(?: some)? questions").containsMatchIn(it) } ?: false
        require(!askedForQuestions || unanswered.isNotEmpty()) { "The user requested questions; ask a relevant unanswered question" }
        val proposedSuggestions = j.objects("suggestions").map { s ->
            FeatureSuggestion(s.getString("id"), s.getString("title"), s.getString("benefit"), before.suggestions.firstOrNull { it.id == s.getString("id") }?.accepted ?: false)
        }
        require(proposedSuggestions.size <= 3 && proposedSuggestions.map { it.id }.distinct().size == proposedSuggestions.size && proposedSuggestions.all { it.id.length in 1..64 && it.title.length in 1..200 && it.benefit.length in 1..500 })
        // An assistant omission or reused ID cannot revoke or rewrite an explicitly accepted feature.
        val acceptedSuggestions = before.suggestions.filter { it.accepted }
        val suggestions = (acceptedSuggestions + if (j.has("suggestions")) proposedSuggestions else before.suggestions)
            .distinctBy { it.id }
        require(suggestions.size <= 64) { "Too many saved feature suggestions; review this plan before adding more" }
        val assumptions = j.strings("assumptions"); require(assumptions.size <= 12 && assumptions.all { it.length in 1..1000 })
        val plan = j.optJSONObject("plan")?.let(NativePlan::read)
        // An early plan is allowed, but essential unknowns remain visible assumptions.
        require(!requestPlan || plan != null || j.optString("unsupported").isNotBlank()) { "The requested plan is incomplete" }
        val earlyAssumptions = if (requestPlan) unanswered.filter { it.essential }.map { "Not decided: ${it.question}" } else emptyList()
        val next = before.copy(answers = answers, questions = if (requestPlan) emptyList() else unanswered,
            assumptions = (assumptions + earlyAssumptions).distinct(), suggestions = suggestions,
            plan = plan, starterOption = j.optString("offlineStarter").takeIf { name -> plan != null && com.buildai.app.data.model.QuickTemplate.entries.any { it.name == name } }, unsupported = j.optString("unsupported").takeIf(String::isNotBlank), approvedVersion = null, planDraft = null, planningPending = false)
        require(next.plan != null || next.questions.isNotEmpty() || next.unsupported != null) { "The reply has neither an unanswered question nor a usable plan" }
        val reply = j.optString("reply").take(2000).ifBlank {
            next.unsupported ?: unanswered.joinToString("\n") { it.question }.ifBlank { "Your plan is ready to review." }
        }
        require(reply.isNotBlank())
        val shown = reply + next.questions.filterNot { reply.contains(it.question) }.joinToString("") { "\n${it.question}" }
        return PlanningTurn(shown, next)
    }
    private fun normalized(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
}
private fun JSONObject.optionalArray(key: String): JSONArray? = if (has(key)) getJSONArray(key) else null
internal fun JSONObject.objects(key: String): List<JSONObject> = optionalArray(key)?.let { a ->
    require(a.length() <= 64); (0 until a.length()).map { a.getJSONObject(it) }
}.orEmpty()
internal fun JSONObject.strings(key: String): List<String> = optionalArray(key)?.let { a ->
    require(a.length() <= 64); (0 until a.length()).map { a.get(it).also { value -> require(value is String) { "$key must contain strings" } } as String }
}.orEmpty()
fun strictObject(raw: String): JSONObject {
    require(raw.length <= 65_536) { "The structured answer exceeded its supported size" }
    val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    require(text.startsWith("{") && text.endsWith("}")) { "The model did not return a complete structured answer" }
    val tokens = JSONTokener(text)
    val value = tokens.nextValue()
    require(value is JSONObject && tokens.nextClean() == '\u0000') { "Return one JSON object without trailing content" }
    return value
}

internal fun sameJson(left: Any, right: Any): Boolean = when {
    left is JSONObject && right is JSONObject -> {
        val keys = left.keys().asSequence().toSet()
        keys == right.keys().asSequence().toSet() && keys.all { sameJson(left.get(it), right.get(it)) }
    }
    left is JSONArray && right is JSONArray -> left.length() == right.length() && (0 until left.length()).all { sameJson(left.get(it), right.get(it)) }
    else -> left == right
}
