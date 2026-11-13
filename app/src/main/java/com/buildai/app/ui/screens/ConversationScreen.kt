package com.buildai.app.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import com.buildai.app.data.model.*
import com.buildai.app.ui.components.*
import com.buildai.app.ui.theme.*
import com.buildai.app.util.SpeechState
import com.buildai.app.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(viewModel: ConversationViewModel, onPlanConfirmed: () -> Unit, onOpenSettings: () -> Unit,
    onBack: () -> Unit, onGuidedConfiguration: () -> Unit = {}, embedded: Boolean = false, title: String = "Chat", onExport: (() -> Unit)? = null) {
    val messages by viewModel.messages.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val plan by viewModel.projectPlan.collectAsState()
    val brief by viewModel.brief.collectAsState()
    val draft by viewModel.draftText.collectAsState()
    val queue by viewModel.queuedInputs.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val speech by viewModel.speechState.collectAsState()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showQueue by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var followLatest by rememberSaveable { mutableStateOf(true) }
    var programmaticScroll by remember { mutableStateOf(false) }
    val jump by remember { derivedStateOf { list.canScrollForward && messages.isNotEmpty() } }
    val preferences = remember { com.buildai.app.data.storage.UiPreferences(viewModel.getApplication()) }
    val offline by preferences.offlineTools.collectAsState(preferences.offlineToolsEnabled())
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) { viewModel.speechManager.startListening(); listening = true } else viewModel.speechManager.permissionDenied() }
    if (!embedded) BackHandler(onBack = onBack)
    LaunchedEffect(speech) { when (val s = speech) {
        is SpeechState.Result -> { listening = false; viewModel.setDraftText(listOf(draft, s.text).filter(String::isNotBlank).joinToString(" ")); viewModel.speechManager.resetToIdle() }
        is SpeechState.Error, SpeechState.Idle -> listening = false
        else -> Unit
    } }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.canScrollForward }.collect { (scrolling, canForward) ->
            if (scrolling && !programmaticScroll) followLatest = !canForward
        }
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, loading) {
        if (messages.lastOrNull()?.role == MessageRole.USER) followLatest = true
        if (followLatest && messages.isNotEmpty()) {
            programmaticScroll = true
            try { list.scrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } finally { programmaticScroll = false }
        }
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = { if (!embedded) TopAppBar(title = { Column { Text(title, maxLines = 1); AIStatus(viewModel.operationLabel, onOpenSettings) } }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }, actions = { if (onExport != null) IconButton(onClick = onExport) { Icon(Icons.Default.IosShare, "Export conversation") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Background.copy(alpha = .72f))) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (messages.isEmpty()) item { WorkspaceEmptyState(if (mode == AssistantMode.CREATE) "What would make your day easier?" else "What’s on your mind?", "Start with an idea or a question.") }
                items(messages, key = { it.id }) { message -> ChatBubble(message, if (message.failed && message.role == MessageRole.USER) ({ viewModel.retryLastMessage() }) else null) }
                if (loading) item { TypingIndicator() }
                if (mode == AssistantMode.CREATE && (brief?.plan != null || plan != null)) item {
                    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(brief?.plan?.name ?: plan?.appName.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Text("${brief?.plan?.features?.size ?: plan?.features?.size ?: 0} features in your plan", color = OnSurfaceVariant)
                        if (brief?.assumptions?.isNotEmpty() == true) Text("Review ${brief?.assumptions?.size} assumption(s) before building.", color = OnSurfaceVariant)
                        Button(onClick = onPlanConfirmed, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Review requirements") }
                    } }
                }
                if (mode == AssistantMode.CREATE && phase == ConversationPhase.CLARIFYING && !loading && messages.any { it.role == MessageRole.USER && !it.failed }) item {
                    TextButton(onClick = viewModel::draftPlan) { Text("Use sensible defaults") }
                }
            }
            if (jump) SmallFloatingActionButton(onClick = { scope.launch { followLatest = true; programmaticScroll = true; try { list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } finally { programmaticScroll = false } } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), containerColor = SurfaceVariant) { Icon(Icons.Default.ArrowDownward, "Jump to latest") }
            }
            if (queue.isNotEmpty()) TextButton(onClick = { showQueue = true }, modifier = Modifier.align(Alignment.End)) { Text("${queue.size} queued") }
            error?.let { message -> Column(Modifier.padding(horizontal = 16.dp)) {
                Text(message, color = Error); FlowRow { TextButton(onClick = viewModel::retryLastMessage) { Text("Retry") }; TextButton(onClick = viewModel::clearError) { Text("Dismiss") }; TextButton(onClick = onOpenSettings) { Text("AI setup") } }
            } }
            (speech as? SpeechState.Error)?.let { Text(it.message, color = Error, modifier = Modifier.padding(horizontal = 16.dp)) }
            if (embedded && offline && mode == AssistantMode.CREATE && !loading) TextButton(onClick = onGuidedConfiguration) { Text("Offline tools") }
            MessageComposer(draft, viewModel::setDraftText, if (mode == AssistantMode.CHAT) "Message BuildAI…" else "Add an idea or answer…", {
                if (phase == ConversationPhase.CONFIRMED) viewModel.resumePlanning()
                viewModel.sendUserMessage(draft); viewModel.setDraftText("")
            }, onVoice = { if (listening) viewModel.speechManager.stopListening() else permission.launch(Manifest.permission.RECORD_AUDIO) }, listening = listening,
                loading = loading, onStop = viewModel::stopReply, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), onSteer = { viewModel.steer(draft); viewModel.setDraftText("") })
        }
    }
    if (showQueue) AlertDialog(onDismissRequest = { showQueue = false }, title = { Text("Queued requests") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            queue.forEachIndexed { i, text -> key(i, text) {
                var edited by remember { mutableStateOf(text) }
                OutlinedTextField(edited, { edited = it }, label = { Text("Request ${i + 1}") }, modifier = Modifier.fillMaxWidth())
                FlowRow { TextButton(onClick = { viewModel.editQueued(i, edited) }, enabled = edited.isNotBlank() && edited != text) { Text("Save") }
                    TextButton(onClick = { viewModel.editQueued(i, edited); viewModel.steerQueued(i); showQueue = false }, enabled = edited.isNotBlank()) { Text("Send now & steer") }
                    TextButton(onClick = { viewModel.removeQueued(i) }) { Text("Remove", color = Error) } }
            } }
        }
    }, confirmButton = { TextButton(onClick = { showQueue = false }) { Text("Close") } })
}
