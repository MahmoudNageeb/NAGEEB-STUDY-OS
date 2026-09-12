package com.nageebstudyos.study.domain

/**
 * V2 use cases. Mirrors V1's [StudyActions]: validate intent, apply domain policy, then
 * delegate to [V2Repository]. No Android framework types are referenced so the rules stay
 * unit-testable on the JVM.
 */
class V2Actions(private val repository: V2Repository) {

    // ---- Reviews --------------------------------------------------------------------------

    suspend fun addReview(
        lessonId: String,
        subjectId: String,
        scheduledAt: Long,
        note: String = "",
    ): String {
        require(scheduledAt > 0)
        return repository.saveReview(
            Review(
                id = newId(),
                lessonId = lessonId,
                subjectId = subjectId,
                scheduledAt = scheduledAt,
                scheduledDay = StudyTime.dayOf(scheduledAt),
                completedAt = null,
                status = ReviewStatus.PENDING,
                note = note.trim(),
            )
        )
    }

    suspend fun saveReview(review: Review): String {
        require(review.scheduledAt > 0)
        return repository.saveReview(review)
    }

    suspend fun completeReview(id: String, note: String? = null) =
        repository.completeReview(id, note?.trim()?.ifBlank { null })

    suspend fun skipReview(id: String) = repository.skipReview(id)

    suspend fun rescheduleReview(id: String, scheduledAt: Long) {
        require(scheduledAt > 0)
        repository.rescheduleReview(id, scheduledAt, StudyTime.dayOf(scheduledAt))
    }

    suspend fun deleteReview(id: String) = repository.deleteReview(id)

    /** Default next-review suggestion: two local days from [day] at 09:00. */
    fun suggestReviewAt(day: Int = StudyTime.today(), plusDays: Long = 2): Long =
        StudyTime.at(day + plusDays.toInt(), 9, 0)

    // ---- Sessions -------------------------------------------------------------------------

    suspend fun saveSession(session: StudySession): String {
        if (session.endTime < session.startTime || session.durationSeconds < 0)
            throw StudyException(Problem.INVALID_DURATION)
        return repository.saveSession(session)
    }

    /** Builds a session from a finished focus run plus the user's post-session answers. */
    fun sessionFromResult(
        result: FocusResult,
        goal: String,
        outcome: SessionResult?,
        notes: String,
        page: Int?,
    ): StudySession {
        val c = result.config
        val start = result.startWallClock
        val end = result.endWallClock
        return StudySession(
            id = newId(),
            subjectId = c.subjectId,
            folderId = c.folderId,
            lessonId = c.lessonId,
            startTime = start,
            endTime = end,
            durationSeconds = result.focusedSeconds,
            goal = goal.ifBlank { c.goal },
            result = outcome,
            notes = notes.trim(),
            lastPage = page?.takeIf { it > 0 },
            dayIndex = StudyTime.dayOf(start),
            createdAt = System.currentTimeMillis(),
        )
    }

    // ---- Tasks ----------------------------------------------------------------------------

    suspend fun saveTask(task: Task): String {
        val title = StudyRules.title(task.title)
        if (task.day != null && task.dueDay != null && task.dueDay < task.day)
            throw StudyException(Problem.INVALID_DATE)
        return repository.saveTask(task.copy(title = title))
    }

    suspend fun cycleTaskStatus(task: Task) {
        val next =
            when (task.status) {
                TaskStatus.PENDING -> TaskStatus.IN_PROGRESS
                TaskStatus.IN_PROGRESS -> TaskStatus.DONE
                TaskStatus.DONE -> TaskStatus.PENDING
            }
        repository.setTaskStatus(task.id, next)
    }

    suspend fun completeTask(id: String) = repository.setTaskStatus(id, TaskStatus.DONE)

    suspend fun rescheduleTask(id: String, day: Int?, dueDay: Int?) =
        repository.rescheduleTask(id, day, dueDay)

    suspend fun deleteTask(id: String) = repository.deleteTask(id)

    // ---- Plans ----------------------------------------------------------------------------

    suspend fun savePlan(plan: StudyPlan): String {
        val title = StudyRules.title(plan.title)
        if (plan.endDay < plan.startDay) throw StudyException(Problem.INVALID_DATE)
        return repository.savePlan(plan.copy(title = title))
    }

    suspend fun deletePlan(id: String) = repository.deletePlan(id)

    // ---- Presets --------------------------------------------------------------------------

    suspend fun savePreset(preset: FocusPreset): String {
        val title = StudyRules.title(preset.title)
        with(preset) {
            if (focusMinutes !in 1..240 ||
                shortBreakMinutes !in 0..60 ||
                longBreakMinutes !in 0..90 ||
                sessionsCount !in 1..12 ||
                longBreakInterval !in 1..sessionsCount
            )
                throw StudyException(Problem.INVALID_PRESET)
        }
        return repository.savePreset(preset.copy(title = title))
    }

    suspend fun deletePreset(id: String) = repository.deletePreset(id)

    companion object {
        val builtInPresets
            get() =
                listOf(
                    FocusPreset(
                        id = "preset-quick",
                        title = "سريعة",
                        focusMinutes = 25,
                        shortBreakMinutes = 5,
                        longBreakMinutes = 15,
                        sessionsCount = 4,
                        longBreakInterval = 2,
                        autoStart = false,
                        builtIn = true,
                        position = 0,
                    ),
                    FocusPreset(
                        id = "preset-standard",
                        title = "معيارية",
                        focusMinutes = 50,
                        shortBreakMinutes = 10,
                        longBreakMinutes = 25,
                        sessionsCount = 4,
                        longBreakInterval = 2,
                        autoStart = false,
                        builtIn = true,
                        position = 1,
                    ),
                    FocusPreset(
                        id = "preset-deep",
                        title = "دراسة عميقة",
                        focusMinutes = 90,
                        shortBreakMinutes = 20,
                        longBreakMinutes = 40,
                        sessionsCount = 3,
                        longBreakInterval = 2,
                        autoStart = false,
                        builtIn = true,
                        position = 2,
                    ),
                    FocusPreset(
                        id = "preset-review",
                        title = "مراجعة",
                        focusMinutes = 30,
                        shortBreakMinutes = 5,
                        longBreakMinutes = 15,
                        sessionsCount = 4,
                        longBreakInterval = 2,
                        autoStart = false,
                        builtIn = true,
                        position = 3,
                    ),
                )
    }
}
