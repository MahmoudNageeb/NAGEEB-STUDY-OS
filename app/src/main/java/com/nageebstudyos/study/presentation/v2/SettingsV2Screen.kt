package com.nageebstudyos.study.presentation.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nageebstudyos.study.R
import com.nageebstudyos.study.presentation.V2ViewModel
import com.nageebstudyos.study.ui.Lux

@Composable
fun SettingsV2Screen(
    vm: V2ViewModel,
    notificationsAllowed: Boolean,
    onRequestNotifications: () -> Unit,
    onTags: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val goal by vm.dailyGoalMinutes.collectAsState()
    var goalDrag by remember(goal) { mutableStateOf(goal) }
    val reminderEnabled = settings["reminderEnabled"] == "1"
    val reminderHour = settings["reminderHour"]?.toIntOrNull() ?: 20
    var timeMenu by remember { mutableStateOf(false) }
    val reminderTimes = listOf(17 to "05:00 م", 18 to "06:00 م", 20 to "08:00 م", 21 to "09:00 م", 22 to "10:00 م")

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.displaySmall)
        }
        item {
            LuxCard {
                Text(stringResource(R.string.daily_goal), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.daily_goal_minutes, goalDrag),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Lux.EmeraldBright,
                )
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = goalDrag.toFloat(),
                    onValueChange = { goalDrag = it.toInt() },
                    onValueChangeFinished = {
                        if (goalDrag != goal) vm.setDailyGoal(goalDrag)
                    },
                    valueRange = 15f..480f,
                    steps = 30,
                )
                Row {
                    listOf(30, 60, 120, 240).forEach { value ->
                        TextButton(
                            onClick = {
                                goalDrag = value
                                vm.setDailyGoal(value)
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) { Text("$value") }
                    }
                }
            }
        }
        item {
            LuxCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Notifications, null, tint = Lux.Gold)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.notifications), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                if (notificationsAllowed) R.string.notifications_enabled
                                else R.string.notifications_disabled
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!notificationsAllowed) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRequestNotifications,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.enable_notifications))
                    }
                    Text(
                        stringResource(R.string.notification_rationale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SwitchRow(
                stringResource(R.string.study_reminder),
                stringResource(R.string.reminder_time),
                reminderEnabled,
            ) { checked ->
                if (checked && !notificationsAllowed) onRequestNotifications()
                vm.setReminder(checked, reminderHour, 0)
            }
        }
        item {
            LuxCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reminder_time), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Box {
                        TextButton(onClick = { timeMenu = true }) {
                            Text(
                                reminderTimes.firstOrNull { it.first == reminderHour }?.second
                                    ?: String.format("%02d:00", reminderHour)
                            )
                            Icon(Icons.Outlined.ArrowDropDown, null)
                        }
                        DropdownMenu(timeMenu, { timeMenu = false }) {
                            reminderTimes.forEach { (hour, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        timeMenu = false
                                        vm.setReminder(reminderEnabled, hour, 0)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onTags,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Label, null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.manage_tags))
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Lock, null, tint = Lux.Emerald)
                    Text(stringResource(R.string.privacy), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.dark_mode_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.version_label), style = MaterialTheme.typography.bodySmall)
        }
    }
}
