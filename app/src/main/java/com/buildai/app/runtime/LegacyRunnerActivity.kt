package com.buildai.app.runtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.buildai.app.ui.theme.prepareBuildAiWindow
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.buildai.app.data.storage.ConversationRepository
import com.buildai.app.ui.components.GeneratedAppWebView

/** Preserve old HTML and its exact generatedAppOrigin; no automatic native conversion. */
class LegacyRunnerActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); prepareBuildAiWindow()
        val id = intent.getStringExtra("appId")
        val projects = ConversationRepository(this)
        val project = id?.let(projects::load)
        val html = id?.let(projects::generatedFile)?.readText()
        setContent {
            com.buildai.app.ui.theme.BuildAITheme {
                var issue by remember { mutableStateOf<String?>(null) }
                Scaffold(topBar = { TopAppBar(title = { Text(project?.displayName ?: "Saved app") }, navigationIcon = { TextButton(onClick = ::finish) { Text("Close") } }) }) { padding ->
                    if (html == null) Text("The saved app could not be opened. Its project is retained.", Modifier.padding(padding).padding(24.dp))
                    else Box(Modifier.fillMaxSize().padding(padding)) {
                        AndroidView(factory = { context -> GeneratedAppWebView(context) }, update = { view ->
                            view.onPreviewIssue = { issue = it }; view.loadApp(html, id, 0)
                        }, onReset = null, onRelease = { it.destroy() }, modifier = Modifier.fillMaxSize())
                    }
                }
                issue?.let { message -> AlertDialog(onDismissRequest = { issue = null }, text = { Text(message) }, confirmButton = { TextButton(onClick = { issue = null }) { Text("OK") } }) }
            }
        }
    }
}
