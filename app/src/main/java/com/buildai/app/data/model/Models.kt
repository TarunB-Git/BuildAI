package com.buildai.app.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ── Chat message ─────────────────────────────────────────────────────────────

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val failed: Boolean = false   // true if this message hit an error — shows retry
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("role", role.name); put("content", content)
        put("timestamp", timestamp); put("failed", failed)
    }
    companion object {
        fun fromJson(j: JSONObject) = Message(
            id        = j.getString("id"),
            role      = MessageRole.valueOf(j.getString("role")),
            content   = j.getString("content"),
            timestamp = j.getLong("timestamp"),
            failed    = j.optBoolean("failed", false)
        )
    }
}

enum class MessageRole { USER, ASSISTANT }
enum class AssistantMode { CHAT, CREATE }

// ── Project plan ──────────────────────────────────────────────────────────────

data class ProjectPlan(
    val appName: String,
    val description: String,
    val type: AppType,
    val features: List<String>,
    val screens: List<String>,
    val targetUser: String,
    val complexity: Complexity,
    val estimatedBuildTime: String,
    val buildMode: BuildMode = BuildMode.CUSTOM,
    val quickTemplate: QuickTemplate = QuickTemplate.NOTES
) {
    fun toJson() = JSONObject().apply {
        put("appName", appName); put("description", description)
        put("type", type.name); put("targetUser", targetUser)
        put("complexity", complexity.name)
        put("estimatedBuildTime", estimatedBuildTime)
        put("buildMode", buildMode.name); put("quickTemplate", quickTemplate.name)
        put("features", JSONArray(features)); put("screens", JSONArray(screens))
    }
    companion object {
        fun fromResponse(text: String): ProjectPlan {
            val block = Regex("<plan>([\\s\\S]*?)</plan>").find(text)?.groupValues?.get(1)
                ?: error("The AI returned an incomplete project plan. Try again.")
            return fromJson(JSONObject(block)).let { plan ->
                plan.copy(buildMode = BuildMode.CUSTOM, quickTemplate = QuickTemplate.suggest(plan.appName + " " + plan.description))
            }.also {
                require(it.appName.isNotBlank() && it.description.isNotBlank()
                    && it.features.isNotEmpty() && it.features.all(String::isNotBlank)
                    && it.screens.isNotEmpty() && it.screens.all(String::isNotBlank)) {
                    "The AI returned an incomplete project plan. Try again."
                }
            }
        }

        fun fromJson(j: JSONObject): ProjectPlan {
            fun arr(key: String) = j.optJSONArray(key)
                ?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            return ProjectPlan(
                appName           = j.optString("appName", "My App"),
                description       = j.optString("description", ""),
                type              = runCatching { AppType.valueOf(j.optString("type", "APP").uppercase()) }.getOrDefault(AppType.APP),
                features          = arr("features"),
                screens           = arr("screens"),
                targetUser        = j.optString("targetUser", "Everyone"),
                complexity        = runCatching { Complexity.valueOf(j.optString("complexity", "SIMPLE").uppercase()) }.getOrDefault(Complexity.SIMPLE),
                estimatedBuildTime = j.optString("estimatedBuildTime", "A few minutes"),
                buildMode = runCatching { BuildMode.valueOf(j.optString("buildMode", "CUSTOM")) }.getOrDefault(BuildMode.CUSTOM),
                quickTemplate = runCatching { QuickTemplate.valueOf(j.optString("quickTemplate", "NOTES")) }.getOrDefault(QuickTemplate.NOTES)
            )
        }
    }
}

enum class AppType(val label: String) { APP("App"), AUTOMATION("Automation") }
enum class Complexity(val label: String, val colorValue: Long) {
    SIMPLE("Simple",  0xFF10B981L),
    MEDIUM("Medium",  0xFFF59E0BL),
    COMPLEX("Complex", 0xFFEF4444L)
}

