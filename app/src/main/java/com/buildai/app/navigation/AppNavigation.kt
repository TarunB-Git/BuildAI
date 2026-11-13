package com.buildai.app.navigation

import android.content.Intent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.buildai.app.ui.components.ScreenTransitionMillis
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.first
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.buildai.app.BuildAiApplication
import com.buildai.app.data.model.*
import com.buildai.app.runtime.*
import com.buildai.app.ui.screens.*
import com.buildai.app.ui.theme.*
import com.buildai.app.viewmodel.*

object Routes {
    const val APPS = "apps"
    const val CHATS = "chats"
    const val HOME = "create"
    const val ACTIVITY = "activity"
    const val WORKSPACE = "workspace/{projectId}"
    const val CONVERSATION = "chat/{projectId}"
    const val DESIGN = "appearance/{projectId}?build={build}"
    const val GUIDED = "guided/{projectId}"
    const val SETTINGS = "settings?page={page}"
    fun workspace(id: String) = "workspace/$id"
    fun conversation(id: String?) = "chat/${id.orEmpty()}"
}
@Composable
fun AppNavigation(requestedProjectId: String? = null, onProjectHandled: () -> Unit = {}, requestedOpenApp: Boolean = false) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as BuildAiApplication
    val container = app.container
    val vm: ConversationViewModel = viewModel(factory = viewModelFactory { initializer { ConversationViewModel(app, container.conversation, container.projects, container.settings, createSavedStateHandle()) } })
    val home: HomeViewModel = viewModel()
    val settings: SettingsViewModel = viewModel()
    val design: DesignViewModel = viewModel(factory = viewModelFactory { initializer { DesignViewModel(app, container.projects, null) } })
    val generation: GenerationViewModel = viewModel(factory = viewModelFactory { initializer { GenerationViewModel(app, container.projects, container.settings) } })
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun root(route: String = Routes.APPS) {
        if (nav.currentDestination?.route == route) return
        nav.navigate(route) {
            popUpTo(Routes.CHATS) { saveState = false }
            launchSingleTop = true
        }
    }
    suspend fun showProject(id: String) {
        val project = withContext(Dispatchers.IO) { container.projects.load(id) }
        lifecycle.withResumed {
            val route = if (project?.assistantMode == AssistantMode.CHAT) Routes.CONVERSATION else Routes.WORKSPACE
            if (nav.currentDestination?.route != route || nav.currentBackStackEntry?.arguments?.getString("projectId") != id) {
                nav.navigate(if (route == Routes.CONVERSATION) Routes.conversation(id) else Routes.workspace(id)) { launchSingleTop = true }
            }
        }
    }
    fun openProject(id: String) { scope.launch { showProject(id) } }
    suspend fun showApp(id: String) {
        val type = withContext(Dispatchers.IO) {
            if (NativeAppRepository(app).dao.head(id) != null) NativeRunnerActivity::class.java
            else if (container.projects.generatedFile(id) != null) LegacyRunnerActivity::class.java else null
        }
        if (type != null) lifecycle.withResumed { app.startActivity(Intent(app, type).putExtra("appId", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } else showProject(id)
    }
    fun openApp(id: String) { scope.launch { showApp(id) } }
    fun returnToLibrary(id: String) { scope.launch {
        val finished = withContext(Dispatchers.IO) { NativeAppRepository(app).dao.head(id) != null || container.projects.generatedFile(id) != null }
        lifecycle.withResumed { root(if (finished) Routes.APPS else Routes.HOME) }
    } }
    fun setup() { nav.navigate("settings?page=ai") }
    fun start(mode: AssistantMode, initial: String = "") {
        val needsSetup = if (container.settings.getChatBackend() == "local") com.buildai.app.data.local.LocalModelStore(app).selectedFile() == null else !container.settings.hasApiKey()
        vm.startNewConversation(mode)
        val id = checkNotNull(vm.projectId)
        if (initial.isNotBlank()) {
            if (needsSetup) { vm.setDraftText(initial); container.projects.update(id) { it.copy(title = initial.take(60)) } }
            else vm.sendUserMessage(initial)
        }
        home.setEntryText(""); openProject(id)
    }
    fun manual() { vm.startNewConversation(); nav.navigate("guided/${vm.projectId}") }
    LaunchedEffect(requestedProjectId, requestedOpenApp) { if (!requestedProjectId.isNullOrBlank()) { nav.currentBackStackEntryFlow.first(); if (requestedOpenApp) showApp(requestedProjectId) else showProject(requestedProjectId); onProjectHandled() } }
    val destination = nav.currentBackStackEntryAsState().value?.destination?.route
    // Keep the departing screen's state until its exit fade completes.
    LaunchedEffect(destination) {
        if (destination in listOf(Routes.APPS, Routes.CHATS, Routes.HOME)) {
            val departingProject = vm.projectId
            delay(ScreenTransitionMillis.toLong())
            if (vm.projectId == departingProject) { vm.leaveProject(); generation.reset() }
        }
    }
    @Composable fun library(content: @Composable () -> Unit) {
        Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
            NavigationBar(containerColor = Surface.copy(alpha = .82f)) {
                listOf(Triple(Routes.CHATS, "Chat", Icons.Default.ChatBubbleOutline), Triple(Routes.HOME, "Create", Icons.Default.AddCircleOutline), Triple(Routes.APPS, "Apps", Icons.Default.Apps)).forEach { (route, label, icon) ->
                    NavigationBarItem(destination == route, modifier = Modifier.testTag("root-$label"), onClick = { root(route) }, icon = { Icon(icon, null) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Primary, selectedTextColor = Primary, indicatorColor = UserBubble))
                }
            }
        }) { padding -> Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) { content() } }
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(UserBubble, Background, Background, Surface)))) {
    NavHost(nav, startDestination = Routes.CHATS, modifier = Modifier.fillMaxSize(),
        enterTransition = { fadeIn(tween(ScreenTransitionMillis)) }, exitTransition = { fadeOut(tween(ScreenTransitionMillis)) },
        popEnterTransition = { fadeIn(tween(ScreenTransitionMillis)) }, popExitTransition = { fadeOut(tween(ScreenTransitionMillis)) }) {
        composable(Routes.APPS) { library { AppsScreen(home, ::openApp, ::openProject, { root() }, onCreate = { root(Routes.HOME) }, onContinue = ::openProject, onSettings = { nav.navigate("settings?page=root") }, onActivity = { nav.navigate(Routes.ACTIVITY) }) } }
        composable(Routes.CHATS) { library { ChatsScreen(home, onNew = { start(AssistantMode.CHAT) }, onOpen = ::openProject, onSettings = { nav.navigate("settings?page=root") }, onActivity = { nav.navigate(Routes.ACTIVITY) }) } }
        composable(Routes.HOME) { library { HomeScreen(home, { start(AssistantMode.CREATE, it) }, ::openProject, home::delete, ::setup, { root() }, onManual = ::manual, onStarter = { template ->
            vm.startNewConversation(AssistantMode.CREATE, nativePlanning = false)
            vm.updatePlan(ProjectPlan(template.label, "A personal ${template.label.lowercase()} that works offline.", AppType.APP, template.features, listOf("Home"), "You", Complexity.SIMPLE, "", BuildMode.TEMPLATE, template))
            container.projects.update(checkNotNull(vm.projectId)) { it.copy(title = template.label, titleOwned = true, workspaceTab = "plan") }
            openProject(checkNotNull(vm.projectId))
        }, onSettings = { nav.navigate("settings?page=root") }) } }
        composable(Routes.WORKSPACE, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { entry ->
            val id = checkNotNull(entry.arguments?.getString("projectId"))
            ProjectWorkspaceScreen(app, id, vm, generation, design, { returnToLibrary(id) }, ::openApp, { nav.navigate("settings?page=root") }, { nav.navigate(Routes.ACTIVITY) },
                { nav.navigate("guided/$id") }, { nav.navigate("appearance/$id") }, { home.delete(it); root() }, onDesignToBuild = { nav.navigate("appearance/$id?build=true") }, onAiSetup = ::setup)
        }
        composable(Routes.CONVERSATION, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { entry ->
            val id = checkNotNull(entry.arguments?.getString("projectId")); var loaded by remember(id) { mutableStateOf(false) }
            LaunchedEffect(id) { vm.loadConversation(id); loaded = true }
            if (loaded) ConversationScreen(vm, { openProject(id) }, ::setup, { root(Routes.CHATS) }, title = container.projects.load(id)?.displayName ?: "Chat", onExport = {
                runCatching { container.projects.load(id)?.let { app.startActivity(Intent.createChooser(container.projects.buildShareIntent(it), "Export conversation").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    .onFailure { android.widget.Toast.makeText(app, "Could not export this conversation. Check available storage and retry.", android.widget.Toast.LENGTH_LONG).show() }
            })
        }
        composable(Routes.GUIDED, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { entry ->
            val id = checkNotNull(entry.arguments?.getString("projectId"))
            GuidedConfigurationScreen(app, id, { nav.popBackStack() }, onOpenApp = { openApp(it); if (nav.currentDestination?.route == Routes.GUIDED) { nav.popBackStack(); openProject(it) } })
        }
        composable(Routes.DESIGN, arguments = listOf(navArgument("projectId") { type = NavType.StringType }, navArgument("build") { type = NavType.BoolType; defaultValue = false })) { entry ->
            val id = checkNotNull(entry.arguments?.getString("projectId"))
            LaunchedEffect(id) { if (vm.projectId != id) vm.loadConversation(id); design.bind(id, editDraft = true) }
            val buildAfterSave = entry.arguments?.getBoolean("build") == true
            val nativeRepo = remember { NativeAppRepository(app) }
            val heads by remember { nativeRepo.dao.observeHeads() }.collectAsState(emptyList())
            val initialHead = remember(id) { nativeRepo.dao.head(id) }
            val head = heads.firstOrNull { it.appId == id } ?: initialHead
            val nativeSpec = remember(id, head?.revisionId) { nativeRepo.spec(id) }
            val nativePlan = vm.brief.collectAsState().value?.plan?.takeIf { vm.projectPlan.value?.buildMode != BuildMode.TEMPLATE }
            DesignScreen(vm, design, onBuild = {
                if (buildAfterSave) runCatching {
                    if (nativePlan != null) { vm.approveNativePlan(); container.nativeBuilds.enqueue(id) }
                    else generation.generate(id, checkNotNull(vm.projectPlan.value), design.spec.value)
                }.onSuccess { nav.popBackStack() }.onFailure { design.reportError(it.message ?: "Could not start the build") }
                else nav.popBackStack()
            }, onBack = { nav.popBackStack() }, onStartOver = { root(Routes.HOME) }, onEditIdea = { nav.popBackStack() }, appearanceOnly = true,
                nativeScreens = nativePlan?.screens, buildAfterSave = buildAfterSave, nativeSpec = nativeSpec)

        }
        composable(Routes.ACTIVITY) { ActivityScreen(app, ::openProject, onSettings = { nav.navigate("settings?page=root") }, onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS, arguments = listOf(navArgument("page") { defaultValue = "root" })) { entry -> SettingsScreen(settings, { nav.popBackStack() }, initialPage = entry.arguments?.getString("page") ?: "root") }
    }
    }
}
