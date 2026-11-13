package com.buildai.app.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.buildai.app.data.model.*
import com.buildai.app.ui.components.*
import com.buildai.app.ui.theme.*
import com.buildai.app.util.SpeechManager
import com.buildai.app.util.SpeechState
import com.buildai.app.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(homeViewModel: HomeViewModel, onNewIdea: (String) -> Unit, onContinue: (String) -> Unit,
    onDeleteConversation: (String) -> Unit, onOpenSettings: () -> Unit, onOpenApps: () -> Unit,
    onManual: () -> Unit = {}, onStarter: (QuickTemplate) -> Unit = {}, onSettings: () -> Unit = onOpenSettings) {
    val context = LocalContext.current
    val projects by homeViewModel.conversations.collectAsState()
    val nativeIds by homeViewModel.nativeAppIds.collectAsState()
    val buildingIds by homeViewModel.buildingIds.collectAsState()
    val preferences = remember { com.buildai.app.data.storage.UiPreferences(context) }
    val offline by preferences.offlineTools.collectAsState(preferences.offlineToolsEnabled())
    val drafts = projects.filter { it.assistantMode == AssistantMode.CREATE && it.generatedFileName == null && it.id !in nativeIds }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val draft by homeViewModel.entryText.collectAsState()
    val speech = remember { SpeechManager(context) }
    val speechState by speech.speechState.collectAsState()
    val listening = speechState is SpeechState.Listening || speechState is SpeechState.Partial
    var starters by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) speech.startListening() else speech.permissionDenied() }
    LaunchedEffect(speechState) { (speechState as? SpeechState.Result)?.let { homeViewModel.setEntryText(listOf(draft, it.text).filter(String::isNotBlank).joinToString(" ")); speech.resetToIdle() } }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = { TopAppBar(title = { Text("Create") }, actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Background.copy(alpha = .72f))) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("What would you like your app to do?", style = MaterialTheme.typography.headlineMedium) }
                item { AIStatus(homeViewModel.operationLabel, onOpenSettings) }
                if (offline) item { OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Configure an offline tool") } }
                if (offline) item { TextButton(onClick = { starters = true }, modifier = Modifier.fillMaxWidth()) { Text("Use an offline starter") } }
                if (drafts.isNotEmpty()) item { Text("Drafts", style = MaterialTheme.typography.titleMedium) }
                items(drafts, key = { it.id }) { project -> ConversationRow(project, project.id in buildingIds, { onContinue(project.id) }, { deleteId = project.id }) }
                item { Text("Start with an idea", style = MaterialTheme.typography.titleMedium) }
                listOf("Study notes" to "Save and find my study notes.", "Daily routine" to "A simple morning checklist.", "Trip budget" to "Track trip expenses and show the remaining budget.").forEach { (title, prompt) -> item {
                    OutlinedCard(onClick = { homeViewModel.setEntryText(prompt) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProjectAvatar(title); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(prompt, color = OnSurfaceVariant) }
                        }
                    }
                } }
            }
            (speechState as? SpeechState.Error)?.let { Text(it.message, color = Error, modifier = Modifier.padding(horizontal = 20.dp)) }
            MessageComposer(draft, homeViewModel::setEntryText, "An app that helps me…", { onNewIdea(draft) },
                onVoice = { if (listening) speech.stopListening() else permission.launch(Manifest.permission.RECORD_AUDIO) }, listening = listening,
                actionLabel = "Start", modifier = Modifier.padding(12.dp))
        }
    }
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete this draft?") }, confirmButton = { TextButton(onClick = { onDeleteConversation(id); deleteId = null }) { Text("Delete", color = Error) } }, dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }) }
    if (starters) AlertDialog(onDismissRequest = { starters = false }, title = { Text("Use an offline starter") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose a working web app. Its listed features define the starter plan.", color = OnSurfaceVariant)
            QuickTemplate.entries.forEach { option -> TextButton(onClick = { starters = false; onStarter(option) }, Modifier.fillMaxWidth()) { Text(option.label) } }
        }
    }, confirmButton = { TextButton(onClick = { starters = false }) { Text("Close") } })
}

@Composable
internal fun ConversationRow(project: Conversation, building: Boolean, onOpen: () -> Unit, onDelete: () -> Unit, onRename: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    val status = if (building) "Work continues in background" else if (project.assistantMode == AssistantMode.CHAT) "Chat" else if (project.brief?.ready == true || project.projectPlan != null) "Plan ready" else "Draft"
    Surface(onClick = onOpen, color = Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Outline)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ProjectAvatar(project.displayName, chat = project.assistantMode == AssistantMode.CHAT)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(project.displayName, style = MaterialTheme.typography.titleMedium); Text(status, color = OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for ${project.displayName}") }
                DropdownMenu(menu, { menu = false }) {
                    if (onRename != null) DropdownMenuItem(text = { Text("Rename chat") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete", color = Error) }, onClick = { menu = false; onDelete() }) } }
        }
    }
}
