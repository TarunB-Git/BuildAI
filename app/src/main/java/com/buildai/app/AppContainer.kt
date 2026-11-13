package com.buildai.app

import android.app.Application
import com.buildai.app.data.api.GeminiApi
import com.buildai.app.data.api.GeminiRepository
import com.buildai.app.data.api.PwaGenerator
import com.buildai.app.data.storage.ConversationRepository
import com.buildai.app.data.storage.SecureStorage

class AppContainer(app: Application, api: GeminiApi = GeminiApi()) {
    val projects = ConversationRepository(app)
    val settings = SecureStorage(app)
    val conversation = GeminiRepository(api)
    val generator = PwaGenerator(api)
    val sessions = com.buildai.app.data.jobs.ConversationSessions(app, conversation, projects, settings)
    val localBuilds = com.buildai.app.data.jobs.LocalBuildJobs(app, projects)
    val nativeBuilds = com.buildai.app.builder.NativeBuildJobs(app, projects, com.buildai.app.builder.BuilderModels(app, api))
    val nativeEdits = com.buildai.app.builder.NativeEditJobs(app, com.buildai.app.builder.BuilderModels(app, api))
}

class BuildAiApplication : Application() {
    // Instrumentation tests replace the container before launching an activity.
    lateinit var container: AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        if (getProcessName().endsWith(":inference")) return
        container = AppContainer(this)
        com.buildai.app.data.storage.BuildAiDatabase.get(this).jobs().recoverInterrupted(System.currentTimeMillis())
    }
    internal fun useTestContainer(container: AppContainer) {
        check(com.buildai.app.BuildConfig.DEBUG)
        this.container = container
    }
}
