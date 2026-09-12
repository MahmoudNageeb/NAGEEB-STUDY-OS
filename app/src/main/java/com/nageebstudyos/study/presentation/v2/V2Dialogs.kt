package com.nageebstudyos.study.presentation.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nageebstudyos.study.R
import com.nageebstudyos.study.domain.*
import com.nageebstudyos.study.ui.Lux
import java.time.Instant
import java.time.ZoneOffset

// ---- Date picker field -------------------------------------------------------------------

private fun dayToUtcMillis(day: Int): Long =
    StudyTime.localDate(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcMillisToDay(millis: Long): Int =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay().toInt()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    day: Int?,
    onPick: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val today = StudyTime.today()
    Surface(
        onClick = { open = true },
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = Lux.Emerald, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    day?.let { mediumDate(it) } ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (day != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (clearable && day != null) {
                IconButton(onClick = { onPick(null) }) { Icon(Icons.Outlined.Close, null, Modifier.size(18.dp)) }
            }
        }
    }
    if (open) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = dayToUtcMillis(day ?: today),
            )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(utcMillisToDay(it)) }
                    open = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = state, showModeToggle = false, title = null)
        }
    }
}

// ---- Full-screen dialog scaffold ----------------------------------------------------------

@Composable
fun FullScreenDialog(
    title: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                    Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge)
                    Button(onClick = onSave, enabled = saveEnabled) { Text(stringResource(R.string.save)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}

// ---- Preset editor ------------------------------------------------------------------------

@Composable
fun PresetDialog(
    preset: FocusPreset?,
    onDismiss: () -> Unit,
    onSave: (FocusPreset) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by rememberSaveable(preset?.id) { mutableStateOf(preset?.title ?: "") }
    var focus by rememberSaveable(preset?.id) { mutableIntStateOf(preset?.focusMinutes ?: 50) }
    var short by rememberSaveable(preset?.id) { mutableIntStateOf(preset?.shortBreakMinutes ?: 10) }
    var long by rememberSaveable(preset?.id) { mutableIntStateOf(preset?.longBreakMinutes ?: 25) }
    var rounds by rememberSaveable(preset?.id) { mutableIntStateOf(preset?.sessionsCount ?: 4) }
    var interval by rememberSaveable(preset?.id) {
        mutableIntStateOf(preset?.longBreakInterval ?: 2)
    }
    var auto by rememberSaveable(preset?.id) { mutableStateOf(preset?.autoStart ?: false) }
    var sound by rememberSaveable(preset?.id) { mutableStateOf(preset?.sound ?: true) }
    var vibration by rememberSaveable(preset?.id) { mutableStateOf(preset?.vibration ?: true) }

    FullScreenDialog(
        title = stringResource(if (preset == null) R.string.new_custom_preset else R.string.edit_preset),
        onClose = onDismiss,
        onSave = {
            onSave(
                FocusPreset(
                    id = preset?.id ?: com.nageebstudyos.study.domain.newId(),
                    title = title.ifBlank { "نمط مخصص" },
                    focusMinutes = focus,
                    shortBreakMinutes = short,
                    longBreakMinutes = long,
                    sessionsCount = rounds,
                    longBreakInterval = interval.coerceAtMost(rounds),
                    autoStart = auto,
                    sound = sound,
                    vibration = vibration,
                    builtIn = preset?.builtIn ?: false,
                    position = preset?.position ?: -1,
                )
            )
        },
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.preset_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        DialogSlider(R.string.focus_duration, focus, 5..180, "د") { focus = it }
        DialogSlider(R.string.short_break, short, 0..30, "د") { short = it }
        DialogSlider(R.string.long_break, long, 0..60, "د") { long = it }
        DialogSlider(R.string.sessions_count, rounds, 1..12, "") { rounds = it }
        DialogSlider(R.string.long_break_interval, interval.coerceAtMost(rounds), 1..rounds, "") {
            interval = it
        }
        SwitchRow(stringResource(R.string.auto_start), null, auto) { auto = it }
        SwitchRow(stringResource(R.string.sound), null, sound) { sound = it }
        SwitchRow(stringResource(R.string.vibration), null, vibration) { vibration = it }
        if (onDelete != null && preset != null && !preset.builtIn) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onDelete() }) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete_preset), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DialogSlider(label: Int, value: Int, range: IntRange, suffix: String, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("$value $suffix", style = MaterialTheme.typography.titleMedium, color = Lux.Emerald)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

// ---- Session result -----------------------------------------------------------------------

@Composable
fun SessionResultSheet(
    focusedSeconds: Long,
    defaultGoal: String,
    onDismiss: () -> Unit,
    onSave: (SessionResult?, String, Int?) -> Unit,
) {
    var goal by rememberSaveable { mutableStateOf(defaultGoal) }
    var notes by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<SessionResult?>(SessionResult.YES) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.session_result), style = MaterialTheme.typography.headlineLarge)
                LuxCard {
                    Text(stringResource(R.string.study_time), style = MaterialTheme.typography.labelMedium)
                    Text(
                        durationText(focusedSeconds),
                        style = MaterialTheme.typography.displaySmall,
                        color = Lux.EmeraldBright,
                    )
                }
                OutlinedTextField(
                    goal, { goal = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.goal_label)) },
                    leadingIcon = { Icon(Icons.Outlined.Flag, null) },
                    singleLine = true,
                )
                Text(stringResource(R.string.result_question), style = MaterialTheme.typography.titleMedium)
                SegmentedControl(
                    listOf(
                        stringResource(R.string.result_yes),
                        stringResource(R.string.result_partial),
                        stringResource(R.string.result_no),
                    ),
                    selectedIndex =
                        when (result) {
                            SessionResult.YES -> 0
                            SessionResult.PARTIALLY -> 1
                            SessionResult.NO -> 2
                            null -> 0
                        },
                    onSelect = {
                        result = when (it) {
                            0 -> SessionResult.YES
                            1 -> SessionResult.PARTIALLY
                            else -> SessionResult.NO
                        }
                    },
                )
                OutlinedTextField(
                    notes, { notes = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.notes_label)) },
                    leadingIcon = { Icon(Icons.Outlined.Notes, null) },
                    minLines = 3,
                )
                OutlinedTextField(
                    page, { page = it.filter { c -> c.isDigit() }.take(4) },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.page_label)) },
                    leadingIcon = { Icon(Icons.Outlined.Book, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.not_now))
                    }
                    Button(
                        onClick = {
                            onSave(result, notes, page.toIntOrNull())
                        },
                        Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Outlined.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.save_session))
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewPromptDialog(onSchedule: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Replay, null, tint = Lux.Gold) },
        title = { Text(stringResource(R.string.schedule_review_question)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    1L to stringResource(R.string.tomorrow),
                    2L to stringResource(R.string.in_days, 2),
                    4L to stringResource(R.string.in_days, 4),
                    8L to stringResource(R.string.in_days, 8),
                ).forEach { (days, label) ->
                    Surface(
                        onClick = { onSchedule(days) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(label, Modifier.padding(14.dp), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) }
        },
    )
}

