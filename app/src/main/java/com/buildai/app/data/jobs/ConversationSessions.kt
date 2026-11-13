package com.buildai.app.data.jobs

import android.app.Application
import com.buildai.app.data.api.GeminiRepository
import com.buildai.app.data.model.AssistantMode
import com.buildai.app.data.model.Conversation
import com.buildai.app.data.storage.ConversationRepository
import com.buildai.app.data.storage.SecureStorage
import kotlinx.coroutines.*

/** Application-owned sessions; a view model observes one and may detach freely. */
class ConversationSessions(
    private val app: Application,
    private val gemini: GeminiRepository,
    private val projects: ConversationRepository,
    private val settings: SecureStorage
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessions = mutableMapOf<String, ConversationSession>()
    fun get(id: String): ConversationSession? {
        if (projects.load(id) == null) return null
        return sessions.getOrPut(id) { ConversationSession(app, gemini, projects, settings, scope, id) }
    }
    fun create(mode: AssistantMode = AssistantMode.CREATE, nativePlanning: Boolean = true): ConversationSession {
        val project = Conversation(assistantMode = mode, brief = if (mode == AssistantMode.CREATE && nativePlanning) com.buildai.app.builder.ProjectBrief() else null)
        projects.save(project)
        return checkNotNull(get(project.id))
    }
    fun cancel(id: String) { sessions.remove(id)?.stopReply() }
}
