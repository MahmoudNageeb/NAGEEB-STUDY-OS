package com.nageebstudyos.study.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nageebstudyos.study.R
import com.nageebstudyos.study.domain.*
import java.text.DateFormat
import java.util.Date

fun Kind.label() =
    when (this) {
        Kind.SUBJECT -> R.string.subject
        Kind.FOLDER -> R.string.folder
        Kind.LESSON -> R.string.lesson
        Kind.FILE -> R.string.file
        Kind.LINK -> R.string.link
        Kind.NOTE -> R.string.note
        Kind.TAG -> R.string.tag
    }

fun Kind.icon(): ImageVector =
    when (this) {
        Kind.SUBJECT -> Icons.Outlined.AutoStories
        Kind.FOLDER -> Icons.Outlined.FolderOpen
        Kind.LESSON -> Icons.AutoMirrored.Outlined.MenuBook
        Kind.FILE -> Icons.AutoMirrored.Outlined.InsertDriveFile
        Kind.LINK -> Icons.Outlined.Link
        Kind.NOTE -> Icons.AutoMirrored.Outlined.Notes
        Kind.TAG -> Icons.AutoMirrored.Outlined.Label
    }

fun LessonStatus.label() =
    when (this) {
        LessonStatus.NOT_STARTED -> R.string.not_started
        LessonStatus.IN_PROGRESS -> R.string.in_progress
        LessonStatus.NEEDS_REVIEW -> R.string.needs_review
        LessonStatus.MASTERED -> R.string.mastered
        LessonStatus.WEAK -> R.string.weak
    }

fun Problem.message() =
    when (this) {
        Problem.TITLE_REQUIRED -> R.string.error_title_required
        Problem.INVALID_URL -> R.string.error_invalid_url
        Problem.INVALID_MOVE -> R.string.error_invalid_move
        Problem.INVALID_PROGRESS -> R.string.error_progress
        Problem.INVALID_OWNER -> R.string.error_owner
        Problem.MISSING_ITEM -> R.string.error_missing
        Problem.FILE_ACCESS -> R.string.error_file
        Problem.IMPORT_FAILED -> R.string.error_import
        Problem.DUPLICATE_TAG -> R.string.error_duplicate_tag
        else -> R.string.error_database
    }

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(text, Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EntryRow(
    entry: Entry,
    onOpen: () -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 15.dp, bottom = 15.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val accent =
                if (entry.kind == Kind.SUBJECT) SubjectAccents[entry.accent.coerceIn(0, 4)]
                else MaterialTheme.colorScheme.primary
            Box(
                Modifier.size(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(accent.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(entry.kind.icon(), null, tint = accent, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail =
                    when (entry.kind) {
                        Kind.SUBJECT -> stringResource(R.string.lesson_count, entry.lessonCount)
                        Kind.LESSON -> stringResource(entry.status.label())
                        Kind.FILE ->
                            stringResource(
                                if (entry.storageType == StorageType.IMPORTED) R.string.imported
                                else R.string.linked
                            )
                        Kind.LINK -> entry.url
                        else -> stringResource(entry.kind.label())
                    }
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    fun action(key: String) {
                        menu = false
                        onAction(key)
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = { action("edit") },
                    )
                    if (entry.kind in setOf(Kind.LESSON, Kind.FILE, Kind.NOTE))
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_tags)) },
                            onClick = { action("tags") },
                        )
                    if (entry.kind == Kind.FOLDER)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.create_child)) },
                            onClick = { action("child") },
                        )
                    if (entry.kind in setOf(Kind.FOLDER, Kind.LESSON))
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move)) },
                            onClick = { action("move") },
                        )
                    if (entry.kind in setOf(Kind.SUBJECT, Kind.FOLDER, Kind.LESSON)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_up)) },
                            onClick = { action("up") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_down)) },
                            onClick = { action("down") },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = { action("delete") },
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySpace(title: Int, body: Int, onAdd: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                null,
                Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (onAdd != null) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.create))
            }
        }
    }
}

@Composable
fun Failure(problem: Problem, retry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
        Text(stringResource(problem.message()))
        TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun MetaDate(time: Long) {
    if (time > 0)
        Text(
            stringResource(
                R.string.updated,
                DateFormat.getDateInstance(
                        DateFormat.MEDIUM,
                        androidx.compose.ui.platform.LocalConfiguration.current.locales[0],
                    )
                    .format(Date(time)),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
}
