package com.buildai.app.runtime

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.buildai.app.data.model.ColorPalette

object NativeAppearance {
    fun scheme(base: ColorScheme, palette: ColorPalette): ColorScheme {
        val primary = Color(palette.primary); val secondary = Color(palette.accent)
        fun foreground(color: Color) = if (color.luminance() > .179f) Color.Black else Color.White
        return base.copy(primary = primary, onPrimary = foreground(primary), secondary = secondary, onSecondary = foreground(secondary))
    }
}
