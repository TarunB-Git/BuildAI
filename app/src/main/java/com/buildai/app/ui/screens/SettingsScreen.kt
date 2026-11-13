package com.buildai.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.buildai.app.BuildConfig
import com.buildai.app.data.storage.UiPreferences
import com.buildai.app.ui.theme.*
import com.buildai.app.ui.components.MotionContent
import com.buildai.app.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, initialPage: String = "root") {
    var page by rememberSaveable { mutableStateOf(initialPage) }
    val backend by viewModel.chatBackend.collectAsState()
    val localName by viewModel.localModelName.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val model by viewModel.selectedModel.collectAsState()
    val saved by viewModel.saveSuccess.collectAsState()
    val importing by viewModel.modelImporting.collectAsState()
    val download by viewModel.downloadStatus.collectAsState()
    val check by viewModel.modelCheck.collectAsState()
    val modelError by viewModel.modelError.collectAsState()
    val context = LocalContext.current
    val preferences = remember { UiPreferences(context) }
    val theme by preferences.theme.collectAsState(preferences.selectedTheme())
    val uri = LocalUriHandler.current
    var showKey by remember { mutableStateOf(false) }
    val selectedLocal by viewModel.selectedLocal.collectAsState()
    val installed by viewModel.installed.collectAsState()
    val discovered by viewModel.discovered.collectAsState()
    val fallback by viewModel.fallback.collectAsState()
    val selectedProvider by viewModel.cloudProvider.collectAsState()
    val offline by preferences.offlineTools.collectAsState(preferences.offlineToolsEnabled())
    var modelInfo by remember { mutableStateOf<com.buildai.app.data.local.CatalogModel?>(null) }
    var catalogue by rememberSaveable { mutableStateOf(false) }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var providerError by remember { mutableStateOf<String?>(null) }
    val busy = importing || download is ModelDownloadStatus.Running || check is LocalModelCheck.Running
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::importModel) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result -> result?.let {
        runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }; viewModel.refreshModels()
    } }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshModels() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(page) { if (page == "local" || page == "catalogue") viewModel.refreshModels() }
    fun back() {
        if (page == "cloud" && viewModel.providerDirty()) confirmation = "leave"
        else when (page) { "root" -> onBack(); "ai", "themes", "about", "advanced" -> page = "root"; "catalogue" -> page = "local"; else -> page = "ai" }
    }
    BackHandler { back() }
    LaunchedEffect(saved) { if (saved) { kotlinx.coroutines.delay(2_000); viewModel.clearSaveSuccess() } }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(title = { Text(when (page) { "ai" -> "AI & privacy"; "local" -> "Local AI"; "cloud" -> "Cloud AI"; "catalogue" -> "Models"; "advanced" -> "Advanced"; "themes" -> "Theme"; "about" -> "About BuildAI"; else -> "Settings" }) },
            navigationIcon = { IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background.copy(alpha = .72f)))
    }) { padding ->
        MotionContent(page, Modifier.fillMaxSize().padding(padding)) { shownPage ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (shownPage) {
                "root" -> {
                    SettingsLink("AI & privacy", "${if (backend == "local") "Local AI" else "Cloud AI"} · ${if (backend == "local") (if (localName != null) "Model installed" else "Setup needed") else (if (viewModel.hasApiKey()) "Key saved" else "Setup needed")}", Icons.Default.Shield) { page = "ai" }
                    SettingsLink("Theme", theme.title, Icons.Default.Palette) { page = "themes" }
                    SettingsLink("About BuildAI", "Version ${BuildConfig.VERSION_NAME}", Icons.Default.Info) { page = "about" }
                    SettingsLink("Advanced", "Offline tools", Icons.Default.Tune) { page = "advanced" }
                }
                "themes" -> {
                    Text("Make it feel like your space", style = MaterialTheme.typography.headlineMedium)
                    Text("Applies immediately across BuildAI and your native apps.", color = OnSurfaceVariant)
                    AppTheme.entries.forEach { option ->
                        val c = colorsFor(option)
                        OutlinedCard(onClick = { preferences.selectTheme(option) }, Modifier.fillMaxWidth(),
                            colors = CardDefaults.outlinedCardColors(containerColor = c.surface)) {
                            Row(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(color = c.primary, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(40.dp)) {}
                                Column(Modifier.weight(1f)) { Text(option.title, color = c.ink, style = MaterialTheme.typography.titleMedium)
                                    Text(when (option) { AppTheme.SEA_BLUE -> "Sea glass & warm sand"; AppTheme.FOREST_GREEN -> "Deep & pale greens"; AppTheme.NIGHT_GRAY -> "Soft dark surfaces" }, color = c.muted) }
                                RadioButton(theme == option, onClick = { preferences.selectTheme(option) }, colors = RadioButtonDefaults.colors(selectedColor = c.primary, unselectedColor = c.muted))
                            }
                        }
                    }
                }
                "ai" -> {
                    Text("Where should the assistant work?", style = MaterialTheme.typography.headlineMedium)
                    Text("Your saved apps work without AI. This choice applies to future replies and builds.", color = OnSurfaceVariant)
                    SettingsLink("Local AI${if (backend == "local") " · selected" else ""}", if (localName != null) "Runs on your phone · model installed" else "Runs on your phone · setup needed", Icons.Default.Shield) { page = "local" }
                    SettingsLink("Cloud AI${if (backend == "cloud") " · selected" else ""}", "${selectedProvider.title} · ${if (viewModel.hasApiKey()) "key saved" else "add your key"}", Icons.Default.CloudQueue) { page = "cloud" }
                }
                "local" -> {
                    Text("AI on your phone", style = MaterialTheme.typography.headlineMedium)
                    Text("Prompts stay on this device. Saved apps are available even without a model.", color = OnSurfaceVariant)
                    Text(localName ?: "No model installed", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { viewModel.selectChatBackend("local") }, enabled = backend != "local", modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (backend == "local") "Local AI selected" else "Use local AI")
                    }
                    if (localName == null) Text("Setup needed: download a model or import a GGUF file.", color = OnSurfaceVariant)
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (importing) "Importing model…" else "Import model file") }
                    val knownInstalled = installed.mapNotNull { it.catalogId }.toSet()
                    val available = viewModel.downloadableModels.filter { item -> item.id !in knownInstalled && installed.none { it.name == item.name } }
                    val count = installed.size + available.size
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { if (count > 10) page = "catalogue" else catalogue = !catalogue }, modifier = Modifier.fillMaxWidth()) {
                            Text("Browse available models", Modifier.weight(1f)); Icon(if (catalogue) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }
                        DropdownMenu(expanded = catalogue && count <= 10, onDismissRequest = { catalogue = false }, modifier = Modifier.fillMaxWidth(.9f).heightIn(max = 420.dp)) {
                            if (installed.isNotEmpty()) { Text("Installed", Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                                installed.forEach { item -> DropdownMenuItem(text = { Column { Text(item.name); Text("Source: ${item.source}", style = MaterialTheme.typography.bodySmall) } },
                                    onClick = { viewModel.selectLocalModel(item.fileName); catalogue = false }, trailingIcon = { if (selectedLocal == item.fileName) Icon(Icons.Default.Check, "Selected") }) }
                            }
                            if (available.isNotEmpty()) { Text("Not installed", Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                                available.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { catalogue = false; modelInfo = item }, trailingIcon = { Icon(Icons.Default.Info, "Model information") }) }
                            }
                        }
                    }
                    if (installed.isNotEmpty()) Text("Installed models", style = MaterialTheme.typography.titleMedium)
                    installed.forEach { item -> ModelInventoryRow(item, selectedLocal == item.fileName, { viewModel.selectLocalModel(item.fileName) }, { viewModel.removeModel(item.fileName) }) }
                    TextButton(onClick = { folderPicker.launch(null) }) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Find models in a folder") }
                    if (discovered.isNotEmpty()) {
                        Text("Found on your device", style = MaterialTheme.typography.titleMedium)
                        discovered.forEach { item -> ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text("Source: User · ${"%.2f".format(item.bytes / 1_000_000_000.0)} GB") }, trailingContent = { TextButton(onClick = { viewModel.importModel(item.uri) }, enabled = !busy) { Text("Import") } }) }
                    }
                    when (val status = download) {
                        is ModelDownloadStatus.Running -> {
                            Text("Downloading model · ${"%.0f".format(status.bytes / 1_000_000.0)} MB${if (status.total > 0) " of ${"%.0f".format(status.total / 1_000_000.0)} MB" else ""}")
                            if (status.total > 0) LinearProgressIndicator(progress = { (status.bytes.toFloat() / status.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            else LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick = viewModel::cancelModelDownload) { Text("Cancel download", color = Error) }
                        }
                        is ModelDownloadStatus.Failed -> Text(status.message, color = Error)
                        ModelDownloadStatus.Complete -> Text("Model verified and installed.", color = Success)
                        else -> Unit
                    }
                    if (localName != null) {
                        OutlinedButton(onClick = { if (check is LocalModelCheck.Running) viewModel.stopModelCheck() else viewModel.testLocalModel() }, enabled = !importing && download !is ModelDownloadStatus.Running) { Text(if (check is LocalModelCheck.Running) "Stop reply test" else "Test a short reply") }
                        Text("Checks chat compatibility. App creation needs separate validation.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        when (val result = check) {
                            is LocalModelCheck.Running -> Text(result.status)
                            is LocalModelCheck.Passed -> Text("Reply test passed in ${"%.1f".format(result.elapsedMs / 1000.0)} s: ${result.reply}", color = Success)
                            is LocalModelCheck.Failed -> Text(result.message, color = Error)
                            else -> Unit
                        }
                        TextButton(onClick = { confirmation = "model" }, enabled = !busy) { Text("Remove local model", color = Error) }
                    }
                    modelError?.let { Text(it, color = Error) }
                }
                "cloud" -> {
                    CloudProviderSettings(viewModel, selectedProvider, backend)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Generation fallback", style = MaterialTheme.typography.titleMedium); Text("Try another compatible model if the selected one fails.", color = OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                        Switch(fallback, viewModel::setFallback)
                    }
                }
                "catalogue" -> {
                    OutlinedTextField(modelSearch, { modelSearch = it }, label = { Text("Find a model") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { if (modelSearch.isNotBlank()) IconButton(onClick = { modelSearch = "" }) { Icon(Icons.Default.Close, "Clear model search") } })
                    if (installed.isNotEmpty()) Text("Installed", style = MaterialTheme.typography.titleMedium)
                    installed.filter { it.name.contains(modelSearch, true) }.forEach { item -> ModelInventoryRow(item, selectedLocal == item.fileName, { viewModel.selectLocalModel(item.fileName) }, { viewModel.removeModel(item.fileName) }) }
                    Text("Not installed", style = MaterialTheme.typography.titleMedium)
                    viewModel.downloadableModels.filter { item -> item.name.contains(modelSearch, true) && installed.none { it.catalogId == item.id || it.name == item.name } }.forEach { item ->
                        ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text("${"%.2f".format(item.sizeBytes / 1_000_000_000.0)} GB · ${item.minRamGb} GB RAM") }, trailingContent = { IconButton(onClick = { modelInfo = item }) { Icon(Icons.Default.Info, "Information about ${item.name}") } })
                    }
                }
                "advanced" -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Show offline tools", style = MaterialTheme.typography.titleMedium); Text("Enable manual configuration and ready-made starters in Create.", color = OnSurfaceVariant) }
                        Switch(offline, preferences::enableOfflineTools)
                    }
                }
                "about" -> { Text("BuildAI", style = MaterialTheme.typography.headlineMedium); Text("Version ${BuildConfig.VERSION_NAME}")
                    Text("Make and use personal tools. Native apps keep records on this device. AI availability and quality depend on your selected model.", color = OnSurfaceVariant) }
            }
            Spacer(Modifier.height(12.dp))
        } }
    }
    modelInfo?.let { item -> AlertDialog(onDismissRequest = { modelInfo = null }, title = { Text(item.name) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Text & code · ${"%.2f".format(item.sizeBytes / 1_000_000_000.0)} GB")
            Text(if (item.id.startsWith("gemma")) "This model family can understand images and audio with additional components. BuildAI currently runs its text model only; image and audio input are not supported." else "Answers questions and generates text or code. It cannot see images or hear audio in this app.")
            Text("Suggested phone memory: ${item.minRamGb} GB")
            TextButton(onClick = { uri.openUri(item.sourceUrl) }) { Text("Source: ${item.repository}") }
        }
    }, confirmButton = { TextButton(onClick = { viewModel.downloadModel(item.id); modelInfo = null }, enabled = !busy) { Text("Download") } }, dismissButton = { TextButton(onClick = { modelInfo = null }) { Text("Close") } }) }
    confirmation?.let { kind -> AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(when (kind) { "model" -> "Remove local model?"; "key" -> "Remove cloud key?"; "leave" -> "Unsaved key changes"; else -> "Use cloud AI?" }) },
        text = { Text(when (kind) { "model" -> "Your apps remain available. This removes the installed file. If Local AI is selected, it will need a model again."; "key" -> "Cloud replies will need a key again. Local AI and saved apps remain available."; "leave" -> "Save the edited key or discard these changes. To remove a saved key, use Remove API key."; else -> "Future AI requests will be sent to the selected provider." }) },
        confirmButton = { TextButton( onClick = {
            if (kind == "leave") {
                providerError = viewModel.providerDraftError()
                if (providerError == null) { viewModel.saveProviderDrafts(); page = "ai"; confirmation = null }
            } else { when (kind) { "model" -> viewModel.removeLocalModel(); "key" -> viewModel.clearApiKey(); else -> viewModel.selectChatBackend("cloud") }; confirmation = null }
        }) { Text(if (kind == "leave") "Save changes" else if (kind == "cloud") "Use cloud AI" else "Remove") } },
        dismissButton = { TextButton(onClick = { if (kind == "leave") { viewModel.discardProviderDrafts(); page = "ai" }; confirmation = null }) { Text(if (kind == "leave") "Discard changes" else "Cancel") } }) }
    providerError?.let { message -> AlertDialog(onDismissRequest = { providerError = null }, title = { Text("Check provider settings") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { providerError = null; confirmation = null }) { Text("Edit settings") } }) }
}

