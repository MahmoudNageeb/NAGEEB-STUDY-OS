package com.nageebstudyos.study.presentation.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nageebstudyos.study.R
import com.nageebstudyos.study.domain.FocusPhase
import com.nageebstudyos.study.domain.FocusPreset
import com.nageebstudyos.study.domain.FocusStatus
import com.nageebstudyos.study.domain.SessionResult
import com.nageebstudyos.study.presentation.FocusViewModel
import com.nageebstudyos.study.ui.Lux

@Composable
fun FocusScreen(vm: FocusViewModel, onFinishFlow: () -> Unit) {
    val context = LocalContext.current
    val presets by vm.presets.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val goal by vm.goal.collectAsStateWithLifecycle()
    val linking by vm.linking.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val savedFlow by vm.saved.collectAsStateWithLifecycle()

    var presetEditor by remember { mutableStateOf<FocusPreset?>(null) }
    var creatingPreset by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var showReviewPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(result) {
        if (result != null) showResult = true
    }

    val running = snapshot != null && snapshot?.status != FocusStatus.FINISHED

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(stringResource(R.string.focus_title), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.focus_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (running && snapshot != null) {
            item { RunningFocus(snapshot!!, vm, context) }
        } else {
            item { LinkingCard(linking) }

            item {
                SectionHeader(stringResource(R.string.choose_preset)) {
                    TextButton(onClick = { creatingPreset = true }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.new_custom_preset))
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(presets, key = { it.id }) { preset ->
                        PresetCard(
                            preset = preset,
                            selected = preset.id == selectedId,
                            onSelect = { vm.selectPreset(preset.id) },
                            onEdit = { presetEditor = preset },
                        )
                    }
                }
            }

            if (draft != null) {
                val cfg = draft!!
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberStepper(
                            stringResource(R.string.focus_duration),
                            cfg.focusMinutes,
                            5..180,
                            stringResource(R.string.focus_minutes_unit),
                            { value -> vm.updateDraft { it.copy(focusMinutes = value) } },
                            Modifier.weight(1f),
                        )
                        NumberStepper(
                            stringResource(R.string.short_break),
                            cfg.shortBreakMinutes,
                            0..30,
                            stringResource(R.string.focus_minutes_unit),
                            { value -> vm.updateDraft { it.copy(shortBreakMinutes = value) } },
                            Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberStepper(
                            stringResource(R.string.long_break),
                            cfg.longBreakMinutes,
                            0..60,
                            stringResource(R.string.focus_minutes_unit),
                            { value -> vm.updateDraft { it.copy(longBreakMinutes = value) } },
                            Modifier.weight(1f),
                        )
                        NumberStepper(
                            stringResource(R.string.sessions_count),
                            cfg.sessionsCount,
                            1..12,
                            stringResource(R.string.focus_minutes_unit),
                            { value ->
                                vm.updateDraft {
                                    it.copy(
                                        sessionsCount = value,
                                        longBreakInterval = it.longBreakInterval.coerceAtMost(value),
                                    )
                                }
                            },
                            Modifier.weight(1f),
                        )
                    }
                }
                item {
                    NumberStepper(
                        stringResource(R.string.long_break_interval),
                        cfg.longBreakInterval.coerceAtMost(cfg.sessionsCount),
                        1..cfg.sessionsCount,
                        stringResource(R.string.focus_minutes_unit),
                        { value -> vm.updateDraft { it.copy(longBreakInterval = value) } },
                    )
                }
                item {
                    SwitchRow(
                        stringResource(R.string.auto_start),
                        null,
                        cfg.autoStart,
                    ) { checked -> vm.updateDraft { it.copy(autoStart = checked) } }
                }
                item {
                    SwitchRow(stringResource(R.string.sound), null, cfg.sound) { checked ->
                        vm.updateDraft { it.copy(sound = checked) }
                    }
                }
                item {
                    SwitchRow(stringResource(R.string.vibration), null, cfg.vibration) { checked ->
                        vm.updateDraft { it.copy(vibration = checked) }
                    }
                }
                item {
                    OutlinedTextField(
                        value = goal,
                        onValueChange = vm::setGoal,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.goal_label)) },
                        leadingIcon = { Icon(Icons.Outlined.Flag, null) },
                        singleLine = true,
                    )
                }
                item {
                    Button(
                        onClick = { vm.start(context) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.start_focus), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (creatingPreset) {
        PresetDialog(
            preset = null,
            onDismiss = { creatingPreset = false },
            onSave = {
                vm.savePreset(it)
                creatingPreset = false
            },
        )
    }
    presetEditor?.let { editing ->
        PresetDialog(
            preset = editing,
            onDismiss = { presetEditor = null },
            onSave = { updated ->
                vm.savePreset(updated.copy(id = editing.id, builtIn = editing.builtIn))
                presetEditor = null
            },
            onDelete = if (!editing.builtIn) {
                {
                    vm.deletePreset(editing)
                    presetEditor = null
                }
            } else null,
        )
    }

    if (showResult && result != null) {
        SessionResultSheet(
            focusedSeconds = result!!.focusedSeconds,
            defaultGoal = goal.ifBlank { result!!.config.goal },
            onDismiss = {
                showResult = false
                vm.dismissWithoutSaving()
            },
            onSave = { outcome, notes, page ->
                vm.saveResult(outcome, notes, page) {
                    showResult = false
                    if (result!!.config.lessonId != null) showReviewPrompt = true
                    else {
                        vm.resetPrompt()
                        onFinishFlow()
                    }
                }
            },
        )
    }
    if (showReviewPrompt) {
        ReviewPromptDialog(
            onSchedule = { days ->
                vm.scheduleReview(days) {
                    showReviewPrompt = false
                    onFinishFlow()
                }
            },
            onDismiss = {
                showReviewPrompt = false
                vm.resetPrompt()
                onFinishFlow()
            },
        )
    }
}

@Composable
private fun LinkingCard(linking: com.nageebstudyos.study.presentation.FocusLinking) {
    LuxCard(brush = Lux.cardGradient) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Link, null, tint = Lux.Emerald, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.linked_to),
                style = MaterialTheme.typography.labelMedium,
                color = Lux.Emerald,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (!linking.linked) {
            Text(
                stringResource(R.string.free_study),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.focus_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val chain =
                listOfNotNull(
                    linking.subjectTitle.ifBlank { null },
                    linking.folderTitle.ifBlank { null },
                    linking.lessonTitle.ifBlank { null },
                )
            chain.forEachIndexed { index, text ->
                Text(
                    text,
                    style = if (index == chain.lastIndex) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyMedium,
                    color = if (index == chain.lastIndex) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: FocusPreset,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    LuxCard(
        onClick = onSelect,
        modifier = Modifier.width(168.dp),
        border = if (selected) Lux.Emerald else MaterialTheme.colorScheme.outlineVariant,
        brush =
            if (selected)
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Lux.Emerald.copy(alpha = 0.18f), MaterialTheme.colorScheme.surface)
                )
            else Lux.cardGradient,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(preset.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${preset.focusMinutes}/${preset.shortBreakMinutes}",
            style = MaterialTheme.typography.headlineMedium,
            color = if (selected) Lux.EmeraldBright else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.session_rounds_done, preset.sessionsCount, preset.sessionsCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RunningFocus(
    snapshot: com.nageebstudyos.study.domain.FocusSnapshot,
    vm: FocusViewModel,
    context: android.content.Context,
) {
    val isFocus = snapshot.phase == FocusPhase.FOCUS
    val paused = snapshot.status == FocusStatus.PAUSED
    LuxCard(
        brush = Lux.heroGradient,
        contentPadding = PaddingValues(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                when {
                    paused -> stringResource(R.string.paused_focus)
                    isFocus -> stringResource(R.string.in_focus)
                    else -> stringResource(if (snapshot.phase == FocusPhase.LONG_BREAK) R.string.long_break else R.string.short_break)
                },
                if (isFocus) Lux.Emerald else Lux.Sky,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.round_of, snapshot.round, snapshot.totalRounds),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BigTimer(snapshot)
        }
        Spacer(Modifier.height(26.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (paused)
                Button(
                    onClick = { vm.resume(context) },
                    modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.resume))
                }
            else
                FilledTonalButton(
                    onClick = { vm.pause(context) },
                    modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Outlined.Pause, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pause))
                }
            OutlinedButton(
                onClick = { vm.skip(context) },
                modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.skip_phase))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundDots(snapshot)
        }
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = { vm.stop(context) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.StopCircle, null, tint = Lux.Rose)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.end_session), color = Lux.Rose)
        }
    }
}

