package com.nageebstudyos.study.presentation.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nageebstudyos.study.R
import com.nageebstudyos.study.domain.*
import com.nageebstudyos.study.presentation.V2ViewModel
import com.nageebstudyos.study.ui.Lux
import kotlinx.coroutines.flow.Flow

@Composable
fun PlannerScreen(
    vm: V2ViewModel,
    subjects: List<Entry>,
    onOpenCalendar: () -> Unit,
    onOpenLesson: (String) -> Unit,
) {
    val plans by vm.plans.collectAsStateWithLifecycle()
    val loose by vm.looseTasks.collectAsStateWithLifecycle()

    var planDialog by remember { mutableStateOf<StudyPlan?>(null) }
    var createPlan by remember { mutableStateOf(false) }
    var taskDialog by remember { mutableStateOf<Task?>(null) }
    var newLooseTask by remember { mutableStateOf(false) }
    var newTaskForPlan by remember { mutableStateOf<String?>(null) }
    var rescheduleTarget by remember { mutableStateOf<Task?>(null) }
    var deletePlanTarget by remember { mutableStateOf<StudyPlan?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.planner_title), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.planner_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedButton(
                onOpenCalendar,
                Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.CalendarMonth, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.calendar_title))
            }
        }

        item {
            SectionHeader(stringResource(R.string.study_goals)) {
                TextButton(onClick = { createPlan = true }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.new_plan))
                }
            }
        }
        if (plans.isEmpty())
            item {
                LuxCard {
                    Text(
                        stringResource(R.string.no_plans),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        items(plans, key = { it.id }) { plan ->
            PlanCard(
                plan = plan,
                expanded = plan.id in expanded,
                tasksFlow = vm.planTasks(plan.id),
                onToggle = {
                    expanded =
                        if (plan.id in expanded) expanded - plan.id else expanded + plan.id
                },
                onEdit = { planDialog = plan },
                onAddTask = { newTaskForPlan = plan.id },
                onTaskCycle = vm::cycleTask,
                onTaskEdit = { taskDialog = it },
                onTaskReschedule = { rescheduleTarget = it },
                onTaskDelete = vm::deleteTask,
                onDelete = { deletePlanTarget = plan },
            )
        }

        item {
            SectionHeader(stringResource(R.string.add_task)) {
                TextButton(onClick = { newLooseTask = true }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add_task))
                }
            }
        }
        if (loose.isEmpty())
            item {
                LuxCard {
                    Text(
                        stringResource(R.string.no_tasks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        items(loose, key = { it.id }) { task ->
            TaskCard(
                task = task,
                onCycle = { vm.cycleTask(task) },
                onEdit = { taskDialog = task },
                onReschedule = { rescheduleTarget = task },
                onDelete = { vm.deleteTask(task) },
                onOpenLesson = { task.lessonId?.let(onOpenLesson) },
            )
        }
    }

    if (createPlan)
        PlanDialog(null, subjects, onDismiss = { createPlan = false }, onSave = {
            vm.savePlan(it)
            createPlan = false
            expanded = expanded + it.id
        })
    planDialog?.let { editing ->
        PlanDialog(editing, subjects, onDismiss = { planDialog = null }, onSave = {
            vm.savePlan(it)
            planDialog = null
        })
    }
    if (newLooseTask)
        TaskDialog(
            task = null,
            subjects = subjects,
            onDismiss = { newLooseTask = false },
            onSave = {
                vm.saveTask(it)
                newLooseTask = false
            },
        )
    newTaskForPlan?.let { planId ->
        TaskDialog(
            task = null,
            subjects = subjects,
            fixedPlanId = planId,
            onDismiss = { newTaskForPlan = null },
            onSave = {
                vm.saveTask(it)
                newTaskForPlan = null
            },
        )
    }
    taskDialog?.let { editing ->
        TaskDialog(
            task = editing,
            subjects = subjects,
            onDismiss = { taskDialog = null },
            onSave = {
                vm.saveTask(it)
                taskDialog = null
            },
        )
    }
    rescheduleTarget?.let { task ->
        RescheduleDialog(
            task = task,
            onDismiss = { rescheduleTarget = null },
            onConfirm = { day, due ->
                vm.rescheduleTask(task, day, due)
                rescheduleTarget = null
            },
        )
    }
    deletePlanTarget?.let { plan ->
        AlertDialog(
            onDismissRequest = { deletePlanTarget = null },
            icon = { Icon(Icons.Outlined.DeleteOutline, null) },
            title = { Text(stringResource(R.string.delete_plan)) },
            text = { Text(stringResource(R.string.delete_plan_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePlan(plan)
                    deletePlanTarget = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletePlanTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PlanCard(
    plan: StudyPlan,
    expanded: Boolean,
    tasksFlow: Flow<List<Task>>,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onAddTask: () -> Unit,
    onTaskCycle: (Task) -> Unit,
    onTaskEdit: (Task) -> Unit,
    onTaskReschedule: (Task) -> Unit,
    onTaskDelete: (Task) -> Unit,
) {
    val tasks by tasksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val percent = if (plan.taskCount > 0) (plan.doneCount * 100) / plan.taskCount else 0
    LuxCard(onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plan.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (plan.subjectTitle.isNotBlank())
                    Text(
                        plan.subjectTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Lux.Emerald,
                    )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${shortDate(plan.startDay)} → ${shortDate(plan.endDay)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
            color = Lux.Emerald,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.tasks_progress, plan.doneCount, plan.taskCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (plan.description.isNotBlank())
                Text(
                    plan.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            tasks.forEach { task ->
                TaskCard(
                    task,
                    onCycle = { onTaskCycle(task) },
                    onEdit = { onTaskEdit(task) },
                    onReschedule = { onTaskReschedule(task) },
                    onDelete = { onTaskDelete(task) },
                    onOpenLesson = null,
                    compact = true,
                )
            }
            TextButton(onClick = onAddTask, contentPadding = PaddingValues(vertical = 6.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_task))
            }
        }
    }
}

@Composable
fun TaskCard(
    task: Task,
    onCycle: () -> Unit,
    onEdit: () -> Unit,
    onReschedule: () -> Unit,
    onDelete: () -> Unit,
    onOpenLesson: (() -> Unit)?,
    compact: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    val tint = priorityColor(task.priority)
    val done = task.status == TaskStatus.DONE
    Surface(
        onClick = onEdit,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 3.dp else 4.dp),
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(tint))
            Spacer(Modifier.width(8.dp))
            Checkbox(
                checked = done,
                onCheckedChange = { onCycle() },
                colors = CheckboxDefaults.colors(checkedColor = Lux.Emerald),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details =
                    listOfNotNull(
                        task.dueDay?.let {
                            val overdue = it < StudyTime.today() && !done
                            if (overdue) "${stringResource(R.string.overdue)} · ${shortDate(it)}"
                            else shortDate(it)
                        },
                        if (task.status == TaskStatus.IN_PROGRESS)
                            stringResource(R.string.task_in_progress)
                        else null,
                        task.subjectTitle.ifBlank { null },
                    ).joinToString(" · ")
                if (details.isNotBlank())
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
            }
            if (onOpenLesson != null && task.lessonId != null) {
                IconButton(onClick = onOpenLesson) {
                    Icon(Icons.Outlined.MenuBook, null, Modifier.size(18.dp))
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null, Modifier.size(18.dp)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (task.status) {
                                    TaskStatus.PENDING -> stringResource(R.string.mark_in_progress)
                                    TaskStatus.IN_PROGRESS -> stringResource(R.string.complete_task)
                                    TaskStatus.DONE -> stringResource(R.string.reopen_task)
                                }
                            )
                        },
                        onClick = { menu = false; onCycle() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reschedule_task)) },
                        onClick = { menu = false; onReschedule() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_task), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
fun RescheduleDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: (Int?, Int?) -> Unit,
) {
    var day by remember { mutableStateOf(task.day) }
    var due by remember { mutableStateOf(task.dueDay) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reschedule_task)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(stringResource(R.string.task_date), day, { day = it })
                DateField(stringResource(R.string.due_date), due, { due = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(day, due) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
