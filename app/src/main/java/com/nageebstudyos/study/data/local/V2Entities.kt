package com.nageebstudyos.study.data.local

import androidx.room.*

/**
 * V2 entities — Reviews, Study Sessions, Planner (Plans & Tasks), Focus Presets and the
 * "Continue studying" state.
 *
 * Time model
 * ----------
 *  - Every wall-clock timestamp is `INTEGER` epoch millis (UTC).
 *  - Every entity that participates in the calendar also stores a local *day index*
 *    (`java.time.LocalDate.toEpochDay()` evaluated with the device zone). Day buckets are
 *    therefore grouped correctly in SQL without loading sessions into memory, and remain
 *    stable regardless of the UTC offset used by aggregation queries.
 *
 * Foreign keys intentionally SET NULL for history tables (sessions, tasks, plans): deleting
 * a subject must never erase study analytics. Reviews are tightly owned by a lesson and
 * cascade, exactly like lesson files and links in V1.
 */

@Entity(
    tableName = "Review",
    foreignKeys =
        [
            ForeignKey(
                entity = Lesson::class,
                parentColumns = ["id"],
                childColumns = ["lessonId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [
            Index("lessonId"),
            Index("subjectId"),
            Index("scheduledDay"),
            Index(value = ["status", "scheduledDay"]),
        ],
)
data class ReviewEntity(
    @PrimaryKey val id: String,
    val lessonId: String,
    val subjectId: String,
    val scheduledAt: Long,
    val scheduledDay: Int,
    val completedAt: Long?,
    val status: String, // PENDING | DONE | SKIPPED
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "StudySession",
    foreignKeys =
        [
            ForeignKey(
                entity = Subject::class,
                parentColumns = ["id"],
                childColumns = ["subjectId"],
                onDelete = ForeignKey.SET_NULL,
            ),
            ForeignKey(
                entity = Lesson::class,
                parentColumns = ["id"],
                childColumns = ["lessonId"],
                onDelete = ForeignKey.SET_NULL,
            ),
        ],
    indices =
        [
            Index("subjectId"),
            Index("lessonId"),
            Index("startTime"),
            Index("dayIndex"),
        ],
)
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val subjectId: String?,
    val folderId: String?,
    val lessonId: String?,
    val startTime: Long,
    val endTime: Long,
    /** Actual focused seconds (breaks excluded). */
    val duration: Long,
    val goal: String,
    val result: String?, // YES | PARTIALLY | NO (null until the user reviews the session)
    val notes: String,
    val lastPage: Int?,
    val dayIndex: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "Plan",
    foreignKeys =
        [
            ForeignKey(
                entity = Subject::class,
                parentColumns = ["id"],
                childColumns = ["subjectId"],
                onDelete = ForeignKey.SET_NULL,
            )
        ],
    indices = [Index("subjectId"), Index("startDay"), Index("endDay")],
)
data class PlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val subjectId: String?,
    val startDay: Int,
    val endDay: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "Task",
    foreignKeys =
        [
            ForeignKey(
                entity = PlanEntity::class,
                parentColumns = ["id"],
                childColumns = ["planId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Subject::class,
                parentColumns = ["id"],
                childColumns = ["subjectId"],
                onDelete = ForeignKey.SET_NULL,
            ),
            ForeignKey(
                entity = Lesson::class,
                parentColumns = ["id"],
                childColumns = ["lessonId"],
                onDelete = ForeignKey.SET_NULL,
            ),
        ],
    indices =
        [
            Index("planId"),
            Index("subjectId"),
            Index("lessonId"),
            Index("status"),
            Index("day"),
            Index("dueDay"),
        ],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val planId: String?,
    val title: String,
    val description: String,
    val day: Int?,
    val dueDay: Int?,
    val subjectId: String?,
    val lessonId: String?,
    val status: String, // PENDING | IN_PROGRESS | DONE
    val priority: String, // LOW | NORMAL | HIGH
    val position: Int,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "FocusPreset", indices = [Index(value = ["position"], unique = true)])
data class FocusPresetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val focusMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val sessionsCount: Int,
    val longBreakInterval: Int,
    val autoStart: Boolean,
    val sound: Boolean,
    val vibration: Boolean,
    val builtIn: Boolean,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Single-row (id = 0) snapshot powering "Continue studying". */
@Entity(tableName = "LastActive")
data class LastActiveEntity(
    @PrimaryKey val id: Int = 0,
    val subjectId: String?,
    val folderId: String?,
    val lessonId: String?,
    val fileId: String?,
    val page: Int?,
    val updatedAt: Long,
)

// ---- Display / aggregation projection rows ---------------------------------------------

data class IdTitle(val id: String, val title: String)

data class LessonRefRow(
    val id: String,
    val title: String,
    val subjectId: String,
    val folderId: String?,
)

data class FolderRefRow(
    val id: String,
    val title: String,
    val subjectId: String,
    val parentId: String?,
)

data class FileRefRow(val id: String, val title: String, val lessonId: String)

data class PlanCountRow(val planId: String, val total: Int, val done: Int)

// ---- Aggregation projection rows -------------------------------------------------------

data class TotalRow(val total: Long)

data class CountRow(val count: Int)

data class DayBucketRow(
    @ColumnInfo(name = "dayIndex") val dayIndex: Int,
    @ColumnInfo(name = "seconds") val seconds: Long,
    @ColumnInfo(name = "sessions") val sessions: Int,
    @ColumnInfo(name = "positive") val positive: Int,
)

data class SubjectSecondsRow(
    @ColumnInfo(name = "subjectId") val subjectId: String?,
    @ColumnInfo(name = "seconds") val seconds: Long,
    @ColumnInfo(name = "sessions") val sessions: Int,
)

data class SubjectProgressRow(
    @ColumnInfo(name = "subjectId") val subjectId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "accent") val accent: Int,
    @ColumnInfo(name = "lessonCount") val lessonCount: Int,
    @ColumnInfo(name = "notStarted") val notStarted: Int,
    @ColumnInfo(name = "inProgress") val inProgress: Int,
    @ColumnInfo(name = "needsReview") val needsReview: Int,
    @ColumnInfo(name = "mastered") val mastered: Int,
    @ColumnInfo(name = "weak") val weak: Int,
    @ColumnInfo(name = "avgSkill") val avgSkill: Double,
)
