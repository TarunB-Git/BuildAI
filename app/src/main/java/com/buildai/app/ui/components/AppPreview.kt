package com.buildai.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buildai.app.data.model.DesignDefaults
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.ui.theme.*

/** Readable sample content, rather than miniature unreadable phone chrome. */
@Composable
fun AppPreview(spec: DesignSpec, appName: String, modifier: Modifier = Modifier) {
    val palette = DesignDefaults.palette(spec.paletteId)
    val accent = Color(palette.primary)
    val tabbed = DesignDefaults.template(spec.templateId).hasBottomNav
    Surface(modifier, color = Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Outline)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = accent, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(DesignDefaults.icon(spec.iconKey).vector, null, tint = OnPrimary) }
                }
                Text(appName.ifBlank { "My app" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (tabbed) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                spec.tabs.take(3).forEachIndexed { index, tab -> Surface(color = if (index == 0) accent.copy(alpha = .12f) else SurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Text(tab.name, Modifier.padding(8.dp), color = if (index == 0) accent else OnSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                } }
            }
            if (spec.templateId == "dashboard") Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("12 saved", "3 today").forEach { text -> Surface(color = accent.copy(alpha = .1f), shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)) { Text(text, Modifier.padding(14.dp), color = accent) } }
            }
            listOf("Welcome note" to "A quick example", "Study ideas" to "Things to explore").forEach { (title, detail) ->
                Surface(color = if (spec.templateId == "list") Surface else SurfaceVariant, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Outline), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(DesignDefaults.icon("note").vector, null, tint = accent)
                        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(if (spec.templateId == "journal") "Today · $detail" else detail, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant) }
                    }
                }
            }
        }
    }
}