// ── Conversation (persisted) ──────────────────────────────────────────────────

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Idea",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<Message> = emptyList(),
    val projectPlan: ProjectPlan? = null,
    val phase: String = "IDEA_INPUT",  // ConversationPhase name
    val modelLastUsed: String? = null,
    val designSpec: DesignSpec? = null,
    val generatedFileName: String? = null,
    val draftText: String = "",
    val queuedInputs: List<String> = emptyList(),
    val replyStatus: String = "IDLE",
    val assistantMode: AssistantMode = AssistantMode.CREATE,
    val assistantEntryDraft: Boolean = false,
    val brief: com.buildai.app.builder.ProjectBrief? = null,
    val nativeEditDraft: String = "", val nativeEditScope: String = "", val guidedDraft: String = "",
    val titleOwned: Boolean = false, val workspaceTab: String = "chat", val legacyPlanDraft: String = "", val pinnedAt: Long = 0
) {
    val displayName: String get() = if (titleOwned) title else projectPlan?.appName ?: brief?.plan?.name ?: title

    /** Derive title from first user message */
    fun withDerivedTitle(): Conversation {
        if (titleOwned) return this
        val first = messages.firstOrNull { it.role == MessageRole.USER }?.content ?: return this
        return copy(title = first.take(45).trimEnd() + if (first.length > 45) "…" else "")
    }

    fun toJson() = JSONObject().apply {
        put("id", id); put("title", title)
        put("createdAt", createdAt); put("updatedAt", updatedAt)
        put("phase", phase)
        put("draftText", draftText)
        put("queuedInputs", JSONArray(queuedInputs))
        put("replyStatus", replyStatus)
        put("assistantMode", assistantMode.name)
        put("assistantEntryDraft", assistantEntryDraft)
        brief?.let { put("brief", it.json()) }
        put("nativeEditDraft", nativeEditDraft); put("nativeEditScope", nativeEditScope)
        put("guidedDraft", guidedDraft)
        put("pinnedAt", pinnedAt); put("titleOwned", titleOwned); put("workspaceTab", workspaceTab); put("legacyPlanDraft", legacyPlanDraft)
        modelLastUsed?.let { put("modelLastUsed", it) }
        put("messages", JSONArray(messages.map { it.toJson() }))
        projectPlan?.let { put("projectPlan", it.toJson()) }
        designSpec?.let { put("designSpec", it.toJson()) }
        generatedFileName?.let { put("generatedFileName", it) }
    }

    companion object {
        fun fromJson(j: JSONObject): Conversation {
            val msgs = j.optJSONArray("messages")
                ?.let { a -> (0 until a.length()).map { Message.fromJson(a.getJSONObject(it)) } }
                ?: emptyList()
            val plan = j.optJSONObject("projectPlan")?.let { ProjectPlan.fromJson(it) }
            return Conversation(
                id            = j.getString("id"),
                title         = j.optString("title", "New Idea"),
                createdAt     = j.getLong("createdAt"),
                updatedAt     = j.getLong("updatedAt"),
                messages      = msgs,
                projectPlan   = plan,
                phase         = j.optString("phase", "IDEA_INPUT"),
                modelLastUsed = j.optString("modelLastUsed", "").takeIf { it.isNotBlank() },
                designSpec = j.optJSONObject("designSpec")?.let(DesignSpec::fromJson),
                generatedFileName = j.optString("generatedFileName", "").takeIf { it.isNotBlank() },
                draftText = j.optString("draftText", ""),
                queuedInputs = j.optJSONArray("queuedInputs")?.let { array ->
                    (0 until array.length()).map { array.getString(it) }
                }.orEmpty(),
                replyStatus = j.optString("replyStatus", "IDLE"),
                assistantMode = runCatching { AssistantMode.valueOf(j.optString("assistantMode", "CREATE")) }
                    .getOrDefault(AssistantMode.CREATE),
                assistantEntryDraft = j.optBoolean("assistantEntryDraft", false),
                brief = j.optJSONObject("brief")?.let(com.buildai.app.builder.ProjectBrief::read),
                nativeEditDraft = j.optString("nativeEditDraft"), nativeEditScope = j.optString("nativeEditScope"), guidedDraft = j.optString("guidedDraft"),
                titleOwned = j.optBoolean("titleOwned"), workspaceTab = j.optString("workspaceTab", "chat"), legacyPlanDraft = j.optString("legacyPlanDraft"), pinnedAt = j.optLong("pinnedAt", 0)
            )
        }
    }
}