@Composable
private fun SettingsLink(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Surface, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = Primary)
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(description, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant) }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun ModelInventoryRow(model: com.buildai.app.data.local.InstalledModel, selected: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    val uri = LocalUriHandler.current
    var remove by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text(model.name) }, supportingContent = { Column {
        Text("Source: ${model.source}", color = OnSurfaceVariant)
        model.sourceUrl?.takeIf { it.startsWith("https://") }?.let { url -> TextButton(onClick = { uri.openUri(url) }) { Text("Publisher source") } }
    } }, leadingContent = { RadioButton(selected, onClick = onSelect) }, trailingContent = { IconButton(onClick = { remove = true }) { Icon(Icons.Default.DeleteOutline, "Remove ${model.name}") } })
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Remove ${model.name}?") }, text = { Text("This removes the installed copy. Your other models and saved apps are kept.") },
        confirmButton = { TextButton(onClick = { onRemove(); remove = false }) { Text("Remove", color = Error) } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Cancel") } })
}

@Composable
private fun CloudProviderSettings(vm: SettingsViewModel, selected: com.buildai.app.data.api.CloudProvider, backend: String) {
    var expanded by rememberSaveable { mutableStateOf("gemini") }
    com.buildai.app.data.api.CloudProvider.entries.forEach { provider -> key(provider.id) {
        var apiKey by remember { mutableStateOf(vm.draftProviderKey(provider)) }
        var model by remember { mutableStateOf(vm.draftProviderModel(provider)) }
        var fallbacks by remember { mutableStateOf(vm.providerFallbacks(provider)) }
        var savedKey by remember { mutableStateOf(vm.providerKey(provider)) }
        var visible by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf(false) }
        val uri = LocalUriHandler.current
        Surface(color = Surface, shape = MaterialTheme.shapes.medium) {
            Column {
                Surface(onClick = { expanded = if (expanded == provider.id) "" else provider.id }, color = androidx.compose.ui.graphics.Color.Transparent) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(provider.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        if (backend == "cloud" && selected == provider) Icon(Icons.Default.CheckCircle, "Selected provider", tint = Primary)
                        Icon(if (expanded == provider.id) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                }
                androidx.compose.animation.AnimatedVisibility(expanded == provider.id) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(apiKey, { apiKey = it; saved = false; vm.editProviderKey(provider, it) }, Modifier.fillMaxWidth(), label = { Text("${provider.title} API key") }, singleLine = true,
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Hide key" else "Show key") } })
                        OutlinedTextField(model, { model = it; saved = false; vm.editProviderModel(provider, it) }, Modifier.fillMaxWidth(), label = { Text("Model ID") }, singleLine = true)
                        OutlinedTextField(fallbacks, { fallbacks = it.take(1000); vm.saveFallbacks(provider, fallbacks) }, Modifier.fillMaxWidth(), label = { Text("Fallback model IDs") }, supportingText = { Text("Optional · comma separated · saved automatically") })
                        Button(onClick = { vm.saveProvider(provider, apiKey, model); savedKey = apiKey.trim(); saved = true }, enabled = apiKey.isNotBlank() && model.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Saved" else "Save key & model") }
                        TextButton(onClick = { uri.openUri(provider.keyUrl) }) { Text("Get a ${provider.title} key") }
                        val dirty = apiKey.trim() != vm.providerKey(provider) || model.trim() != vm.providerModel(provider)
                        OutlinedButton(onClick = { vm.useProvider(provider); savedKey = apiKey.trim(); saved = true }, enabled = apiKey.isNotBlank() && model.isNotBlank() && (dirty || !(backend == "cloud" && selected == provider)), modifier = Modifier.fillMaxWidth()) {
                            Text(if (dirty) "Save & use ${provider.title}" else if (backend == "cloud" && selected == provider) "${provider.title} selected" else "Use ${provider.title}")
                        }
                        if (savedKey.isNotBlank()) TextButton(onClick = { vm.clearProvider(provider); apiKey = ""; savedKey = ""; model = vm.providerModel(provider); saved = false }) { Text("Remove key", color = Error) }
                    }
                }
            }
        }
    } }
}
