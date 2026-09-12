package com.nageebstudyos.study.data.repository

import com.nageebstudyos.study.data.local.*
import com.nageebstudyos.study.domain.*

internal fun ReviewEntity.toDomain(lessonTitle: String, subjectTitle: String) =
    Review(
        id = id,
        lessonId = lessonId,
        subjectId = subjectId,
        scheduledAt = scheduledAt,
        scheduledDay = scheduledDay,
        completedAt = completedAt,
        status = runCatching {
            com.nageebstudyos.study.domain.ReviewStatus.valueOf(status)
        }.getOrDefault(com.nageebstudyos.study.domain.ReviewStatus.PENDING),
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lessonTitle = lessonTitle,
        subjectTitle = subjectTitle,
    )

internal fun StudySessionEntity.toDomain(
    subjectTitle: String,
    folderTitle: String,
    lessonTitle: String,
) = StudySession(
    id = id,
    subjectId = subjectId,
    folderId = folderId,
    lessonId = lessonId,
    startTime = startTime,
    endTime = endTime,
    durationSeconds = duration,
    goal = goal,
    result = result?.let { runCatching { SessionResult.valueOf(it) }.getOrNull() },
    notes = notes,
    lastPage = lastPage,
    dayIndex = dayIndex,
    createdAt = createdAt,
    subjectTitle = subjectTitle,
    folderTitle = folderTitle,
    lessonTitle = lessonTitle,
)

internal fun TaskEntity.toDomain(
    subjectTitle: String,
    lessonTitle: String,
    planTitle: String,
) = Task(
    id = id,
    planId = planId,
    title = title,
    description = description,
    day = day,
    dueDay = dueDay,
    subjectId = subjectId,
    lessonId = lessonId,
    status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.PENDING),
    priority = runCatching { TaskPriority.valueOf(priority) }.getOrDefault(TaskPriority.NORMAL),
    position = position,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    subjectTitle = subjectTitle,
    lessonTitle = lessonTitle,
    planTitle = planTitle,
)

internal fun PlanEntity.toDomain(subjectTitle: String, total: Int, done: Int) =
    StudyPlan(
        id = id,
        title = title,
        description = description,
        subjectId = subjectId,
        startDay = startDay,
        endDay = endDay,
        createdAt = createdAt,
        updatedAt = updatedAt,
        subjectTitle = subjectTitle,
        taskCount = total,
        doneCount = done,
    )

internal fun FocusPresetEntity.toDomain() =
    FocusPreset(
        id = id,
        title = title,
        focusMinutes = focusMinutes,
        shortBreakMinutes = shortBreakMinutes,
        longBreakMinutes = longBreakMinutes,
        sessionsCount = sessionsCount,
        longBreakInterval = longBreakInterval,
        autoStart = autoStart,
        sound = sound,
        vibration = vibration,
        builtIn = builtIn,
        position = position,
    )

internal fun FocusPreset.toEntity(now: Long, createdAt: Long) =
    FocusPresetEntity(
        id = id,
        title = title,
        focusMinutes = focusMinutes,
        shortBreakMinutes = shortBreakMinutes,
        longBreakMinutes = longBreakMinutes,
        sessionsCount = sessionsCount,
        longBreakInterval = longBreakInterval,
        autoStart = autoStart,
        sound = sound,
        vibration = vibration,
        builtIn = builtIn,
        position = position,
        createdAt = createdAt,
        updatedAt = now,
    )

internal fun LastActiveEntity.toDomain(
    subjectTitle: String,
    folderTitle: String,
    lessonTitle: String,
    fileTitle: String,
) = ContinueState(
    subjectId = subjectId,
    folderId = folderId,
    lessonId = lessonId,
    fileId = fileId,
    page = page,
    updatedAt = updatedAt,
    subjectTitle = subjectTitle,
    folderTitle = folderTitle,
    lessonTitle = lessonTitle,
    fileTitle = fileTitle,
)
