package com.nageebstudyos.study.presentation.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nageebstudyos.study.R
import com.nageebstudyos.study.domain.*
import com.nageebstudyos.study.ui.Lux
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    data: DashboardData,
    onContinue: (ContinueState) -> Unit,
    onStartFocus: () -> Unit,
    onNewTask: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenSubjects: () -> Unit,
    onOpenActivity: (ActivityItem) -> Unit,
    onOpenAnalytics: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { DashboardHeader(data) }

        item {
            ContinueCard(data.continueState, onContinue, onStartFocus)
        }

        item {
            GoalRingCard(data, onOpenAnalytics)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    Icons.Outlined.LocalFireDepartment,
                    data.currentStreak.toString(),
                    stringResource(R.string.current_streak),
                    Lux.Gold,
                    Modifier.weight(1f),
                )
                StatTile(
                    Icons.Outlined.TaskAlt,
                    data.todaySessions.toString(),
                    stringResource(R.string.today_sessions),
                    Lux.Emerald,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    Icons.Outlined.Replay,
                    data.reviewsDue.toString(),
                    stringResource(R.string.reviews_due),
                    Lux.GoldBright,
                    Modifier.weight(1f),
                    onOpenReviews,
                )
                StatTile(
                    Icons.Outlined.Checklist,
                    data.tasksDue.toString(),
                    stringResource(R.string.tasks_due),
                    Lux.Sky,
                    Modifier.weight(1f),
                    onOpenTasks,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartFocus,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Outlined.CenterFocusStrong, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.start_focus))
                }
                FilledTonalButton(
                    onClick = onNewTask,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Outlined.AddTask, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_task))
                }
            }
        }

        item {
            WeekCard(data.week, onOpenCalendar)
        }

        item {
            SectionHeader(stringResource(R.string.recent_activity)) {
                TextButton(onClick = onOpenAnalytics) {
                    Text(stringResource(R.string.analytics))
                }
            }
            if (data.activity.isEmpty()) {
                LuxCard {
                    Icon(Icons.Outlined.Bolt, null, tint = Lux.Emerald)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.recent_activity_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onOpenSubjects, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.subjects))
                    }
                }
            }
        }
        if (data.activity.isNotEmpty())
            items(data.activity, key = { it.id }) { activity ->
                ActivityRow(activity) { onOpenActivity(activity) }
            }
    }
}

@Composable
private fun DashboardHeader(data: DashboardData) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting =
        when (hour) {
            in 5..11 -> stringResource(R.string.greeting_morning)
            in 12..16 -> stringResource(R.string.greeting_afternoon)
            else -> stringResource(R.string.greeting_evening)
        }
    val today =
        DateFormat.getDateInstance(DateFormat.FULL, LocalConfiguration.current.locales[0])
            .format(Date())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.displaySmall, maxLines = 1)
            Text(
                today,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            color = Lux.Gold.copy(alpha = 0.13f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocalFireDepartment, null, tint = Lux.Gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.streak_days, data.currentStreak),
                    style = MaterialTheme.typography.labelLarge,
                    color = Lux.GoldBright,
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(state: ContinueState?, onContinue: (ContinueState) -> Unit, onStart: () -> Unit) {
    LuxCard(
        onClick = if (state != null) ({ onContinue(state) }) else onStart,
        brush = Lux.heroGradient,
        contentPadding = PaddingValues(22.dp),
    ) {
        if (state == null) {
            Icon(Icons.Outlined.PlayCircle, null, tint = Lux.Emerald, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.continue_studying), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.continue_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MenuBook, null, tint = Lux.Emerald, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.continue_studying),
                    style = MaterialTheme.typography.labelMedium,
                    color = Lux.Emerald,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(16.dp))
            val parts =
                listOfNotNull(
                    state.subjectTitle.ifBlank { null },
                    state.folderTitle.ifBlank { null },
                    state.lessonTitle.ifBlank { null },
                    state.fileTitle.ifBlank { null },
                )
            Text(
                state.lessonTitle.ifBlank { state.subjectTitle.ifBlank { "—" } },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                parts.joinToString(" → "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.page != null && state.page > 0) {
                Spacer(Modifier.height(10.dp))
                StatusPill(stringResource(R.string.page_value, state.page), Lux.Gold)
            }
        }
    }
}

@Composable
private fun GoalRingCard(data: DashboardData, onOpenAnalytics: () -> Unit) {
    LuxCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.study_time), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    durationText(data.todayMinutes * 60L),
                    style = MaterialTheme.typography.displaySmall,
                    color = Lux.EmeraldBright,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.goal_percent, goalPercent(data)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ProgressRing(percent = goalPercent(data), size = 132, ringWidth = 11) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${goalPercent(data)}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = Lux.EmeraldBright,
                    )
                    Text(
                        stringResource(R.string.today_goal),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun goalPercent(data: DashboardData): Int {
    if (data.todayGoalMinutes <= 0) return 0
    return ((data.todayMinutes * 100) / data.todayGoalMinutes).coerceIn(0, 100)
}

@Composable
private fun WeekCard(week: List<DayMinutes>, onOpenCalendar: () -> Unit) {
    LuxCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.week_chart),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onOpenCalendar) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.calendar))
            }
        }
        Spacer(Modifier.height(12.dp))
        val labels =
            week.map { day ->
                val dow = Calendar.getInstance().apply {
                    timeInMillis = StudyTime.at(day.day, 12, 0)
                }.get(Calendar.DAY_OF_WEEK)
                weekdayInitial(dow)
            }
        StudyBars(week.map { it.minutes.toFloat() }, labels = labels, height = 96)
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    val icon: ImageVector =
        when (item.kind) {
            Kind.NOTE -> Icons.Outlined.Replay
            Kind.TAG -> Icons.Outlined.CheckCircleOutline
            else -> Icons.Outlined.Timer
        }
    val tint =
        when (item.kind) {
            Kind.NOTE -> Lux.Gold
            Kind.TAG -> Lux.Emerald
            else -> Lux.Sky
        }
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp)) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                timeAgo(item.time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
