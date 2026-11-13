package com.buildai.app.data.local

import org.junit.Assert.*
import org.junit.Test

class InferenceWatchdogTest {
    private var now = 0L
    private val watchdog = InferenceWatchdog({ now })

    @Test fun productiveRequestContinuesBeyondThreeMinutes() {
        watchdog.record(InferenceProgress(InferenceStage.DECODING, 0))
        repeat(20) { i ->
            now += 60_000
            watchdog.record(InferenceProgress(InferenceStage.DECODING, i + 1L))
            assertNull(watchdog.stalledStage())
        }
        assertTrue(now > 180_000)
    }

    @Test fun repeatedOrRegressingCountersDoNotKeepAStallAlive() {
        watchdog.record(InferenceProgress(InferenceStage.DECODING, 5))
        now = 119_999
        watchdog.record(InferenceProgress(InferenceStage.DECODING, 5))
        watchdog.record(InferenceProgress(InferenceStage.DECODING, 4))
        assertNull(watchdog.stalledStage())
        now = 120_000
        assertEquals(InferenceStage.DECODING, watchdog.stalledStage())
    }

    @Test fun loadingAndPrefillHaveSeparateFiveMinuteBudgets() {
        watchdog.record(InferenceProgress(InferenceStage.LOADING, 0, 1000))
        now = 299_999
        assertNull(watchdog.stalledStage())
        now = 300_000
        assertEquals(InferenceStage.LOADING, watchdog.stalledStage())
        watchdog.record(InferenceProgress(InferenceStage.PREFILL, 0, 1024))
        now += 299_999
        assertNull(watchdog.stalledStage())
        now += 1
        assertEquals(InferenceStage.PREFILL, watchdog.stalledStage())
    }

    @Test fun actualPrefillProgressResetsInactivity() {
        watchdog.record(InferenceProgress(InferenceStage.PREFILL, 0, 1024))
        now = 290_000
        watchdog.record(InferenceProgress(InferenceStage.PREFILL, 256, 1024))
        now += 290_000
        assertNull(watchdog.stalledStage())
        now += 10_000
        assertEquals(InferenceStage.PREFILL, watchdog.stalledStage())
    }

    @Test fun queuedRequestsDoNotExpireWhileAnotherRequestUsesTheModel() {
        now = 3_600_000
        assertNull(watchdog.stalledStage())
    }

    @Test fun decodeBudgetBeginsAtDecodeNotAtRequestStart() {
        watchdog.record(InferenceProgress(InferenceStage.LOADING, 0))
        now = 240_000
        watchdog.record(InferenceProgress(InferenceStage.PREFILL, 32, 32))
        now = 400_000
        watchdog.record(InferenceProgress(InferenceStage.DECODING, 0))
        now += 119_999
        assertNull(watchdog.stalledStage())
        now++
        assertEquals(InferenceStage.DECODING, watchdog.stalledStage())
    }
}
