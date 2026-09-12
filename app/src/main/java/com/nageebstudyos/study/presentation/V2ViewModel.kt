package com.nageebstudyos.study.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nageebstudyos.study.AppContainer
import com.nageebstudyos.study.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class CalendarState(
    val gridStart: Int = 0,
    val gridEnd: Int = 0,
    val anchorDay: Int = 0,
    val selectedDay: Int = 0,
    val monthLabel: String = "",
    val events: List<CalendarEvent> = emptyList(),
    val selectedEvents: List<CalendarEvent> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class V2ViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.v2Repository
    private val actions = container.v2Actions
    private val scheduler = container.scheduler

    private val mutex = Mutex()
    val busy = MutableStateFlow(false)

    private val notices = Channel<Notice>(Channel.BUFFERED)
    val noticeFlow = notices.receiveAsFlow()

    private fun launchOp(block: suspend () -> Unit) =
        viewModelScope.launch {
            mutex.withLock {
                busy.value = true
                try {
                    block()
                    notices.send(Notice.Saved)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    notices.send(
                        Notice.Failure((e as? StudyException)?.problem ?: Problem.DATABASE)
                    )
                } finally {
                    busy.value = false
                }
            }
        }

    // ---- Settings -------------------------------------------------------------------------

    val settings: StateFlow<Map<String, String>> =
        repo.settings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val dailyGoalMinutes: StateFlow<Int> =
        settings
            .map { it["dailyGoalMinutes"]?.toIntOrNull()?.coerceIn(15, 480) ?: 60 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 60)

    fun setSetting(key: String, value: String) = launchOp { repo.setSetting(key, value) }

    fun setDailyGoal(minutes: Int) = launchOp {
        repo.setSetting("dailyGoalMinutes", minutes.toString())
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = launchOp {
        repo.setSetting("reminderEnabled", if (enabled) "1" else "0")
        repo.setSetting("reminderHour", hour.toString())
        repo.setSetting("reminderMinute", minute.toString())
        scheduler.setDailyReminder(enabled, hour, minute)
    }

    // ---- Dashboard ------------------------------------------------------------------------

    val dashboard: StateFlow<DashboardData> = run {
        val today = StudyTime.today()
        combine(
            listOf(
                repo.continueState().onStart { emit(null) },
                repo.dueReviews().onStart { emit(emptyList()) },
                repo.dueTasks().onStart { emit(emptyList()) },
                repo.observeDayBuckets(today, today).onStart { emit(emptyList()) },
                repo.observeDayBuckets(today - 6, today).onStart { emit(emptyList()) },
                repo.recentActivity().onStart { emit(emptyList()) },
                repo.observeStreakBuckets().onStart { emit(emptyList()) },
                dailyGoalMinutes,
            )
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            DashboardData(
                loading = false,
                continueState = values[0] as ContinueState?,
                reviewsDue = (values[1] as List<*>).size,
                tasksDue = (values[2] as List<*>).size,
                todayMinutes = ((values[3] as List<DayMinutes>).firstOrNull()?.minutes) ?: 0,
                todaySessions = (values[3] as List<DayMinutes>).firstOrNull()?.sessions ?: 0,
                week = (values[4] as List<DayMinutes>),
                activity = (values[5] as List<ActivityItem>),
                currentStreak = StreakRules.current(values[6] as List<StreakRules.Bucket>),
                bestStreak = StreakRules.best(values[6] as List<StreakRules.Bucket>),
                todayGoalMinutes = values[7] as Int,
            )
        }.catch { emit(DashboardData()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardData())
    }

    // ---- Analytics ------------------------------------------------------------------------

    private val range = MutableStateFlow(TimeRange.WEEK)
    val selectedRange: StateFlow<TimeRange> = range.asStateFlow()

    val analytics: StateFlow<AnalyticsData> =
        range
            .flatMapLatest { selected ->
                val (from, to) = StudyTime.range(selected)
                val (fromDay, toDay) = StudyTime.dayRange(selected)
                combine(
                    listOf(
                        repo.observeDayBuckets(fromDay, toDay).onStart { emit(emptyList()) },
                        repo.observeSubjectSeconds(from, to).onStart { emit(emptyList()) },
                        repo.completedLessonsCount().onStart { emit(0) },
                        repo.pendingLessonsCount().onStart { emit(0) },
                        repo.completedReviewsCount().onStart { emit(0) },
                        repo.observeStreakBuckets().onStart { emit(emptyList()) },
                        repo.subjectProgress().onStart { emit(emptyList()) },
                        flowOf(selected),
                    )
                ) { values ->
                    @Suppress("UNCHECKED_CAST")
                    val series = values[0] as List<DayMinutes>
                    val distribution = values[1] as List<SubjectSeconds>
                    AnalyticsData(
                        range = selected,
                        totalMinutes = (distribution.sumOf { it.seconds } / 60).toInt(),
                        sessions = distribution.sumOf { it.sessions },
                        completedLessons = values[2] as Int,
                        pendingLessons = values[3] as Int,
                        reviewsCompleted = values[4] as Int,
                        currentStreak = StreakRules.current(values[5] as List<StreakRules.Bucket>),
                        bestStreak = StreakRules.best(values[5] as List<StreakRules.Bucket>),
                        series = series,
                        distribution = distribution,
                        subjectProgress = values[6] as List<SubjectProgressInfo>,
                    )
                }
            }
            .catch { emit(AnalyticsData()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsData())

    fun selectRange(value: TimeRange) {
        range.value = value
    }

    // ---- Calendar -------------------------------------------------------------------------

    private val monthAnchor = MutableStateFlow(StudyTime.today())
    private val selectedDay = MutableStateFlow(StudyTime.today())

    @OptIn(ExperimentalCoroutinesApi::class)
    val calendar: StateFlow<CalendarState> =
        combine(monthAnchor, selectedDay) { anchor, selected -> anchor to selected }
            .flatMapLatest { (anchor, selected) ->
                val anchorDate = StudyTime.localDate(anchor)
                val firstOfMonth = anchorDate.withDayOfMonth(1)
                // Weeks start on Saturday (common academic calendar in the region).
                val firstDow = firstOfMonth.dayOfWeek.value // Mon=1..Sun=7
                val saturdayOffset = (firstDow + 1) % 7 // days back to Saturday
                val gridStart = firstOfMonth.minusDays(saturdayOffset.toLong())
                val gridEnd = gridStart.plusDays(41)
                val label =
                    java.text.DateFormat.getDateInstance(
                        java.text.DateFormat.LONG,
                        java.util.Locale("ar"),
                    ).format(
                        java.util.Date(
                            StudyTime.at(
                                anchorDate.withDayOfMonth(15).toEpochDay().toInt(),
                                12,
                                0,
                            )
                        )
                    )
                combine(
                    repo.eventsForRange(gridStart.toEpochDay().toInt(), gridEnd.toEpochDay().toInt()),
                    repo.eventsOnDay(selected),
                ) { events, dayEvents ->
                    CalendarState(
                        gridStart = gridStart.toEpochDay().toInt(),
                        gridEnd = gridEnd.toEpochDay().toInt(),
                        anchorDay = anchor,
                        selectedDay = selected,
                        monthLabel = label,
                        events = events,
                        selectedEvents = dayEvents,
                        loading = false,
                    )
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                CalendarState(loading = true),
            )

    fun shiftMonth(delta: Long) {
        monthAnchor.update { StudyTime.localDate(it).plusMonths(delta).toEpochDay().toInt() }
    }

    fun selectDay(day: Int) {
        selectedDay.value = day
    }

    // Subjects snapshot used by planner/task dialogs.
    val subjects: StateFlow<List<Entry>> =
        flow {
            emit(container.repository.library(Scope()).first().rows)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Planner --------------------------------------------------------------------------

    val plans: StateFlow<List<StudyPlan>> =
        repo.plans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val looseTasks: StateFlow<List<Task>> =
        repo.looseTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun planTasks(planId: String) = repo.planTasks(planId)

    fun savePlan(plan: StudyPlan) = launchOp { actions.savePlan(plan) }

    fun deletePlan(plan: StudyPlan) = launchOp {
        repo.planTasks(plan.id).first().forEach { scheduler.cancelTask(it.id) }
        actions.deletePlan(plan.id)
    }

    fun saveTask(task: Task) = launchOp {
        val id = actions.saveTask(task)
        if (task.status != TaskStatus.DONE) scheduler.scheduleTask(id, task.dueDay)
        else scheduler.cancelTask(id)
    }

    fun setTaskStatus(task: Task, status: TaskStatus) = launchOp {
        repo.setTaskStatus(task.id, status)
        if (status == TaskStatus.DONE) scheduler.cancelTask(task.id)
        else scheduler.scheduleTask(task.id, task.dueDay)
    }

    fun cycleTask(task: Task) = launchOp {
        val next =
            when (task.status) {
                TaskStatus.PENDING -> TaskStatus.IN_PROGRESS
                TaskStatus.IN_PROGRESS -> TaskStatus.DONE
                TaskStatus.DONE -> TaskStatus.PENDING
            }
        repo.setTaskStatus(task.id, next)
        if (next == TaskStatus.DONE) scheduler.cancelTask(task.id)
        else scheduler.scheduleTask(task.id, task.dueDay)
    }

    fun setTaskStatusById(id: String, status: TaskStatus) = launchOp {
        repo.setTaskStatus(id, status)
        if (status == TaskStatus.DONE) scheduler.cancelTask(id)
        else repo.task(id)?.dueDay?.let { scheduler.scheduleTask(id, it) }
    }

    fun completeReviewById(id: String) = launchOp {
        val review = repo.review(id) ?: return@launchOp
        actions.completeReview(id)
        scheduler.cancelReview(id)
    }

    fun rescheduleTask(task: Task, day: Int?, dueDay: Int?) = launchOp {
        repo.rescheduleTask(task.id, day, dueDay)
        scheduler.scheduleTask(task.id, dueDay)
    }

    fun deleteTask(task: Task) = launchOp {
        scheduler.cancelTask(task.id)
        repo.deleteTask(task.id)
    }

    // ---- Reviews --------------------------------------------------------------------------

    fun addReview(lessonId: String, subjectId: String, at: Long, note: String = "") = launchOp {
        val id = actions.addReview(lessonId, subjectId, at, note)
        scheduler.scheduleReview(id, at)
    }

    fun saveReview(review: Review, at: Long) = launchOp {
        val rescheduled =
            review.copy(scheduledAt = at, scheduledDay = StudyTime.dayOf(at),
                status = ReviewStatus.PENDING, completedAt = null)
        val id = actions.saveReview(rescheduled)
        scheduler.scheduleReview(id, at)
    }

    fun completeReview(review: Review, note: String? = null) = launchOp {
        actions.completeReview(review.id, note)
        scheduler.cancelReview(review.id)
    }

    fun skipReview(review: Review) = launchOp {
        actions.skipReview(review.id)
        scheduler.cancelReview(review.id)
    }

    fun rescheduleReview(review: Review, at: Long) = launchOp {
        actions.rescheduleReview(review.id, at)
        scheduler.scheduleReview(review.id, at)
    }

    fun deleteReview(review: Review) = launchOp {
        scheduler.cancelReview(review.id)
        actions.deleteReview(review.id)
    }

    // ---- Presets --------------------------------------------------------------------------

    val presets: StateFlow<List<FocusPreset>> =
        repo.presets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun savePreset(preset: FocusPreset) = launchOp { actions.savePreset(preset) }

    fun deletePreset(preset: FocusPreset) = launchOp { actions.deletePreset(preset.id) }

    // ---- Continue / lesson panel ----------------------------------------------------------

    fun recordOpen(entry: Entry) = launchOp {
        when (entry.kind) {
            Kind.SUBJECT -> repo.recordOpen(entry.id, null, null)
            Kind.FOLDER ->
                repo.recordOpen(entry.subjectId ?: entry.id, entry.id, null)
            Kind.LESSON ->
                repo.recordOpen(entry.subjectId, entry.parentId, entry.id)
            Kind.FILE -> {
                val file = container.repository.get(Kind.FILE, entry.id)
                val lesson = file?.ownerId?.let { container.repository.get(Kind.LESSON, it) }
                repo.recordOpen(
                    lesson?.subjectId,
                    lesson?.parentId,
                    lesson?.id,
                    entry.id,
                )
            }
            else -> Unit
        }
    }

    fun recordPage(lessonId: String?, fileId: String?, page: Int?) = launchOp {
        repo.recordPage(lessonId, fileId, page)
    }

    private val lessonPanelId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val lessonPanel: StateFlow<LessonStudyData?> =
        lessonPanelId
            .flatMapLatest { id -> if (id == null) flowOf(null) else repo.lessonStudy(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectLessonPanel(lessonId: String?) {
        lessonPanelId.value = lessonId
    }
}
