package com.buildai.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.buildai.app.data.model.AssistantMode
import com.buildai.app.ui.theme.*

@Composable
fun AssistantModeSwitch(mode: AssistantMode, onChange: (AssistantMode) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = SurfaceVariant, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(4.dp)) {
            listOf(AssistantMode.CREATE, AssistantMode.CHAT).forEach { option ->
                Surface(onClick = { onChange(option) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp), color = if (mode == option) Surface else Color.Transparent,
                    selected = mode == option) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (option == AssistantMode.CREATE) Icons.Default.AutoAwesome else Icons.Default.ChatBubbleOutline,
                            null, Modifier.size(18.dp), tint = if (mode == option) Primary else OnSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(if (option == AssistantMode.CREATE) "Create" else "Chat", style = MaterialTheme.typography.labelLarge,
                            color = if (mode == option) Primary else OnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun AIStatus(label: String, onClick: () -> Unit) {
    val local = label.startsWith("Local", ignoreCase = true)
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val app = context as com.buildai.app.BuildAiApplication
    val store = remember(context) { com.buildai.app.data.local.LocalModelStore(context) }
    val modelPrefs = remember(context) { context.getSharedPreferences("local_model", android.content.Context.MODE_PRIVATE) }
    var installed by remember(context) { mutableStateOf(store.selectedFile() != null) }
    DisposableEffect(modelPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> installed = store.selectedFile() != null }
        modelPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { modelPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val ready = if (local) installed else app.container.settings.hasApiKey()
    val accent = if (!ready) Warning else if (local) Success else Primary
    Surface(onClick = onClick, shape = CircleShape, color = Color.Transparent) {
        Row(Modifier.heightIn(min = 32.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (local) Icons.Default.Shield else Icons.Default.CloudQueue, null, Modifier.size(16.dp), tint = accent)
            Text((if (local) "Local AI" else "Cloud AI") + if (!ready) " · Setup needed" else "", style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** The field stays editable during a reply; send queues input and stop remains a separate action. */
@Composable
fun MessageComposer(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    onSend: () -> Unit, onVoice: () -> Unit, listening: Boolean = false,
    loading: Boolean = false, onStop: () -> Unit = {}, actionLabel: String? = null,
    modifier: Modifier = Modifier, onSteer: (() -> Unit)? = null
) {
    Surface(modifier = modifier.fillMaxWidth(), color = Surface.copy(alpha = .90f), shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, if (listening) Primary else Outline), shadowElevation = 1.dp) {
        Column(Modifier.background(Brush.verticalGradient(listOf(Surface.copy(alpha = .9f), UserBubble.copy(alpha = .35f))))) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onVoice, modifier = Modifier.size(48.dp)) {
                    Icon(if (listening) Icons.Default.MicOff else Icons.Default.Mic,
                        if (listening) "Stop listening" else "Speak", tint = if (listening) Error else OnSurfaceVariant)
                }
                BasicTextField(value, onValueChange, Modifier.weight(1f).padding(vertical = 10.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnSurface), cursorBrush = SolidColor(Primary),
                    minLines = 1, maxLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank()) onSend() }),
                    decorationBox = { inner -> Box { if (value.isEmpty()) Text(if (listening) "Listening…" else placeholder, color = OnSurfaceVariant); inner() } })
                if (loading && value.isBlank()) FilledIconButton(onClick = onStop, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Stop, "Stop reply")
                } else if (actionLabel != null) Button(onClick = onSend, enabled = value.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
                else FilledIconButton(onClick = onSend, enabled = value.isNotBlank(), modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, if (loading) "Queue message" else "Send")
                }
            }
            if (loading && value.isNotBlank()) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onStop, modifier = Modifier.semantics { contentDescription = "Stop reply" }) { Text("Stop reply") }
                if (onSteer != null) TextButton(onClick = onSteer) { Text("Send now & steer") }
            }
        }
    }
}

@Composable
fun ProjectAvatar(title: String, modifier: Modifier = Modifier, chat: Boolean = false, iconKey: String? = null) {
    val accent = Primary
    Surface(modifier.size(52.dp), shape = RoundedCornerShape(17.dp), color = accent.copy(alpha = .10f)) {
        Box(contentAlignment = Alignment.Center) {
            if (chat) Icon(Icons.Default.ChatBubbleOutline, null, tint = accent, modifier = Modifier.size(24.dp))
            else if (iconKey != null) Icon(com.buildai.app.data.model.DesignDefaults.icon(iconKey).vector, null, tint = accent)
            else Icon(Icons.Default.Apps, null, tint = accent, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun WorkspaceEmptyState(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(24.dp), color = UserBubble, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = Primary, modifier = Modifier.size(30.dp)) }
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = OnBackground)
        Text(description, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun ActivityButton(onClick: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val jobs by remember { com.buildai.app.data.storage.BuildAiDatabase.get(app).jobs().observe() }.collectAsState(emptyList())
    val count = jobs.distinctBy { it.projectId to it.kind }.count { it.status in listOf("REVIEW", "FAILED", "WAITING", "INTERRUPTED") }
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
            Icon(Icons.Default.History, if (count > 0) "Activity, $count needs attention" else "Activity", tint = OnSurfaceVariant)
        }
    }
}
