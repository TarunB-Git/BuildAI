package com.buildai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.buildai.app.data.model.AssistantMode
import com.buildai.app.ui.components.WorkspaceEmptyState
import com.buildai.app.ui.theme.*
import com.buildai.app.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(vm: HomeViewModel, onNew: () -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit, onActivity: () -> Unit) {
    val projects by vm.conversations.collectAsState()
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var rename by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.buildai.app.BuildAiApplication
    var delete by remember { mutableStateOf<String?>(null) }
    val chats = projects.filter { it.assistantMode == AssistantMode.CHAT && (query.isBlank() || it.title.contains(query, true)) }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = { TopAppBar(title = { Text("Chat") }, actions = {
        com.buildai.app.ui.components.ActivityButton(onActivity)
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Background.copy(alpha = .72f))) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Button(onClick = onNew, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New chat") } }
            if (projects.any { it.assistantMode == AssistantMode.CHAT }) item { OutlinedTextField(query, { query = it }, label = { Text("Find a chat") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } }) }
            items(chats, key = { it.id }) { chat -> ConversationRow(chat, false, { onOpen(chat.id) }, { delete = chat.id }, { name = chat.displayName; rename = chat.id }) }
            if (chats.isEmpty()) item { WorkspaceEmptyState(if (query.isBlank()) "A place to think" else "No matching chats", if (query.isBlank()) "Ask a question or explore an idea.\nApp drafts live in Create." else "Try a different name or clear the search.") }
        }
    }
    rename?.let { id -> AlertDialog(onDismissRequest = { rename = null }, title = { Text("Rename chat") },
        text = { OutlinedTextField(name, { name = it.take(80) }, label = { Text("Chat name") }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { app.container.projects.update(id) { it.copy(title = name.trim(), titleOwned = true) }; rename = null }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel") } }) }
    delete?.let { id -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete this chat?") }, text = { Text("Its messages will be permanently removed.") },
        confirmButton = { TextButton(onClick = { vm.delete(id); delete = null }) { Text("Delete", color = Error) } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } }) }
}
