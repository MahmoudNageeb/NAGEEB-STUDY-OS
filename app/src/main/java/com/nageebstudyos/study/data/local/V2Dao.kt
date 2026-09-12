package com.nageebstudyos.study.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * V2 data access. Calendar and analytics screens are served by bounded, aggregated queries
 * (day windows / GROUP BY) so that large session histories are never materialised.
 */
@Dao
interface V2Dao {

    // ---- Reviews -------------------------------------------------------------------------

    @Query("SELECT * FROM Review WHERE lessonId=:lessonId ORDER BY scheduledAt ASC")
    fun reviewsForLesson(lessonId: String): Flow<List<ReviewEntity>>

    @Query(
        "SELECT * FROM Review WHERE status='PENDING' AND scheduledDay<=:today ORDER BY scheduledDay ASC LIMIT 500"
    )
    fun dueReviews(today: Int): Flow<List<ReviewEntity>>

    @Query(
        "SELECT * FROM Review WHERE scheduledDay BETWEEN :from AND :to ORDER BY scheduledAt ASC LIMIT 1000"
    )
    fun reviewsForRange(from: Int, to: Int): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM Review WHERE scheduledDay IS :day ORDER BY scheduledAt ASC")
    fun reviewsOnDay(day: Int): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM Review WHERE status='PENDING' ORDER BY scheduledAt ASC LIMIT 1000")
    suspend fun pendingReviews(): List<ReviewEntity>

    @Query("SELECT * FROM Review WHERE status='DONE' AND completedAt IS NOT NULL ORDER BY completedAt DESC LIMIT 6")
    fun recentDoneReviews(): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM Review WHERE id=:id") suspend fun review(id: String): ReviewEntity?

    @Upsert suspend fun put(value: ReviewEntity)

    @Query(
        "UPDATE Review SET status=:status, completedAt=:completedAt, note=:note, updatedAt=:time WHERE id=:id"
    )
    suspend fun updateReviewStatus(
        id: String,
        status: String,
        completedAt: Long?,
        note: String,
        time: Long,
    )

    @Query(
        "UPDATE Review SET scheduledAt=:scheduledAt, scheduledDay=:scheduledDay, status='PENDING', completedAt=NULL, updatedAt=:time WHERE id=:id"
    )
    suspend fun rescheduleReview(id: String, scheduledAt: Long, scheduledDay: Int, time: Long)

    @Query("DELETE FROM Review WHERE id=:id") suspend fun deleteReview(id: String)

    @Query("SELECT COUNT(*) FROM Review WHERE status='DONE'")
    fun completedReviewsCount(): Flow<Int>

    // ---- Study sessions -------------------------------------------------------------------

    @Upsert suspend fun put(value: StudySessionEntity)

    @Query("SELECT * FROM StudySession WHERE id=:id")
    suspend fun session(id: String): StudySessionEntity?

    @Query("SELECT * FROM StudySession ORDER BY startTime DESC LIMIT :limit")
    fun recentSessions(limit: Int): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM StudySession WHERE lessonId=:lessonId ORDER BY startTime DESC LIMIT 100")
    fun sessionsForLesson(lessonId: String): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM StudySession WHERE dayIndex=:day ORDER BY startTime ASC")
    fun sessionsOnDay(day: Int): Flow<List<StudySessionEntity>>

    @Query("SELECT COALESCE(SUM(duration),0) AS total FROM StudySession WHERE startTime>=:from AND startTime<:to")
    suspend fun secondsBetween(from: Long, to: Long): Long

    @Query(
        "SELECT dayIndex, COALESCE(SUM(duration),0) AS seconds, COUNT(*) AS sessions, COALESCE(SUM(CASE WHEN result IN ('YES','PARTIALLY') THEN 1 ELSE 0 END),0) AS positive FROM StudySession WHERE dayIndex BETWEEN :from AND :to GROUP BY dayIndex"
    )
    fun observeDayBuckets(from: Int, to: Int): Flow<List<DayBucketRow>>

