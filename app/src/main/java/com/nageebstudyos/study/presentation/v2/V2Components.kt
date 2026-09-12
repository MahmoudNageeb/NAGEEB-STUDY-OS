package com.nageebstudyos.study.presentation.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nageebstudyos.study.R
import com.nageebstudyos.study.domain.CalendarEventKind
import com.nageebstudyos.study.domain.ReviewStatus
import com.nageebstudyos.study.domain.SessionResult
import com.nageebstudyos.study.domain.TaskPriority
import com.nageebstudyos.study.domain.TaskStatus
import com.nageebstudyos.study.ui.Eyebrow
import com.nageebstudyos.study.ui.Lux
import com.nageebstudyos.study.ui.SubjectAccents
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

// ---- Surfaces -----------------------------------------------------------------------------

@Composable
fun LuxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    brush: Brush = Lux.cardGradient,
    border: Color = MaterialTheme.colorScheme.outlineVariant,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val base =
        Modifier
            .then(modifier)
            .clip(shape)
            .background(brush)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                // Hairline top highlight for the "carved from ink" premium feel.
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(24f, 1f),
                    end = Offset(size.width - 24f, 1f),
                    strokeWidth = 1f,
                )
            }
    Surface(color = Color.Transparent, shape = shape, border = BorderStroke(1.dp, border)) {
        Box(base) { Column(Modifier.padding(contentPadding), content = content) }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(title, Modifier.weight(1f))
        trailing?.invoke()
    }
}

// ---- Stat tiles ---------------------------------------------------------------------------

@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    LuxCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
    ) {
        Box(
            Modifier.size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Progress ring ------------------------------------------------------------------------

@Composable
fun ProgressRing(
    percent: Int,
    modifier: Modifier = Modifier,
    size: Int = 148,
    ringWidth: Int = 12,
    brush: Brush = Lux.emeraldGradient,
    center: @Composable BoxScope.() -> Unit,
) {
    val target = (percent.coerceIn(0, 100)) / 100f
    val animated by animateFloatAsState(target, tween(900), label = "ring")
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = ringWidth.dp.toPx(), cap = StrokeCap.Round)
            val inset = ringWidth.dp.toPx() / 2
            val arcSize = Size(this.size.width - ringWidth.dp.toPx(), this.size.height - ringWidth.dp.toPx())
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = this.size.minDimension / 2 - inset,
                style = stroke,
            )
            drawArc(
                brush = brush,
                startAngle = -90f,
                sweepAngle = animated * 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        center()
    }
}

// ---- Bars ---------------------------------------------------------------------------------

@Composable
fun StudyBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
    height: Int = 110,
) {
    val maxValue = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            values.forEachIndexed { index, v ->
                val animated by animateFloatAsState(
                    if (maxValue == 0f) 0f else (v / maxValue).coerceIn(0f, 1f),
                    tween(700, delayMillis = index * 35),
                    label = "bar",
                )
                val isToday = index == values.lastIndex
                Box(
                    Modifier.weight(1f).fillMaxHeight(animated.coerceIn(0.02f, 1f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (isToday && v > 0) Lux.emeraldGradient
                                else Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = if (v > 0) 0.55f else 0.16f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = if (v > 0) 0.25f else 0.08f),
                                    )
                                )
                            )
                    )
                }
            }
        }
        if (labels != null) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEach { label ->
                    Text(
                        label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Horizontal proportional distribution bar. */
@Composable
fun DistributionBar(segments: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    val total = segments.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(1f)
    Row(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        segments.forEach { (value, color) ->
            if (value > 0)
                Box(Modifier.weight((value / total).coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .background(color))
        }
    }
}

// ---- Controls -----------------------------------------------------------------------------

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else Color.Transparent
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun NumberStepper(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LuxCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$value ",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(suffix, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            StepperButton(Icons.Outlined.Remove, value > range.first) { onChange((value - 1).coerceIn(range)) }
            Spacer(Modifier.width(8.dp))
            StepperButton(Icons.Outlined.Add, value < range.last) { onChange((value + 1).coerceIn(range)) }
        }
    }
}

@Composable
private fun StepperButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

// ---- Chips / badges -----------------------------------------------------------------------

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        color = color.copy(alpha = 0.14f),
        shape = CircleShape,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
fun priorityColor(priority: TaskPriority): Color =
    when (priority) {
        TaskPriority.HIGH -> Lux.Rose
        TaskPriority.NORMAL -> Lux.Gold
        TaskPriority.LOW -> Lux.Sky
    }

@Composable
fun statusColor(status: TaskStatus): Color =
    when (status) {
        TaskStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        TaskStatus.IN_PROGRESS -> Lux.Gold
        TaskStatus.DONE -> Lux.Emerald
    }

@Composable
fun reviewStatusColor(status: ReviewStatus): Color =
    when (status) {
        ReviewStatus.PENDING -> Lux.Gold
        ReviewStatus.DONE -> Lux.Emerald
        ReviewStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
fun resultColor(result: SessionResult?): Color =
    when (result) {
        SessionResult.YES -> Lux.Emerald
        SessionResult.PARTIALLY -> Lux.Gold
        SessionResult.NO -> Lux.Rose
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
fun eventColor(kind: CalendarEventKind): Color =
    when (kind) {
        CalendarEventKind.REVIEW -> Lux.Gold
        CalendarEventKind.TASK -> Lux.Emerald
        CalendarEventKind.SESSION -> Lux.Sky
        CalendarEventKind.PLAN -> Lux.Violet
    }

@Composable
fun accentAt(index: Int): Color = SubjectAccents[index.coerceIn(0, SubjectAccents.lastIndex)]

// ---- Text helpers -------------------------------------------------------------------------

@Composable
fun durationText(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> stringResource(R.string.hours_minutes, h.toInt(), m.toInt())
        m > 0 -> stringResource(R.string.minutes_only, m.toInt())
        else -> stringResource(R.string.seconds_only, s.toInt())
    }
}

@Composable
fun clockText(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}

fun weekdayInitial(dayOfWeek: Int): String =
    when (dayOfWeek) {
        Calendar.SATURDAY -> "س"
        Calendar.SUNDAY -> "ح"
        Calendar.MONDAY -> "ن"
        Calendar.TUESDAY -> "ث"
        Calendar.WEDNESDAY -> "ر"
        Calendar.THURSDAY -> "خ"
        Calendar.FRIDAY -> "ج"
        else -> ""
    }

@Composable
fun mediumDate(day: Int): String {
    val millis = com.nageebstudyos.study.domain.StudyTime.at(day, 12, 0)
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(millis))
}

@Composable
fun shortDate(day: Int): String {
    val millis = com.nageebstudyos.study.domain.StudyTime.at(day, 12, 0)
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(millis))
}

@Composable
fun timeAgo(millis: Long): String {
    if (millis <= 0) return ""
    val diff = (System.currentTimeMillis() - millis).coerceAtLeast(0)
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "الآن"
        minutes < 60 -> "قبل $minutes د"
        hours < 24 -> "قبل $hours س"
        days < 30 -> "قبل $days يوم"
        else -> mediumDate(com.nageebstudyos.study.domain.StudyTime.dayOf(millis))
    }
}
