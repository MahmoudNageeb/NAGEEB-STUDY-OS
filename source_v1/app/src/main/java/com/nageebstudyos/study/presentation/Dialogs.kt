package com.nageebstudyos.study.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nageebstudyos.study.R
import com.nageebstudyos.study.domain.*
import com.nageebstudyos.study.ui.*

@Composable
fun EditorDialog(
    entry: Entry,
    busy: Boolean,
    error: Problem?,
    onSave: (Entry) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable(entry.id) { mutableStateOf(entry.title) }
    var body by rememberSaveable(entry.id) { mutableStateOf(entry.body) }
    var url by rememberSaveable(entry.id) { mutableStateOf(entry.url) }
    var linkType by rememberSaveable(entry.id) { mutableStateOf(entry.linkType) }
    var accent by rememberSaveable(entry.id) { mutableIntStateOf(entry.accent) }
    var status by rememberSaveable(entry.id) { mutableStateOf(entry.status.name) }
    var understanding by rememberSaveable(entry.id) { mutableIntStateOf(entry.understanding) }
    var application by rememberSaveable(entry.id) { mutableIntStateOf(entry.application) }
    var revision by rememberSaveable(entry.id) { mutableIntStateOf(entry.revision) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val edited =
        entry.copy(
            title = title,
            body = body,
            url = url,
            linkType = linkType,
            accent = accent,
            status = LessonStatus.valueOf(status),
            understanding = understanding,
            application = application,
            revision = revision,
        )
    fun dismiss() {
        if (!busy) {
            if (edited != entry) confirmDiscard = true else onDismiss()
        }
    }
    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false),
    ) {
        BackHandler { dismiss() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { dismiss() }, enabled = !busy) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.close))
                    }
                    Text(
                        stringResource(entry.kind.label()),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Button(
                        onClick = { onSave(edited) },
                        enabled = !busy,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(stringResource(if (busy) R.string.working else R.string.save))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Eyebrow(
                        stringResource(if (entry.createdAt > 0) R.string.edit else R.string.create)
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.title)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        enabled = !busy,
                    )
                    if (entry.kind == Kind.SUBJECT) {
                        Text(
                            stringResource(R.string.accent),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubjectAccents.forEachIndexed { index, color ->
                                val description = stringResource(R.string.accent_number, index + 1)
                                Surface(
                                    onClick = { accent = index },
                                    color = color.copy(alpha = .3f),
                                    shape = CircleShape,
                                    border =
                                        if (accent == index) BorderStroke(2.dp, color) else null,
                                    modifier =
                                        Modifier.size(48.dp).semantics {
                                            contentDescription = description
                                        },
                                ) {
                                    if (accent == index)
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Outlined.Check,
                                                null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                }
                            }
                        }
                    }
                    if (entry.kind == Kind.NOTE)
                        OutlinedTextField(
                            value = body,
                            onValueChange = { body = it },
                            label = { Text(stringResource(R.string.body)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 12,
                            enabled = !busy,
                        )
                    if (entry.kind == Kind.LINK) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(stringResource(R.string.url)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            keyboardOptions =
                                androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
                                ),
                        )
                        OutlinedTextField(
                            value = linkType,
                            onValueChange = { linkType = it },
                            label = { Text(stringResource(R.string.link_type)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                        )
                    }
                    if (entry.kind == Kind.LESSON) {
                        Text(
                            stringResource(R.string.status),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        LessonStatus.entries.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                    status = item.name
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = item.name == status,
                                    onClick = { status = item.name },
                                    enabled = !busy,
                                )
                                Text(stringResource(item.label()))
                            }
                        }
                        Text(
                            stringResource(R.string.manual_progress),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ProgressSlider(R.string.understanding, understanding, !busy) {
                            understanding = it
                        }
                        ProgressSlider(R.string.application, application, !busy) {
                            application = it
                        }
                        ProgressSlider(R.string.revision, revision, !busy) { revision = it }
                    }
                    if (error != null)
                        Text(
                            stringResource(error.message()),
                            color = MaterialTheme.colorScheme.error,
                        )
                    if (entry.createdAt > 0) MetaDate(entry.updatedAt)
                }
            }
        }
    }
    if (confirmDiscard)
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_body)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
        )
}

