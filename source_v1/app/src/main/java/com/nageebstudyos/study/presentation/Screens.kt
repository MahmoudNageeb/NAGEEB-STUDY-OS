package com.nageebstudyos.study.presentation

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nageebstudyos.study.R
import com.nageebstudyos.study.domain.*
import com.nageebstudyos.study.ui.*

@Composable
fun HomeScreen(
    state: LoadState<List<Entry>>,
    add: () -> Unit,
    search: () -> Unit,
    subjects: () -> Unit,
    open: (Entry) -> Unit,
    action: (Entry, String) -> Unit,
    retry: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Eyebrow(stringResource(R.string.local_first))
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.greeting), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.home_caption),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = search,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Icon(Icons.Outlined.Search, null)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.search_hint),
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (state.loading) item { Loading() }
        else if (state.error != null) item { Failure(state.error, retry) }
        else if (state.data.isEmpty())
            item { EmptySpace(R.string.empty_library, R.string.empty_library_body, add) }
        else {
            val continuing =
                state.data.firstOrNull {
                    it.kind == Kind.LESSON && it.status == LessonStatus.IN_PROGRESS
                }
            if (continuing != null) {
                item {
                    SectionTitle(stringResource(R.string.continue_studying))
                    Surface(
                        onClick = { open(continuing) },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(24.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                            Spacer(Modifier.height(20.dp))
                            Text(continuing.title, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                stringResource(continuing.status.label()),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            listOf(
                    Kind.SUBJECT to R.string.recent_subjects,
                    Kind.LESSON to R.string.recent_lessons,
                    Kind.FILE to R.string.recent_files,
                )
                .forEach { (kind, label) ->
                    val entries = state.data.filter { it.kind == kind }
                    if (entries.isNotEmpty()) {
                        item(key = kind.name) {
                            SectionTitle(stringResource(label)) {
                                if (kind == Kind.SUBJECT)
                                    TextButton(onClick = subjects) {
                                        Text(stringResource(R.string.view_all))
                                    }
                            }
                        }
                        items(entries, key = { it.id }) { entry ->
                            EntryRow(entry, { open(entry) }, { action(entry, it) })
                        }
                    }
                }
        }
        item {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(18.dp))
            Eyebrow(stringResource(R.string.design_signature))
        }
    }
}

@Composable
fun LibraryScreen(
    scope: Scope,
    state: LoadState<Library>,
    sorting: String,
    setSort: (String) -> Unit,
    add: () -> Unit,
    retry: () -> Unit,
    open: (Entry) -> Unit,
    action: (Entry, String) -> Unit,
    showTree: () -> Unit,
    root: () -> Unit,
    openFile: (Entry) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    val data = state.data
    val current = data.current
    val sorted =
        remember(data.rows, sorting) {
            when (sorting) {
                "alpha" -> data.rows.sortedBy { it.title.lowercase() }
                "created" -> data.rows.sortedByDescending { it.createdAt }
                "updated" -> data.rows.sortedByDescending { it.updatedAt }
                else -> data.rows
            }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            if (scope.kind != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = root, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.subjects))
                    }
                    Text(" / ", color = MaterialTheme.colorScheme.outline)
                    Text(
                        stringResource(scope.kind.label()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Eyebrow(stringResource(R.string.local_first))
                Spacer(Modifier.height(16.dp))
            }
            Text(
                current?.title ?: stringResource(R.string.library),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(8.dp))
            if (scope.kind == null)
                Text(
                    stringResource(R.string.library_caption),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else current?.let { MetaDate(it.updatedAt) }
            if (scope.kind in setOf(Kind.FOLDER, Kind.LESSON) && data.subjectTitle.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.subject) + ": " + data.subjectTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.parent) +
                        ": " +
                        data.parentTitle.ifBlank { stringResource(R.string.root) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (scope.kind in setOf(Kind.SUBJECT, Kind.FOLDER)) {
                TextButton(
                    onClick = showTree,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Outlined.AccountTree, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tree))
                }
                val ancestors = StudyRules.ancestors(current?.parentId, data.folders)
                if (ancestors.isNotEmpty())
                    Text(
                        ancestors.joinToString(" / ") { it.title },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
        if (state.loading) item { Loading() }
        else if (state.error != null) item { Failure(state.error, retry) }
        else {
            if (current?.kind == Kind.LESSON)
                item {
                    LessonOverview(
                        current,
                        { action(current, "edit") },
                        { action(current, "tags") },
                    )
                }
            if (current?.kind == Kind.FILE)
                item { FileOverview(current, { openFile(current) }, { action(current, "tags") }) }
            item {
                SectionTitle(stringResource(R.string.contents)) {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.sort))
                        }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            listOf(
                                    "manual" to R.string.manual,
                                    "alpha" to R.string.alphabetical,
                                    "created" to R.string.created_date,
                                    "updated" to R.string.updated_date,
                                )
                                .forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        onClick = {
                                            setSort(key)
                                            sortMenu = false
                                        },
                                        trailingIcon = {
                                            if (sorting == key) Icon(Icons.Outlined.Check, null)
                                        },
                                    )
                                }
                        }
                    }
                }
            }
            if (sorted.isEmpty())
                item {
                    EmptySpace(
                        if (scope.kind == null) R.string.empty_library
                        else if (scope.kind == Kind.LESSON) R.string.empty_lesson
                        else R.string.empty_folder,
                        if (scope.kind == null) R.string.empty_library_body
                        else if (scope.kind == Kind.LESSON) R.string.empty_lesson_body
                        else R.string.empty_folder_body,
                        add,
                    )
                }
            items(sorted, key = { it.id }) { entry ->
                EntryRow(entry, { open(entry) }, { action(entry, it) })
            }
        }
    }
}

