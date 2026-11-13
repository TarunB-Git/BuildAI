package com.buildai.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buildai.app.ui.components.*
import com.buildai.app.ui.theme.*
import com.buildai.app.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(viewModel: HomeViewModel, onOpen: (String) -> Unit, onEdit: (String) -> Unit, onBack: () -> Unit,
    onCreate: () -> Unit = onBack, onContinue: (String) -> Unit = onEdit, onSettings: () -> Unit = {}, onActivity: () -> Unit = {}) {
    val projects by viewModel.conversations.collectAsState()
    val context = LocalContext.current
    val nativeIds by viewModel.nativeAppIds.collectAsState()
    val buildingIds by viewModel.buildingIds.collectAsState()
    var deleteId by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = projects.filter { (it.assistantMode == com.buildai.app.data.model.AssistantMode.CREATE || it.generatedFileName != null || it.id in nativeIds) && (query.isBlank() || it.displayName.contains(query, true) || it.projectPlan?.appName?.contains(query, true) == true || it.brief?.goal?.contains(query, true) == true) }
    val apps = filtered.filter { it.generatedFileName != null || it.id in nativeIds }.sortedWith(compareByDescending<com.buildai.app.data.model.Conversation> { it.pinnedAt > 0 }.thenByDescending { it.pinnedAt }.thenByDescending { it.updatedAt })
    val drafts = filtered.filter { it.generatedFileName == null && it.id !in nativeIds }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(title = { Text("Apps", style = MaterialTheme.typography.titleLarge) }, actions = {
            ActivityButton(onActivity)
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = OnSurfaceVariant) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Background.copy(alpha = .72f)))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (projects.isNotEmpty()) item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Find an app") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Outline, focusedBorderColor = Primary),
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } })
            }
            items(apps, key = { it.id }) { app ->
                var menu by remember { mutableStateOf(false) }
                OutlinedCard(onClick = { onOpen(app.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.outlinedCardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Outline)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val title = app.displayName
                            ProjectAvatar(title, iconKey = app.designSpec?.iconKey)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (app.id in buildingIds) "Updating in background" else app.projectPlan?.description ?: app.brief?.goal.orEmpty(), color = OnSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                            if (app.pinnedAt > 0) Icon(Icons.Default.PushPin, "Pinned app", Modifier.size(16.dp), tint = Primary)
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for $title") }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(text = { Text(if (app.pinnedAt > 0) "Unpin app" else "Pin to top") }, leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = { menu = false; viewModel.togglePin(app.id) })
                                    DropdownMenuItem(text = { Text("Open workspace") }, onClick = { menu = false; onEdit(app.id) })
                                    DropdownMenuItem(text = { Text("Add home-screen shortcut") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.AddToHomeScreen, null) }, onClick = {
                                        menu = false
                                        com.buildai.app.widgets.ProjectHomeActions.requestShortcut(context, app.id, app.displayName, app.designSpec ?: com.buildai.app.data.model.DesignSpec())
                                    })
                                    DropdownMenuItem(text = { Text("Add home-screen widget") }, leadingIcon = { Icon(Icons.Default.Widgets, null) }, onClick = {
                                        menu = false
                                        com.buildai.app.widgets.ProjectHomeActions.requestWidget(context, app.id)
                                    })
                                    DropdownMenuItem(text = { Text("Delete app", color = Error) }, onClick = { menu = false; deleteId = app.id },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = Error) })
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { onOpen(app.id) }, Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Open app")
                            }
                            OutlinedButton(onClick = { onEdit(app.id) }, Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Edit app")
                            }
                        }
                    }
                }
            }
            if (apps.isEmpty()) item {
                WorkspaceEmptyState(if (query.isBlank()) "Your next useful thing starts here" else "No matches yet",
                    if (query.isBlank()) "Describe an idea. Shape it with the assistant.\nYour finished apps will live here." else "Try a different name or clear your search.")
            }
        }
    }
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete this project?") },
        text = { Text("The app, project and saved app data will be removed.") },
        confirmButton = { TextButton(onClick = { viewModel.delete(id); deleteId = null }) { Text("Delete", color = Error) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }) }
}
