package com.nageebstudyos.study

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.nageebstudyos.study.domain.*
import com.nageebstudyos.study.presentation.*
import com.nageebstudyos.study.ui.*
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as StudyApplication).container
            val vm: StudyViewModel =
                viewModel(
                    factory =
                        object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                StudyViewModel(container) as T
                        }
                )
            val settings by vm.settings.collectAsStateWithLifecycle()
            val settingsError by vm.settingsError.collectAsStateWithLifecycle()
            val configuration = LocalConfiguration.current
            val language =
                settings?.get("language")
                    ?: if (configuration.locales[0].language == "ar") "ar" else "en"
            val localizedConfig =
                remember(configuration, language) {
                    Configuration(configuration).apply {
                        setLocale(Locale(language))
                        setLayoutDirection(Locale(language))
                    }
                }
            val localizedContext =
                remember(localizedConfig) {
                    // Preserve the Activity chain for SAF launchers, dialogs and back dispatch.
                    android.view.ContextThemeWrapper(this, R.style.Theme_Nageeb).apply {
                        applyOverrideConfiguration(localizedConfig)
                    }
                }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig,
                LocalLayoutDirection provides
                    if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                NageebTheme(if (settings == null) "dark" else settings?.get("theme") ?: "system") {
                    val background = MaterialTheme.colorScheme.background
                    SideEffect {
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = background.red > .5f
                            isAppearanceLightNavigationBars = background.red > .5f
                        }
                    }
                    if (settingsError != null)
                        Surface(Modifier.fillMaxSize()) {
                            Box(Modifier.safeDrawingPadding()) {
                                Failure(settingsError!!, vm::retrySettings)
                            }
                        }
                    else if (settings == null)
                        Surface(Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    else StudyRoot(vm, settings!! + ("language" to language))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyRoot(vm: StudyViewModel, settings: Map<String, String>) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val args = backStack?.arguments
    val scope =
        if (route.startsWith("library/"))
            Scope(
                Kind.valueOf(args?.getString("kind") ?: "SUBJECT"),
                args?.getString("id"),
                args?.getString("subject")?.takeUnless { it == "_" },
            )
        else Scope()
    val library by vm.library.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()
    val editorError by vm.editorError.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val tagState by vm.tagState.collectAsStateWithLifecycle()
    val tagTarget by vm.tagTarget.collectAsStateWithLifecycle()
    val attached by vm.attachedTags.collectAsStateWithLifecycle()
    val moving by vm.moveTarget.collectAsStateWithLifecycle()
    val moveFolders by vm.folders.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    var quickAdd by remember { mutableStateOf(false) }
    var fileChoice by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<Entry?>(null) }
    var showTree by remember { mutableStateOf(false) }
    var pendingLesson by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImported by rememberSaveable { mutableStateOf(false) }
    var currentMenu by remember { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val lesson = pendingLesson
            if (uri != null && lesson != null)
                vm.attach(uri, lesson, pendingImported, context.getString(R.string.file))
            pendingLesson = null
        }
    LaunchedEffect(context) {
        vm.notices.collect { notice ->
            val message =
                when (notice) {
                    is Notice.Failure -> notice.problem.message()
                    Notice.Saved -> R.string.saved
                    Notice.Deleted -> R.string.deleted
                }
            snackbars.showSnackbar(context.getString(message))
        }
    }
    LaunchedEffect(route, scope) {
        if (route == "subjects" || route.startsWith("library/")) vm.selectScope(scope)
    }
    fun navigateEntry(entry: Entry) {
        when (entry.kind) {
            Kind.NOTE -> vm.edit(entry)
            Kind.LINK -> vm.openLink(entry)
            Kind.TAG -> nav.navigate("tags")
            else ->
                nav.navigate(
                    "library/${entry.kind.name}/${entry.id}/${entry.subjectId ?: if(entry.kind == Kind.SUBJECT) entry.id else "_"}"
                )
        }
    }
    fun action(entry: Entry, key: String) {
        when (key) {
            "edit" -> vm.edit(entry)
            "delete" -> deleteEntry = entry
            "tags" -> vm.showTags(entry)
            "move" -> vm.prepareMove(entry)
            "up" -> {
                vm.setting("sort", "manual")
                vm.reorder(entry, -1)
            }
            "down" -> {
                vm.setting("sort", "manual")
                vm.reorder(entry, 1)
            }
            "child" -> vm.create(Kind.FOLDER, Scope(Kind.FOLDER, entry.id, entry.subjectId))
        }
    }
    val isMain = route == "home" || route == "subjects"
    val canAdd = isMain || route.startsWith("library/") && !library.loading && library.error == null
    val quickAddDescription = stringResource(R.string.quick_add)
    Scaffold(
        modifier = Modifier.testTag("study-root-${settings["theme"] ?: "system"}"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isMain) {
                            Box(
                                Modifier.size(32.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onBackground,
                                        RoundedCornerShape(8.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "N",
                                    color = MaterialTheme.colorScheme.background,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    if (!isMain)
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                stringResource(R.string.back),
                            )
                        }
                },
                actions = {
                    if (route != "search")
                        IconButton(onClick = { nav.navigate("search") }) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.search))
                        }
                    if (route.startsWith("library/") && library.data.current != null)
                        Box {
                            IconButton(onClick = { currentMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, stringResource(R.string.more))
                            }
                            DropdownMenu(currentMenu, { currentMenu = false }) {
                                val e = library.data.current!!
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit)) },
                                    onClick = {
                                        currentMenu = false
                                        action(e, "edit")
                                    },
                                )
                                if (e.kind in setOf(Kind.FOLDER, Kind.LESSON))
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.move)) },
                                        onClick = {
                                            currentMenu = false
                                            action(e, "move")
                                        },
                                    )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = {
                                        currentMenu = false
                                        action(e, "delete")
                                    },
                                )
                            }
                        }
                    else if (route != "settings")
                        IconButton(onClick = { nav.navigate("settings") }) {
                            Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
                        }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
            )
        },
        bottomBar = {
            if (isMain || route.startsWith("library/")) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                    ) {
                        val destinations =
                            listOf(
                                Triple("home", R.string.home, Icons.Outlined.Home),
                                Triple("subjects", R.string.subjects, Icons.Outlined.AutoStories),
                                Triple("planner", R.string.planner, Icons.Outlined.CalendarToday),
                                Triple("focus", R.string.focus, Icons.Outlined.CenterFocusStrong),
                                Triple("analytics", R.string.analytics, Icons.Outlined.BarChart),
                            )
                        destinations.forEachIndexed { index, (target, label, icon) ->
                            NavigationBarItem(
                                selected =
                                    route == target ||
                                        target == "subjects" && route.startsWith("library/"),
                                onClick = {
                                    nav.navigate(target) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                enabled = index < 2,
                                icon = {
                                    Icon(
                                        icon,
                                        if (index >= 2) stringResource(R.string.later) else null,
                                        Modifier.size(22.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        stringResource(label),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 2,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (canAdd)
                ExtendedFloatingActionButton(
                    modifier =
                        Modifier.testTag("quick-add").semantics {
                            contentDescription = quickAddDescription
                        },
                    onClick = { quickAdd = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(R.string.add)) },
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.weight(1f),
            ) {
                composable("home") {
                    HomeScreen(
                        recent,
                        { vm.create(Kind.SUBJECT, Scope()) },
                        { nav.navigate("search") },
                        { nav.navigate("subjects") },
                        ::navigateEntry,
                        ::action,
                        vm::refreshLists,
                    )
                }
                composable("subjects") {
                    LibraryScreen(
                        Scope(),
                        library,
                        settings["sort"] ?: "manual",
                        { vm.setting("sort", it) },
                        { quickAdd = true },
                        vm::retry,
                        ::navigateEntry,
                        ::action,
                        { showTree = true },
                        { nav.navigate("subjects") },
                        vm::openFile,
                    )
                }
                composable(
                    "library/{kind}/{id}/{subject}",
                    arguments =
                        listOf(
                            navArgument("kind") { type = NavType.StringType },
                            navArgument("id") { type = NavType.StringType },
                            navArgument("subject") { type = NavType.StringType },
                        ),
                ) {
                    LibraryScreen(
                        scope,
                        library,
                        settings["sort"] ?: "manual",
                        { vm.setting("sort", it) },
                        { quickAdd = true },
                        vm::retry,
                        ::navigateEntry,
                        ::action,
                        { showTree = true },
                        { nav.navigate("subjects") },
                        vm::openFile,
                    )
                }
                composable("search") {
                    SearchScreen(
                        query,
                        search,
                        { vm.query.value = it },
                        ::navigateEntry,
                        ::action,
                        vm::refreshLists,
                    )
                }
                composable("settings") {
                    SettingsScreen(settings, busy, vm::setting, { nav.navigate("tags") })
                }
                composable("tags") {
                    TagsScreen(
                        tagState,
                        { vm.create(Kind.TAG, Scope()) },
                        ::action,
                        vm::refreshLists,
                    )
                }
            }
        }
    }
    if (quickAdd)
        QuickAddDialog(
            scope,
            { kind ->
                quickAdd = false
                if (kind == Kind.FILE) fileChoice = true else vm.create(kind, scope)
            },
            { quickAdd = false },
        )
    if (fileChoice)
        FileChoiceDialog(
            { imported ->
                fileChoice = false
                pendingLesson = scope.id
                pendingImported = imported
                picker.launch(
                    arrayOf(
                        "application/pdf",
                        "image/*",
                        "text/*",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-powerpoint",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "audio/*",
                        "video/*",
                    )
                )
            },
            { fileChoice = false },
        )
    deleteEntry?.let { entry ->
        DeleteDialog(
            entry,
            busy,
            {
                vm.delete(entry) {
                    deleteEntry = null
                    if (scope.id == entry.id) nav.popBackStack()
                }
            },
            { deleteEntry = null },
        )
    }
    if (showTree)
        TreeDialog(
            library.data.folders,
            null,
            busy,
            { id ->
                showTree = false
                val subject = scope.subjectId ?: scope.id.orEmpty()
                if (id == null) navigateEntry(Entry(subject, "", Kind.SUBJECT))
                else navigateEntry(Entry(id, "", Kind.FOLDER, subjectId = subject))
            },
            { showTree = false },
        )
    moving?.let { entry ->
        TreeDialog(moveFolders, entry, busy, { vm.move(entry, it) }, vm::dismissMove)
    }
    tagTarget?.let { entry ->
        TagsDialog(
            entry,
            tags,
            attached,
            busy,
            { id, selected -> vm.assignTag(entry, id, selected) },
            { vm.create(Kind.TAG, Scope()) },
            { vm.showTags(null) },
        )
    }
    editor?.let { entry -> EditorDialog(entry, busy, editorError, vm::save, vm::dismissEditor) }
}
