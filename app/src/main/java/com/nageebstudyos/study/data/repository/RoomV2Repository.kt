package com.nageebstudyos.study.data.repository

import androidx.room.withTransaction
import com.nageebstudyos.study.data.local.*
import com.nageebstudyos.study.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private class TitleCatalog(
    val subjects: Map<String, String>,
    val lessons: Map<String, LessonRefRow>,
    val folders: Map<String, FolderRefRow>,
    val files: Map<String, FileRefRow>,
)

class RoomV2Repository(private val db: StudyDatabase) : V2Repository {
    private val v2 = db.v2Dao()
    private val legacy = db.dao()

    private suspend fun catalog(): TitleCatalog {
        val lessons = v2.lessonRefs().associateBy { it.id }
        return TitleCatalog(
            subjects = v2.subjectTitles().associate { it.id to it.title },
            lessons = lessons,
            folders = v2.folderRefs().associateBy { it.id },
            files = v2.fileRefs().associateBy { it.id },
        )
    }

    // ---- Settings -------------------------------------------------------------------------

    override fun settings(): Flow<Map<String, String>> =
        v2.settings().map { list -> list.associate { it.key to it.value } }

    override suspend fun setSetting(key: String, value: String) {
        v2.put(AppSetting(key, value, System.currentTimeMillis()))
    }

    // ---- Continue studying ----------------------------------------------------------------

    override fun continueState(): Flow<ContinueState?> =
        v2.lastActive().map { row ->
            if (row == null || row.lessonId == null && row.subjectId == null && row.fileId == null)
                null
            else {
                val c = catalog()
                ContinueState(
                    subjectId = row.subjectId,
                    folderId = row.folderId,
                    lessonId = row.lessonId,
                    fileId = row.fileId,
                    page = row.page,
                    updatedAt = row.updatedAt,
                    subjectTitle = row.subjectId?.let { c.subjects[it] }.orEmpty(),
                    folderTitle = row.folderId?.let { c.folders[it]?.title }.orEmpty(),
                    lessonTitle = row.lessonId?.let { c.lessons[it]?.title }.orEmpty(),
                    fileTitle = row.fileId?.let { c.files[it]?.title }.orEmpty(),
                )
            }
        }

