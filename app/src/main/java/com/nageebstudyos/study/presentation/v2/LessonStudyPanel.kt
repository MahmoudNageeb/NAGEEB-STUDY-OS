package com.nageebstudyos.study.presentation.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nageebstudyos.study.R
import com.nageebstudyos.study.domain.*
import com.nageebstudyos.study.presentation.V2ViewModel
import com.nageebstudyos.study.ui.Lux

@Composable
fun LessonStudyPanel(
    lesson: Entry,
    data: LessonStudyData?,
    vm: V2ViewModel,
    onStartFocus: () -> Unit,
) {
    var addReview by remember { mutableStateOf(false) }
    var reschedule by remember { mutableStateOf<Review?>(null) }

    val reviews = data?.reviews.orEmpty()
    val sessions = data?.sessions.orEmpty()
    val pending = reviews.filter { it.status == ReviewStatus.PENDING }
    val handled = reviews.filter { it.status != ReviewStatus.PENDING }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onStartFocus,
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Outlined.CenterFocusStrong, null, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.start_focus_lesson), style = MaterialTheme.typography.titleMedium)
        }

        SectionHeader(stringResource(R.string.lesson_reviews)) {
            TextButton(onClick = { addReview = true }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.add_review))
            }
        }
        if (pending.isEmpty() && handled.isEmpty())
            LuxCard {
                Text(
                    stringResource(R.string.review_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        pending.forEach { review ->
            ReviewRow(review, vm, onReschedule = { reschedule = review })
        }
        if (handled.isNotEmpty()) {
            Text(
                stringResource(R.string.reviews_completed) + ": " + handled.count { it.status == ReviewStatus.DONE },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader(stringResource(R.string.lesson_sessions))
        if (sessions.isEmpty())
            Text(
                stringResource(R.string.no_lesson_sessions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        sessions.take(8).forEach { session -> SessionRow(session) }
    }

    if (addReview)
        ReviewDialog(
            initialDay = StudyTime.today() + 2,
            titleRes = R.string.add_review,
            onDismiss = { addReview = false },
            onSave = { day, note ->
                vm.addReview(
                    lesson.id,
                    lesson.subjectId.orEmpty(),
                    StudyTime.at(day, 9, 0),
                    note,
                )
                addReview = false
            },
        )
    reschedule?.let { review ->
        ReviewDialog(
            initialDay = review.scheduledDay,
            initialNote = review.note,
            titleRes = R.string.reschedule_review,
            onDismiss = { reschedule = null },
            onSave = { day, _ ->
                vm.rescheduleReview(review, StudyTime.at(day, 9, 0))
                reschedule = null
            },
        )
    }
}

@Composable
private fun ReviewRow(review: Review, vm: V2ViewModel, onReschedule: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val overdue = review.scheduledDay < StudyTime.today()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                    .background(Lux.Gold.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Replay, null, tint = Lux.Gold, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    mediumDate(review.scheduledDay) + if (overdue) " · ${stringResource(R.string.overdue)}" else "",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (review.note.isNotBlank())
                    Text(
                        review.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
            }
            IconButton(onClick = { vm.completeReview(review) }) {
                Icon(Icons.Outlined.TaskAlt, stringResource(R.string.complete_review), tint = Lux.Emerald)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null, Modifier.size(18.dp)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.complete_review)) },
                        onClick = { menu = false; vm.completeReview(review) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.skip_review)) },
                        onClick = { menu = false; vm.skipReview(review) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reschedule_review)) },
                        onClick = { menu = false; onReschedule() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menu = false; vm.deleteReview(review) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: StudySession) {
    val color = resultColor(session.result)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                    .background(Lux.Sky.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Timer, null, tint = Lux.Sky, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(shortDate(session.dayIndex), style = MaterialTheme.typography.titleSmall)
                if (session.goal.isNotBlank())
                    Text(
                        session.goal,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
            }
            Text(durationText(session.durationSeconds), style = MaterialTheme.typography.titleSmall, color = Lux.EmeraldBright)
            Spacer(Modifier.width(8.dp))
            val label =
                when (session.result) {
                    SessionResult.YES -> stringResource(R.string.result_yes)
                    SessionResult.PARTIALLY -> stringResource(R.string.result_partial)
                    SessionResult.NO -> stringResource(R.string.result_no)
                    null -> "—"
                }
            StatusPill(label, color)
        }
    }
}
