package com.nageebstudyos.study.presentation.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nageebstudyos.study.R
import com.nageebstudyos.study.domain.SubjectProgressInfo
import com.nageebstudyos.study.domain.SubjectSeconds
import com.nageebstudyos.study.domain.TimeRange
import com.nageebstudyos.study.presentation.V2ViewModel
import com.nageebstudyos.study.ui.Lux
import java.util.Calendar

@Composable
fun AnalyticsScreen(vm: V2ViewModel, onOpenSubject: (String) -> Unit) {
    val data by vm.analytics.collectAsStateWithLifecycle()
    val range by vm.selectedRange.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(stringResource(R.string.analytics_title), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.analytics_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SegmentedControl(
                listOf(
                    stringResource(R.string.range_today),
                    stringResource(R.string.range_week),
                    stringResource(R.string.range_month),
                ),
                selectedIndex = range.ordinal,
                onSelect = {
                    vm.selectRange(
                        when (it) {
                            0 -> TimeRange.TODAY
                            1 -> TimeRange.WEEK
                            else -> TimeRange.MONTH
                        }
                    )
                },
            )
        }
        item {
            LuxCard(brush = Lux.heroGradient, contentPadding = PaddingValues(24.dp)) {
                Text(stringResource(R.string.total_time), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    durationText(data.totalMinutes * 60L),
                    style = MaterialTheme.typography.displaySmall,
                    color = Lux.EmeraldBright,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.count_sessions, data.sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (data.totalMinutes == 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.no_time_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    Icons.Outlined.MenuBook,
                    data.completedLessons.toString(),
                    stringResource(R.string.completed_lessons),
                    Lux.Emerald,
                    Modifier.weight(1f),
                )
                StatTile(
                    Icons.Outlined.HourglassBottom,
                    data.pendingLessons.toString(),
                    stringResource(R.string.pending_lessons),
                    Lux.Gold,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    Icons.Outlined.Replay,
                    data.reviewsCompleted.toString(),
                    stringResource(R.string.reviews_completed),
                    Lux.Sky,
                    Modifier.weight(1f),
                )
                StatTile(
                    Icons.Outlined.LocalFireDepartment,
                    data.currentStreak.toString(),
                    stringResource(R.string.current_streak),
                    Lux.Gold,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            LuxCard {
                Text(
                    if (range == TimeRange.MONTH) stringResource(R.string.monthly_time)
                    else stringResource(R.string.weekly_time),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(16.dp))
                if (data.series.size > 1) {
                    val labels =
                        data.series.map { day ->
                            val dow =
                                Calendar.getInstance().apply {
                                    timeInMillis =
                                        com.nageebstudyos.study.domain.StudyTime.at(day.day, 12, 0)
                                }.get(Calendar.DAY_OF_WEEK)
                            if (range == TimeRange.MONTH) "" else weekdayInitial(dow)
                        }
                    StudyBars(
                        data.series.map { it.minutes.toFloat() },
                        labels = labels,
                        height = if (range == TimeRange.MONTH) 90 else 120,
                    )
                } else
                    Text(
                        stringResource(R.string.no_time_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
        item { DistributionCard(data.distribution) }
        item {
            SectionHeader(stringResource(R.string.streak_explained))
        }
        item {
            Text(
                stringResource(R.string.streak_explained),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { SectionHeader(stringResource(R.string.subject_progress_title)) }
        if (data.subjectProgress.isEmpty())
            item {
                Text(
                    stringResource(R.string.analytics_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        items(data.subjectProgress, key = { it.subjectId }) { subject ->
            SubjectProgressCard(subject, onOpenSubject)
        }
        item {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.progress_explained),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DistributionCard(distribution: List<SubjectSeconds>) {
    LuxCard {
        Text(stringResource(R.string.subject_distribution), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(14.dp))
        if (distribution.isEmpty() || distribution.sumOf { it.seconds } == 0L) {
            Text(
                stringResource(R.string.no_time_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@LuxCard
        }
        DistributionBar(
            distribution.map {
                it.seconds.toFloat() to accentAt(distribution.indexOf(it).coerceAtMost(4))
            }
        )
        Spacer(Modifier.height(16.dp))
        distribution.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(11.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accentAt(index))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    durationText(item.seconds),
                    style = MaterialTheme.typography.titleSmall,
                    color = Lux.EmeraldBright,
                )
            }
        }
    }
}

@Composable
private fun SubjectProgressCard(subject: SubjectProgressInfo, onOpenSubject: (String) -> Unit) {
    LuxCard(onClick = { onOpenSubject(subject.subjectId) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(subject.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(
                "${subject.percent}%",
                style = MaterialTheme.typography.titleMedium,
                color = Lux.EmeraldBright,
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { subject.percent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Lux.Emerald,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            buildString {
                if (subject.mastered > 0) append("${stringResource(R.string.mastered)} ${subject.mastered}  ·  ")
                if (subject.inProgress > 0) append("${stringResource(R.string.in_progress)} ${subject.inProgress}  ·  ")
                if (subject.needsReview > 0) append("${stringResource(R.string.needs_review)} ${subject.needsReview}  ·  ")
                if (subject.weak > 0) append("${stringResource(R.string.weak)} ${subject.weak}  ·  ")
                append("${stringResource(R.string.lesson_count, subject.lessonCount)}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
