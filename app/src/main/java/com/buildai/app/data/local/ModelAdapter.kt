package com.buildai.app.data.local

import com.buildai.app.data.model.Message
import kotlinx.coroutines.flow.Flow
import java.io.File

data class ModelResourceProfile(val maxConcurrentRequests: Int, val accelerator: String, val measuredPeakBytes: Long? = null)

/** Streaming text plus genuine progress; callers own bounded task/repair contracts. */
interface ModelAdapter {
    val supportsStructuredOutput: Boolean
    val resourceProfile: ModelResourceProfile
    fun reply(
        modelFile: File, systemPrompt: String, history: List<Message>,
        maxTokens: Int = 768, contextSize: Int = 0,
        stopAt: String? = null, grammar: String? = null,
        onStatus: (String) -> Unit = {}, onProgress: (InferenceProgress) -> Unit = {},
        priority: InferencePriority = InferencePriority.INTERACTIVE
    ): Flow<String>
    suspend fun unload()
}
