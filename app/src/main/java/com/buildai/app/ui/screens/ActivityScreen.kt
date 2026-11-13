package com.buildai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.buildai.app.ui.components.WorkspaceEmptyState
import com.buildai.app.ui.theme.*
import androidx.compose.ui.unit.dp
import com.buildai.app.BuildAiApplication
import com.buildai.app.data.storage.BuildAiDatabase
import androidx.work.WorkManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityScreen(app: BuildAiApplication, onOpen: (String) -> Unit, onSettings: () -> Unit = {}, onBack: () -> Unit = {}) {
    var retryError by remember { mutableStateOf<String?>(null) }
    val jobs by remember { BuildAiDatabase.get(app).jobs().observe() }.collectAsState(emptyList())
    val legacy by remember { WorkManager.getInstance(app).getWorkInfosByTagFlow("generation") }.collectAsState(emptyList())
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = { TopAppBar(title = { Text("Activity") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = OnSurfaceVariant) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Background.copy(alpha = .72f))) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(app.container.projects.importErrors) { warning -> Text(warning, color = MaterialTheme.colorScheme.error) }
            retryError?.let { message -> item { Text(message, color = Error) } }
            item {
                Text("Your work", style = MaterialTheme.typography.headlineMedium)
                Text("Replies, builds and checks, all in one place.", color = OnSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            if (jobs.isEmpty() && legacy.isEmpty()) item { WorkspaceEmptyState("You’re all caught up", "Replies and builds will appear here.\nYou can leave a job running and return later.") }
            val actionable = jobs.distinctBy { it.projectId to it.kind }.filter { it.status in listOf("RUNNING", "QUEUED", "REVIEW", "WAITING", "FAILED", "INTERRUPTED") }
            if (actionable.isNotEmpty()) item { Text("Running & needs attention", style = MaterialTheme.typography.titleMedium) }
            val ordered = actionable + jobs.filterNot { old -> actionable.any { it.id == old.id } }
            items(ordered, key = { it.id }) { job ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(app.container.projects.load(job.projectId)?.displayName ?: "Saved project", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val active = job.status in listOf("RUNNING", "QUEUED")
                            val statusColor = when (job.status) { "COMPLETED" -> Success; "FAILED", "INTERRUPTED" -> Error; else -> Primary }
                            if (active) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Surface(color = statusColor.copy(alpha = .08f), shape = MaterialTheme.shapes.small) {
                                Text(when (job.status) { "REVIEW" -> "Needs review"; "WAITING" -> "Waiting for setup"; "INTERRUPTED" -> "Interrupted"; else -> job.status.lowercase().replaceFirstChar(Char::uppercase) }, color = statusColor,
                                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                            Text(when (job.kind) { "CHAT" -> "Reply"; "NATIVE_EDIT" -> "App edit"; "NATIVE_BUILD", "BUILD" -> "App build"; else -> "Project work" }, color = OnSurfaceVariant)
                        }
                        Text(job.stage)
                        var diagnostics by remember(job.id) { mutableStateOf(false) }
                        TextButton(onClick = { diagnostics = !diagnostics }) { Text(if (diagnostics) "Hide details" else "Technical details") }
                        if (diagnostics) runCatching { org.json.JSONObject(job.progressJson) }.getOrNull()?.let { progress ->
                            val input = runCatching { org.json.JSONObject(job.inputJson) }.getOrNull()
                            val cloud = input?.optString("backend") == "cloud" || input?.optJSONObject("model")?.optString("backend") == "cloud"
                            val amount = progress.optLong("completed")
                            when (progress.optString("stage")) {
                                "DECODING" -> Text("Writing: $amount ${if (cloud) "characters" else "tokens"}")
                                "PREFILL" -> Text("Reading the request: $amount of ${progress.optLong("total")} tokens")
                                "TOOL" -> Text("Checked features: $amount of ${progress.optLong("total")}")
                            }
                        }
                        job.error?.let { Text(it, color = Error) }
                        if (job.kind == "NATIVE_EDIT" && job.status == "REVIEW") TextButton(onClick = { app.container.projects.update(job.projectId) { it.copy(workspaceTab = "chat") }; onOpen(job.projectId) }) { Text("Review change") }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onOpen(job.projectId) }) { Text("Open project") }
                            if (job.status in listOf("QUEUED", "RUNNING")) TextButton(onClick = {
                                if (job.kind == "CHAT") app.container.sessions.cancel(job.projectId)
                                else if (job.kind == "NATIVE_BUILD") app.container.nativeBuilds.cancel(job.projectId)
                                else if (job.kind == "NATIVE_EDIT") app.container.nativeEdits.cancel(job.projectId)
                                else app.container.localBuilds.cancel(job.projectId)
                            }) { Text("Stop") }
                            if (job.kind == "BUILD" && job.status in listOf("WAITING", "INTERRUPTED", "FAILED", "CANCELLED")) TextButton(onClick = {
                                runCatching { app.container.localBuilds.retry(job.id) }.onSuccess { onOpen(job.projectId) }.onFailure { retryError = it.message ?: "Open this project and review its current plan before retrying." }
                            }) { Text("Retry saved build") }
                            if (job.kind == "NATIVE_BUILD" && job.status in listOf("WAITING", "INTERRUPTED", "FAILED", "CANCELLED")) TextButton(onClick = {
                                runCatching { app.container.nativeBuilds.retry(job.id) }.onSuccess { onOpen(job.projectId) }.onFailure { retryError = it.message ?: "Open this project and review its current plan before retrying." }
                            }) { Text("Retry native build") }
                        }
                    }
                }
            }
            items(legacy, key = { it.id }) { work ->
                val id = work.tags.firstOrNull { it.startsWith("generation-") }?.removePrefix("generation-")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Connected or legacy build • ${work.state.name.lowercase()}")
                        work.progress.getString("step")?.let { Text(it) }
                        id?.let { TextButton(onClick = { onOpen(it) }) { Text("Open project") } }
                        if (!work.state.isFinished) TextButton(onClick = { WorkManager.getInstance(app).cancelWorkById(work.id) }) { Text("Stop build", color = Error) }
                        work.outputData.getString(com.buildai.app.data.api.GenerationWorker.ERROR)?.let { Text(it, color = Error) }
                    }
                }
            }
        }
    }
}
