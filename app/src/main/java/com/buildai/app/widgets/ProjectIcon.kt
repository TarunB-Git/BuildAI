package com.buildai.app.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.buildai.app.data.model.DesignDefaults
import com.buildai.app.data.model.DesignSpec

/** Simple, locally drawn icon shared by project shortcuts and widget cards. */
object ProjectIcon {
    fun glyph(key: String): String = when (key) {
        "note" -> "✎"; "check" -> "✓"; "calendar" -> "31"; "timer" -> "◷"
        "book" -> "▤"; "favorite" -> "♥"; "fitness" -> "◆"; "restaurant" -> "♨"
        "music" -> "♫"; "camera" -> "◉"; "map" -> "⌖"; "search" -> "⌕"
        "nature" -> "♣"; "pets" -> "●"; "cart" -> "▣"; "school" -> "▤"
        "home" -> "⌂"; "star" -> "★"; else -> "✦"
    }

    fun bitmap(design: DesignSpec, size: Int = 192): Bitmap {
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = DesignDefaults.palette(design.paletteId).primary.toInt()
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * .22f, size * .22f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * .53f
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        val glyph = glyph(design.iconKey)
        canvas.drawText(glyph, size / 2f, size / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
        return result
    }
}
