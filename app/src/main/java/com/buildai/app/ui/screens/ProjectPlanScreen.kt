package com.buildai.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.buildai.app.data.model.*
import com.buildai.app.ui.theme.*
import com.buildai.app.viewmodel.ConversationViewModel
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectPlanScreen(viewModel: ConversationViewModel, onDesignApp: () -> Unit, onStartOver: () -> Unit,
    onBack: () -> Unit, onEditIdea: () -> Unit, embedded: Boolean = false, onBuild: (() -> Unit)? = null, building: Boolean = false, onAppearanceOnly: () -> Unit = onDesignApp) {
    val plan by viewModel.projectPlan.collectAsState()
    val p = plan ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { com.buildai.app.data.storage.UiPreferences(context) }
    val offline by preferences.offlineTools.collectAsState(preferences.offlineToolsEnabled())
    val initial = remember(viewModel.projectId) { runCatching { JSONObject(viewModel.currentProject()?.legacyPlanDraft.orEmpty()) }.getOrNull() ?: p.toJson() }
    fun initialLines(key: String) = initial.optJSONArray(key)?.let { a -> (0 until a.length()).joinToString("\n") { a.getString(it) } }.orEmpty()
    var name by rememberSaveable(viewModel.projectId) { mutableStateOf(initial.optString("appName", p.appName)) }
    var description by rememberSaveable(viewModel.projectId) { mutableStateOf(initial.optString("description", p.description)) }
    var features by rememberSaveable(viewModel.projectId) { mutableStateOf(initialLines("features")) }
    var screens by rememberSaveable(viewModel.projectId) { mutableStateOf(initialLines("screens")) }
    var audience by rememberSaveable(viewModel.projectId) { mutableStateOf(initial.optString("targetUser", p.targetUser)) }
    var mode by rememberSaveable(viewModel.projectId) { mutableStateOf(runCatching { BuildMode.valueOf(initial.optString("buildMode")) }.getOrDefault(p.buildMode)) }
    var starter by rememberSaveable(viewModel.projectId) { mutableStateOf(runCatching { QuickTemplate.valueOf(initial.optString("quickTemplate")) }.getOrDefault(p.quickTemplate)) }
    fun lines(text: String) = text.lines().map(String::trim).filter(String::isNotBlank).distinct()
    val valid = name.isNotBlank() && description.isNotBlank() && (mode == BuildMode.TEMPLATE || lines(features).isNotEmpty() && lines(screens).isNotEmpty())
    fun persistDraft() { viewModel.saveLegacyPlanDraft(p.toJson().put("appName", name).put("description", description).put("targetUser", audience)
        .put("features", JSONArray(features.lines())).put("screens", JSONArray(screens.lines())).put("buildMode", mode.name).put("quickTemplate", starter.name).toString()) }
    fun commit() { persistDraft(); if (valid) viewModel.updatePlan(p.copy(appName = name.trim(), description = description.trim(), targetUser = audience.trim().ifBlank { "You" },
        features = if (mode == BuildMode.TEMPLATE) starter.features else lines(features), screens = if (mode == BuildMode.TEMPLATE) listOf("Home") else lines(screens), buildMode = mode, quickTemplate = starter)) }
    LaunchedEffect(name, description, features, screens, audience, mode, starter) { persistDraft() }
    if (!embedded) BackHandler { persistDraft(); onBack() }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = { if (!embedded) TopAppBar(title = { Text("App plan") }, navigationIcon = { IconButton(onClick = { persistDraft(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
        bottomBar = { Surface(color = Surface) { Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!valid) Text("Add an app name, purpose, feature and screen to continue.", color = Error)
            Button(onClick = { commit(); viewModel.confirmPlan(); (onBuild ?: onDesignApp)() }, enabled = valid && !building, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (building) "Build in progress" else if (onBuild != null) "Build app" else "Continue to appearance") }
            TextButton(onClick = { persistDraft(); onEditIdea() }, modifier = Modifier.fillMaxWidth()) { Text("Add an idea in chat") }
        } } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Shape what your app will do", style = MaterialTheme.typography.headlineMedium)
            Text("Draft changes are saved as you type. Your current app stays available until a build finishes.", color = OnSurfaceVariant)
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("App name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("What the app does") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            if (mode == BuildMode.CUSTOM) {
                OutlinedTextField(features, { features = it }, label = { Text("Features — one per line") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(screens, { screens = it }, label = { Text("Screens — one per line") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
            var details by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Who it’s for") }
            if (details) OutlinedTextField(audience, { audience = it }, label = { Text("Audience") }, modifier = Modifier.fillMaxWidth())
            if (offline || mode == BuildMode.TEMPLATE) { Text("Build approach", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(mode == BuildMode.CUSTOM, onClick = { mode = BuildMode.CUSTOM }, label = { Text("My idea") })
                FilterChip(mode == BuildMode.TEMPLATE, onClick = { mode = BuildMode.TEMPLATE }, label = { Text("Offline starter") })
            }
            }
            if (mode == BuildMode.TEMPLATE) {
                Text("A starter implements exactly these features. Your custom draft is kept if you switch back.", color = OnSurfaceVariant)
                if (offline) QuickTemplate.entries.forEach { option -> FilterChip(starter == option, onClick = { starter = option }, label = { Text(option.label) }) }
                starter.features.forEach { Text("• $it") }
            }
            OutlinedButton(onClick = { commit(); onAppearanceOnly() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Appearance · optional") }
        }
    }
}
