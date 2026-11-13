package com.buildai.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.buildai.app.ui.components.GeneratedAppWebView
import com.buildai.app.ui.theme.*
import com.buildai.app.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(generationVm: GenerationViewModel, conversationVm: ConversationViewModel, designVm: DesignViewModel,
    onBack: () -> Unit, onStartOver: () -> Unit, onRegenerate: () -> Unit, onEditApp: () -> Unit,
    onEditDesign: () -> Unit, embedded: Boolean = false, onOpenApp: () -> Unit = {}) {
    val state by generationVm.state.collectAsState()
    val done = state as? GenerationState.Done ?: conversationVm.projectId?.let(generationVm::savedOutput)
    var issue by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    if (!embedded) BackHandler(onBack = onBack)
    if (done == null) { Text("No saved app is available yet.", Modifier.padding(20.dp)); return }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = { if (!embedded) TopAppBar(title = { Text("App preview") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Current web app · uses saved data", color = OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenApp) { Text("Open app") }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { GeneratedAppWebView(it) }, update = { view -> view.onPreviewIssue = { issue = it }; view.loadApp(done.html, done.projectId, reload) }, onReset = null, onRelease = { it.destroy() }, modifier = Modifier.fillMaxSize())
                issue?.let { problem -> Surface(modifier = Modifier.align(Alignment.Center).padding(20.dp), color = Surface, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(20.dp)) { Text(problem); TextButton(onClick = { issue = null; reload++ }) { Text("Reload preview") } }
                } }
            }
        }
    }
}