    @Query(
        "SELECT dayIndex, COALESCE(SUM(duration),0) AS seconds, COUNT(*) AS sessions, COALESCE(SUM(CASE WHEN result IN ('YES','PARTIALLY') THEN 1 ELSE 0 END),0) AS positive FROM StudySession WHERE dayIndex>=:since GROUP BY dayIndex ORDER BY dayIndex ASC LIMIT 400"
    )
    suspend fun dayBucketsSince(since: Int): List<DayBucketRow>

    @Query(
        "SELECT subjectId, COALESCE(SUM(duration),0) AS seconds, COUNT(*) AS sessions FROM StudySession WHERE startTime>=:from AND startTime<:to GROUP BY subjectId"
    )
    fun observeSubjectSeconds(from: Long, to: Long): Flow<List<SubjectSecondsRow>>

    @Query("SELECT COUNT(*) FROM StudySession") suspend fun totalSessions(): Int

    // ---- Plans ----------------------------------------------------------------------------

    @Upsert suspend fun put(value: PlanEntity)

    @Query("SELECT * FROM Plan ORDER BY startDay DESC, updatedAt DESC LIMIT 200")
    fun plans(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM Plan WHERE startDay<=:to AND endDay>=:from ORDER BY startDay ASC")
    fun plansForRange(from: Int, to: Int): Flow<List<PlanEntity>>

    @Query("SELECT * FROM Plan WHERE startDay<=:day AND endDay>=:day ORDER BY startDay ASC")
    fun plansOnDay(day: Int): Flow<List<PlanEntity>>

    @Query("SELECT * FROM Plan WHERE id=:id") suspend fun plan(id: String): PlanEntity?

    @Query("DELETE FROM Plan WHERE id=:id") suspend fun deletePlan(id: String)

    // ---- Tasks ----------------------------------------------------------------------------

    @Upsert suspend fun put(value: TaskEntity)

    @Query("SELECT * FROM Task WHERE planId IS NULL ORDER BY completedAt IS NOT NULL, dueDay IS NULL, dueDay ASC, position ASC LIMIT 500")
    fun looseTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM Task WHERE planId=:planId ORDER BY position ASC, createdAt ASC")
    fun planTasks(planId: String): Flow<List<TaskEntity>>

    @Query(
        "SELECT * FROM Task WHERE status!='DONE' AND ((day IS NOT NULL AND day BETWEEN :from AND :to) OR (dueDay IS NOT NULL AND dueDay BETWEEN :from AND :to)) ORDER BY dueDay ASC LIMIT 1000"
    )
    fun activeTasksForRange(from: Int, to: Int): Flow<List<TaskEntity>>

    @Query(
        "SELECT * FROM Task WHERE status!='DONE' AND ((day IS :day) OR (dueDay IS :day)) ORDER BY dueDay ASC"
    )
    fun activeTasksOnDay(day: Int): Flow<List<TaskEntity>>

    @Query(
        "SELECT * FROM Task WHERE status!='DONE' AND dueDay IS NOT NULL AND dueDay<=:today ORDER BY dueDay ASC LIMIT 500"
    )
    fun dueTasks(today: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM Task WHERE status!='DONE' ORDER BY (dueDay IS :today OR day IS :today) DESC, dueDay ASC LIMIT 500")
    fun tasksForToday(today: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM Task WHERE status!='DONE' AND dueDay IS NOT NULL ORDER BY dueDay ASC LIMIT 1000")
    suspend fun pendingTasks(): List<TaskEntity>

    @Query("SELECT * FROM Task WHERE status='DONE' AND completedAt IS NOT NULL ORDER BY completedAt DESC LIMIT 6")
    fun recentDoneTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM Task WHERE lessonId=:lessonId ORDER BY dueDay ASC LIMIT 200")
    fun tasksForLesson(lessonId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM Task WHERE id=:id") suspend fun task(id: String): TaskEntity?

    @Query(
        "UPDATE Task SET status=:status, completedAt=:completedAt, updatedAt=:time WHERE id=:id"
    )
    suspend fun updateTaskStatus(id: String, status: String, completedAt: Long?, time: Long)

    @Query(
        "UPDATE Task SET day=:day, dueDay=:dueDay, updatedAt=:time WHERE id=:id"
    )
    suspend fun rescheduleTask(id: String, day: Int?, dueDay: Int?, time: Long)

    @Query("DELETE FROM Task WHERE id=:id") suspend fun deleteTask(id: String)

    @Query("SELECT COALESCE(MAX(position),-1)+1 FROM Task WHERE planId IS :planId")
    suspend fun nextTaskPosition(planId: String?): Int

    // ---- Focus presets --------------------------------------------------------------------

    @Upsert suspend fun put(value: FocusPresetEntity)

    @Query("SELECT * FROM FocusPreset ORDER BY position ASC")
    fun presets(): Flow<List<FocusPresetEntity>>

    @Query("SELECT * FROM FocusPreset ORDER BY position ASC")
    suspend fun presetList(): List<FocusPresetEntity>

    @Query("SELECT * FROM FocusPreset WHERE id=:id")
    suspend fun preset(id: String): FocusPresetEntity?

    @Query("DELETE FROM FocusPreset WHERE id=:id AND builtIn=0")
    suspend fun deleteCustomPreset(id: String)

    @Query("SELECT planId AS planId, COUNT(*) AS total, COALESCE(SUM(CASE WHEN status='DONE' THEN 1 ELSE 0 END),0) AS done FROM Task WHERE planId IS NOT NULL GROUP BY planId")
    fun planCounts(): Flow<List<PlanCountRow>>

    // ---- Title references for display joins ----------------------------------------------

    @Query("SELECT id AS id, title AS title FROM Subject")
    suspend fun subjectTitles(): List<IdTitle>

    @Query("SELECT id AS id, title AS title, subjectId AS subjectId, folderId AS folderId FROM Lesson")
    suspend fun lessonRefs(): List<LessonRefRow>

    @Query("SELECT id AS id, title AS title, subjectId AS subjectId, parentId AS parentId FROM Folder")
    suspend fun folderRefs(): List<FolderRefRow>

    @Query("SELECT id AS id, title AS title, lessonId AS lessonId FROM StudyFile")
    suspend fun fileRefs(): List<FileRefRow>

    // ---- Continue studying ---------------------------------------------------------------

    @Upsert suspend fun put(value: LastActiveEntity)

    @Query("SELECT * FROM LastActive WHERE id=0")
    fun lastActive(): Flow<LastActiveEntity?>

    // ---- Settings / lesson aggregation ----------------------------------------------------

    @Upsert suspend fun put(value: AppSetting)

    @Query("SELECT * FROM AppSetting") fun settings(): Flow<List<AppSetting>>

    @Query(
        """
        SELECT s.id AS subjectId, s.title AS title, s.accent AS accent,
          COUNT(l.id) AS lessonCount,
          COALESCE(SUM(CASE WHEN l.status='NOT_STARTED' THEN 1 ELSE 0 END),0) AS notStarted,
          COALESCE(SUM(CASE WHEN l.status='IN_PROGRESS' THEN 1 ELSE 0 END),0) AS inProgress,
          COALESCE(SUM(CASE WHEN l.status='NEEDS_REVIEW' THEN 1 ELSE 0 END),0) AS needsReview,
          COALESCE(SUM(CASE WHEN l.status='MASTERED' THEN 1 ELSE 0 END),0) AS mastered,
          COALESCE(SUM(CASE WHEN l.status='WEAK' THEN 1 ELSE 0 END),0) AS weak,
          COALESCE(AVG((l.understanding + l.application + l.revision) / 3.0),0) AS avgSkill
        FROM Subject s LEFT JOIN Lesson l ON l.subjectId = s.id
        GROUP BY s.id ORDER BY s.position, s.title
        """
    )
    fun subjectProgress(): Flow<List<SubjectProgressRow>>

    @Query("SELECT COUNT(*) FROM Lesson WHERE status='MASTERED'")
    fun masteredLessonsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM Lesson WHERE status!='MASTERED'")
    fun pendingLessonsCount(): Flow<Int>
}