@Composable
private fun ProgressSlider(label: Int, value: Int, enabled: Boolean, change: (Int) -> Unit) {
    val description = stringResource(label)
    Column {
        Row {
            Text(description, Modifier.weight(1f))
            Text(stringResource(R.string.percent, value))
        }
        Slider(
            value.toFloat(),
            { change(it.toInt()) },
            valueRange = 0f..100f,
            steps = 99,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
fun DeleteDialog(entry: Entry, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.DeleteOutline, null) },
        title = { Text(stringResource(R.string.delete_title, entry.title)) },
        text = {
            Text(
                stringResource(
                    if (entry.kind == Kind.TAG) R.string.delete_tag_message
                    else R.string.delete_message
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun QuickAddDialog(scope: Scope, onChoose: (Kind) -> Unit, onDismiss: () -> Unit) {
    val kinds =
        when (scope.kind) {
            Kind.SUBJECT,
            Kind.FOLDER -> listOf(Kind.FOLDER, Kind.LESSON, Kind.NOTE)
            Kind.LESSON -> listOf(Kind.FILE, Kind.LINK, Kind.NOTE)
            Kind.FILE -> listOf(Kind.NOTE)
            else -> listOf(Kind.SUBJECT)
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_add)) },
        text = {
            Column {
                kinds.forEach { kind ->
                    TextButton(onClick = { onChoose(kind) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(kind.icon(), null)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(kind.label()), Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun FileChoiceDialog(onChoose: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attach_file)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = { onChoose(false) }) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            stringResource(R.string.link_existing),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.link_existing_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                HorizontalDivider()
                TextButton(onClick = { onChoose(true) }) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            stringResource(R.string.import_file),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.import_file_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun TagsDialog(
    entry: Entry,
    tags: List<TagItem>,
    attached: Set<String>,
    busy: Boolean,
    toggle: (String, Boolean) -> Unit,
    create: () -> Unit,
    dismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.attach_tags)) },
        text = {
            Column {
                Text(entry.title, style = MaterialTheme.typography.bodyMedium)
                if (tags.isEmpty()) Text(stringResource(R.string.no_tags))
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(tags, key = { it.id }) { tag ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                toggle(tag.id, tag.id !in attached)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                tag.id in attached,
                                onCheckedChange = { toggle(tag.id, it) },
                                enabled = !busy,
                            )
                            Text(tag.title)
                        }
                    }
                }
                TextButton(onClick = create, enabled = !busy) {
                    Icon(Icons.Outlined.Add, null)
                    Text(stringResource(R.string.tag))
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.done)) } },
    )
}

data class TreeLine(val node: FolderNode, val depth: Int)

fun visibleTree(folders: List<FolderNode>, expanded: Set<String>): List<TreeLine> {
    val grouped = folders.groupBy { it.parentId }
    val stack = java.util.ArrayDeque<TreeLine>()
    val result = mutableListOf<TreeLine>()
    val seen = hashSetOf<String>()
    grouped[null].orEmpty().asReversed().forEach { stack.push(TreeLine(it, 0)) }
    while (stack.isNotEmpty()) {
        val line = stack.pop()
        if (!seen.add(line.node.id)) continue
        result.add(line)
        if (line.node.id in expanded)
            grouped[line.node.id].orEmpty().asReversed().forEach {
                stack.push(TreeLine(it, line.depth + 1))
            }
    }
    return result
}

@Composable
fun TreeDialog(
    folders: List<FolderNode>,
    moving: Entry?,
    busy: Boolean,
    select: (String?) -> Unit,
    dismiss: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    val rows = remember(folders, expanded) { visibleTree(folders, expanded.toSet()) }
    val parents = remember(folders) { folders.mapNotNull { it.parentId }.toSet() }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(stringResource(if (moving != null) R.string.move_destination else R.string.tree))
        },
        text = {
            Column {
                if (moving != null)
                    Text(
                        stringResource(R.string.move_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                TextButton(onClick = { select(null) }, enabled = !busy) {
                    Text(stringResource(R.string.root))
                }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(rows, key = { it.node.id }) { line ->
                        val allowed =
                            moving == null ||
                                StudyRules.canMove(
                                    moving.id,
                                    moving.subjectId.orEmpty(),
                                    line.node.id,
                                    folders,
                                )
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(start = (line.depth.coerceAtMost(5) * 12).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (line.node.id in parents)
                                IconButton(
                                    onClick = {
                                        expanded =
                                            if (line.node.id in expanded) expanded - line.node.id
                                            else expanded + line.node.id
                                    }
                                ) {
                                    Icon(
                                        if (line.node.id in expanded)
                                            Icons.Outlined.KeyboardArrowDown
                                        else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                        stringResource(
                                            if (line.node.id in expanded) R.string.collapse
                                            else R.string.expand
                                        ),
                                    )
                                }
                            else Spacer(Modifier.width(48.dp))
                            TextButton(
                                onClick = { select(line.node.id) },
                                enabled = allowed && !busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(line.node.title, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) } },
    )
}