// ---- Review dialog ------------------------------------------------------------------------

@Composable
fun ReviewDialog(
    initialDay: Int,
    initialNote: String = "",
    titleRes: Int = R.string.add_review,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit,
) {
    var day by rememberSaveable { mutableIntStateOf(initialDay) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    FullScreenDialog(
        title = stringResource(titleRes),
        onClose = onDismiss,
        onSave = { onSave(day, note) },
    ) {
        Text(stringResource(R.string.review_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DateField(stringResource(R.string.review_date), day, { day = it ?: day }, clearable = false)
        OutlinedTextField(
            note, { note = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.review_note)) },
            leadingIcon = { Icon(Icons.Outlined.Notes, null) },
            minLines = 3,
        )
    }
}

// ---- Task dialog --------------------------------------------------------------------------

@Composable
fun TaskDialog(
    task: Task?,
    subjects: List<com.nageebstudyos.study.domain.Entry>,
    fixedSubjectId: String? = null,
    fixedLessonId: String? = null,
    fixedPlanId: String? = null,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
) {
    var title by rememberSaveable(task?.id) { mutableStateOf(task?.title ?: "") }
    var description by rememberSaveable(task?.id) { mutableStateOf(task?.description ?: "") }
    var priority by rememberSaveable(task?.id) { mutableStateOf(task?.priority ?: TaskPriority.NORMAL) }
    var status by rememberSaveable(task?.id) { mutableStateOf(task?.status ?: TaskStatus.PENDING) }
    var day by rememberSaveable(task?.id) { mutableStateOf(task?.day) }
    var due by rememberSaveable(task?.id) { mutableStateOf(task?.dueDay) }
    var subjectId by rememberSaveable(task?.id) { mutableStateOf(task?.subjectId ?: fixedSubjectId) }
    var menuOpen by remember { mutableStateOf(false) }

    FullScreenDialog(
        title = stringResource(if (task == null) R.string.add_task else R.string.edit),
        onClose = onDismiss,
        onSave = {
            onSave(
                Task(
                    id = task?.id ?: newId(),
                    planId = task?.planId ?: fixedPlanId,
                    title = title,
                    description = description,
                    day = day,
                    dueDay = due,
                    subjectId = subjectId,
                    lessonId = task?.lessonId ?: fixedLessonId,
                    status = status,
                    priority = priority,
                    position = task?.position ?: 0,
                )
            )
        },
        saveEnabled = title.isNotBlank(),
    ) {
        OutlinedTextField(
            title, { title = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.task_name)) },
            singleLine = true,
        )
        OutlinedTextField(
            description, { description = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.task_description)) },
            minLines = 2,
        )
        Text(stringResource(R.string.priority), style = MaterialTheme.typography.titleMedium)
        SegmentedControl(
            listOf(
                stringResource(R.string.priority_low),
                stringResource(R.string.priority_normal),
                stringResource(R.string.priority_high),
            ),
            when (priority) {
                TaskPriority.LOW -> 0
                TaskPriority.NORMAL -> 1
                TaskPriority.HIGH -> 2
            },
            {
                priority = when (it) {
                    0 -> TaskPriority.LOW
                    1 -> TaskPriority.NORMAL
                    else -> TaskPriority.HIGH
                }
            },
        )
        if (task != null) {
            Text(stringResource(R.string.status), style = MaterialTheme.typography.titleMedium)
            SegmentedControl(
                listOf(
                    stringResource(R.string.task_pending),
                    stringResource(R.string.task_in_progress),
                    stringResource(R.string.task_done),
                ),
                when (status) {
                    TaskStatus.PENDING -> 0
                    TaskStatus.IN_PROGRESS -> 1
                    TaskStatus.DONE -> 2
                },
                {
                    status = when (it) {
                        0 -> TaskStatus.PENDING
                        1 -> TaskStatus.IN_PROGRESS
                        else -> TaskStatus.DONE
                    }
                },
            )
        }
        DateField(stringResource(R.string.task_date), day, { day = it })
        DateField(stringResource(R.string.due_date), due, { due = it })
        Box {
            Surface(onClick = { menuOpen = true }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoStories, null, tint = Lux.Emerald, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        subjects.firstOrNull { it.id == subjectId }?.title
                            ?: stringResource(R.string.no_subject),
                        Modifier.weight(1f),
                    )
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
            }
            DropdownMenu(menuOpen, { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.no_subject)) },
                    onClick = { subjectId = null; menuOpen = false },
                )
                subjects.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.title) },
                        onClick = { subjectId = entry.id; menuOpen = false },
                    )
                }
            }
        }
    }
}

