package com.buildai.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.buildai.app.MainActivity
import com.buildai.app.R
import com.buildai.app.data.model.DesignDefaults
import com.buildai.app.data.storage.ConversationRepository
import com.buildai.app.data.storage.WidgetSummaryStore

/** A real Android home-screen widget that opens one saved local app. */
class ProjectWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_PINNED = "com.buildai.app.ACTION_PROJECT_WIDGET_PINNED"
        private const val PREFS = "project_widgets"
        private fun key(id: Int) = "project_$id"

        fun refreshProject(context: Context, projectId: String) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, ProjectWidgetProvider::class.java))
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ids.filter { prefs.getString(key(it), null) in listOf(null, projectId) }
                .forEach { update(context, manager, it) }
        }

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val repo = ConversationRepository(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val savedId = prefs.getString(key(widgetId), null)
            val project = if (savedId != null) repo.load(savedId)
                else repo.listAll().firstOrNull { com.buildai.app.data.storage.BuildAiDatabase.get(context).nativeApps().head(it.id) != null || repo.generatedFile(it.id) != null }
            // Widgets added from the launcher must keep their original project after later saves.
            if (savedId == null && project != null) prefs.edit().putString(key(widgetId), project.id).apply()
            val title = project?.displayName ?: "BuildAI"
            val spec = project?.designSpec
            val summary = project?.let { WidgetSummaryStore.read(context, it.id) }
            val views = RemoteViews(context.packageName, R.layout.widget_project).apply {
                setTextViewText(R.id.widget_title, title.take(35))
                setTextViewText(R.id.widget_hint, when {
                    project != null && summary != null -> "${summary.done} of ${summary.total} done · tap to open"
                    project != null -> "Tap to open your app"
                    savedId != null -> "This project was deleted"
                    else -> "Create a local app"
                })
                setTextViewText(R.id.widget_icon, ProjectIcon.glyph(spec?.iconKey ?: "star"))
                setInt(R.id.widget_icon, "setBackgroundColor",
                    DesignDefaults.palette(spec?.paletteId ?: "ocean").primary.toInt())
                val launch = project?.let { ProjectHomeActions.projectIntent(context, it.id) }
                    ?: Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(context, widgetId, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                setOnClickPendingIntent(R.id.widget_root, pending)
            }
            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PINNED) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val projectId = intent.getStringExtra(MainActivity.EXTRA_PROJECT_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID && projectId != null) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(id), projectId).apply()
                update(context, AppWidgetManager.getInstance(context), id)
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { edit.remove(key(it)) }
        edit.apply()
    }
}