@Composable
private fun LessonOverview(entry: Entry, edit: () -> Unit, tags: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.progress),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = edit) {
                    Icon(Icons.Outlined.Tune, stringResource(R.string.edit_progress))
                }
            }
            SuggestionChip(onClick = edit, label = { Text(stringResource(entry.status.label())) })
            listOf(
                    R.string.understanding to entry.understanding,
                    R.string.application to entry.application,
                    R.string.revision to entry.revision,
                )
                .forEach { (label, value) ->
                    Row {
                        Text(
                            stringResource(label),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.percent, value),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { value / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            Text(
                stringResource(R.string.manual_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = tags, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Label, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.attach_tags))
            }
        }
    }
}

@Composable
private fun FileOverview(entry: Entry, open: () -> Unit, tags: () -> Unit) {
    val context = LocalContext.current
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Eyebrow(stringResource(R.string.storage))
            Text(
                stringResource(
                    if (entry.storageType == StorageType.IMPORTED) R.string.imported
                    else R.string.linked
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(entry.mime, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(
                    R.string.file_size,
                    if (entry.size >= 0) Formatter.formatFileSize(context, entry.size)
                    else stringResource(R.string.unknown_size),
                )
            )
            Text(
                stringResource(R.string.file_safety),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.relink_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                Button(onClick = open) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.open))
                }
                TextButton(onClick = tags) { Text(stringResource(R.string.tags)) }
            }
        }
    }
}

@Composable
fun SearchScreen(
    query: String,
    state: LoadState<List<Entry>>,
    change: (String) -> Unit,
    open: (Entry) -> Unit,
    action: (Entry, String) -> Unit,
    retry: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(stringResource(R.string.search), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.search_caption),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                query,
                change,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
            )
        }
        if (state.loading) item { Loading() }
        else if (state.error != null) item { Failure(state.error, retry) }
        else if (query.isNotBlank() && state.data.isEmpty())
            item { EmptySpace(R.string.no_results, R.string.no_results_body) }
        items(state.data, key = { it.id }) { entry ->
            EntryRow(entry, { open(entry) }, { action(entry, it) })
        }
        if (state.data.size >= 200)
            item {
                Text(
                    stringResource(R.string.search_limit),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
    }
}

@Composable
fun SettingsScreen(
    settings: Map<String, String>,
    busy: Boolean,
    set: (String, String) -> Unit,
    tags: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Eyebrow(stringResource(R.string.design_signature))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge)
        }
        item {
            SectionTitle(stringResource(R.string.appearance))
            SettingChoices(
                listOf(
                    "system" to R.string.system_theme,
                    "light" to R.string.light,
                    "dark" to R.string.dark,
                ),
                settings["theme"] ?: "system",
                busy,
            ) {
                set("theme", it)
            }
        }
        item {
            SectionTitle(stringResource(R.string.language))
            SettingChoices(
                listOf("en" to R.string.english, "ar" to R.string.arabic),
                settings["language"] ?: "en",
                busy,
            ) {
                set("language", it)
            }
        }
        item {
            OutlinedButton(
                onClick = tags,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Label, null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.manage_tags))
            }
        }
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.privacy),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.privacy_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.backup_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.version_label), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingChoices(
    choices: List<Pair<String, Int>>,
    selected: String,
    busy: Boolean,
    select: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (key, label) ->
            Surface(
                onClick = { select(key) },
                enabled = !busy,
                color =
                    if (selected == key) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(label), Modifier.weight(1f))
                    if (selected == key) Icon(Icons.Outlined.Check, null)
                }
            }
        }
    }
}

@Composable
fun TagsScreen(
    state: LoadState<List<TagItem>>,
    create: () -> Unit,
    action: (Entry, String) -> Unit,
    retry: () -> Unit,
) {
    val tags = state.data
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.manage_tags),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = create) {
                Icon(Icons.Outlined.Add, null)
                Text(stringResource(R.string.tag))
            }
        }
        if (state.loading) item { Loading() }
        else if (state.error != null) item { Failure(state.error, retry) }
        else if (tags.isEmpty()) item { Text(stringResource(R.string.no_tags)) }
        items(tags, key = { it.id }) { tag ->
            val entry = Entry(tag.id, tag.title, Kind.TAG)
            EntryRow(entry, { action(entry, "edit") }, { action(entry, it) })
        }
    }
}
