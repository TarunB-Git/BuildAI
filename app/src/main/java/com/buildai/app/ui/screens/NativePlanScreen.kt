package com.buildai.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.buildai.app.BuildAiApplication
import com.buildai.app.data.storage.BuildAiDatabase
import com.buildai.app.ui.components.ProjectAvatar
import com.buildai.app.ui.theme.*
import com.buildai.app.viewmodel.ConversationViewModel
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NativePlanScreen(app: BuildAiApplication, vm: ConversationViewModel, onBack: () -> Unit, onOpenApp: (String) -> Unit, onEditApp: (String) -> Unit, embedded: Boolean = false, onDesign: (() -> Unit)? = null) {
    val brief by vm.brief.collectAsState()
    val loading by vm.isLoading.collectAsState()
    val jobs by remember { BuildAiDatabase.get(app).jobs().observe() }.collectAsState(emptyList())
    val id = vm.projectId ?: return
    val job = jobs.firstOrNull { it.projectId == id && it.kind == "NATIVE_BUILD" }
    val running = job?.status in listOf("QUEUED", "RUNNING")
    val editable = !loading && !running
    var error by remember { mutableStateOf<String?>(null) }
    var choices by rememberSaveable(id) { mutableStateOf(emptyMap<String, Boolean>()) }
    var details by rememberSaveable(id) { mutableStateOf(false) }
    if (!embedded) BackHandler(onBack = onBack)
    val b = brief ?: return
    val plan = b.plan ?: return
    val draft = JSONObject(b.planDraft ?: plan.json().toString())
    val planProblem = runCatching { com.buildai.app.builder.NativePlan.read(draft) }.exceptionOrNull()?.message
    fun change(edit: (JSONObject) -> Unit) {
        runCatching { val next = JSONObject(draft.toString()); edit(next); vm.editNativePlanDraft(next.toString()) }.onFailure { error = it.message }
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        if (!embedded) TopAppBar(title = { Text("Your app plan", style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Background.copy(alpha = .72f)))
    }, bottomBar = {
        Surface(color = Surface, shadowElevation = 4.dp) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (running) {
                    LinearProgressIndicator(Modifier.fillMaxWidth()); Text(job?.stage.orEmpty(), modifier = Modifier.padding(top = 8.dp))
                    Row { TextButton(onClick = { app.container.nativeBuilds.cancel(id) }) { Text("Stop build", color = Error) }
                        TextButton(onClick = onBack) { Text("Keep chatting") } }
                } else {
                    if (com.buildai.app.runtime.NativeAppRepository(app).dao.head(id) != null) {
                        Button(onClick = { onOpenApp(id) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Open app") }
                        OutlinedButton(onClick = { onEditApp(id) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Edit app") }
                    } else Button(enabled = b.ready && !loading && planProblem == null, onClick = {
                        if (onDesign != null) onDesign() else runCatching { vm.approveNativePlan(); app.container.nativeBuilds.enqueue(id) }.onFailure { error = it.message }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if (onDesign == null) "Build" else "Choose appearance")
                    }
                    job?.error?.let { Text(it, color = Error) }
                    if (job?.status in listOf("FAILED", "INTERRUPTED", "WAITING", "CANCELLED")) TextButton(onClick = {
                        runCatching { app.container.nativeBuilds.retry(checkNotNull(job).id) }.onFailure { error = it.message }
                    }) { Text("Retry saved checkpoint") }
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Add an idea in chat") }
                }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProjectAvatar(draft.optString("name"))
                    Column(Modifier.weight(1f)) {
                        Text(draft.optString("name"), style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            b.starterOption?.let { option -> com.buildai.app.data.model.QuickTemplate.entries.firstOrNull { it.name == option }?.let { starter -> item {
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("A ready-made option", style = MaterialTheme.typography.titleMedium); Text("The assistant suggests ${starter.label.lowercase()} for this plan."); TextButton(onClick = { vm.chooseStarter(starter) }) { Text("Review this option") } } }
            } } }
            item { Text(b.goal, style = MaterialTheme.typography.bodyLarge, color = OnSurfaceVariant) }
            item { OutlinedTextField(draft.optString("name"), { text -> change { it.put("name", text) } }, label = { Text("App name") }, enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("What your app will do", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${draft.getJSONArray("features").length()} features", color = OnSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
            for (index in 0 until draft.getJSONArray("features").length()) item(key = "feature-$index") {
                val feature = draft.getJSONArray("features").getJSONObject(index)
                var expanded by rememberSaveable(id, feature.getString("id")) { mutableStateOf(false) }
                OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Surface), border = BorderStroke(1.dp, Outline)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RadioButtonUnchecked, null, tint = Primary, modifier = Modifier.size(24.dp))
                            Text(feature.getString("title"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                            IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.Edit,
                                if (expanded) "Close feature editor" else "Edit feature ${feature.getString("title")}", tint = Primary) }
                        }
                        if (expanded) {
                            OutlinedTextField(feature.getString("title"), { text -> change { it.getJSONArray("features").getJSONObject(index).put("title", text) } },
                                label = { Text("Feature name") }, enabled = editable, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(feature.getString("workflow"), { text -> change { it.getJSONArray("features").getJSONObject(index).put("workflow", text) } },
                                label = { Text("What you will do") }, enabled = editable, modifier = Modifier.fillMaxWidth())
                            val checks = feature.getJSONArray("checks")
                            OutlinedTextField((0 until checks.length()).joinToString("\n") { checks.getString(it) }, { text ->
                                change { it.getJSONArray("features").getJSONObject(index).put("checks", JSONArray(text.lines())) }
                            }, label = { Text("Checks — one per line") }, enabled = editable, modifier = Modifier.fillMaxWidth())
                            if (draft.getJSONArray("features").length() > 1) TextButton(enabled = editable, onClick = {
                                change { it.getJSONArray("features").remove(index) }
                            }) { Text("Remove feature", color = Error) }
                        } else {
                            Text(feature.getString("workflow"), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                            Text(if (feature.getJSONArray("checks").length() == 1) "1 check planned" else "${feature.getJSONArray("checks").length()} checks planned", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                        }
                    }
                }
            }
            item {
                if (draft.getJSONArray("features").length() < 12) OutlinedButton(enabled = editable, modifier = Modifier.heightIn(min = 48.dp), onClick = { change {
                    val features = it.getJSONArray("features")
                    val ids = (0 until features.length()).map { n -> features.getJSONObject(n).getString("id") }.toSet()
                    val nextId = generateSequence(1) { n -> n + 1 }.map { n -> "feature$n" }.first { next -> next !in ids }
                    features.put(JSONObject().put("id", nextId).put("title", "New feature").put("workflow", "").put("checks", JSONArray().put("")))
                } }) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add feature") }
            }
            item {
                val screens = draft.getJSONArray("screens")
                val data = draft.getJSONArray("data")
                Text("Screens & saved data", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    for (n in 0 until screens.length()) SuggestionChip(onClick = { details = true }, label = { Text(screens.getString(n)) })
                }
                Text(if (data.length() == 0) "No records need to be saved." else (0 until data.length()).joinToString(" · ") { data.getString(it) },
                    color = OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { details = !details }) { Text(if (details) "Close screen & data editor" else "Edit screens & data") }
                if (details) {
                    OutlinedTextField((0 until screens.length()).joinToString("\n") { screens.getString(it) }, { text -> change { it.put("screens", JSONArray(text.lines())) } },
                        label = { Text("Screens — one per line") }, enabled = editable, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField((0 until data.length()).joinToString("\n") { data.getString(it) }, { text -> change { it.put("data", JSONArray(text.lines().filter(String::isNotBlank))) } },
                        label = { Text("Saved data — one collection per line") }, supportingText = { Text("Leave empty if the app saves no records") }, enabled = editable, modifier = Modifier.fillMaxWidth())
                }
            }
            if (b.answers.isNotEmpty()) item {
                var showAnswers by rememberSaveable(id) { mutableStateOf(false) }
                TextButton(onClick = { showAnswers = !showAnswers }) { Text(if (showAnswers) "Hide remembered answers" else "Remembered answers · ${b.answers.size}") }
                if (showAnswers) b.answers.forEach { Column(Modifier.padding(bottom = 12.dp)) {
                    Text(it.question, style = MaterialTheme.typography.labelLarge, color = OnSurfaceVariant)
                    Text(it.answer, style = MaterialTheme.typography.bodyMedium)
                } }
            }
            if (b.assumptions.isNotEmpty()) item {
                Surface(color = Warning.copy(alpha = .08f), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Assumptions to review", style = MaterialTheme.typography.titleMedium, color = Warning)
                        b.assumptions.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (b.suggestions.isNotEmpty()) item { Text("A few useful additions", style = MaterialTheme.typography.titleMedium) }
            b.suggestions.forEach { suggestion -> item(key = suggestion.id) {
                OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(choices[suggestion.id] ?: suggestion.accepted, { accepted -> choices = choices + (suggestion.id to accepted) }, enabled = !loading && !running)
                    Column(Modifier.weight(1f)) { Text(suggestion.title, style = MaterialTheme.typography.titleSmall); Text(suggestion.benefit, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant) }
                } }
            } }
            if (choices.isNotEmpty()) item { Button(onClick = {
                vm.applySuggestions(choices); choices = emptyMap()
            }, enabled = !loading && !running) { Text("Update plan") } }
            item {
                Surface(color = Success.copy(alpha = .07f), shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Shield, null, tint = Success)
                        Column { Text("Records are saved on this device", style = MaterialTheme.typography.titleSmall, color = Success)
                            Text("Native controls and local storage. AI requests use your selected connection.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant) }
                    }
                }
            }
            planProblem?.let { item { Text(it, color = Error) } }
            error?.let { item { Text(it, color = Error) } }

        }
    }
}