@Composable
private fun BigTimer(snapshot: com.nageebstudyos.study.domain.FocusSnapshot) {
    val progress =
        if (snapshot.phaseLengthMs > 0)
            snapshot.remainingMs.toFloat() / snapshot.phaseLengthMs
        else 0f
    val animated by androidx.compose.animation.core.animateFloatAsState(
        progress,
        androidx.compose.animation.core.tween(600),
        label = "timer",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f),
                radius = size.minDimension / 2 - stroke / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush =
                    if (snapshot.phase == FocusPhase.FOCUS) Lux.emeraldGradient
                    else androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Lux.Sky, Lux.Sky.copy(alpha = 0.5f))
                    ),
                startAngle = -90f,
                sweepAngle = animated.coerceIn(0f, 1f) * 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                clockText(snapshot.remainingMs),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 52.sp),
                color = Lux.EmeraldBright,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                durationText(snapshot.focusedMs / 1000),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RoundDots(snapshot: com.nageebstudyos.study.domain.FocusSnapshot) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(snapshot.totalRounds) { index ->
            val round = index + 1
            val done = round < snapshot.round ||
                snapshot.status == FocusStatus.FINISHED
            val current = round == snapshot.round
            Box(
                Modifier
                    .size(if (current) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            done -> Lux.Emerald
                            current -> Lux.Emerald.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
            )
        }
    }
}
