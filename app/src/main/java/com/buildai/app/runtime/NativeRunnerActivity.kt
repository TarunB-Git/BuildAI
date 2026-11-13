package com.buildai.app.runtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import com.buildai.app.ui.theme.prepareBuildAiWindow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Actual app execution: no builder tab bar, preview frame or active AI requirement. */
class NativeRunnerActivity : ComponentActivity() {
    private var previewDatabase: com.buildai.app.data.storage.BuildAiDatabase? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); prepareBuildAiWindow()
        val id = intent.getStringExtra("appId")
        val previewJob = intent.getStringExtra("previewJob")
        val repository = runCatching {
            if (previewJob == null || id == null) NativeAppRepository(this)
            else {
                val live = com.buildai.app.data.storage.BuildAiDatabase.get(this)
                val job = checkNotNull(live.jobs().load(previewJob)); require(job.projectId == id && job.kind == "NATIVE_EDIT" && job.status == "REVIEW")
                check(NativeAppRepository(this).dao.head(id)?.revisionId == JSONObject(job.inputJson).getString("baseRevision")) { "This preview is stale. Prepare the edit again." }
                val spec = AppSpec.parse(JSONObject(job.checkpoint).getJSONObject("definition").toString())
                val scratch = androidx.room.Room.inMemoryDatabaseBuilder(this, com.buildai.app.data.storage.BuildAiDatabase::class.java).allowMainThreadQueries().build()
                previewDatabase = scratch
                scratch.projects().save(checkNotNull(live.projects().load(id)))
                NativeAppRepository(this, scratch).also { repo ->
                    repo.publish(id, spec.copy(name = "Preview: ${spec.name}".take(100)), null, "Unsaved preview — changes here do not affect your app")
                    live.nativeApps().allRecords(id).forEach(repo.dao::saveRecord)
                    repo.saveRuntime(id, NativeAppRepository(this).runtime(id))
                }
            }
        }
        setContent {
            com.buildai.app.ui.theme.BuildAITheme {
                if (id == null) Column(Modifier.padding(24.dp)) { Text("The app is missing."); TextButton(onClick = ::finish) { Text("Close") } }
                else repository.fold(onSuccess = { NativeAppScreen(it, id, ::finish, intent.getBooleanExtra("showHistory", false)) }, onFailure = { Column(Modifier.padding(24.dp)) { Text(it.message ?: "The preview could not be opened."); TextButton(onClick = ::finish) { Text("Close") } } })
            }
        }
    }
    override fun onDestroy() { super.onDestroy(); previewDatabase?.close() }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NativeAppScreen(repo: NativeAppRepository, appId: String, onClose: () -> Unit, initialHistory: Boolean = false, embedded: Boolean = false) {
    val heads by remember(repo) { repo.dao.observeHeads() }.collectAsState(emptyList())
    val initialHead = remember(repo, appId) { repo.dao.head(appId) }
    val head = heads.firstOrNull { it.appId == appId } ?: initialHead
    val parsed = remember(head?.revisionId) { runCatching { repo.spec(appId) }.getOrNull() }
    if (parsed == null) { Text("This app could not be opened. Your saved records are retained.", Modifier.padding(24.dp)); return }
    var stateText by remember(appId) { mutableStateOf(repo.runtime(appId).toString()) }
    val state = remember(stateText) { JSONObject(stateText) }
    LaunchedEffect(head?.runtimeJson) {
        head?.runtimeJson?.let { text ->
            if (JSONObject(text).optLong("uiVersion") >= JSONObject(stateText).optLong("uiVersion")) stateText = text
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(initialHistory) }
    var dataVersion by remember { mutableIntStateOf(0) }
    val storedRecords by remember(repo, appId) { repo.dao.observeRecords(appId) }.collectAsState(remember(repo, appId) { repo.dao.records(appId) })
    val records = remember(storedRecords) { storedRecords.groupBy { it.entityId }.mapValues { (_, rows) -> rows.map { r ->
        val j = JSONObject(r.json)
        j.keys().asSequence().associateWith { j.get(it) } + ("id" to r.id)
    } } }
    val inputObject = state.optJSONObject("inputs") ?: JSONObject()
    val inputs = inputObject.keys().asSequence().associateWith { inputObject.optString(it) }
    val selected = state.optJSONObject("selectedRecord") ?: JSONObject()
    val currentRecord = SelectedRecord.resolve(selected.keys().asSequence().associateWith { selected.get(it) }, records,
        state.optString("selectedEntity").takeIf { it.isNotBlank() })
    val row = currentRecord?.second.orEmpty()
    val context = ExpressionContext.forSpec(parsed, inputs, row, records, currentRecord?.first)
    val screenId = state.optString("screen", parsed.startScreen)
    val screen = parsed.screens.find { it.id == screenId } ?: parsed.screens.first { it.id == parsed.startScreen }
    fun save(next: JSONObject) {
        next.put("uiVersion", state.optLong("uiVersion") + 1)
        repo.saveRuntime(appId, next); stateText = next.toString()
    }
    var deleting by remember { mutableStateOf<Pair<String, Map<String, Any>>?>(null) }
    fun act(id: String, rowValues: Map<String, Any> = row) {
        if (parsed.actions.firstOrNull { it.id == id }?.kind == ActionKind.DELETE) { deleting = id to rowValues; return }
        runCatching { repo.perform(appId, id, inputs, rowValues) }.onSuccess { next -> stateText = next.toString(); dataVersion++ }
            .onFailure { error = it.message ?: "This action could not finish. Your saved data is retained." }
    }
    fun confirmDelete(id: String, rowValues: Map<String, Any>) {
        runCatching { repo.perform(appId, id, inputs, rowValues) }.onSuccess { next -> stateText = next.toString(); dataVersion++ }
            .onFailure { error = it.message ?: "This action could not finish. Your saved data is retained." }
    }
    fun input(id: String, text: String) {
        runCatching {
            require(text.length <= 4096) { "This field supports up to 4,096 characters" }
            val next = JSONObject(stateText)
            val values = next.optJSONObject("inputs") ?: JSONObject()
            values.put(id, text); next.put("inputs", values); save(next)
        }.onFailure { error = it.message }
    }
    fun navigate(destination: String) {
        runCatching { repo.navigateTopLevel(appId, destination) }.onSuccess { stateText = it.toString() }
            .onFailure { error = it.message ?: "This screen could not be opened." }
    }
    BackHandler(enabled = !embedded || screen.id != parsed.startScreen || (state.optJSONArray("navigationHistory")?.length() ?: 0) > 0) {
        runCatching { repo.back(appId) }.onSuccess { next -> if (next != null) stateText = next.toString() else onClose() }
            .onFailure { error = it.message ?: "The previous screen could not be opened." }
    }
    val appearanceContext = androidx.compose.ui.platform.LocalContext.current
    val projectRows by remember(appearanceContext) { com.buildai.app.data.storage.BuildAiDatabase.get(appearanceContext).projects().observe() }.collectAsState(emptyList())
    val initialAppearance = remember(appId) { com.buildai.app.data.storage.ConversationRepository(appearanceContext).load(appId)?.designSpec }
    val appearance = remember(appId, projectRows) { projectRows.firstOrNull { it.id == appId }?.let { com.buildai.app.data.model.Conversation.fromJson(JSONObject(it.json)).designSpec } ?: initialAppearance }
    val destinations = remember(parsed) { NativeNavigation.topLevelScreens(parsed) }
    val tabs = remember(parsed, appearance) { NativeNavigation.tabs(parsed, appearance ?: com.buildai.app.data.model.DesignSpec()) }
    val tabbed = appearance?.templateId == "tabbed" && destinations.size <= 5
    val palette = appearance?.let { com.buildai.app.data.model.DesignDefaults.palette(it.paletteId) }
    val scheme = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = if (palette == null) scheme else NativeAppearance.scheme(scheme, palette)) {
    Scaffold(bottomBar = {
        if (tabbed && destinations.size > 1) NavigationBar {
            destinations.forEachIndexed { index, destination -> NavigationBarItem(selected = destination.id == screen.id, onClick = { navigate(destination.id) }, icon = { Icon(com.buildai.app.data.model.DesignDefaults.icon(tabs[index].iconKey).vector, null) }, label = { Text(tabs[index].name) }) }
        }
    }, topBar = {
        if (!embedded) TopAppBar(title = { Text(parsed.name) }, navigationIcon = { TextButton(onClick = onClose) { Text("Close") } },
            actions = { TextButton(onClick = { showHistory = true }) { Text("History") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (destinations.size > 1 && !tabbed) item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    destinations.forEach { destination ->
                        FilterChip(selected = destination.id == screen.id, onClick = { navigate(destination.id) }, modifier = Modifier.heightIn(min = 48.dp), label = { Text(destination.title) })
                    }
                }
            }
            screen.components.forEach { component ->
                if (component.kind == ComponentKind.RECORD_LIST) {
                    val queryResult = runCatching { RecordQueries.visible(parsed, component, context) }
                    val visible = queryResult.getOrDefault(emptyList())
                    item(key = "header-${component.id}") {
                        Text(component.label, style = MaterialTheme.typography.titleMedium)
                        if (queryResult.isFailure) Text("Enter valid filter inputs to see these items.")
                        else if (visible.isEmpty()) Text(if (records[component.entityId].orEmpty().isEmpty()) "No saved items yet." else "No items match these filters.")
                    }
                    items(visible, key = { "${component.id}-${it["id"]}" }) { values ->
                        NativeRecordCard(parsed, component, values, records, compact = appearance?.templateId == "list") { action -> act(action, values) }
                    }
                } else item(key = component.id) { when (component.kind) {
                    ComponentKind.INPUT -> if (component.fieldKind == FieldKind.REFERENCE) {
                        var expanded by remember(component.id) { mutableStateOf(false) }
                        val options = records[component.referenceEntity].orEmpty()
                        val selectedOption = options.firstOrNull { it["id"] == inputs[component.id] }
                        val entity = parsed.entities.first { it.id == component.referenceEntity }
                        fun label(values: Map<String, Any>): String = entity.fields.firstOrNull { it.kind == FieldKind.TEXT }?.let { values[it.id]?.toString() }
                            ?: values.filterKeys { it != "id" }.values.firstOrNull()?.toString() ?: "Saved item"
                        Box {
                            OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("${component.label}: ${selectedOption?.let(::label) ?: "Choose a record"}") }
                            if (expanded) {
                                var search by remember { mutableStateOf("") }
                                AlertDialog(onDismissRequest = { expanded = false }, title = { Text("Choose ${component.label}") }, text = {
                                    Column {
                                        OutlinedTextField(search, { search = it }, label = { Text("Search saved items") }, modifier = Modifier.fillMaxWidth())
                                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                                            item { TextButton(onClick = { input(component.id, ""); expanded = false }) { Text("None") } }
                                            val matching = options.filter { label(it).contains(search, true) }
                                            if (matching.isEmpty()) item { Text(if (options.isEmpty()) "No saved items yet. Add one first." else "No matching items.", Modifier.padding(8.dp)) }
                                            items(matching, key = { it["id"].toString() }) { values -> TextButton(onClick = { input(component.id, values["id"].toString()); expanded = false }, modifier = Modifier.fillMaxWidth()) { Text(label(values)) } }
                                        }
                                    }
                                }, confirmButton = { TextButton(onClick = { expanded = false }) { Text("Cancel") } })
                            }
                        }
                    } else if (component.fieldKind == FieldKind.BOOLEAN) {
                        Row {
                            Checkbox(checked = inputs[component.id] == "true", onCheckedChange = { input(component.id, it.toString()) })
                            Text(component.label, Modifier.padding(top = 14.dp))
                        }
                    } else if (component.fieldKind == FieldKind.DATE) {
                        var choosingDate by remember(component.id) { mutableStateOf(false) }
                        OutlinedTextField(inputs[component.id].orEmpty(), { input(component.id, it) }, label = { Text(component.label) }, supportingText = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), trailingIcon = {
                            IconButton(onClick = { choosingDate = true }) { Icon(Icons.Default.DateRange, "Choose ${component.label} date") }
                        })
                        if (choosingDate) {
                            val millis = runCatching { java.time.LocalDate.parse(inputs[component.id]).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
                            val date = rememberDatePickerState(initialSelectedDateMillis = millis)
                            DatePickerDialog(onDismissRequest = { choosingDate = false }, confirmButton = { TextButton(enabled = date.selectedDateMillis != null, onClick = {
                                date.selectedDateMillis?.let { input(component.id, java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()) }; choosingDate = false
                            }) { Text("Use date") } }, dismissButton = { TextButton(onClick = { choosingDate = false }) { Text("Cancel") } }) { DatePicker(date) }
                        }
                    } else OutlinedTextField(value = inputs[component.id].orEmpty(), onValueChange = { input(component.id, it) },
                        label = { Text(component.label) }, modifier = Modifier.fillMaxWidth(),
                        supportingText = if (component.fieldKind == FieldKind.DATE) ({ Text("YYYY-MM-DD") }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = if (component.fieldKind == FieldKind.NUMBER) KeyboardType.Decimal else KeyboardType.Text))
                    ComponentKind.BUTTON -> if (parsed.actions.firstOrNull { it.id == component.actionId }?.kind == ActionKind.DELETE)
                        OutlinedButton(onClick = { act(component.actionId) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(component.label, color = MaterialTheme.colorScheme.error) }
                        else Button(onClick = { act(component.actionId) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(component.label) }
                    ComponentKind.TEXT, ComponentKind.SUMMARY -> {
                        val result = runCatching { component.expression?.let { Expressions.display(Expressions.evaluate(it, context)) } ?: component.label }
                        if (component.kind == ComponentKind.SUMMARY) Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) { Text(component.label); Text(result.getOrDefault("Enter valid inputs"), style = MaterialTheme.typography.headlineSmall) }
                        } else Text(result.getOrDefault("Enter valid inputs"))
                    }
                    ComponentKind.RECORD_LIST -> Unit
                    ComponentKind.TIMER -> {
                        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(component.id) { while (true) { delay(1000); now = System.currentTimeMillis() } }
                        val deadline = state.optJSONObject("timers")?.optLong(component.id) ?: 0L
                        val seconds = ((deadline - now + 999) / 1000).coerceAtLeast(0)
                        Text("${component.label}: ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.headlineSmall)
                    }
                    ComponentKind.QUIZ -> {
                        val quiz = state.optJSONObject("quiz-${component.id}") ?: JSONObject()
                        val cursor = quiz.optInt("cursor")
                        val question = component.questions.getOrNull(cursor)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(component.label, style = MaterialTheme.typography.titleMedium)
                            if (question == null) Text("Finished: ${quiz.optInt("correct")} / ${component.questions.size} correct")
                            else {
                                Text(question.question)
                                question.options.forEachIndexed { index, option ->
                                    OutlinedButton(onClick = {
                                        val next = JSONObject(stateText)
                                        next.put("quiz-${component.id}", JSONObject().put("cursor", cursor + 1).put("correct", quiz.optInt("correct") + if (index == question.answer) 1 else 0))
                                        save(next)
                                    }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(option) }
                                }
                            }
                        }
                    }
                } }
            }
        }
    }
    deleting?.let { (action, values) -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this item?") },
        text = { Text("This removes the saved item. Related records may prevent deletion if they still need it.") },
        confirmButton = { TextButton(onClick = { confirmDelete(action, values); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Please check this action") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
    if (showHistory) com.buildai.app.ui.screens.VersionHistoryDialog(repo, appId) { showHistory = false }

    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NativeRecordCard(spec: AppSpec, component: ComponentSpec, values: Map<String, Any>, records: Map<String, List<Map<String, Any>>>, compact: Boolean = false, onAction: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (compact) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(if (compact) 8.dp else 16.dp)) {
            val entity = spec.entities.first { it.id == component.entityId }
            entity.fields.filter { it.id in component.displayFields }.forEach { field -> values[field.id]?.let { value ->
                val display = if (field.kind == FieldKind.REFERENCE && value.toString().isNotBlank()) {
                    val linked = records[field.referenceEntity].orEmpty().firstOrNull { it["id"] == value }
                    val title = spec.entities.first { it.id == field.referenceEntity }.fields.firstOrNull { it.kind == FieldKind.TEXT }
                    linked?.let { title?.let { f -> it[f.id]?.toString() } ?: "Linked record" } ?: "Linked record unavailable"
                } else value.toString()
                Text("${field.label}: $display")
            } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                component.rowActions.forEach { actionId ->
                    val action = spec.actions.first { it.id == actionId }
                    TextButton(onClick = { onAction(actionId) }, Modifier.heightIn(min = 48.dp)) { Text(action.label, color = if (action.kind == ActionKind.DELETE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}
