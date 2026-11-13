package com.buildai.app.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.buildai.app.ui.theme.AppTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Non-secret preferences, observed by every activity so theme changes apply immediately. */
class UiPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("buildai_ui", Context.MODE_PRIVATE)
    fun selectedTheme() = AppTheme.read(prefs.getString("theme", null))
    fun selectTheme(theme: AppTheme) { prefs.edit().putString("theme", theme.name).apply() }
    fun offlineToolsEnabled() = prefs.getBoolean("offline_tools", false)
    fun enableOfflineTools(enabled: Boolean) { prefs.edit().putBoolean("offline_tools", enabled).apply() }
    val offlineTools = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "offline_tools") trySend(offlineToolsEnabled()) }
        prefs.registerOnSharedPreferenceChangeListener(listener); trySend(offlineToolsEnabled())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
    val theme = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "theme") trySend(selectedTheme()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(selectedTheme())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
