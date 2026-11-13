package com.buildai.app.data.local

import kotlinx.coroutines.CompletableDeferred

enum class InferencePriority { INTERACTIVE, BUILD }

/** One slot, FIFO within each class; at most three chats bypass a waiting build. */
class InferenceQueue {
    class Ticket internal constructor(val priority: InferencePriority, internal val order: Long) {
        val ready = CompletableDeferred<Unit>()
    }
    private var sequence = 0L
    private var active: Ticket? = null
    private val waiting = mutableListOf<Ticket>()
    private var interactiveTurns = 0

    @Synchronized fun submit(priority: InferencePriority): Ticket {
        val ticket = Ticket(priority, sequence++)
        waiting += ticket
        grantNext()
        return ticket
    }
    @Synchronized fun release(ticket: Ticket) {
        if (active === ticket) active = null else waiting.remove(ticket)
        grantNext()
    }
    private fun grantNext() {
        if (active != null || waiting.isEmpty()) return
        val build = waiting.firstOrNull { it.priority == InferencePriority.BUILD }
        val chat = waiting.firstOrNull { it.priority == InferencePriority.INTERACTIVE }
        val next = if (interactiveTurns >= 3 && build != null) build else chat ?: checkNotNull(build)
        waiting.remove(next); active = next
        interactiveTurns = if (next.priority == InferencePriority.INTERACTIVE) interactiveTurns + 1 else 0
        next.ready.complete(Unit)
    }
}
