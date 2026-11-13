package com.buildai.app.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.buildai.app.data.storage.UiPreferences

/** Match the backing window before the first Compose frame, including saved dark themes. */
fun ComponentActivity.prepareBuildAiWindow() {
    val theme = UiPreferences(this).selectedTheme()
    val transparent = android.graphics.Color.TRANSPARENT
    val style = if (theme.dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    window.setBackgroundDrawable(ColorDrawable(colorsFor(theme).background.toArgb()))
    window.isNavigationBarContrastEnforced = false
    window.isStatusBarContrastEnforced = false
}
private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
@Composable
fun BuildAITheme(themeOverride: AppTheme? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { UiPreferences(context) }
    val selected by preferences.theme.collectAsState(preferences.selectedTheme())
    val theme = themeOverride ?: selected
    val c = colorsFor(theme)
    val base = if (theme.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(primary = c.primary, onPrimary = c.onPrimary,
        primaryContainer = c.bubble, onPrimaryContainer = c.primaryDim,
        secondary = c.secondary, onSecondary = c.onPrimary,
        secondaryContainer = c.variant, onSecondaryContainer = c.ink,
        background = c.background, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink, surfaceVariant = c.variant,
        onSurfaceVariant = c.muted, error = c.error, onError = c.background,
        outline = c.muted, outlineVariant = c.outline, surfaceTint = c.surface,
        surfaceContainer = c.card, surfaceContainerLow = c.surface,
        surfaceContainerLowest = c.surface, surfaceContainerHigh = c.variant,
        surfaceContainerHighest = c.variant)
    LaunchedEffect(theme, context) {
        context.activity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !theme.dark
                isAppearanceLightNavigationBars = !theme.dark
            }
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
            window.setBackgroundDrawable(ColorDrawable(c.background.toArgb()))
        }
    }
    CompositionLocalProvider(LocalAppColors provides c, LocalContentColor provides c.ink) {
        MaterialTheme(colorScheme = scheme, typography = Typography,
            shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)), content = content)
    }
}
