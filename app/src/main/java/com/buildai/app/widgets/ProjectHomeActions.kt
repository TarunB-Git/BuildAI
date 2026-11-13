package com.buildai.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.widget.Toast
import com.buildai.app.MainActivity
import com.buildai.app.data.model.DesignSpec

object ProjectHomeActions {
    fun requestShortcut(context: Context, projectId: String, name: String, design: DesignSpec) {
        val result = runCatching { pinShortcut(context, projectId, name, design) }
        Toast.makeText(context, result.fold(
            onSuccess = { if (it) "Confirm the icon in your launcher" else "Your launcher does not support pinned shortcuts" },
            onFailure = { "The shortcut could not be added. Try again from your launcher." }), Toast.LENGTH_LONG).show()
    }

    fun requestWidget(context: Context, projectId: String) {
        val result = runCatching { pinWidget(context, projectId) }
        Toast.makeText(context, result.fold(
            onSuccess = { if (it) "Confirm the widget in your launcher" else "Your launcher does not support pinned widgets" },
            onFailure = { "The widget could not be added. Try again from your launcher." }), Toast.LENGTH_LONG).show()
    }

    fun projectIntent(context: Context, projectId: String) = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(MainActivity.EXTRA_PROJECT_ID, projectId)
        putExtra(MainActivity.EXTRA_OPEN_APP, true)
        data = android.net.Uri.parse("buildai://project/$projectId")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    fun pinShortcut(context: Context, projectId: String, name: String, design: DesignSpec): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        val shortcut = ShortcutInfo.Builder(context, "project-$projectId")
            .setShortLabel(name.take(25).ifBlank { "My app" })
            .setLongLabel(name.take(100).ifBlank { "My app" })
            .setIcon(Icon.createWithBitmap(ProjectIcon.bitmap(design)))
            .setIntent(projectIntent(context, projectId))
            .build()
        return manager.requestPinShortcut(shortcut, null)
    }

    fun pinWidget(context: Context, projectId: String): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val callback = Intent(context, ProjectWidgetProvider::class.java).apply {
            action = ProjectWidgetProvider.ACTION_PINNED
            putExtra(MainActivity.EXTRA_PROJECT_ID, projectId)
            data = android.net.Uri.parse("buildai://pin-widget/$projectId")
        }
        val pending = PendingIntent.getBroadcast(context, projectId.hashCode(), callback,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        return manager.requestPinAppWidget(ComponentName(context, ProjectWidgetProvider::class.java), null, pending)
    }
}
