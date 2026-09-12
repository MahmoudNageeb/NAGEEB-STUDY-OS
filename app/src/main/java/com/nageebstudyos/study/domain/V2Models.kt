package com.nageebstudyos.study.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// ---- Enums -------------------------------------------------------------------------------

enum class ReviewStatus {
    PENDING,
    DONE,
    SKIPPED,
}

enum class SessionResult(val positive: Boolean) {
    YES(true),
    PARTIALLY(true),
    NO(false),
}

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
}

enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH,
}

enum class CalendarEventKind {
    REVIEW,
    TASK,
    SESSION,
    PLAN,
}

enum class TimeRange {
    TODAY,
    WEEK,
    MONTH,
}

// ---- Domain data classes -----------------------------------------------------------------

data class Review(
    val id: String,
    val lessonId: String,
    val subjectId: String,
    val scheduledAt: Long,
    val scheduledDay: Int,
    val completedAt: Long?,
    val status: ReviewStatus,
    val note: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lessonTitle: String = "",
    val subjectTitle: String = "",
)

data class StudySession(
    val id: String,
    val subjectId: String?,
    val folderId: String?,
    val lessonId: String?,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val goal: String = "",
    val result: SessionResult? = null,
    val notes: String = "",
    val lastPage: Int? = null,
    val dayIndex: Int = StudyTime.today(),
    val createdAt: Long = 0,
    val subjectTitle: String = "",
    val folderTitle: String = "",
    val lessonTitle: String = "",
)

data class Task(
    val id: String,
    val planId: String?,
    val title: String,
    val description: String = "",
    val day: Int? = null,
    val dueDay: Int? = null,
    val subjectId: String? = null,
    val lessonId: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val position: Int = 0,
    val completedAt: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val subjectTitle: String = "",
    val lessonTitle: String = "",
    val planTitle: String = "",
)

data class StudyPlan(
    val id: String,
    val title: String,
    val description: String = "",
    val subjectId: String? = null,
    val startDay: Int,
    val endDay: Int,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val subjectTitle: String = "",
    val taskCount: Int = 0,
    val doneCount: Int = 0,
)

data class FocusPreset(
    val id: String,
    val title: String,
    val focusMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val sessionsCount: Int,
    val longBreakInterval: Int,
    val autoStart: Boolean = false,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val builtIn: Boolean = false,
    val position: Int = 0,
)

data class ContinueState(
    val subjectId: String?,
    val folderId: String?,
    val lessonId: String?,
    val fileId: String?,
    val page: Int?,
    val updatedAt: Long,
    val subjectTitle: String = "",
    val folderTitle: String = "",
    val lessonTitle: String = "",
    val fileTitle: String = "",
)

data class ActivityItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: Kind,
    val time: Long,
    val targetId: String? = null,
    val targetSubjectId: String? = null,
    val targetKind: Kind? = null,
)

data class CalendarEvent(
    val id: String,
    val title: String,
    val subtitle: String,
    val day: Int,
    val kind: CalendarEventKind,
    val done: Boolean = false,
    /** Lesson/subject the event belongs to, used for deep navigation. */
    val targetLessonId: String? = null,
    val targetSubjectId: String? = null,
)

data class DayMinutes(val day: Int, val minutes: Int, val sessions: Int = 0)

data class SubjectSeconds(
    val subjectId: String?,
    val title: String,
    val accent: Int,
    val seconds: Long,
    val sessions: Int,
)

data class SubjectProgressInfo(
    val subjectId: String,
    val title: String,
    val accent: Int,
    val percent: Int,
    val lessonCount: Int,
    val notStarted: Int,
    val inProgress: Int,
    val needsReview: Int,
    val mastered: Int,
    val weak: Int,
)

data class DashboardData(
    val loading: Boolean = true,
    val todayMinutes: Int = 0,
    val todayGoalMinutes: Int = 60,
    val todaySessions: Int = 0,
    val reviewsDue: Int = 0,
    val tasksDue: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val week: List<DayMinutes> = emptyList(),
    val continueState: ContinueState? = null,
    val activity: List<ActivityItem> = emptyList(),
)

data class AnalyticsData(
    val range: TimeRange = TimeRange.WEEK,
    val totalMinutes: Int = 0,
    val sessions: Int = 0,
    val completedLessons: Int = 0,
    val pendingLessons: Int = 0,
    val reviewsCompleted: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val series: List<DayMinutes> = emptyList(),
    val distribution: List<SubjectSeconds> = emptyList(),
    val subjectProgress: List<SubjectProgressInfo> = emptyList(),
)

data class LessonStudyData(
    val reviews: List<Review> = emptyList(),
    val sessions: List<StudySession> = emptyList(),
    val tasks: List<Task> = emptyList(),
)

// ---- Time utilities ----------------------------------------------------------------------

/**
 * All "day" math uses the device's local zone. Day indexes are LocalDate epoch days so they
 * are zone-correct at storage time and cheap to aggregate in SQL.
 */
object StudyTime {
    fun zone(): ZoneId = ZoneId.systemDefault()

