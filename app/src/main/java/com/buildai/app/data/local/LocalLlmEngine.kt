package com.buildai.app.data.local

import android.content.*
import android.os.*
import androidx.core.content.ContextCompat
import com.buildai.app.data.model.Message as ChatMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Binder client; JNI and model memory live only in the :inference process. */
class LocalLlmEngine(context: Context) : ModelAdapter {
    private val app = context.applicationContext

    override val supportsStructuredOutput = true
    override val resourceProfile = ModelResourceProfile(1, "CPU")
    override fun reply(
        modelFile: File, systemPrompt: String, history: List<ChatMessage>,
        maxTokens: Int, contextSize: Int,
        stopAt: String?, grammar: String?,
        onStatus: (String) -> Unit, onProgress: (InferenceProgress) -> Unit, priority: InferencePriority
    ): Flow<String> {
        require(modelFile.isFile) { "The local model file is missing. Import it again in Settings." }
        val historyJson = JSONArray(history.map { it.toJson() }).toString()
        require(systemPrompt.length + historyJson.length + (grammar?.length ?: 0) <= 120_000) {
            "This request is too large. Shorten it and retry."
        }
        return request(InferenceProtocol.START, Bundle().apply {
            putString("path", modelFile.absolutePath); putString("prompt", systemPrompt)
            putString("history", historyJson); putInt("maxTokens", maxTokens); putInt("contextSize", contextSize)
            putString("stopAt", stopAt); putString("grammar", grammar)
            putString("priority", priority.name)
        }, onStatus, onProgress)
    }

    override suspend fun unload() { request(InferenceProtocol.UNLOAD, Bundle().apply { putString("priority", InferencePriority.BUILD.name) }, {}, {}).collect() }

    private fun request(
        operation: Int, payload: Bundle,
        onStatus: (String) -> Unit, onProgress: (InferenceProgress) -> Unit
    ): Flow<String> = channelFlow {
        val id = UUID.randomUUID().toString()
        val connected = CompletableDeferred<Messenger>()
        val finished = CompletableDeferred<Unit>()
        var server: Messenger? = null
        var binder: IBinder? = null
        var terminal = false
        val death = IBinder.DeathRecipient {
            val error = IllegalStateException("Local inference was interrupted. The UI is still available; retry from the saved input.")
            connected.completeExceptionally(error)
            finished.completeExceptionally(error)
        }
        val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.data.getString("id") != id) return@Handler true
            try {
                when (message.what) {
                    InferenceProtocol.TOKEN -> trySend(message.data.getString("text").orEmpty())
                    InferenceProtocol.STATUS -> onStatus(message.data.getString("text").orEmpty())
                    InferenceProtocol.PROGRESS -> onProgress(InferenceProgress(
                        InferenceStage.valueOf(checkNotNull(message.data.getString("stage"))),
                        message.data.getLong("completed"), message.data.getLong("total")))
                    InferenceProtocol.DONE -> { terminal = true; finished.complete(Unit) }
                    InferenceProtocol.ERROR -> {
                        terminal = true
                        finished.completeExceptionally(IllegalStateException(message.data.getString("text") ?: "Local inference failed."))
                    }
                }
            } catch (error: Exception) { finished.completeExceptionally(error) }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder = service
                try { service.linkToDeath(death, 0); connected.complete(Messenger(service)) }
                catch (error: RemoteException) { connected.completeExceptionally(error) }
            }
            override fun onServiceDisconnected(name: ComponentName) { death.binderDied() }
            override fun onBindingDied(name: ComponentName) { death.binderDied() }
            override fun onNullBinding(name: ComponentName) { connected.completeExceptionally(IllegalStateException("Local inference service could not start.")) }
        }
        val intent = Intent(app, LocalInferenceService::class.java)
        ContextCompat.startForegroundService(app, intent)
        val bound = app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        check(bound) { "Local inference service could not connect." }
        try {
            server = try { withTimeout(15_000) { connected.await() } }
            catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                throw IllegalStateException("The local inference service could not connect. Retry from the saved input.", timeout)
            }
            payload.putString("id", id)
            server.send(android.os.Message.obtain(null, operation).apply { data = payload; replyTo = receiver })
            finished.await()
        } finally {
            if (!terminal) runCatching { server?.send(android.os.Message.obtain(null, InferenceProtocol.CANCEL).apply {
                data = Bundle().apply { putString("id", id) }
            }) }
            runCatching { binder?.unlinkToDeath(death, 0) }
            app.unbindService(connection)
        }
    }.buffer(Channel.UNLIMITED)
}

internal object InferenceProtocol {
    const val START = 1; const val CANCEL = 2; const val UNLOAD = 3
    const val TOKEN = 10; const val STATUS = 11; const val PROGRESS = 12
    const val DONE = 13; const val ERROR = 14
}
