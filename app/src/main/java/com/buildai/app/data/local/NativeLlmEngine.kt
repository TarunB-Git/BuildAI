package com.buildai.app.data.local

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.Keep
import com.buildai.app.data.model.Message
import com.buildai.app.data.model.MessageRole
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** All model access runs on one background thread; cancellation only writes a native atomic flag. */
@Keep
class NativeLlmEngine(context: Context) {
    private val app = context.applicationContext

    companion object {
        private val executor = ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "buildai-local-inference").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
        private var idleUnload: ScheduledFuture<*>? = null
        private var handle = 0L
        private var loadedPath: String? = null
        private var loadedContextSize = 0
    }

    @Keep
    private class NativeCallbacks(
        private val token: (String) -> Boolean,
        private val stage: (Int) -> Unit,
        private val progress: (Int, Long, Long) -> Unit
    ) {
        fun onToken(text: String): Boolean = token(text)
        fun onStage(value: Int) = stage(value)
        fun onProgress(stage: Int, completed: Long, total: Long) = progress(stage, completed, total)
    }

    private external fun nativeCreateRequest(): Long
    private external fun nativeCancelRequest(request: Long)
    private external fun nativeReleaseRequest(request: Long)
    private external fun nativeLoad(path: String, contextSize: Int, threads: Int, request: Long, callbacks: NativeCallbacks): Long
    private external fun nativeUnload(handle: Long)
    private external fun nativeGenerate(
        handle: Long, roles: Array<String>, contents: Array<String>, maxTokens: Int,
        request: Long, callbacks: NativeCallbacks, grammar: String?
    )

    fun reply(
        modelFile: File, systemPrompt: String, history: List<Message>,
        maxTokens: Int = 768, contextSize: Int = 0,
        stopAt: String? = null, grammar: String? = null,
        onStatus: (String) -> Unit = {},
        onProgress: (InferenceProgress) -> Unit = {}
    ): Flow<String> = channelFlow {
        require(modelFile.isFile) { "The local model file is missing. Import it again in Settings." }
        require(stopAt == null || stopAt.length in 1..128) { "Invalid output stop marker." }
        require(maxTokens in 1..8192) { "Invalid reply length." }
        val info = ActivityManager.MemoryInfo()
        (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        val requestedContext = if (contextSize > 0) contextSize else when {
            info.totalMem < 5L * 1024 * 1024 * 1024 -> 2048
            info.totalMem < 7L * 1024 * 1024 * 1024 -> 3072
            else -> 4096
        }
        require(requestedContext in 512..8192) { "Invalid model context size." }
        val roles = arrayOf("system") + history.map {
            if (it.role == MessageRole.USER) "user" else "assistant"
        }
        val contents = arrayOf(systemPrompt) + history.map { it.content }
        val watchdog = InferenceWatchdog(android.os.SystemClock::elapsedRealtime)
        val monitor = launch {
            var memoryCheckAt = 0L
            while (true) {
                delay(1000)
                val power = app.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                check(power.currentThermalStatus < android.os.PowerManager.THERMAL_STATUS_CRITICAL) {
                    "Paused: this phone is too hot for local AI. Let it cool, then retry the saved step."
                }
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - memoryCheckAt >= 5000) {
                    (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
                    check(!info.lowMemory) { "Paused: this phone is low on memory. Close other apps, then retry the saved step." }
                    memoryCheckAt = now
                }
                watchdog.stalledStage()?.let { throw InferenceStalledException(it) }
            }
        }
        onStatus("Waiting for local model…")
        try { suspendCancellableCoroutine<Unit> { continuation ->
            val requestLock = Any()
            var request = nativeCreateRequest()
            continuation.invokeOnCancellation {
                synchronized(requestLock) {
                    if (request != 0L) nativeCancelRequest(request)
                }
            }
            executor.execute {
                idleUnload?.cancel(false)
                var hasText = false
                var markerTail = ""
                val callbacks = NativeCallbacks(
                    token = { text ->
                        if (!continuation.isActive) false else {
                            hasText = hasText || text.isNotBlank()
                            val sent = trySend(text).isSuccess
                            val marker = stopAt
                            val ended = if (marker == null) false else {
                                val tail = markerTail + text
                                markerTail = tail.takeLast(marker.length - 1)
                                tail.contains(marker, ignoreCase = true)
                            }
                            sent && !ended
                        }
                    },
                    stage = { value ->
                        if (continuation.isActive) {
                            if (value == 3) watchdog.record(InferenceProgress(InferenceStage.DECODING, 0))
                            onStatus(when (value) {
                                1 -> "Preparing local chat…"
                                2 -> "Reading your message…"
                                else -> "Writing a reply…"
                            })
                        }
                    },
                    progress = { stage, completed, total ->
                        if (continuation.isActive) {
                            val event = InferenceProgress(when (stage) {
                                0 -> InferenceStage.LOADING
                                1 -> InferenceStage.PREFILL
                                else -> InferenceStage.DECODING
                            }, completed, total)
                            watchdog.record(event)
                            onProgress(event)
                        }
                    }
                )
                try {
                    if (continuation.isActive) {
                        watchdog.record(InferenceProgress(InferenceStage.LOADING, 0))
                        if (handle == 0L || loadedPath != modelFile.absolutePath || loadedContextSize != requestedContext) {
                            releaseModel()
                            (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
                            check(!info.lowMemory) { "Paused: this phone is low on memory. Close other apps or choose a smaller model, then retry." }
                            check((app.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).currentThermalStatus < android.os.PowerManager.THERMAL_STATUS_CRITICAL) {
                                "Paused: this phone is too hot for local AI. Let it cool, then retry."
                            }
                            onStatus("Loading local model…")
                            handle = nativeLoad(modelFile.absolutePath, requestedContext,
                                (Runtime.getRuntime().availableProcessors() - 2).coerceIn(1, 4), request, callbacks)
                            loadedPath = modelFile.absolutePath
                            loadedContextSize = requestedContext
                        }
                        if (continuation.isActive) {
                            check(handle != 0L) { "The local model could not be loaded." }
                            nativeGenerate(handle, roles, contents, maxTokens, request, callbacks, grammar)
                            if (continuation.isActive) {
                                check(hasText) { "The local model ended without a reply. Try a shorter message or another model." }
                                continuation.resume(Unit)
                            }
                        }
                    }
                } catch (error: Throwable) {
                    releaseModel()
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally {
                    synchronized(requestLock) {
                        nativeReleaseRequest(request)
                        request = 0L
                    }
                    idleUnload = executor.schedule({ releaseModel() }, 90, TimeUnit.SECONDS)
                }
            }
        } } finally { monitor.cancel() }
    }.buffer(Channel.UNLIMITED)

    /** Queued cleanup does not block the UI thread, including during cancellation. */
    suspend fun unload(): Unit = suspendCoroutine { continuation ->
        executor.execute {
            try {
                releaseModel()
                continuation.resume(Unit)
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
            }
        }
    }

    private fun releaseModel() {
        idleUnload?.cancel(false)
        idleUnload = null
        if (handle != 0L) nativeUnload(handle)
        handle = 0L
        loadedPath = null
        loadedContextSize = 0
    }

    init {
        try {
            System.loadLibrary("buildai_llama")
        } catch (error: UnsatisfiedLinkError) {
            throw IllegalStateException("The local CPU runtime could not start on this device. Install the latest BuildAI APK.", error)
        }
    }
}