    fun today(): Int = LocalDate.now(zone()).toEpochDay().toInt()

    fun dayOf(millis: Long): Int =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().toEpochDay().toInt()

    fun localDate(day: Int): LocalDate = LocalDate.ofEpochDay(day.toLong())

    /** Epoch millis for [hour]:[minute] local time on the given day. */
    fun at(day: Int, hour: Int = 9, minute: Int = 0): Long =
        ZonedDateTime.of(localDate(day), java.time.LocalTime.of(hour, minute), zone())
            .toInstant()
            .toEpochMilli()

    fun startOfDay(day: Int): Long = at(day, 0, 0)

    fun endOfDay(day: Int): Long = at(day, 23, 59) + 59_999L

    fun range(range: TimeRange): Pair<Long, Long> {
        val today = LocalDate.now(zone())
        return when (range) {
            TimeRange.TODAY -> {
                val day = today.toEpochDay().toInt()
                at(day, 0, 0) to at(day, 23, 59) + 59_999L
            }
            TimeRange.WEEK -> {
                val from = today.minusDays(6)
                at(from.toEpochDay().toInt(), 0, 0) to
                    at(today.toEpochDay().toInt(), 23, 59) + 59_999L
            }
            TimeRange.MONTH -> {
                val from = today.minusDays(29)
                at(from.toEpochDay().toInt(), 0, 0) to
                    at(today.toEpochDay().toInt(), 23, 59) + 59_999L
            }
        }
    }

    fun dayRange(range: TimeRange): Pair<Int, Int> {
        val today = today()
        return when (range) {
            TimeRange.TODAY -> today to today
            TimeRange.WEEK -> today - 6 to today
            TimeRange.MONTH -> today - 29 to today
        }
    }
}

// ---- Streak rules ------------------------------------------------------------------------

/**
 * A "study day" is honest and easy to explain to the user:
 * a local calendar day on which at least one saved focus session finished with a positive
 * result (نعم / جزئيًا) and the day's total focused time is at least [MIN_FOCUS_SECONDS].
 * Days below one minute, cancelled sessions and "No" sessions never extend the streak.
 */
object StreakRules {
    const val MIN_FOCUS_SECONDS = 60L

    data class Bucket(val day: Int, val seconds: Long, val positive: Int)

    fun qualifies(seconds: Long, positive: Int): Boolean =
        seconds >= MIN_FOCUS_SECONDS && positive > 0

    fun current(buckets: List<Bucket>, today: Int = StudyTime.today()): Int {
        val byDay = buckets.associateBy { it.day }
        var cursor = byDay[today]?.takeIf { qualifies(it.seconds, it.positive) }?.day
            ?: byDay[today - 1]?.takeIf { qualifies(it.seconds, it.positive) }?.day
            ?: return 0
        var streak = 0
        while (true) {
            val bucket = byDay[cursor] ?: break
            if (!qualifies(bucket.seconds, bucket.positive)) break
            streak++
            cursor--
        }
        return streak
    }

    fun best(buckets: List<Bucket>): Int {
        val days = buckets.filter { qualifies(it.seconds, it.positive) }.map { it.day }.toSortedSet()
        var best = 0
        var run = 0
        var previous = Int.MIN_VALUE
        for (day in days) {
            run = if (previous != Int.MIN_VALUE && day == previous + 1) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }
}

// ---- Progress rules ----------------------------------------------------------------------

/**
 * Explainable subject progress.
 *  - Each lesson has a self-reported skill average of (understanding + application +
 *    revision) / 3 in 0..100.
 *  - A status weight anchors lessons whose skill bars are still empty:
 *    NOT_STARTED 0 · IN_PROGRESS .35 · WEAK .20 · NEEDS_REVIEW .60 · MASTERED 1.
 *  - Lesson score = 50% skill + 50% status weight; subject % = mean lesson score.
 */
object ProgressRules {
    fun statusWeight(status: LessonStatus): Double =
        when (status) {
            LessonStatus.NOT_STARTED -> 0.0
            LessonStatus.IN_PROGRESS -> 0.35
            LessonStatus.WEAK -> 0.20
            LessonStatus.NEEDS_REVIEW -> 0.60
            LessonStatus.MASTERED -> 1.0
        }

    fun lessonScore(avgSkill: Double, status: LessonStatus): Double {
        val skill = (avgSkill / 100.0).coerceIn(0.0, 1.0)
        return 0.5 * skill + 0.5 * statusWeight(status)
    }