    override suspend fun recordOpen(
        subjectId: String?,
        folderId: String?,
        lessonId: String?,
        fileId: String?,
    ) {
        val existing = v2.lastActive().first()
        v2.put(
            LastActiveEntity(
                id = 0,
                subjectId = subjectId ?: existing?.subjectId,
                folderId = folderId ?: existing?.folderId,
                lessonId = lessonId ?: existing?.lessonId,
                fileId = fileId ?: existing?.fileId,
                page = existing?.page,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun recordPage(lessonId: String?, fileId: String?, page: Int?) {
        val existing = v2.lastActive().first()
        v2.put(
            (existing ?: LastActiveEntity(
                id = 0,
                subjectId = null,
                folderId = null,
                lessonId = lessonId,
                fileId = fileId,
                page = page,
                updatedAt = System.currentTimeMillis(),
            )).copy(
                lessonId = lessonId ?: existing?.lessonId,
                fileId = fileId ?: existing?.fileId,
                page = page,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    // ---- Reviews --------------------------------------------------------------------------

    override fun reviewsForLesson(lessonId: String): Flow<List<Review>> =
        v2.reviewsForLesson(lessonId).map { rows ->
            val c = catalog()
            rows.map {
                it.toDomain(
                    c.lessons[it.lessonId]?.title.orEmpty(),
                    c.subjects[it.subjectId] ?: c.lessons[it.lessonId]?.let { l ->
                        c.subjects[l.subjectId]
                    }.orEmpty(),
                )
            }
        }

    override fun dueReviews(): Flow<List<Review>> =
        v2.dueReviews(StudyTime.today()).map { rows ->
            val c = catalog()
            rows.map { it.toDomain(c.lessons[it.lessonId]?.title.orEmpty(), c.subjects[it.subjectId].orEmpty()) }
        }

    override fun reviewsForRange(fromDay: Int, toDay: Int): Flow<List<Review>> =
        v2.reviewsForRange(fromDay, toDay).map { rows ->
            val c = catalog()
            rows.map {
                it.toDomain(
                    c.lessons[it.lessonId]?.title.orEmpty(),
                    c.subjects[it.subjectId].orEmpty(),
                )
            }
        }

    override suspend fun review(id: String): Review? {
        val e = v2.review(id) ?: return null
        val c = catalog()
        return e.toDomain(
            c.lessons[e.lessonId]?.title.orEmpty(),
            c.subjects[e.subjectId].orEmpty(),
        )
    }

    override suspend fun saveReview(review: Review): String =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val old = v2.review(review.id)
            val subject =
                review.subjectId.ifBlank { legacy.get(Kind.LESSON, review.lessonId)?.subjectId.orEmpty() }
            v2.put(
                ReviewEntity(
                    id = review.id,
                    lessonId = review.lessonId,
                    subjectId = subject,
                    scheduledAt = review.scheduledAt,
                    scheduledDay =
                        review.scheduledDay
                            .takeIf { it != 0 } ?: StudyTime.dayOf(review.scheduledAt),
                    completedAt = review.completedAt,
                    status = review.status.name,
                    note = review.note,
                    createdAt = old?.createdAt ?: review.createdAt.takeIf { it > 0 } ?: now,
                    updatedAt = now,
                )
            )
            legacy.get(Kind.LESSON, review.lessonId)?.let {
                legacy.touch(Kind.LESSON, it.id)
                legacy.touch(Kind.SUBJECT, it.subjectId)
            }
            review.id
        }

    override suspend fun completeReview(id: String, note: String?) {
        val old = v2.review(id) ?: return
        val now = System.currentTimeMillis()
        v2.updateReviewStatus(
            id,
            com.nageebstudyos.study.domain.ReviewStatus.DONE.name,
            now,
            note ?: old.note,
            now,
        )
    }

    override suspend fun skipReview(id: String) {
        val old = v2.review(id) ?: return
        val now = System.currentTimeMillis()
        v2.updateReviewStatus(
            id,
            com.nageebstudyos.study.domain.ReviewStatus.SKIPPED.name,
            now,
            old.note,
            now,
        )
    }

    override suspend fun rescheduleReview(id: String, scheduledAt: Long, scheduledDay: Int) {
        v2.rescheduleReview(id, scheduledAt, scheduledDay, System.currentTimeMillis())
    }

    override suspend fun deleteReview(id: String) {
        v2.deleteReview(id)
    }

    override suspend fun pendingReviewReminders(): List<Pair<String, Long>> =
        v2.pendingReviews().map { it.id to it.scheduledAt }

    // ---- Sessions -------------------------------------------------------------------------

    private suspend fun mapSession(e: StudySessionEntity, c: TitleCatalog): StudySession {
        val lesson = e.lessonId?.let { c.lessons[it] }
        return e.toDomain(
            subjectTitle = e.subjectId?.let { c.subjects[it] }.orEmpty(),
            folderTitle = e.folderId?.let { c.folders[it]?.title }.orEmpty(),
            lessonTitle = lesson?.title.orEmpty(),
        )
    }

    override suspend fun saveSession(session: StudySession): String =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val old = v2.session(session.id)
            v2.put(
                StudySessionEntity(
                    id = session.id,
                    subjectId = session.subjectId,
                    folderId = session.folderId,
                    lessonId = session.lessonId,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    duration = session.durationSeconds,
                    goal = session.goal,
                    result = session.result?.name,
                    notes = session.notes,
                    lastPage = session.lastPage,
                    dayIndex =
                        session.dayIndex.takeIf { it != 0 }
                            ?: StudyTime.dayOf(session.startTime),
                    createdAt = old?.createdAt ?: session.createdAt.takeIf { it > 0 } ?: now,
                )
            )
            if (session.lessonId != null) {
                legacy.get(Kind.LESSON, session.lessonId)?.let {
                    legacy.touch(Kind.LESSON, it.id)
                    legacy.touch(Kind.SUBJECT, it.subjectId)
                }
            } else if (session.subjectId != null) {
                legacy.touch(Kind.SUBJECT, session.subjectId)
            }
            session.id
        }

    override fun recentSessions(limit: Int): Flow<List<StudySession>> =
        v2.recentSessions(limit).map { rows ->
            val c = catalog()
            rows.map { mapSession(it, c) }
        }

    override fun sessionsForLesson(lessonId: String): Flow<List<StudySession>> =
        v2.sessionsForLesson(lessonId).map { rows ->
            val c = catalog()
            rows.map { mapSession(it, c) }
        }

    override suspend fun secondsBetween(from: Long, to: Long): Long =
        v2.secondsBetween(from, to)

    override suspend fun dayBuckets(sinceDays: Int): List<StreakRules.Bucket> =
        v2.dayBucketsSince(StudyTime.today() - sinceDays).map {
            StreakRules.Bucket(it.dayIndex, it.seconds, it.positive)
        }

    override fun observeDayBuckets(fromDay: Int, toDay: Int): Flow<List<DayMinutes>> =
        v2.observeDayBuckets(fromDay, toDay).map { rows ->
            val byDay = rows.associateBy { it.dayIndex }
            (fromDay..toDay).map { day ->
                val r = byDay[day]
                DayMinutes(day, ((r?.seconds ?: 0) / 60).toInt(), r?.sessions ?: 0)
            }
        }

    override fun observeStreakBuckets(): Flow<List<StreakRules.Bucket>> =
        v2.observeDayBuckets(StudyTime.today() - 399, StudyTime.today()).map { rows ->
            rows.map { StreakRules.Bucket(it.dayIndex, it.seconds, it.positive) }
        }

    override fun observeSubjectSeconds(from: Long, to: Long): Flow<List<SubjectSeconds>> =
        v2.observeSubjectSeconds(from, to).map { rows ->
            val c = catalog()
            rows.map { r ->
                SubjectSeconds(
                    subjectId = r.subjectId,
                    title = r.subjectId?.let { c.subjects[it] } ?: "بدون مادة",
                    accent = r.subjectId?.let { id -> legacy.get(Kind.SUBJECT, id)?.accent } ?: 0,
                    seconds = r.seconds,
                    sessions = r.sessions,
                )
            }.sortedByDescending { it.seconds }
        }

    // ---- Tasks ----------------------------------------------------------------------------

    private suspend fun mapTask(e: TaskEntity, c: TitleCatalog): Task =
        e.toDomain(
            subjectTitle = e.subjectId?.let { c.subjects[it] }.orEmpty(),
            lessonTitle = e.lessonId?.let { id -> c.lessons[id]?.title }.orEmpty(),
            planTitle = e.planId?.let { id -> v2.plan(id)?.title }.orEmpty(),
        )

    override fun looseTasks(): Flow<List<Task>> =
        v2.looseTasks().map { rows ->
            val c = catalog()
            rows.map { mapTask(it, c) }
        }

    override fun planTasks(planId: String): Flow<List<Task>> =
        v2.planTasks(planId).map { rows ->
            val c = catalog()
            rows.map { mapTask(it, c) }
        }

    override fun activeTasksForRange(fromDay: Int, toDay: Int): Flow<List<Task>> =
        v2.activeTasksForRange(fromDay, toDay).map { rows ->
            val c = catalog()
            rows.map { mapTask(it, c) }
        }

    override fun activeTasksOnDay(day: Int): Flow<List<Task>> =
        v2.activeTasksOnDay(day).map { rows ->
            val c = catalog()
            rows.map { mapTask(it, c) }
        }

    override fun dueTasks(): Flow<List<Task>> =
        v2.dueTasks(StudyTime.today()).map { rows ->
            val c = catalog()
            rows.map { mapTask(it, c) }
        }

    override suspend fun task(id: String): Task? {
        val c = catalog()
        return v2.task(id)?.let { mapTask(it, c) }
    }

    override suspend fun saveTask(task: Task): String =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val old = v2.task(task.id)
            v2.put(
                TaskEntity(
                    id = task.id,
                    planId = task.planId,
                    title = task.title,
                    description = task.description,
                    day = task.day,
                    dueDay = task.dueDay,
                    subjectId = task.subjectId,
                    lessonId = task.lessonId,
                    status = task.status.name,
                    priority = task.priority.name,
                    position = old?.position ?: task.position.takeIf { it >= 0 }
                        ?: v2.nextTaskPosition(task.planId),
                    completedAt =
                        if (task.status == TaskStatus.DONE) old?.completedAt ?: now else null,
                    createdAt = old?.createdAt ?: task.createdAt.takeIf { it > 0 } ?: now,
                    updatedAt = now,
                )
            )
            task.id
        }

    override suspend fun setTaskStatus(id: String, status: TaskStatus) {
        val now = System.currentTimeMillis()
        v2.updateTaskStatus(
            id,
            status.name,
            if (status == TaskStatus.DONE) now else null,
            now,
        )
    }

    override suspend fun rescheduleTask(id: String, day: Int?, dueDay: Int?) {
        v2.rescheduleTask(id, day, dueDay, System.currentTimeMillis())
    }

    override suspend fun deleteTask(id: String) {
        v2.deleteTask(id)
    }

    override suspend fun pendingTaskReminders(): List<Pair<String, Long>> =
        v2.pendingTasks().mapNotNull { t ->
            t.dueDay?.let { t.id to StudyTime.at(it, 9, 0) }
        }

    // ---- Plans ----------------------------------------------------------------------------

    override fun plans(): Flow<List<StudyPlan>> =
        combine(v2.plans(), v2.planCounts()) { rows, counts ->
            val c = catalog()
            val byPlan = counts.associateBy { it.planId }
            rows.map { e ->
                val count = byPlan[e.id]
                e.toDomain(
                    subjectTitle = e.subjectId?.let { c.subjects[it] }.orEmpty(),
                    total = count?.total ?: 0,
                    done = count?.done ?: 0,
                )
            }
        }

    override fun plansForRange(fromDay: Int, toDay: Int): Flow<List<StudyPlan>> =
        combine(v2.plansForRange(fromDay, toDay), v2.planCounts()) { rows, counts ->
            val c = catalog()
            val byPlan = counts.associateBy { it.planId }
            rows.map { e ->
                val count = byPlan[e.id]
                e.toDomain(
                    e.subjectId?.let { c.subjects[it] }.orEmpty(),
                    count?.total ?: 0,
                    count?.done ?: 0,
                )
            }
        }

    override suspend fun plan(id: String): StudyPlan? {
        val c = catalog()
        val e = v2.plan(id) ?: return null
        return e.toDomain(e.subjectId?.let { c.subjects[it] }.orEmpty(), 0, 0)
    }

    override suspend fun savePlan(plan: StudyPlan): String =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val old = v2.plan(plan.id)
            v2.put(
                PlanEntity(
                    id = plan.id,
                    title = plan.title,
                    description = plan.description,
                    subjectId = plan.subjectId,
                    startDay = plan.startDay,
                    endDay = plan.endDay,
                    createdAt = old?.createdAt ?: plan.createdAt.takeIf { it > 0 } ?: now,
                    updatedAt = now,
                )
            )
            plan.id
        }

    override suspend fun deletePlan(id: String) {
        v2.deletePlan(id)
    }

    // ---- Presets --------------------------------------------------------------------------

    override fun presets(): Flow<List<FocusPreset>> =
        v2.presets().map { rows -> rows.map { it.toDomain() } }

    override suspend fun ensureBuiltInPresets() {
        val existing = v2.presetList().associateBy { it.id }.toMutableMap()
        val now = System.currentTimeMillis()
        V2Actions.builtInPresets.forEach { preset ->
            if (preset.id !in existing) {
                v2.put(
                    FocusPresetEntity(
                        id = preset.id,
                        title = preset.title,
                        focusMinutes = preset.focusMinutes,
                        shortBreakMinutes = preset.shortBreakMinutes,
                        longBreakMinutes = preset.longBreakMinutes,
                        sessionsCount = preset.sessionsCount,
                        longBreakInterval = preset.longBreakInterval,
                        autoStart = preset.autoStart,
                        sound = preset.sound,
                        vibration = preset.vibration,
                        builtIn = true,
                        position = preset.position,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
    }

    override suspend fun savePreset(preset: FocusPreset): String {
        val now = System.currentTimeMillis()
        val existing = v2.preset(preset.id)
        val position =
            existing?.position
                ?: preset.position.takeIf { it >= 0 }
                ?: (v2.presetList().maxOfOrNull { it.position } ?: -1) + 1
        val created = existing?.createdAt ?: now
        v2.put(preset.copy(position = position).toEntity(now, created))
        return preset.id
    }

    override suspend fun deletePreset(id: String) {
        v2.deleteCustomPreset(id)
    }

    // ---- Analytics ------------------------------------------------------------------------

    override fun completedLessonsCount(): Flow<Int> = v2.masteredLessonsCount()

    override fun pendingLessonsCount(): Flow<Int> = v2.pendingLessonsCount()

    override fun completedReviewsCount(): Flow<Int> = v2.completedReviewsCount()

    override fun subjectProgress(): Flow<List<SubjectProgressInfo>> =
        v2.subjectProgress().map { rows ->
            rows.map { r ->
                val counts =
                    mapOf(
                        LessonStatus.NOT_STARTED to r.notStarted,
                        LessonStatus.IN_PROGRESS to r.inProgress,
                        LessonStatus.NEEDS_REVIEW to r.needsReview,
                        LessonStatus.MASTERED to r.mastered,
                        LessonStatus.WEAK to r.weak,
                    )
                SubjectProgressInfo(
                    subjectId = r.subjectId,
                    title = r.title,
                    accent = r.accent,
                    percent = ProgressRules.subjectPercent(r.lessonCount, r.avgSkill, counts),
                    lessonCount = r.lessonCount,
                    notStarted = r.notStarted,
                    inProgress = r.inProgress,
                    needsReview = r.needsReview,
                    mastered = r.mastered,
                    weak = r.weak,
                )
            }
        }

    // ---- Activity -------------------------------------------------------------------------

    override fun recentActivity(): Flow<List<ActivityItem>> =
        combine(
            v2.recentSessions(8),
            v2.recentDoneReviews(6),
            v2.recentDoneTasks(6),
        ) { sessions, reviews, tasks ->
            val c = catalog()
            buildList {
                sessions.forEach { s ->
                    val lesson = s.lessonId?.let { c.lessons[it] }
                    add(
                        ActivityItem(
                            id = "s" + s.id,
                            title = (lesson?.title
                                ?: s.subjectId?.let { c.subjects[it] } ?: "جلسة تركيز"),
                            subtitle = "${s.duration / 60} دقيقة",
                            kind = Kind.LESSON,
                            time = s.startTime,
                            targetId = s.lessonId,
                            targetSubjectId = s.subjectId ?: lesson?.subjectId,
                            targetKind = if (s.lessonId != null) Kind.LESSON else Kind.SUBJECT,
                        )
                    )
                }
                reviews.forEach { r ->
                    add(
                        ActivityItem(
                            id = "r" + r.id,
                            title = c.lessons[r.lessonId]?.title ?: "مراجعة",
                            subtitle = "مراجعة مكتملة",
                            kind = Kind.NOTE,
                            time = r.completedAt ?: r.updatedAt,
                            targetId = r.lessonId,
                            targetSubjectId = c.lessons[r.lessonId]?.subjectId,
                            targetKind = Kind.LESSON,
                        )
                    )
                }
                tasks.forEach { t ->
                    add(
                        ActivityItem(
                            id = "t" + t.id,
                            title = t.title,
                            subtitle = "مهمة مكتملة",
                            kind = Kind.TAG,
                            time = t.completedAt ?: t.updatedAt,
                            targetId = t.lessonId,
                            targetSubjectId = t.subjectId ?: t.lessonId?.let {
                                c.lessons[it]?.subjectId
                            },
                            targetKind = if (t.lessonId != null) Kind.LESSON else Kind.SUBJECT,
                        )
                    )
                }
            }.sortedByDescending { it.time }.take(10)
        }

    // ---- Calendar -------------------------------------------------------------------------

    override fun eventsForRange(fromDay: Int, toDay: Int): Flow<List<CalendarEvent>> =
        combine(
            v2.reviewsForRange(fromDay, toDay),
            v2.activeTasksForRange(fromDay, toDay),
            v2.plansForRange(fromDay, toDay),
            v2.observeDayBuckets(fromDay, toDay),
        ) { reviews, tasks, plans, buckets ->
            val c = catalog()
            buildList {
                reviews
                    .filter { it.status != com.nageebstudyos.study.domain.ReviewStatus.SKIPPED }
                    .forEach {
                        add(
                            CalendarEvent(
                                id = it.id,
                                title = c.lessons[it.lessonId]?.title ?: "مراجعة",
                                subtitle =
                                    if (it.status == com.nageebstudyos.study.domain.ReviewStatus.DONE)
                                        "مراجعة مكتملة"
                                    else "مراجعة مجدولة",
                                day = it.scheduledDay,
                                kind = CalendarEventKind.REVIEW,
                                done = it.status == com.nageebstudyos.study.domain.ReviewStatus.DONE,
                                targetLessonId = it.lessonId,
                                targetSubjectId = it.subjectId,
                            )
                        )
                    }
                tasks.forEach {
                    add(
                        CalendarEvent(
                            id = it.id,
                            title = it.title,
                            subtitle =
                                when (it.status) {
                                    TaskStatus.IN_PROGRESS -> "قيد التنفيذ"
                                    else -> "مهمة"
                                },
                            day = it.dueDay ?: it.day ?: return@forEach,
                            kind = CalendarEventKind.TASK,
                            targetLessonId = it.lessonId,
                            targetSubjectId = it.subjectId,
                        )
                    )
                }
                plans.forEach { p ->
                    // A plan lights up every day it spans, not just its start date.
                    val first = maxOf(p.startDay, fromDay)
                    val last = minOf(p.endDay, toDay)
                    for (d in first..last) {
                        add(
                            CalendarEvent(
                                id = "${p.id}-$d",
                                title = p.title,
                                subtitle = "خطة دراسية",
                                day = d,
                                kind = CalendarEventKind.PLAN,
                                targetSubjectId = p.subjectId,
                            )
                        )
                    }
                }
                buckets.filter { it.minutes > 0 }.forEach { b ->
                    add(
                        CalendarEvent(
                            id = "day-${b.day}",
                            title = "${b.minutes} دقيقة تركيز",
                            subtitle = "${b.sessions} جلسة",
                            day = b.day,
                            kind = CalendarEventKind.SESSION,
                            done = true,
                        )
                    )
                }
            }
        }

    override fun eventsOnDay(day: Int): Flow<List<CalendarEvent>> =
        combine(
            v2.reviewsOnDay(day),
            v2.activeTasksOnDay(day),
            v2.sessionsOnDay(day),
            v2.plansOnDay(day),
        ) { reviews, tasks, sessions, plans ->
            val c = catalog()
            buildList {
                reviews
                    .filter { it.status != com.nageebstudyos.study.domain.ReviewStatus.SKIPPED }
                    .forEach {
                        add(
                            CalendarEvent(
                                id = it.id,
                                title = c.lessons[it.lessonId]?.title ?: "مراجعة",
                                subtitle =
                                    when (it.status) {
                                        com.nageebstudyos.study.domain.ReviewStatus.DONE ->
                                            "مراجعة مكتملة"
                                        else -> "مراجعة مجدولة"
                                    },
                                day = day,
                                kind = CalendarEventKind.REVIEW,
                                done = it.status == com.nageebstudyos.study.domain.ReviewStatus.DONE,
                                targetLessonId = it.lessonId,
                                targetSubjectId = it.subjectId,
                            )
                        )
                    }
                tasks.forEach {
                    add(
                        CalendarEvent(
                            id = it.id,
                            title = it.title,
                            subtitle = listOfNotNull(
                                    it.subjectId?.let { s -> c.subjects[s] },
                                    "مهمة",
                                )
                                .joinToString(" · "),
                            day = day,
                            kind = CalendarEventKind.TASK,
                            done = it.status == TaskStatus.DONE,
                            targetLessonId = it.lessonId,
                            targetSubjectId = it.subjectId,
                        )
                    )
                }
                sessions.forEach { s ->
                    add(
                        CalendarEvent(
                            id = s.id,
                            title =
                                (s.lessonId?.let { c.lessons[it]?.title }
                                    ?: s.subjectId?.let { c.subjects[it] }
                                    ?: "جلسة تركيز"),
                            subtitle = "${s.duration / 60} دقيقة",
                            day = day,
                            kind = CalendarEventKind.SESSION,
                            done = true,
                            targetLessonId = s.lessonId,
                            targetSubjectId = s.subjectId,
                        )
                    )
                }
                plans.forEach { p ->
                    add(
                        CalendarEvent(
                            id = p.id,
                            title = p.title,
                            subtitle = "خطة دراسية",
                            day = day,
                            kind = CalendarEventKind.PLAN,
                            targetSubjectId = p.subjectId,
                        )
                    )
                }
            }
        }

    // ---- Lesson panel ---------------------------------------------------------------------

    override fun lessonStudy(lessonId: String): Flow<LessonStudyData> =
        combine(
            reviewsForLesson(lessonId),
            sessionsForLesson(lessonId),
            v2.tasksForLesson(lessonId).map { rows ->
                val c = catalog()
                rows.map { mapTask(it, c) }
            },
        ) { reviews, sessions, tasks ->
            LessonStudyData(reviews, sessions, tasks)
        }
}