// ---- Plan dialog --------------------------------------------------------------------------

@Composable
fun PlanDialog(
    plan: StudyPlan?,
    subjects: List<com.nageebstudyos.study.domain.Entry>,
    onDismiss: () -> Unit,
    onSave: (StudyPlan) -> Unit,
) {
    val today = StudyTime.today()
    var title by rememberSaveable(plan?.id) { mutableStateOf(plan?.title ?: "") }
    var description by rememberSaveable(plan?.id) { mutableStateOf(plan?.description ?: "") }
    var start by rememberSaveable(plan?.id) { mutableIntStateOf(plan?.startDay ?: today) }
    var end by rememberSaveable(plan?.id) {
        mutableIntStateOf(plan?.endDay ?: (today + 7))
    }
    var subjectId by rememberSaveable(plan?.id) { mutableStateOf(plan?.subjectId) }
    var menuOpen by remember { mutableStateOf(false) }

    FullScreenDialog(
        title = stringResource(if (plan == null) R.string.new_plan else R.string.edit),
        onClose = onDismiss,
        onSave = {
            onSave(
                StudyPlan(
                    id = plan?.id ?: newId(),
                    title = title,
                    description = description,
                    subjectId = subjectId,
                    startDay = minOf(start, end),
                    endDay = maxOf(start, end),
                )
            )
        },
        saveEnabled = title.isNotBlank(),
    ) {
        OutlinedTextField(
            title, { title = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.plan_name)) },
            singleLine = true,
        )
        OutlinedTextField(
            description, { description = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.plan_description)) },
            minLines = 2,
        )
        DateField(stringResource(R.string.start_date), start, { start = it ?: start }, clearable = false)
        DateField(stringResource(R.string.end_date), end, { end = it ?: end }, clearable = false)
        Box {
            Surface(onClick = { menuOpen = true }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoStories, null, tint = Lux.Emerald, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        subjects.firstOrNull { it.id == subjectId }?.title
                            ?: stringResource(R.string.no_subject),
                        Modifier.weight(1f),
                    )
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
            }
            DropdownMenu(menuOpen, { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.no_subject)) },
                    onClick = { subjectId = null; menuOpen = false },
                )
                subjects.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.title) },
                        onClick = { subjectId = entry.id; menuOpen = false },
                    )
                }
            }
        }
    }
}
