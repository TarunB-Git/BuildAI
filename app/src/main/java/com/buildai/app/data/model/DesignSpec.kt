package com.buildai.app.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import org.json.JSONObject

// ── Template ──────────────────────────────────────────────────────────────────

data class AppTemplate(
    val id: String,
    val name: String,
    val description: String,
    val hasBottomNav: Boolean,
    val defaultTabNames: List<String>
)

// ── Color palette ─────────────────────────────────────────────────────────────

data class ColorPalette(
    val id: String,
    val name: String,
    val primary: Long,
    val accent: Long
)

// ── Icon ──────────────────────────────────────────────────────────────────────

data class AppIcon(
    val key: String,
    val label: String,
    val vector: ImageVector
)

// ── Tab ───────────────────────────────────────────────────────────────────────

data class TabSpec(
    val name: String,
    val iconKey: String = "home",
    val screenId: String = ""
)

// ── Design spec (the full user selection) ─────────────────────────────────────

data class DesignSpec(
    val templateId: String  = "list",
    val paletteId: String   = "ocean",
    val iconKey: String     = "star",
    val tabs: List<TabSpec> = listOf(TabSpec("Home", "home"))
) {
    fun toJson() = JSONObject().apply {
        put("templateId", templateId); put("paletteId", paletteId); put("iconKey", iconKey)
        put("tabs", JSONArray(tabs.map { tab -> JSONObject().put("name", tab.name).put("iconKey", tab.iconKey)
            .also { if (tab.screenId.isNotBlank()) it.put("screenId", tab.screenId) } }))
    }
    companion object {
        fun fromJson(json: JSONObject): DesignSpec {
            val defaults = DesignSpec()
            val tabs = json.optJSONArray("tabs")?.let { values ->
                (0 until values.length()).map { i ->
                    val tab = values.getJSONObject(i)
                    TabSpec(tab.optString("name", "Home"), tab.optString("iconKey", "home"), tab.optString("screenId"))
                }.let { tabs -> tabs.take(if (tabs.all { it.screenId.isNotBlank() }) 12 else 5) }.ifEmpty { defaults.tabs }
            } ?: defaults.tabs
            return DesignSpec(
                templateId = json.optString("templateId", defaults.templateId),
                paletteId = json.optString("paletteId", defaults.paletteId),
                iconKey = json.optString("iconKey", defaults.iconKey), tabs = tabs
            )
        }
    }
}

// ── Predefined options ────────────────────────────────────────────────────────

object DesignDefaults {

    val TEMPLATES = listOf(
        AppTemplate(
            id = "list", name = "Simple List",
            description = "Scroll through items, tap for details",
            hasBottomNav = false,
            defaultTabNames = listOf("Home")
        ),
        AppTemplate(
            id = "tabbed", name = "Tabbed",
            description = "Multiple sections with a bottom menu",
            hasBottomNav = true,
            defaultTabNames = listOf("Home", "Add", "Settings")
        ),
        AppTemplate(
            id = "cards", name = "Card Grid",
            description = "Items shown as cards in a grid",
            hasBottomNav = false,
            defaultTabNames = listOf("Home")
        ),
        AppTemplate(
            id = "journal", name = "Journal",
            description = "Dated entries with a history view",
            hasBottomNav = true,
            defaultTabNames = listOf("Today", "History")
        ),
        AppTemplate(
            id = "dashboard", name = "Dashboard",
            description = "Stats overview with quick actions",
            hasBottomNav = false,
            defaultTabNames = listOf("Home")
        )
    )

