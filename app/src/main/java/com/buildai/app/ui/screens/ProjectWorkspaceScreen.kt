package com.buildai.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.buildai.app.BuildAiApplication
import com.buildai.app.data.model.Conversation
import com.buildai.app.data.storage.*
import com.buildai.app.runtime.*
import com.buildai.app.ui.components.WorkspaceEmptyState
import com.buildai.app.ui.components.MotionContent
import com.buildai.app.ui.theme.*
import com.buildai.app.viewmodel.*
import com.buildai.app.widgets.ProjectHomeActions
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectWorkspaceScreen(app: BuildAiApplication, id: String, vm: ConversationViewModel,
    generation: GenerationViewModel, design: DesignViewModel, onBack: () -> Unit,
    onOpenApp: (String) -> Unit, onSettings: () -> Unit, onActivity: () -> Unit,
    onGuided: () -> Unit, onAppearance: () -> Unit, onDelete: (String) -> Unit, onDesignToBuild: () -> Unit = onAppearance, onAiSetup: () -> Unit = onSettings) {
    val database = remember { BuildAiDatabase.get(app) }
    val records by remember { database.projects().observe() }.collectAsState(emptyList())
    val initialProject = remember(id) { app.container.projects.load(id) }
    val stored = remember(records, id) { records.firstOrNull { it.id == id }?.let { runCatching { Conversation.fromJson(JSONObject(it.json)) }.getOrNull() } } ?: initialProject
    var loaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { vm.loadConversation(id); design.bind(id); generation.loadProject(id); loaded = true }
    BackHandler(onBack = onBack)
    if (!loaded) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    if (stored == null) { Column(Modifier.padding(24.dp)) { Text("This project is no longer available."); TextButton(onClick = onBack) { Text("Back") } }; return }
    val native = remember { NativeAppRepository(app) }
    val heads by remember { native.dao.observeHeads() }.collectAsState(emptyList())
    val initialHead = remember(id) { native.dao.head(id) }
    val head = heads.firstOrNull { it.appId == id } ?: initialHead
    val nativeApp = head != null
    val currentDefinition = remember(id, head?.revisionId) { native.spec(id) }
    val htmlFile = remember(id, stored.generatedFileName) { app.container.projects.generatedFile(id) }
    val ready = nativeApp || htmlFile != null
    val jobs by remember { database.jobs().observe() }.collectAsState(emptyList())
    val currentJobs = jobs.filter { it.projectId == id && it.kind != "CHAT" }.distinctBy { it.kind }
    val work = currentJobs.firstOrNull { it.status in listOf("RUNNING", "QUEUED", "REVIEW", "WAITING", "FAILED", "INTERRUPTED") }
    val legacyState by generation.state.collectAsState()
    val brief by vm.brief.collectAsState()
    val legacyPlan by vm.projectPlan.collectAsState()
    var tab by rememberSaveable(id) { mutableStateOf(stored.workspaceTab.takeIf { it in listOf("chat", "plan", "preview") } ?: "chat") }
    var wasReady by remember(id) { mutableStateOf(ready) }
    LaunchedEffect(ready) {
        if (ready && !wasReady && tab == "plan") { tab = "preview"; vm.persistWorkspaceTab(id, "preview") }
        wasReady = ready
    }
    LaunchedEffect(legacyState is GenerationState.Running) { if (legacyState is GenerationState.Running) { tab = "preview"; vm.persistWorkspaceTab(id, "preview") } }
    var conversation by rememberSaveable(id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(stored.displayName) }
    var delete by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    fun changeTab(next: String) { if (tab != next) { tab = next; vm.persistWorkspaceTab(id, next) } }
    fun open(intent: Intent) { runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { Toast.makeText(app, "No app is available for this action.", Toast.LENGTH_LONG).show() } }
    fun shareHtml(browser: Boolean) {
        htmlFile?.let { file -> val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            if (browser) open(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/html").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            else open(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/html").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share HTML app")) }
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        Column {
            TopAppBar(title = { Column { Text(stored.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis); if (tab == "chat") com.buildai.app.ui.components.AIStatus(vm.operationLabel, onAiSetup) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = {
                IconButton(onClick = onActivity) { Icon(Icons.Default.History, "Activity") }
                Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Project options") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename project") }, onClick = { menu = false; name = stored.displayName; rename = true })
                        if (nativeApp) DropdownMenuItem(text = { Text("Request a change") }, onClick = { menu = false; changeTab("chat"); conversation = false })
                        DropdownMenuItem(text = { Text("Conversation") }, onClick = { menu = false; changeTab("chat"); conversation = true })
                        DropdownMenuItem(text = { Text(if (ready) "App details" else "Requirements") }, onClick = { menu = false; changeTab("plan") })
                        if (ready) DropdownMenuItem(text = { Text("Preview app") }, onClick = { menu = false; changeTab("preview") })
                        if (nativeApp) DropdownMenuItem(text = { Text("Version history") }, onClick = { menu = false; history = true })
                        if (htmlFile != null) {
                            DropdownMenuItem(text = { Text("Share HTML file") }, onClick = { menu = false; shareHtml(false) })
                            DropdownMenuItem(text = { Text("Open in browser") }, onClick = { menu = false; shareHtml(true) })
                        }
                        if (ready) {
                            DropdownMenuItem(text = { Text("Add home-screen shortcut") }, onClick = { menu = false; ProjectHomeActions.requestShortcut(app, id, stored.displayName, stored.designSpec ?: com.buildai.app.data.model.DesignSpec()) })
                            DropdownMenuItem(text = { Text("Add home-screen widget") }, onClick = { menu = false; ProjectHomeActions.requestWidget(app, id) })
                        }
                        DropdownMenuItem(text = { Text("Export conversation") }, onClick = {
                            menu = false
                            runCatching { app.container.projects.buildShareIntent(stored) }
                                .onSuccess { open(Intent.createChooser(it, "Export conversation")) }
                                .onFailure { Toast.makeText(app, "Could not export this conversation. Check available storage and retry.", Toast.LENGTH_LONG).show() }
                        })
                        if (nativeApp || brief?.plan != null || legacyPlan != null) DropdownMenuItem(text = { Text("Appearance") }, onClick = { menu = false; onAppearance() })
                        if (htmlFile != null) DropdownMenuItem(text = { Text("Rebuild from plan") }, onClick = { menu = false; changeTab("plan") })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
                        DropdownMenuItem(text = { Text("Delete project", color = Error) }, onClick = { menu = false; delete = true })
                    }
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Background.copy(alpha = .72f)))
            if (work != null || legacyState is GenerationState.Running || legacyState is GenerationState.Failure) Surface(color = CardBackground) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(when (work?.status) { "REVIEW" -> "Change ready to review"; "FAILED", "INTERRUPTED", "WAITING" -> "Work needs attention"; else -> if (legacyState is GenerationState.Failure) "Work needs attention" else "Work continues in background" }, style = MaterialTheme.typography.labelLarge)
                    Text(work?.stage ?: (legacyState as? GenerationState.Running)?.step ?: (legacyState as? GenerationState.Failure)?.message.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Column {
                if (legacyState is GenerationState.Running) TextButton(onClick = generation::cancel) { Text("Stop build", color = Error) }
                TextButton(onClick = { if (work?.status == "REVIEW") { changeTab("chat"); conversation = false } else onActivity() }) { Text(if (work?.status == "REVIEW") "Review" else "Details") }
                }
            } }
        }
    }) { padding ->
        MotionContent("$tab:$conversation", Modifier.fillMaxSize().padding(padding)) { target ->
        val displayedTab = target.substringBefore(':')
        val showConversation = target.substringAfter(':').toBoolean()
        Box(Modifier.fillMaxSize()) { when (displayedTab) {
            "chat" -> if (nativeApp && !showConversation) NativeEditScreen(app, id, onBack, onOpenApp, embedded = true, onConversation = { conversation = true }, onSettings = onSettings)
                else Column(Modifier.fillMaxSize()) {
                    if (nativeApp) TextButton(onClick = { conversation = false }, modifier = Modifier.fillMaxWidth()) { Text("Request an app change") }
                    Box(Modifier.weight(1f)) { ConversationScreen(vm, { changeTab("plan") }, onSettings, onBack, onGuided, embedded = true) }
                }
            "plan" -> when {
                brief?.plan != null && !nativeApp && legacyPlan?.buildMode != com.buildai.app.data.model.BuildMode.TEMPLATE -> NativePlanScreen(app, vm, onBack = { changeTab("chat"); conversation = true }, onOpenApp = onOpenApp, onEditApp = { changeTab("chat"); conversation = false }, embedded = true, onDesign = onDesignToBuild)
                legacyPlan != null -> ProjectPlanScreen(vm, onDesignToBuild, onBack, onBack, { changeTab("chat") }, embedded = true, building = legacyState is GenerationState.Running, onAppearanceOnly = onAppearance)
                nativeApp -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text("Current app", style = MaterialTheme.typography.headlineMedium) }
                    val spec = currentDefinition
                    item { Text("This is the definition of the running version. Request changes in Chat, then review them before applying.", color = OnSurfaceVariant) }
                    spec?.screens?.forEach { screen -> item { Text(screen.title, style = MaterialTheme.typography.titleMedium); screen.components.forEach { Text(it.label.ifBlank { it.kind.name.lowercase().replace('_', ' ') }, color = OnSurfaceVariant) } } }
                    if (spec?.entities?.isNotEmpty() == true) item {
                        Text("Saved data", style = MaterialTheme.typography.titleMedium)
                        spec.entities.forEach { entity -> Text(entity.id.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleSmall); Text(entity.fields.joinToString { it.label }, color = OnSurfaceVariant) }
                    }
                    item { Button(onClick = { changeTab("chat"); conversation = false }, modifier = Modifier.fillMaxWidth()) { Text("Request a change") } }
                }
                else -> Column(Modifier.padding(20.dp)) { WorkspaceEmptyState("Let’s shape your idea", "Tell the assistant what your app needs to do."); Button(onClick = { changeTab("chat") }, modifier = Modifier.fillMaxWidth()) { Text("Continue conversation") } }
            }
            else -> if (nativeApp) Column(Modifier.fillMaxSize()) {
                val review = currentJobs.firstOrNull { it.kind == "NATIVE_EDIT" && it.status == "REVIEW" }
                var draftPreview by rememberSaveable(review?.id) { mutableStateOf(review != null) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (draftPreview && review != null) "Proposed change · test data" else "Current app · uses saved data", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    TextButton(onClick = { onOpenApp(id) }) { Text("Open app") }
                }
                if (review != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!draftPreview, onClick = { draftPreview = false }, label = { Text("Current app") })
                        FilterChip(draftPreview, onClick = { draftPreview = true }, label = { Text("Proposed change") })
                    }
                    if (draftPreview) Text("Test copy · data changes here are discarded. Review and apply in Chat.", Modifier.padding(horizontal = 16.dp), color = OnSurfaceVariant)
                }
                Box(Modifier.weight(1f)) {
                    if (draftPreview && review != null) ProposedAppPreview(app, id, review.id)
                    else NativeAppScreen(native, id, onBack, embedded = true)
                }
            } else if (htmlFile != null) ResultScreen(generation, vm, design, onBack, onBack, { changeTab("plan") }, { changeTab("plan") }, onAppearance, embedded = true, onOpenApp = { onOpenApp(id) })
            else Column(Modifier.padding(20.dp)) {
                when (val gs = legacyState) {
                    is GenerationState.Running -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(gs.step, Modifier.padding(top = 16.dp)) }
                    is GenerationState.Failure -> { Text("The build needs attention", style = MaterialTheme.typography.titleLarge); Text(gs.message, color = Error, modifier = Modifier.padding(top = 12.dp)); TextButton(onClick = { changeTab("plan") }) { Text("Review plan and retry") } }
                    else -> { WorkspaceEmptyState("Your app will appear here", "Review your plan, then build a runnable app."); Button(onClick = { changeTab("plan") }, modifier = Modifier.fillMaxWidth()) { Text("Review plan") } }
                }
            }
        } } }
    }
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text("Rename project") }, text = { OutlinedTextField(name, { name = it.take(80) }, label = { Text("Project name") }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { app.container.projects.update(id) { it.copy(title = name.trim(), titleOwned = true) }; runCatching { com.buildai.app.widgets.ProjectWidgetProvider.refreshProject(app, id) }; rename = false }) { Text("Rename") } }, dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } })
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete ${stored.displayName}?") }, text = { Text("The app, conversation, records and version history will be permanently removed.") },
        confirmButton = { TextButton(onClick = { delete = false; onDelete(id) }) { Text("Delete", color = Error) } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
    if (history) VersionHistoryDialog(native, id, onDismiss = { history = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistoryDialog(repo: NativeAppRepository, id: String, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf<ProjectRevision?>(null) }
    var inspected by remember { mutableStateOf<ProjectRevision?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val head = repo.dao.head(id)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { Text("Version history", style = MaterialTheme.typography.headlineMedium) }
            items(repo.dao.history(id), key = { it.id }) { revision ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (revision.id == head?.revisionId) Icons.Default.CheckCircle else Icons.Default.History, null, tint = if (revision.id == head?.revisionId) Success else OnSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(revision.summary, style = MaterialTheme.typography.titleMedium)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(revision.createdAt)), color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        if (revision.id == head?.revisionId) Text("Current version", color = Success)
                        else Row { TextButton(onClick = { inspected = revision }) { Text("Inspect") }; TextButton(onClick = { target = revision }) { Text("Restore") } }
                    }
                }
            }
            item { Text("Current saved records are kept when restoring.", color = OnSurfaceVariant) }
        }
    }
    inspected?.let { revision -> AlertDialog(onDismissRequest = { inspected = null }, title = { Text(revision.summary) }, text = {
        val spec = remember(revision.id) { AppSpec.parse(revision.specJson) }
        LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            spec.screens.forEach { screen -> item { Text(screen.title, style = MaterialTheme.typography.titleMedium); screen.components.forEach { Text(it.label.ifBlank { it.kind.name.lowercase().replace('_', ' ') }) } } }
            if (spec.entities.isNotEmpty()) item { Text("Saved data", style = MaterialTheme.typography.titleMedium); spec.entities.forEach { entity -> Text(entity.fields.joinToString { it.label }) } }
        }
    }, confirmButton = { TextButton(onClick = { inspected = null; target = revision }) { Text("Restore this version") } }, dismissButton = { TextButton(onClick = { inspected = null }) { Text("Close") } }) }
    target?.let { revision -> AlertDialog(onDismissRequest = { target = null }, title = { Text("Restore this version?") }, text = {
        Column { Text(revision.summary); Text("Current saved records will be kept.", Modifier.padding(top = 12.dp)); error?.let { Text(it, color = Error) } }
    }, confirmButton = { TextButton(onClick = { runCatching { repo.undo(id, revision.id, checkNotNull(head).revisionId) }.onSuccess { onDismiss() }.onFailure { error = it.message } }) { Text("Restore version") } }, dismissButton = { TextButton(onClick = { target = null }) { Text("Cancel") } }) }
}

