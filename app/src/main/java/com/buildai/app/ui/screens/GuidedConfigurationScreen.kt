package com.buildai.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.buildai.app.BuildAiApplication
import com.buildai.app.builder.GuidedConfiguration
import com.buildai.app.runtime.*
import org.json.JSONObject
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuidedConfigurationScreen(app: BuildAiApplication, id: String, onBack: () -> Unit, onOpenApp: (String) -> Unit) {
    val project = app.container.projects.load(id) ?: return
    var draft by rememberSaveable(id) { mutableStateOf(project.guidedDraft.ifBlank { GuidedConfiguration().json().toString() }) }
    val config = GuidedConfiguration.read(JSONObject(draft))
    var error by remember { mutableStateOf<String?>(null) }
    fun update(next: GuidedConfiguration) {
        draft = next.json().toString(); app.container.projects.update(id) { it.copy(guidedDraft = draft, title = next.name.trim().take(80).ifBlank { it.title }, titleOwned = next.name.isNotBlank() || it.titleOwned) }
    }
    val numeric = config.fields.filter { it.kind == FieldKind.NUMBER }
    val existing = NativeAppRepository(app).dao.head(id) != null
    val problem = runCatching { config.compose() }.exceptionOrNull()?.message
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text("Build manually") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }, bottomBar = {
        Surface { Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!existing && problem != null) Text(problem, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!existing) Button(enabled = problem == null, onClick = {
                runCatching {
                    val spec = config.compose()
                    NativeAppRepository(app).publish(id, spec, null, "Guided configuration: ${spec.name}".take(500),
                        JSONObject().put("structure", "passed").put("method", "Explicit user configuration; no AI used").put("featureAcceptance", "User preview and physical acceptance pending"))
                    app.container.projects.update(id) { it.copy(title = spec.name, titleOwned = true, phase = "CONFIRMED") }
                    onOpenApp(id)
                }.onFailure { error = it.message }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Build app") }
            else { Button(onClick = { onOpenApp(id) }) { Text("Open app") } }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Make a useful tool with fields, saved records, search, and a calculation. No model or API key needed. Your draft saves as you go.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { OutlinedTextField(config.name, { update(config.copy(name = it)) }, label = { Text("App name") }, modifier = Modifier.fillMaxWidth()) }
            config.fields.forEach { field -> item(key = field.id) {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(field.label, { label -> update(config.copy(fields = config.fields.map { if (it.id == field.id) it.copy(label = label) else it })) }, label = { Text("Field label") }, modifier = Modifier.fillMaxWidth())
                    Text("Type: ${field.kind.name.lowercase()}")
                    TextButton(onClick = { update(config.copy(fields = config.fields.filterNot { it.id == field.id })) }) { Text("Remove field") }
                } }
            } }
            item {
                Text("Add an input", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(FieldKind.TEXT, FieldKind.NUMBER, FieldKind.BOOLEAN, FieldKind.DATE).forEach { kind -> TextButton(enabled = config.fields.size < 12, onClick = {
                        update(config.copy(fields = config.fields + FieldSpec("field" + UUID.randomUUID().toString().take(8), "New ${kind.name.lowercase()} field", kind)))
                    }) { Text(kind.name.lowercase().replaceFirstChar(Char::uppercase)) } }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(config.saveRecords, role = Role.Checkbox, onValueChange = { update(config.copy(saveRecords = it)) }), verticalAlignment = Alignment.CenterVertically) { Checkbox(config.saveRecords, null); Text("Save records locally") }
                if (config.saveRecords) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(config.search, role = Role.Checkbox, onValueChange = { update(config.copy(search = it)) }), verticalAlignment = Alignment.CenterVertically) { Checkbox(config.search, null); Text("Add record search") }
                Text("Optional live calculation", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to "None", "ADD" to "+", "SUBTRACT" to "−", "MULTIPLY" to "×", "DIVIDE" to "÷").forEach { (operation, label) ->
                        FilterChip(config.calculation == operation, onClick = { update(config.copy(calculation = operation)) }, label = { Text(label) })
                    }
                }
                if (config.calculation.isNotBlank()) {
                    Text("Choose the first and second numeric fields")
                    numeric.forEach { field -> Row {
                        Text(field.label, Modifier.weight(1f).padding(top = 12.dp))
                        FilterChip(config.left == field.id, { update(config.copy(left = field.id)) }, label = { Text("First") })
                        FilterChip(config.right == field.id, { update(config.copy(right = field.id)) }, label = { Text("Second") })
                    } }
                    OutlinedTextField(config.resultLabel, { update(config.copy(resultLabel = it)) }, label = { Text("Result label") }, modifier = Modifier.fillMaxWidth())
                }
            }

        }
    }
}
