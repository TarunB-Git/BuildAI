package com.buildai.app.data.local

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.buildai.app.R
import com.buildai.app.data.model.Message as ChatMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Native failures are isolated here; clients observe Binder death and retain checkpoints. */
class LocalInferenceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = ConcurrentHashMap<String, Job>()
    private val queue = InferenceQueue()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var engine: NativeLlmEngine
    private var idleStop: Runnable? = null
    private val messenger by lazy { Messenger(Handler(Looper.getMainLooper()) { message ->
        val id = message.data.getString("id") ?: return@Handler true
        when (message.what) {
            InferenceProtocol.CANCEL -> requests[id]?.cancel()
            InferenceProtocol.START, InferenceProtocol.UNLOAD -> {
                val client = message.replyTo ?: return@Handler true
                val operation = message.what
                val input = Bundle(message.data)
                idleStop?.let(handler::removeCallbacks)
                val death = IBinder.DeathRecipient { requests[id]?.cancel() }
                try { client.binder.linkToDeath(death, 0) } catch (_: RemoteException) { return@Handler true }
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    val ticket = queue.submit(runCatching { InferencePriority.valueOf(input.getString("priority").orEmpty()) }
                        .getOrDefault(InferencePriority.INTERACTIVE))
                    fun send(what: Int, text: String = "", event: InferenceProgress? = null) {
                        client.send(android.os.Message.obtain(null, what).apply {
                            data = Bundle().apply {
                                putString("id", id); putString("text", text)
                                event?.let { putString("stage", it.stage.name); putLong("completed", it.completed); putLong("total", it.total) }
                            }
                        })
                    }
                    try {
                        send(InferenceProtocol.STATUS, "Waiting for local model…")
                        ticket.ready.await()
                        if (operation == InferenceProtocol.UNLOAD) engine.unload()
                        else {
                            val file = File(checkNotNull(input.getString("path"))).canonicalFile
                            require(file.path.startsWith(filesDir.canonicalPath + "/") || file.path.startsWith(cacheDir.canonicalPath + "/")) { "Model must be in app-private storage." }
                            val history = JSONArray(input.getString("history"))
                            engine.reply(file, input.getString("prompt").orEmpty(),
                                (0 until history.length()).map { ChatMessage.fromJson(history.getJSONObject(it)) },
                                maxTokens = input.getInt("maxTokens"), contextSize = input.getInt("contextSize"),
                                stopAt = input.getString("stopAt"), grammar = input.getString("grammar"),
                                onStatus = { send(InferenceProtocol.STATUS, it) },
                                onProgress = { send(InferenceProtocol.PROGRESS, event = it) }).collect { send(InferenceProtocol.TOKEN, it) }
                        }
                        send(InferenceProtocol.DONE)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        runCatching { send(InferenceProtocol.ERROR, error.message ?: "Local inference failed.") }
                        // A truly stalled JNI call may ignore cancellation. Restart only this
                        // process so retry cannot queue forever behind the hung native thread.
                        if (error is InferenceStalledException) handler.postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 50)
                    }
                    finally {
                        queue.release(ticket)
                        runCatching { client.binder.unlinkToDeath(death, 0) }
                        requests.remove(id)
                        handler.post {
                            if (requests.isEmpty()) {
                                idleStop?.let(handler::removeCallbacks)
                                idleStop = Runnable { if (requests.isEmpty()) stopSelf() }.also { handler.postDelayed(it, 90_000) }
                            }
                        }
                    }
                }
                requests[id] = job
                job.invokeOnCompletion {
                    requests.remove(id)
                    runCatching { client.binder.unlinkToDeath(death, 0) }
                    handler.post {
                        if (requests.isEmpty()) {
                            idleStop?.let(handler::removeCallbacks)
                            idleStop = Runnable { if (requests.isEmpty()) stopSelf() }.also { handler.postDelayed(it, 90_000) }
                        }
                    }
                }
                job.start()
            }
        }
        true
    }) }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("local_inference", "Local AI", NotificationManager.IMPORTANCE_LOW))
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launch?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val notification = NotificationCompat.Builder(this, "local_inference")
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle("Local AI is active")
            .setContentText("Your work can continue while you leave BuildAI").setOngoing(true)
            .apply { pending?.let(::setContentIntent) }.build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(2001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(2001, notification)
        engine = NativeLlmEngine(this)
        idleStop = Runnable { if (requests.isEmpty()) stopSelf() }.also { handler.postDelayed(it, 90_000) }
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onDestroy() {
        idleStop?.let(handler::removeCallbacks)
        scope.cancel()
        super.onDestroy()
    }
}
