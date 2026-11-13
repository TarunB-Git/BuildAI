package com.buildai.app.data.storage

import android.content.Context

/** Small per-project counts for the launcher widget; generated pages cannot read other app data. */
object WidgetSummaryStore {
    data class Summary(val total: Int, val done: Int)
    private const val PREFS = "project_widget_summary"
    private fun key(id: String, field: String) = "$id.$field"

    fun write(context: Context, projectId: String, total: Int, done: Int) {
        val size = total.coerceIn(0, 100_000)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(key(projectId, "total"), size)
            .putInt(key(projectId, "done"), done.coerceIn(0, size))
            .apply()
    }

    fun read(context: Context, projectId: String): Summary? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(key(projectId, "total"))) return null
        return Summary(prefs.getInt(key(projectId, "total"), 0),
            prefs.getInt(key(projectId, "done"), 0))
    }

    fun delete(context: Context, projectId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(projectId, "total")).remove(key(projectId, "done")).apply()
    }
}
