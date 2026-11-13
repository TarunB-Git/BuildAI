package com.buildai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.buildai.app.data.model.Message
import com.buildai.app.data.model.MessageRole
import com.buildai.app.ui.theme.*

@Composable
fun ChatBubble(message: Message, onRetry: (() -> Unit)? = null) {
    val user = message.role == MessageRole.USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Surface(modifier = if (user) Modifier.fillMaxWidth(.88f) else Modifier.fillMaxWidth(),
            color = when { message.failed -> Error.copy(alpha = .07f); user -> UserBubble; else -> Background },
            shape = RoundedCornerShape(20.dp, 20.dp, if (user) 6.dp else 20.dp, 20.dp)) {
            if (user || message.failed) SelectionContainer {
                Text(message.content, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge, color = if (message.failed) Error else OnBackground)
            } else ChatMarkdown(message.content)
        }
        if (message.failed && onRetry != null) TextButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, null, tint = Error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp)); Text("Retry", color = Error)
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
    }
}