    val PALETTES = listOf(
        ColorPalette("ocean",    "Ocean",    0xFF096B87L, 0xFF85BCB0L),
        ColorPalette("forest",   "Forest",   0xFF155E3CL, 0xFF91BD78L),
        ColorPalette("sunset",   "Sunset",   0xFFEA580CL, 0xFF9333EAL),
        ColorPalette("berry",    "Berry",    0xFF9333EAL, 0xFFEC4899L),
        ColorPalette("minimal",  "Minimal",  0xFF374151L, 0xFF9CA3AFL),
        ColorPalette("midnight", "Midnight", 0xFF1E40AFL, 0xFF22D3EEL),
        ColorPalette("earth",    "Earth",    0xFF92400EL, 0xFF16A34AL),
        ColorPalette("rose",     "Rose",     0xFFBE185DL, 0xFFF43F5EL)
    )

    val ICONS = listOf(
        AppIcon("restaurant", "Food",     Icons.Default.Restaurant),
        AppIcon("book",       "Book",     Icons.Default.Book),
        AppIcon("favorite",   "Heart",    Icons.Default.Favorite),
        AppIcon("car",        "Car",      Icons.Default.DirectionsCar),
        AppIcon("fitness",    "Fitness",  Icons.Default.FitnessCenter),
        AppIcon("cart",       "Shopping", Icons.Default.ShoppingCart),
        AppIcon("home",       "Home",     Icons.Default.Home),
        AppIcon("star",       "Star",     Icons.Default.Star),
        AppIcon("flight",     "Travel",   Icons.Default.Flight),
        AppIcon("pets",       "Pets",     Icons.Default.Pets),
        AppIcon("work",       "Work",     Icons.Default.Work),
        AppIcon("school",     "School",   Icons.Default.School),
        AppIcon("cafe",       "Café",     Icons.Default.LocalCafe),
        AppIcon("gaming",     "Gaming",   Icons.Default.SportsEsports),
        AppIcon("health",     "Health",   Icons.Default.Healing),
        AppIcon("nature",     "Nature",   Icons.Default.Nature),
        AppIcon("camera",     "Camera",   Icons.Default.CameraAlt),
        AppIcon("music",      "Music",    Icons.Default.MusicNote),
        AppIcon("check",      "Tasks",    Icons.Default.CheckCircle),
        AppIcon("timer",      "Timer",    Icons.Default.Timer),
        AppIcon("map",        "Map",      Icons.Default.Map),
        AppIcon("note",       "Notes",    Icons.Default.NoteAlt),
        AppIcon("calendar",   "Calendar", Icons.Default.DateRange),
        AppIcon("search",     "Search",   Icons.Default.Search),
        AppIcon("settings",   "Settings", Icons.Default.Settings),
        AppIcon("add",        "Add",      Icons.Default.AddCircle)
    )

    // Sensible default icon per tab position so preview doesn't show three Home icons
    fun defaultTabIcon(tabName: String, fallbackIndex: Int): String = when {
        tabName.lowercase().contains("home")     -> "home"
        tabName.lowercase().contains("add")      ||
        tabName.lowercase().contains("new")      ||
        tabName.lowercase().contains("create")   -> "add"
        tabName.lowercase().contains("setting")  ||
        tabName.lowercase().contains("profile")  ||
        tabName.lowercase().contains("account")  -> "settings"
        tabName.lowercase().contains("search")   -> "search"
        tabName.lowercase().contains("histor")   ||
        tabName.lowercase().contains("archive")  -> "calendar"
        tabName.lowercase().contains("today")    ||
        tabName.lowercase().contains("now")      -> "timer"
        tabName.lowercase().contains("note")     -> "note"
        tabName.lowercase().contains("favorit")  ||
        tabName.lowercase().contains("saved")    -> "favorite"
        else -> listOf("home","star","check","calendar","note")[fallbackIndex.coerceIn(0,4)]
    }

    // Quick lookup helpers
    fun palette(id: String)  = PALETTES.firstOrNull { it.id == id } ?: PALETTES[0]
    fun template(id: String) = TEMPLATES.firstOrNull { it.id == id } ?: TEMPLATES[0]
    fun icon(key: String)    = ICONS.firstOrNull { it.key == key } ?: ICONS[7] // star fallback
}
