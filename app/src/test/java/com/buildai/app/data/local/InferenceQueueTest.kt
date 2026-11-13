package com.buildai.app.data.local

import org.junit.Assert.*
import org.junit.Test

class InferenceQueueTest {
    @Test fun chatsHavePriorityBetweenStepsButBuildsDoNotStarve() {
        val queue = InferenceQueue()
        val active = queue.submit(InferencePriority.BUILD)
        val firstBuild = queue.submit(InferencePriority.BUILD)
        val secondBuild = queue.submit(InferencePriority.BUILD)
        val chats = (1..4).map { queue.submit(InferencePriority.INTERACTIVE) }
        assertTrue(active.ready.isCompleted)
        assertTrue(chats.none { it.ready.isCompleted })
        queue.release(active)
        chats.take(3).forEach { chat ->
            assertTrue(chat.ready.isCompleted)
            assertFalse(firstBuild.ready.isCompleted)
            queue.release(chat)
        }
        assertTrue(firstBuild.ready.isCompleted)
        assertFalse(chats.last().ready.isCompleted)
        queue.release(firstBuild)
        assertTrue(chats.last().ready.isCompleted)
        queue.release(chats.last())
        assertTrue(secondBuild.ready.isCompleted)
    }
    @Test fun cancelledWaiterDoesNotBlockTheQueueOrCancelActiveWork() {
        val queue = InferenceQueue()
        val active = queue.submit(InferencePriority.INTERACTIVE)
        val cancelled = queue.submit(InferencePriority.BUILD)
        val next = queue.submit(InferencePriority.BUILD)
        queue.release(cancelled)
        assertFalse(next.ready.isCompleted)
        queue.release(active)
        assertTrue(next.ready.isCompleted)
        assertFalse(cancelled.ready.isCompleted)
    }
}
