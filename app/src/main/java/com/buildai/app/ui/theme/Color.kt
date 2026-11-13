package com.buildai.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** All builder surfaces and the native runner share the selected palette. */
enum class AppTheme(val title: String, val dark: Boolean = false) {
    SEA_BLUE("Sea blue"), FOREST_GREEN("Forest green"), NIGHT_GRAY("Night gray", true);
    companion object { fun read(value: String?) = entries.firstOrNull { it.name == value } ?: SEA_BLUE }
}
data class AppColors(
    val primary: Color, val primaryDim: Color, val onPrimary: Color,
    val secondary: Color, val background: Color, val surface: Color,
    val variant: Color, val card: Color, val outline: Color,
    val ink: Color, val muted: Color, val success: Color, val warning: Color,
    val error: Color, val bubble: Color
)
fun colorsFor(theme: AppTheme): AppColors = when (theme) {
    AppTheme.SEA_BLUE -> AppColors(Color(0xFF096B87), Color(0xFF064C63), Color(0xFFFFF4DC),
        Color(0xFF147B72), Color(0xFFF2E9D6), Color(0xFFF8EFD9), Color(0xFFE6E4D4), Color(0xFFEEE6D2),
        Color(0xFFCACDBB), Color(0xFF153C47), Color(0xFF4D6668), Color(0xFF11694E), Color(0xFF815400), Color(0xFFAB3036), Color(0xFFCCE4DE))
    AppTheme.FOREST_GREEN -> AppColors(Color(0xFF155E3C), Color(0xFF0C4229), Color(0xFFE9F6DF),
        Color(0xFF466D24), Color(0xFFDFEBD8), Color(0xFFEEF6E7), Color(0xFFD2E3C7), Color(0xFFE4EEDD),
        Color(0xFFB8D0AE), Color(0xFF142E20), Color(0xFF48624D), Color(0xFF155E3C), Color(0xFF805600), Color(0xFFAA2B35), Color(0xFFC9E1BB))
    AppTheme.NIGHT_GRAY -> AppColors(Color(0xFFC7D0DC), Color(0xFFE1E7F0), Color(0xFF18202A),
        Color(0xFFABBACD), Color(0xFF14171C), Color(0xFF1C2027), Color(0xFF292F39), Color(0xFF222730),
        Color(0xFF444D5C), Color(0xFFF0F2F6), Color(0xFFB7C0CF), Color(0xFF75D9A3), Color(0xFFF2C576), Color(0xFFFF9AA5), Color(0xFF313B4C))
}
val LocalAppColors = staticCompositionLocalOf { colorsFor(AppTheme.SEA_BLUE) }
val Primary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primary
val PrimaryDim: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primaryDim
val OnPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.onPrimary
val Secondary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.secondary
val OnSecondary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.onPrimary
val Background: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.background
val Surface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
val SurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.variant
val CardBackground: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.card
val Outline: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.outline
val OnBackground: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ink
val OnSurface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ink
val OnSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.muted
val Success: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.success
val Warning: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.warning
val Error: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.error
val UserBubble: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.bubble
val AiBubble: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
