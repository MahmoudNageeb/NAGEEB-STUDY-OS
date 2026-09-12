package com.nageebstudyos.study.presentation.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nageebstudyos.study.R
import com.nageebstudyos.study.domain.CalendarEvent
import com.nageebstudyos.study.domain.CalendarEventKind
import com.nageebstudyos.study.domain.StudyTime
import com.nageebstudyos.study.domain.TaskStatus
import com.nageebstudyos.study.presentation.CalendarState
import com.nageebstudyos.study.presentation.V2ViewModel
import com.nageebstudyos.study.ui.Lux
import java.time.LocalDate

@Composable
fun CalendarScreen(vm: V2ViewModel, onOpenLesson: (String) -> Unit) {
    val state by vm.calendar.collectAsStateWithLifecycle()
    val today = StudyTime.today()
    val anchorMonth = StudyTime.localDate(state.anchorDay).monthValue
    val eventsByDay = state.events.groupBy { it.day }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(stringResource(R.string.calendar_title), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.calendar_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftMonth(-1) }) {
                    Icon(Icons.Outlined.ChevronRight, stringResource(R.string.previous_month))
                }
                Text(
                    state.monthLabel,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { vm.shiftMonth(1) }) {
                    Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.next_month))
                }
            }
        }
        item {
            Row {
                listOf(
                    R.string.weekday_saturday,
                    R.string.weekday_sunday,
                    R.string.weekday_monday,
                    R.string.weekday_tuesday,
                    R.string.weekday_wednesday,
                    R.string.weekday_thursday,
                    R.string.weekday_friday,
                ).forEach { res ->
                    Text(
                        stringResource(res),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        items(count = 6, key = { "grid-row-$it" }) { rowIndex ->
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val day = state.gridStart + rowIndex * 7 + col
                    if (day > state.gridEnd) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    DayCell(
                        day = day,
                        inMonth = StudyTime.localDate(day).monthValue == anchorMonth,
                        isToday = day == today,
                        selected = day == state.selectedDay,
                        events = eventsByDay[day].orEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = { vm.selectDay(day) },
                    )
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        item {
            val selected = state.selectedDay
            Text(
                if (selected == today) stringResource(R.string.today) else mediumDate(selected),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            val events = state.selectedEvents
            if (events.isEmpty())
                Text(
                    stringResource(R.string.no_events),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        items(state.selectedEvents, key = { it.id }) { event ->
            EventRow(event, vm, onOpenLesson)
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    inMonth: Boolean,
    isToday: Boolean,
    selected: Boolean,
    events: List<CalendarEvent>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dayNumber = StudyTime.localDate(day).dayOfMonth
    val background =
        when {
            selected -> Lux.Emerald.copy(alpha = 0.2f)
            isToday -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> androidx.compose.ui.graphics.Color.Transparent
        }
    Column(
        modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .then(
                if (isToday)
                    Modifier.border(1.dp, Lux.Emerald.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            dayNumber.toString(),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            ),
            color = when {
                !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                selected -> Lux.EmeraldBright
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            events.distinctBy { it.kind }.take(4).forEach { event ->
                Box(
                    Modifier.size(6.dp)
                        .clip(CircleShape)
                        .background(eventColor(event.kind))
                )
            }
        }
    }
}

@Composable
private fun EventRow(
    event: CalendarEvent,
    vm: V2ViewModel,
    onOpenLesson: (String) -> Unit,
) {
    val color = eventColor(event.kind)
    val icon: ImageVector =
        when (event.kind) {
            CalendarEventKind.REVIEW -> Icons.Outlined.Replay
            CalendarEventKind.TASK -> Icons.Outlined.Checklist
            CalendarEventKind.SESSION -> Icons.Outlined.Timer
            CalendarEventKind.PLAN -> Icons.Outlined.Flag
        }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(19.dp)) }
            Column(
                Modifier.weight(1f).padding(horizontal = 12.dp)
            ) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    event.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (event.kind) {
                CalendarEventKind.TASK -> {
                    if (!event.done)
                        IconButton(onClick = {
                            vm.setTaskStatusById(event.id, TaskStatus.DONE)
                        }) {
                            Icon(Icons.Outlined.CheckCircleOutline, null, tint = Lux.Emerald)
                        }
                }
                CalendarEventKind.REVIEW -> {
                    if (!event.done)
                        IconButton(onClick = { vm.completeReviewById(event.id) }) {
                            Icon(Icons.Outlined.TaskAlt, null, tint = Lux.Gold)
                        }
                }
                else -> Unit
            }
            val lesson = event.targetLessonId
            if (lesson != null)
                IconButton(onClick = { onOpenLesson(lesson) }) {
                    Icon(Icons.Outlined.MenuBook, null, Modifier.size(18.dp))
                }
        }
    }
}


