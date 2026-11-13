package com.buildai.app.ui

import com.buildai.app.data.model.*
import com.buildai.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class MobileDesignTest {
    @Test fun oldProjectsRemainReadableAndUnfinishedDraftsRoundTrip() {
        val project = Conversation(title = "My project", titleOwned = true, workspaceTab = "plan", legacyPlanDraft = "{\"appName\":\"\",\"features\":[]}", draftText = "Next idea")
        assertEquals(project, Conversation.fromJson(project.toJson()))
        val old = project.toJson().apply { remove("titleOwned"); remove("workspaceTab"); remove("legacyPlanDraft") }
        val restored = Conversation.fromJson(old)
        assertFalse(restored.titleOwned); assertEquals("chat", restored.workspaceTab); assertEquals("", restored.legacyPlanDraft)
    }
    @Test fun explicitNamesSurviveNewMessages() {
        val renamed = Conversation(title = "My toolbox", titleOwned = true, messages = listOf(Message(role = MessageRole.USER, content = "A different request")))
        assertEquals("My toolbox", renamed.withDerivedTitle().displayName)
        assertEquals("A different request", renamed.copy(titleOwned = false).withDerivedTitle().displayName)
    }
    private fun luminance(c: androidx.compose.ui.graphics.Color): Double {
        fun channel(v: Float) = if (v <= .04045f) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
        return .2126 * channel(c.red) + .7152 * channel(c.green) + .0722 * channel(c.blue)
    }
    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    @Test fun allThemesMeetNormalTextContrastOnTheirSharedSurfaces() {
        AppTheme.entries.forEach { theme ->
            val c = colorsFor(theme)
            listOf(c.background, c.surface, c.card, c.variant, c.bubble).forEach { surface ->
                assertTrue("${theme.title} text", contrast(c.ink, surface) >= 4.5)
                assertTrue("${theme.title} secondary text", contrast(c.muted, surface) >= 4.5)
            }
            assertTrue("${theme.title} button text", contrast(c.primary, c.onPrimary) >= 4.5)
        }
        assertEquals(AppTheme.SEA_BLUE, AppTheme.read("unknown"))
    }
    @Test fun generatedAppButtonTextContrastsWithEveryPaletteInLightAndDarkThemes() {
        for (base in listOf(androidx.compose.material3.lightColorScheme(), androidx.compose.material3.darkColorScheme())) {
            DesignDefaults.PALETTES.forEach { palette ->
                val scheme = com.buildai.app.runtime.NativeAppearance.scheme(base, palette)
                assertTrue(palette.name, contrast(scheme.primary, scheme.onPrimary) >= 4.5)
                assertTrue(palette.name, contrast(scheme.secondary, scheme.onSecondary) >= 4.5)
            }
        }
    }
}