    fun subjectPercent(lessonCount: Int, avgSkill: Double, statusCounts: Map<LessonStatus, Int>): Int {
        if (lessonCount == 0) return 0
        val skill = (avgSkill / 100.0).coerceIn(0.0, 1.0)
        val statusPart =
            statusCounts.entries.sumOf { (status, count) -> statusWeight(status) * count } /
                lessonCount
        return ((0.5 * skill + 0.5 * statusPart) * 100).toInt().coerceIn(0, 100)
    }
}

// ---- Focus configuration -----------------------------------------------------------------

data class FocusConfig(
    val presetId: String?,
    val focusMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val sessionsCount: Int,
    val longBreakInterval: Int,
    val autoStart: Boolean,
    val sound: Boolean,
    val vibration: Boolean,
    val subjectId: String? = null,
    val folderId: String? = null,
    val lessonId: String? = null,
    val goal: String = "",
) {
    val focusMs get() = focusMinutes * 60_000L
    val shortBreakMs get() = shortBreakMinutes * 60_000L
    val longBreakMs get() = longBreakMinutes * 60_000L
}

enum class FocusPhase {
    IDLE,
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK,
    FINISHED,
}

enum class FocusStatus {
    IDLE,
    RUNNING,
    PAUSED,
    FINISHED,
}

/** Immutable rendering snapshot of the engine. */
data class FocusSnapshot(
    val status: FocusStatus,
    val phase: FocusPhase,
    val round: Int,
    val totalRounds: Int,
    val phaseLengthMs: Long,
    val remainingMs: Long,
    val focusedMs: Long,
    val startedAtElapsed: Long,
)

/** Result delivered when a focus run finishes or is ended by the user. */
data class FocusResult(
    val config: FocusConfig,
    val focusedSeconds: Long,
    val completedRounds: Int,
    val startWallClock: Long,
    val endWallClock: Long,
)

// ---- V2 repository contract --------------------------------------------------------------

interface V2Repository {
    fun settings(): Flow<Map<String, String>>

    suspend fun setSetting(key: String, value: String)

    // Continue studying
    fun continueState(): Flow<ContinueState?>

    suspend fun recordOpen(
        subjectId: String?,
        folderId: String?,
        lessonId: String?,
        fileId: String? = null,
    )

    suspend fun recordPage(lessonId: String?, fileId: String?, page: Int?)

    // Reviews
    fun reviewsForLesson(lessonId: String): Flow<List<Review>>

    fun dueReviews(): Flow<List<Review>>

    fun reviewsForRange(fromDay: Int, toDay: Int): Flow<List<Review>>

    suspend fun review(id: String): Review?

    suspend fun saveReview(review: Review): String

    suspend fun completeReview(id: String, note: String?)

    suspend fun skipReview(id: String)

    suspend fun rescheduleReview(id: String, scheduledAt: Long, scheduledDay: Int)

    suspend fun deleteReview(id: String)

    suspend fun pendingReviewReminders(): List<Pair<String, Long>>

    // Sessions
    suspend fun saveSession(session: StudySession): String

    fun recentSessions(limit: Int): Flow<List<StudySession>>

    fun sessionsForLesson(lessonId: String): Flow<List<StudySession>>

    suspend fun secondsBetween(from: Long, to: Long): Long

    suspend fun dayBuckets(sinceDays: Int = 400): List<StreakRules.Bucket>

    fun observeDayBuckets(fromDay: Int, toDay: Int): Flow<List<DayMinutes>>

    fun observeStreakBuckets(): Flow<List<StreakRules.Bucket>>

    fun observeSubjectSeconds(from: Long, to: Long): Flow<List<SubjectSeconds>>

    // Tasks
    fun looseTasks(): Flow<List<Task>>

    fun planTasks(planId: String): Flow<List<Task>>

    fun activeTasksForRange(fromDay: Int, toDay: Int): Flow<List<Task>>

    fun activeTasksOnDay(day: Int): Flow<List<Task>>

    fun dueTasks(): Flow<List<Task>>

    suspend fun task(id: String): Task?

    suspend fun saveTask(task: Task): String

    suspend fun setTaskStatus(id: String, status: TaskStatus)

    suspend fun rescheduleTask(id: String, day: Int?, dueDay: Int?)

    suspend fun deleteTask(id: String)

    suspend fun pendingTaskReminders(): List<Pair<String, Long>>

    // Plans
    fun plans(): Flow<List<StudyPlan>>

    fun plansForRange(fromDay: Int, toDay: Int): Flow<List<StudyPlan>>

    suspend fun plan(id: String): StudyPlan?

    suspend fun savePlan(plan: StudyPlan): String

    suspend fun deletePlan(id: String)

    // Presets
    fun presets(): Flow<List<FocusPreset>>

    suspend fun ensureBuiltInPresets()

    suspend fun savePreset(preset: FocusPreset): String

    suspend fun deletePreset(id: String)

    // Analytics
    fun completedLessonsCount(): Flow<Int>

    fun pendingLessonsCount(): Flow<Int>

    fun completedReviewsCount(): Flow<Int>

    fun subjectProgress(): Flow<List<SubjectProgressInfo>>

    // Activity feed
    fun recentActivity(): Flow<List<ActivityItem>>

    // Calendar
    fun eventsForRange(fromDay: Int, toDay: Int): Flow<List<CalendarEvent>>

    fun eventsOnDay(day: Int): Flow<List<CalendarEvent>>

    // Lessons panel
    fun lessonStudy(lessonId: String): Flow<LessonStudyData>
}
