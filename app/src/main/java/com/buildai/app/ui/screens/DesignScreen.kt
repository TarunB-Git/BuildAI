package com.buildai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.buildai.app.data.model.AppTemplate
import com.buildai.app.data.model.ColorPalette
import com.buildai.app.data.model.DesignDefaults
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.ui.components.AppPreview
import com.buildai.app.ui.theme.Background
import com.buildai.app.ui.theme.CardBackground
import com.buildai.app.ui.theme.OnBackground
import com.buildai.app.ui.theme.OnSurface
import com.buildai.app.ui.theme.OnSurfaceVariant
import com.buildai.app.ui.theme.Primary
import com.buildai.app.ui.theme.Surface
import com.buildai.app.ui.theme.SurfaceVariant
import com.buildai.app.viewmodel.ConversationViewModel
import com.buildai.app.viewmodel.DesignViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DesignScreen(
    conversationVm: ConversationViewModel,
    designVm: DesignViewModel,
    onBuild: () -> Unit,
    onBack: () -> Unit,
    onStartOver: () -> Unit,
    onEditIdea: () -> Unit,
    appearanceOnly: Boolean = false, nativeScreens: List<String>? = null, buildAfterSave: Boolean = false,
    nativeSpec: com.buildai.app.runtime.AppSpec? = null
) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val spec     by designVm.spec.collectAsState()
    val saveError by designVm.error.collectAsState()
    val plan     by conversationVm.projectPlan.collectAsState()
    val brief by conversationVm.brief.collectAsState()
    val appName  = nativeSpec?.name ?: plan?.appName ?: brief?.plan?.name ?: "My App"
    val template = DesignDefaults.template(spec.templateId)
    val nativeMode = nativeSpec != null || nativeScreens != null
    val nativeDestinations = nativeSpec?.let { com.buildai.app.runtime.NativeNavigation.topLevelScreens(it) }
    val tabCount = nativeDestinations?.size ?: nativeScreens?.size ?: 0
    LaunchedEffect(nativeSpec, spec.templateId) {
        if (nativeSpec != null && spec.templateId == "tabbed") designVm.setNativeTabs(nativeSpec)
    }

    var resetConfirm by rememberSaveable { mutableStateOf(false) }
    var discardConfirm by rememberSaveable { mutableStateOf(false) }
    val savedSpec = conversationVm.currentProject()?.designSpec ?: DesignSpec()
    fun leave() { if (appearanceOnly && spec != savedSpec) discardConfirm = true else onBack() }
    androidx.activity.compose.BackHandler { leave() }
    if (resetConfirm) AlertDialog(onDismissRequest = { resetConfirm = false }, title = { Text("Reset appearance?") },
        text = { Text("Return the layout, colors, icon, and tabs to their defaults. Save to apply these changes.") },
        confirmButton = { TextButton(onClick = { designVm.reset(); resetConfirm = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("Cancel") } })
    if (discardConfirm) AlertDialog(onDismissRequest = { discardConfirm = false }, title = { Text("Save appearance changes?") },
        text = { Text(if (nativeSpec != null) "Save these settings for your running app." else "These settings will be used the next time you build this app.") },
        confirmButton = { TextButton(onClick = { if (designVm.applyDraft()) { discardConfirm = false; onBack() } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { discardConfirm = false; onBack() }) { Text("Discard") } })
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Appearance",
                            style = MaterialTheme.typography.titleMedium.copy(color = OnBackground))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { resetConfirm = true }) {
                        Icon(Icons.Default.Refresh, "Reset", tint = OnSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background.copy(alpha = .72f))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            saveError?.let { Text(it, color = com.buildai.app.ui.theme.Error, modifier = Modifier.padding(20.dp)) }
            if (!appearanceOnly) TextButton(onClick = onEditIdea, modifier = Modifier.fillMaxWidth()) { Text("Edit idea and requirements", color = Primary) }
            // ── Live preview ──────────────────────────────────────────────────
            PreviewSection(spec = spec, appName = appName)

            // ── Template picker ───────────────────────────────────────────────
            SectionHeader("Layout")
            TemplatePicker(
                templates   = if (!nativeMode) DesignDefaults.TEMPLATES else DesignDefaults.TEMPLATES.filter { it.id in listOf("list", "cards", "tabbed") && (it.id != "tabbed" || tabCount <= 5) }.map { if (it.id == "cards") it.copy(name = "Cards", description = "Comfortable record cards") else it },
                selectedId  = spec.templateId,
                onSelect    = { id -> designVm.selectTemplate(id); if (id == "tabbed") { if (nativeSpec != null) designVm.setNativeTabs(nativeSpec, reset = true) else if (nativeScreens != null) designVm.setNativeTabs(nativeScreens) } }
            )
            if (nativeMode && tabCount > 5) Text("This app has $tabCount main screens. Screen buttons keep every section reachable; bottom tabs support up to five.", Modifier.padding(horizontal = 20.dp), color = OnSurfaceVariant)

            // ── Colour palette ────────────────────────────────────────────────
            SectionHeader("Colours")
            PalettePicker(
                palettes   = DesignDefaults.PALETTES,
                selectedId = spec.paletteId,
                onSelect   = designVm::selectPalette
            )

            // ── App icon ──────────────────────────────────────────────────────
            SectionHeader("Icon")
            IconPicker(
                icons      = DesignDefaults.ICONS,
                selectedKey = spec.iconKey,
                onSelect   = designVm::selectIcon
            )

            // ── Tab editor (only for tabbed templates) ─────────────────────────
            if (template.hasBottomNav) {
                SectionHeader("Tabs")
                if (nativeSpec != null) Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Detail screens open through their connected actions.", color = OnSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    spec.tabs.forEachIndexed { index, tab ->
                        val title = nativeDestinations?.firstOrNull { it.id == tab.screenId }?.title ?: tab.name
                        OutlinedTextField(tab.name, { designVm.updateTabName(index, it) }, label = { Text(title) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    }
                } else if (nativeScreens != null) Column(Modifier.padding(horizontal = 20.dp)) {
                    nativeScreens.take(5).forEachIndexed { index, title -> OutlinedTextField(spec.tabs.getOrNull(index)?.name ?: title, { designVm.updateTabName(index, it) }, label = { Text("Screen ${index + 1}") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) }
                } else TabEditor(
                    tabs     = spec.tabs,
                    onAdd    = designVm::addTab,
                    onRemove = designVm::removeTab,
                    onRename = designVm::updateTabName,
                    onIcon   = designVm::updateTabIcon
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── CTA ───────────────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Button(
                    onClick  = { focus.clearFocus(); keyboard?.hide(); if (!appearanceOnly || designVm.applyDraft()) onBuild() },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (buildAfterSave) "Save & build" else if (appearanceOnly) "Save appearance" else "Build app", modifier = Modifier.padding(vertical = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
                if (!appearanceOnly) TextButton(onClick = onStartOver, modifier = Modifier.fillMaxWidth()) {
                    Text("Start a new idea", color = OnSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Preview section ───────────────────────────────────────────────────────────

@Composable
private fun PreviewSection(spec: DesignSpec, appName: String) {
    val palette = DesignDefaults.palette(spec.paletteId)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        color  = CardBackground,
        shape  = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Style preview · example content",
                style    = MaterialTheme.typography.labelMedium.copy(color = OnSurfaceVariant),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            AppPreview(
                spec    = spec,
                appName = appName,
                modifier = Modifier
                    .fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            // Colour preview dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(palette.primary)))
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(palette.accent)))
                Spacer(Modifier.width(4.dp))
                Text(palette.name, style = MaterialTheme.typography.labelMedium.copy(color = OnSurfaceVariant))
            }
        }
    }
}

// ── Template picker ───────────────────────────────────────────────────────────

@Composable
private fun TemplatePicker(
    templates: List<AppTemplate>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        contentPadding     = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        items(templates) { tmpl ->
            val isSelected = tmpl.id == selectedId
            Surface(
                modifier = Modifier
                    .width(130.dp)
                    .clickable { onSelect(tmpl.id) }
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) Primary else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    ),
                color = if (isSelected) Primary.copy(alpha = 0.08f) else CardBackground,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Mini template sketch
                    TemplateSketch(tmpl, isSelected)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        tmpl.name,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isSelected) Primary else OnBackground,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    )
                    Text(
                        tmpl.description,
                        style    = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateSketch(tmpl: AppTemplate, isSelected: Boolean) {
    val lineColor = if (isSelected) Primary.copy(alpha = 0.5f) else SurfaceVariant
    val barColor  = if (isSelected) Primary else SurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Background)
            .padding(4.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Header bar
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
            when (tmpl.id) {
                "cards" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) {
                            Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(3.dp)).background(lineColor))
                        }
                    }
                }
                "dashboard" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) {
                            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(lineColor))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp)).background(lineColor.copy(alpha = 0.5f)))
                }
                else -> {
                    repeat(3) {
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp)).background(lineColor))
                    }
                }
            }
            if (tmpl.hasBottomNav) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(1.dp)).background(barColor.copy(alpha = 0.4f)))
            }
        }
    }
}