/** An isolated database makes the edit preview safe to interact with. */
@Composable
private fun ProposedAppPreview(app: BuildAiApplication, id: String, jobId: String) {
    val scratch = remember(jobId) { androidx.room.Room.inMemoryDatabaseBuilder(app, BuildAiDatabase::class.java).allowMainThreadQueries().build() }
    val result = remember(jobId) { runCatching {
        val live = BuildAiDatabase.get(app)
        val job = checkNotNull(live.jobs().load(jobId))
        require(job.status == "REVIEW" && job.projectId == id)
        val current = NativeAppRepository(app)
        check(current.dao.head(id)?.revisionId == JSONObject(job.inputJson).getString("baseRevision")) { "Your app changed. Prepare a new edit before previewing." }
        scratch.projects().save(checkNotNull(live.projects().load(id)))
        NativeAppRepository(app, scratch).also { repo ->
            repo.publish(id, AppSpec.parse(JSONObject(job.checkpoint).getJSONObject("definition").toString()), null, "Unsaved preview")
            live.nativeApps().allRecords(id).forEach(repo.dao::saveRecord)
            repo.saveRuntime(id, current.runtime(id))
        }
    } }
    DisposableEffect(scratch) { onDispose { scratch.close() } }
    result.fold(onSuccess = { NativeAppScreen(it, id, {}, embedded = true) }, onFailure = { Text(it.message ?: "The proposed change could not be previewed.", Modifier.padding(20.dp), color = Error) })
}
