package com.buildai.app.data.local

/** Counters represent completed work, never UI messages or estimated percentages. */
enum class InferenceStage { QUEUED, LOADING, PREFILL, DECODING }

data class InferenceProgress(val stage: InferenceStage, val completed: Long, val total: Long = 0)

class InferenceStalledException(stage: InferenceStage) : IllegalStateException(
    "Local inference stopped making progress during ${stage.name.lowercase()}. Retry the interrupted step or choose a smaller model."
)

/** One instance per request. Queuing has no model inactivity deadline. */
class InferenceWatchdog(
    private val clock: () -> Long,
    private val loadingBudgetMs: Long = 300_000,
    private val decodingBudgetMs: Long = 120_000
) {
    private var stage = InferenceStage.QUEUED
    private var highWater = 0L
    private var productiveAt = clock()

    @Synchronized fun record(event: InferenceProgress) {
        require(event.completed >= 0 && event.total >= 0)
        if (event.stage != stage) {
            stage = event.stage
            highWater = event.completed
            productiveAt = clock()
        } else if (event.completed > highWater) {
            highWater = event.completed
            productiveAt = clock()
        }
    }

    @Synchronized fun stalledStage(): InferenceStage? {
        if (stage == InferenceStage.QUEUED) return null
        val budget = if (stage == InferenceStage.DECODING) decodingBudgetMs else loadingBudgetMs
        return stage.takeIf { clock() - productiveAt >= budget }
    }
}
