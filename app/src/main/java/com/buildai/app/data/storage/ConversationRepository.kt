package com.buildai.app.data.storage

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.buildai.app.data.model.Conversation
import com.buildai.app.data.model.MessageRole
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import com.buildai.app.data.model.DesignSpec

class ConversationRepository(private val context: Context) {
    companion object { private val lock = Any() }
    private fun checkId(id: String) = require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")))

    private val dir: File
        get() = File(context.filesDir, "conversations").also { it.mkdirs() }

    private val database = BuildAiDatabase.get(context)
    val importErrors = mutableListOf<String>()
    init {
        synchronized(lock) {
            database.runInTransaction {
                dir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                    try {
                        val original = Conversation.fromJson(JSONObject(file.readText()))
                        checkId(original.id)
                        require(file.nameWithoutExtension == original.id)
                        database.projects().importOriginal(StoredProject(original.id, original.toJson().toString()))
                    } catch (_: Exception) {
                        importErrors += "Could not import saved project ${file.name}; the original file was retained."
                    }
                }
            }
        }
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    fun save(conv: Conversation) = synchronized(lock) {
        checkId(conv.id)
        database.projects().save(StoredProject(conv.id, conv.toJson().toString()))
    }

    fun load(id: String): Conversation? = synchronized(lock) {
        runCatching {
            checkId(id)
            database.projects().load(id)?.let { Conversation.fromJson(JSONObject(it.json)) }
        }.getOrNull()
    }

    fun update(id: String, change: (Conversation) -> Conversation): Conversation? = synchronized(lock) {
        var result: Conversation? = null
        database.runInTransaction {
            load(id)?.let { existing ->
                result = change(existing).also { require(it.id == id); save(it) }
            }
        }
        result
    }

    /** Commit the final planning state and its job before publishing readiness to observers. */
    fun completePlanningReply(id: String, jobId: String, expectedVersion: Int, brief: com.buildai.app.builder.ProjectBrief,
        pendingMessageId: String, reply: com.buildai.app.data.model.Message, modelLabel: String): Conversation = synchronized(lock) {
        var completed: Conversation? = null
        database.runInTransaction {
            val current = checkNotNull(load(id)) { "The project no longer exists" }
            val job = checkNotNull(database.jobs().load(jobId)) { "The planning job no longer exists" }
            check(current.brief?.version == expectedVersion && brief.version == expectedVersion && !brief.planningPending) { "Your requirements changed. Review the latest brief." }
            check(job.projectId == id && job.kind == "CHAT" && job.status == "RUNNING" &&
                JSONObject(job.inputJson).optJSONObject("brief")?.optInt("version") == expectedVersion) { "The planning job is no longer running" }
            require(reply.role == MessageRole.ASSISTANT)
            completed = current.copy(messages = current.messages.filterNot { it.id == pendingMessageId } + reply,
                brief = brief, phase = if (brief.ready) "PLAN_READY" else "CLARIFYING", modelLastUsed = modelLabel,
                replyStatus = "IDLE", updatedAt = System.currentTimeMillis()).withDerivedTitle()
            save(checkNotNull(completed))
            database.jobs().save(job.copy(status = "COMPLETED", stage = "Requirements and plan saved", checkpoint = brief.json().toString(), error = null,
                updatedAt = System.currentTimeMillis()))
        }
        checkNotNull(completed)
    }

    fun saveDesign(id: String, design: DesignSpec) {
        update(id) { it.copy(designSpec = design, updatedAt = System.currentTimeMillis()) }
        runCatching { com.buildai.app.widgets.ProjectWidgetProvider.refreshProject(context, id) }
    }

