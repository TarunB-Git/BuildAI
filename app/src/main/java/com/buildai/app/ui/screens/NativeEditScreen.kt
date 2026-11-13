package com.buildai.app.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.buildai.app.BuildAiApplication
import com.buildai.app.builder.EditScope
import com.buildai.app.data.storage.BuildAiDatabase
import com.buildai.app.runtime.*
import com.buildai.app.ui.theme.*
import com.buildai.app.util.*
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeEditScreen(app: BuildAiApplication, id: String, onBack: () -> Unit, onOpenApp: (String) -> Unit,
    embedded: Boolean = false, onConversation: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    val repo = remember { NativeAppRepository(app) }
    val heads by remember { repo.dao.observeHeads() }.collectAsState(emptyList())
    val initialHead = remember(repo, id) { repo.dao.head(id) }
    val head = heads.firstOrNull { it.appId == id } ?: initialHead
    val definition = remember(head?.revisionId) { repo.spec(id) } ?: return
    val jobs by remember { BuildAiDatabase.get(app).jobs().observe() }.collectAsState(emptyList())
    val job = jobs.firstOrNull { it.projectId == id && it.kind == "NATIVE_EDIT" }
    val running = job?.status in listOf("RUNNING", "QUEUED")
    val project = remember(id) { app.container.projects.load(id) } ?: return
    var request by rememberSaveable(id) { mutableStateOf(project.nativeEditDraft) }
    var scopeJson by rememberSaveable(id) { mutableStateOf(project.nativeEditScope.ifBlank { EditScope(definition.startScreen, definition.screens.first { it.id == definition.startScreen }.components.map { it.id }.toSet()).json().toString() }) }
    val savedScope = runCatching { EditScope.read(JSONObject(scopeJson)) }.getOrNull()
    val selectedScreen = definition.screens.firstOrNull { it.id == savedScope?.screenId } ?: definition.screens.first()
    val target = EditScope(selectedScreen.id, savedScope?.componentIds.orEmpty().filter { id -> selectedScreen.components.any { it.id == id } }.toSet())
    var error by remember { mutableStateOf<String?>(null) }
    var advanced by rememberSaveable(id) { mutableStateOf(false) }
    var screens by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val speech = remember { SpeechManager(context) }
    val speechState by speech.speechState.collectAsState()
    val listening = speechState is SpeechState.Listening || speechState is SpeechState.Partial
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) speech.startListening() else speech.permissionDenied() }
    fun save() { app.container.projects.update(id) { it.copy(nativeEditDraft = request, nativeEditScope = scopeJson) } }
    LaunchedEffect(speechState) { (speechState as? SpeechState.Result)?.let { request = listOf(request, it.text).filter(String::isNotBlank).joinToString(" "); save(); speech.resetToIdle() } }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }
    if (!embedded) BackHandler(onBack = onBack)
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = { if (!embedded) TopAppBar(title = { Text("Edit ${definition.name}") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onSettings != null) item { com.buildai.app.ui.components.AIStatus(if (app.container.settings.getChatBackend() == "local") "Local AI" else "Cloud AI", onSettings) }
            item { Text("What would you like to change?", style = MaterialTheme.typography.headlineMedium); Text("Your current app stays available while a change is prepared.", color = OnSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
            item { OutlinedTextField(request, { request = it; save() }, label = { Text(if (running) "Draft your next change" else "Describe the change") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (listening) speech.stopListening() else permission.launch(Manifest.permission.RECORD_AUDIO) }) { Icon(if (listening) Icons.Default.MicOff else Icons.Default.Mic, if (listening) "Stop listening" else "Speak") }
                    Box { TextButton(onClick = { screens = true }, enabled = !running) { Text("Change on: ${selectedScreen.title}") }
                        DropdownMenu(screens, { screens = false }) { definition.screens.forEach { screen -> DropdownMenuItem(text = { Text(screen.title) }, onClick = { scopeJson = EditScope(screen.id, screen.components.map { it.id }.toSet()).json().toString(); save(); screens = false }) } }
                    }
                }
            }
            (speechState as? SpeechState.Error)?.let { item { Text(it.message, color = Error) } }
            item { TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide selected controls" else "Choose specific controls · optional") } }
            if (advanced) selectedScreen.components.forEach { control -> item(key = control.id) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(control.id in target.componentIds, { checked -> scopeJson = target.copy(componentIds = if (checked) target.componentIds + control.id else target.componentIds - control.id).json().toString(); save() }, enabled = !running)
                    Text(control.label.ifBlank { control.kind.name.lowercase().replace('_', ' ') }, Modifier.weight(1f))
                }
            } }
            if (advanced) item { Text("New controls can also be added to this screen. Existing records and unrelated controls are retained.", color = OnSurfaceVariant) }
            item {
                if (running) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(job?.stage.orEmpty(), modifier = Modifier.padding(top = 8.dp)); Text("Your next request is saved as a draft. Prepare it after this change finishes.", color = OnSurfaceVariant)
                    TextButton(onClick = { app.container.nativeEdits.cancel(id) }) { Text("Stop change", color = Error) } }
                else if (job?.status != "REVIEW") Button(enabled = request.isNotBlank(), onClick = {
                    runCatching { app.container.nativeEdits.enqueue(id, request, target) }.onFailure { error = it.message }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Prepare change") }
            }
            if (job?.status == "REVIEW") item {
                val prepared = JSONObject(checkNotNull(job).checkpoint)
                val candidate = runCatching { AppSpec.parse(prepared.getJSONObject("definition").toString()) }.getOrNull()
                val changed = prepared.getJSONArray("changedIds")
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ready to review · not applied", color = Primary, style = MaterialTheme.typography.labelLarge)
                    Text(prepared.getString("summary"), style = MaterialTheme.typography.titleMedium)
                    for (i in 0 until changed.length()) {
                        val key = changed.getString(i)
                        Text("• ${candidate?.let { com.buildai.app.builder.ScopedEditTools.changeLabel(definition, it, key) } ?: key}")
                    }
                    var checks by remember { mutableStateOf(false) }
                    TextButton(onClick = { checks = !checks }) { Text(if (checks) "Hide check details" else "View check results") }
                    if (checks) { val results = prepared.getJSONObject("verification").getJSONArray("results")
                        for (i in 0 until results.length()) Text(results.getJSONObject(i).getString("check"))
                        Text("These checks used isolated storage. Try the preview to check the experience yourself.", color = OnSurfaceVariant) }
                    OutlinedButton(onClick = { app.startActivity(Intent(app, NativeRunnerActivity::class.java).putExtra("appId", id).putExtra("previewJob", job?.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Preview change") }
                    Button(onClick = { runCatching { app.container.nativeEdits.apply(checkNotNull(job).id) }.onFailure { error = it.message } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Apply change") }
                    TextButton(onClick = { runCatching { app.container.nativeEdits.discard(checkNotNull(job).id) }.onFailure { error = it.message } }) { Text("Discard proposal", color = Error) }
                } }
            }
            job?.error?.let { item { Text(it, color = Error) } }
            item { OutlinedButton(onClick = { onOpenApp(id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open current app") } }
            onConversation?.let { item { TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("View project conversation") } } }
            error?.let { item { Text(it, color = Error) } }
        }
    }
}
