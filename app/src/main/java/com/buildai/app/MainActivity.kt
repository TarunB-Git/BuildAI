package com.buildai.app

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.buildai.app.navigation.AppNavigation
import com.buildai.app.ui.theme.BuildAITheme
import com.buildai.app.ui.theme.prepareBuildAiWindow

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_PROJECT_ID = "projectId"; const val EXTRA_OPEN_APP = "openApp" }

    private var requestedProjectId by mutableStateOf<String?>(null)
    private var requestedOpenApp by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareBuildAiWindow()
        requestedProjectId = intent?.getStringExtra(EXTRA_PROJECT_ID)
        requestedOpenApp = intent?.getBooleanExtra(EXTRA_OPEN_APP, false) ?: false
        setContent {
            BuildAITheme {
                AppNavigation(requestedProjectId, onProjectHandled = {
                    requestedProjectId = null
                    intent.removeExtra(EXTRA_PROJECT_ID)
                    intent.removeExtra(EXTRA_OPEN_APP)
                }, requestedOpenApp = requestedOpenApp)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedProjectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        requestedOpenApp = intent.getBooleanExtra(EXTRA_OPEN_APP, false)
    }
}