// ── Palette picker ────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PalettePicker(
    palettes: List<ColorPalette>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    FlowRow(
        modifier             = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement  = Arrangement.spacedBy(10.dp)
    ) {
        palettes.forEach { palette ->
            val isSelected = palette.id == selectedId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(palette.id) })
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(Color(palette.primary), Color(palette.accent))
                            )
                        )
                        .then(
                            if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                            else Modifier
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    palette.name,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isSelected) Primary else OnSurfaceVariant
                    )
                )
            }
        }
    }
}

// ── Icon picker ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(
    icons: List<com.buildai.app.data.model.AppIcon>,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    FlowRow(
        modifier             = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement  = Arrangement.spacedBy(8.dp)
    ) {
        icons.forEach { icon ->
            val isSelected = icon.key == selectedKey
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Primary.copy(alpha = 0.15f) else CardBackground)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) Primary else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(icon.key) }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon.vector,
                    contentDescription = icon.label,
                    tint               = if (isSelected) Primary else OnSurfaceVariant,
                    modifier           = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ── Tab editor ────────────────────────────────────────────────────────────────

@Composable
private fun TabEditor(
    tabs: List<com.buildai.app.data.model.TabSpec>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
    onIcon: (Int, String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        color = CardBackground,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tabs.forEachIndexed { i, tab ->
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var choosingIcon by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { choosingIcon = true }) { Icon(DesignDefaults.icon(tab.iconKey).vector, "Change ${tab.name} icon") }
                        DropdownMenu(expanded = choosingIcon, onDismissRequest = { choosingIcon = false }) {
                            DesignDefaults.ICONS.forEach { icon -> DropdownMenuItem(text = { Text(icon.label) }, leadingIcon = { Icon(icon.vector, null) }, onClick = { onIcon(i, icon.key); choosingIcon = false }) }
                        }
                    }
                    // Tab number badge
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium.copy(color = Primary))
                    }

                    // Name field
                    OutlinedTextField(
                        value         = tab.name,
                        onValueChange = { onRename(i, it) },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        textStyle     = MaterialTheme.typography.bodyMedium.copy(color = OnBackground),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape         = RoundedCornerShape(10.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Primary,
                            unfocusedBorderColor    = SurfaceVariant,
                            focusedContainerColor   = Surface,
                            unfocusedContainerColor = Surface
                        )
                    )

                    // Remove button (only if more than 1 tab)
                    if (tabs.size > 1) {
                        IconButton(onClick = { onRemove(i) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, "Remove tab", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Add tab button (max 5)
            if (tabs.size < 5) {
                TextButton(
                    onClick  = onAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, tint = Primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add tab", color = Primary)
                }
            }

            Text(
                "Up to 5 tabs. Use short, descriptive names that explain where each tab goes.",
                style    = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style    = MaterialTheme.typography.titleMedium.copy(color = OnBackground, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp)
    )
}