    fun saveGenerated(id: String, html: String, expectedInput: Conversation? = null): File = synchronized(lock) {
        checkId(id)
        val project = load(id) ?: error("Project no longer exists")
        expectedInput?.let { expected ->
            check(project.projectPlan?.toJson().toString() == expected.projectPlan?.toJson().toString()
                && project.designSpec?.toJson().toString() == expected.designSpec?.toJson().toString()) {
                "Requirements changed during this build. Review and build again; your previous app is preserved."
            }
        }
        val generatedDir = File(context.filesDir, "generated").also { it.mkdirs() }
        val file = File(generatedDir, "$id-${UUID.randomUUID()}.html")
        atomicWrite(file, html)
        try {
            save(project.copy(generatedFileName = file.name, updatedAt = System.currentTimeMillis()))
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        generatedDir.listFiles { f -> f.name.startsWith("$id-") && f != file }?.forEach { it.delete() }
        file
    }

    fun generatedFile(id: String): File? = synchronized(lock) {
        val name = load(id)?.generatedFileName ?: return@synchronized null
        if (!name.startsWith("$id-") || name != File(name).name || !name.endsWith(".html")) return@synchronized null
        File(context.filesDir, "generated/$name").takeIf { it.isFile }
    }

    private fun atomicWrite(file: File, text: String) {
        val temporary = File(file.parentFile, ".${file.name}-${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8)); stream.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    fun listAll(): List<Conversation> = synchronized(lock) {
        database.projects().list().map { Conversation.fromJson(JSONObject(it.json)) }
            .filterNot { it.assistantEntryDraft }
            .sortedByDescending { it.updatedAt }
    }

    /** The unsent Assistant entry is persisted without impersonating a user project. */
    fun assistantEntry(): Conversation = synchronized(lock) {
        database.projects().list().map { Conversation.fromJson(JSONObject(it.json)) }
            .firstOrNull { it.assistantEntryDraft }
            ?: Conversation(assistantEntryDraft = true).also(::save)
    }

    fun delete(id: String) = synchronized(lock) {
        checkId(id)
        File(dir, "$id.json").delete()
        database.runInTransaction {
            database.projects().delete(id)
            database.jobs().deleteForProject(id)
            database.nativeApps().deleteRecords(id)
            database.nativeApps().deleteRevisions(id)
            database.nativeApps().deleteHead(id)
        }
        File(context.filesDir, "generated").listFiles { f -> f.name.startsWith("$id-") }?.forEach { it.delete() }
        WidgetSummaryStore.delete(context, id)
        Unit
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /** Builds a plain-text export and returns an Intent to share it */
    fun buildShareIntent(conv: Conversation): Intent {
        val text = buildExportText(conv)
        checkId(conv.id)
        val file = File(context.cacheDir, "BuildAI_${conv.id}.txt")
        file.writeText(text)
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "BuildAI — ${conv.title}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Plain text export — can also share without file if FileProvider isn't set up */
    fun buildExportText(conv: Conversation): String {
        val fmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        return buildString {
            appendLine("━━ BuildAI Export ━━")
            appendLine("Project: ${conv.title}")
            appendLine("Date: ${fmt.format(Date(conv.createdAt))}")
            appendLine()
            conv.messages.forEach { msg ->
                val who = if (msg.role == MessageRole.USER) "You" else "BuildAI"
                appendLine("$who:")
                appendLine(msg.content)
                appendLine()
            }
            conv.projectPlan?.let { plan ->
                appendLine("━━ Project Plan ━━")
                appendLine("App Name: ${plan.appName}")
                appendLine("Description: ${plan.description}")
                appendLine("Type: ${plan.type.label}")
                appendLine("Complexity: ${plan.complexity.label}")
                appendLine("For: ${plan.targetUser}")
                appendLine()
                appendLine("Features:")
                plan.features.forEach { appendLine("  • $it") }
                appendLine()
                appendLine("Screens:")
                plan.screens.forEachIndexed { i, s -> appendLine("  ${i + 1}. $s") }
            }
            conv.brief?.let { brief ->
                appendLine("━━ Current requirements ━━")
                appendLine(brief.goal)
                brief.answers.forEach { appendLine("${it.question}: ${it.answer}") }
                brief.plan?.let { plan ->
                    appendLine("App: ${plan.name}")
                    plan.features.forEach { feature ->
                        appendLine("${feature.title}: ${feature.workflow}")
                        feature.checks.forEach { appendLine("  • $it") }
                    }
                }
            }
        }
    }
}
