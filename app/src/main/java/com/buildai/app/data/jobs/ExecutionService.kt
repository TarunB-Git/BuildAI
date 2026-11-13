package com.buildai.app.data.jobs

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.buildai.app.R
import com.buildai.app.data.storage.BuildAiDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Keeps user-started workflows alive across screens; jobs themselves remain in Room. */
class ExecutionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    companion object {
        fun keepRunning(context: Context) = ContextCompat.startForegroundService(context, Intent(context, ExecutionService::class.java))
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("agent_jobs", "Assistant activity", NotificationManager.IMPORTANCE_LOW))
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launch?.let { PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_IMMUTABLE) }
        val notification = NotificationCompat.Builder(this, "agent_jobs")
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle("BuildAI work is running")
            .setContentText("Return to Activity to review or stop your work").setOngoing(true)
            .apply { pending?.let(::setContentIntent) }.build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(2002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(2002, notification)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BuildAI:UserWork")
            .apply { setReferenceCounted(false); acquire() }
        scope.launch {
            BuildAiDatabase.get(this@ExecutionService).jobs().observe().collect { jobs ->
                if (jobs.none { it.status == "RUNNING" || it.status == "QUEUED" }) stopSelf()
            }
        }
    }
    override fun onBind(intent: Intent): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
