package com.nageebstudyos.study

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nageebstudyos.study.data.local.StudyDatabase
import com.nageebstudyos.study.data.repository.RoomStudyRepository
import com.nageebstudyos.study.data.repository.RoomV2Repository
import com.nageebstudyos.study.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class V2RepositoryTest {
    private lateinit var context: Context
    private lateinit var db: StudyDatabase
    private lateinit var legacy: RoomStudyRepository
    private lateinit var repo: RoomV2Repository
    private lateinit var actions: StudyActions
    private lateinit var v2: V2Actions
    private val name = "test-v2.db"

    @Before
    fun before() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
        db =
            Room.databaseBuilder(context, StudyDatabase::class.java, name)
                .allowMainThreadQueries()
                .addCallback(StudyDatabase.constraints)
                .addMigrations(StudyDatabase.MIGRATION_1_2)
                .build()
        legacy = RoomStudyRepository(db)
        repo = RoomV2Repository(db)
        actions = StudyActions(legacy)
        v2 = V2Actions(repo)
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(name)
    }

    private suspend fun seedLesson(): List<Entry> {
        val subject = Entry(newId(), "Physics", Kind.SUBJECT)
        val folder = Entry(newId(), "Chapter 2", Kind.FOLDER, subjectId = subject.id)
        val lesson =
            Entry(newId(), "Lecture 4", Kind.LESSON, subjectId = subject.id, parentId = folder.id)
        listOf(subject, folder, lesson).forEach { actions.save(it) }
        return listOf(subject, folder, lesson)
    }

    private fun session(
        lesson: Entry,
        seconds: Long,
        result: SessionResult?,
        dayOffset: Int = 0,
    ): StudySession {
        val today = StudyTime.today()
        val start = StudyTime.at(today + dayOffset, 10, 0)
        return StudySession(
            id = newId(),
            subjectId = lesson.subjectId,
            folderId = lesson.parentId,
            lessonId = lesson.id,
            startTime = start,
            endTime = start + seconds * 1000,
            durationSeconds = seconds,
            goal = "Understand momentum",
            result = result,
            notes = "",
            dayIndex = StudyTime.dayOf(start),
        )
    }

    @Test
    fun fullAcceptanceFlowSessionReviewTaskCalendarAnalytics() = runBlocking {
        val (subject, _, lesson) = seedLesson()

        // 1. Finish a focus session (25 minutes, positive result).
        v2.saveSession(session(lesson, 25 * 60L, SessionResult.YES))
        assertEquals(25 * 60L, repo.secondsBetween(StudyTime.range(TimeRange.TODAY).first, StudyTime.range(TimeRange.TODAY).second))

        // Streak recognises the study day.
        val buckets = repo.dayBuckets(400)
        assertEquals(1, StreakRules.current(buckets))
        assertTrue(StreakRules.best(buckets) >= 1)

        // Analytics aggregates by subject.
        val (weekStart, weekEnd) = StudyTime.range(TimeRange.WEEK)
        val distribution = repo.observeSubjectSeconds(weekStart, weekEnd).first()
        assertEquals(1, distribution.size)
        assertEquals(25 * 60L, distribution.first().seconds)
        assertEquals("Physics", distribution.first().title)

        // 2. Add, then complete a review.
        val reviewAt = v2.suggestReviewAt(StudyTime.today(), 2)
        val reviewId = v2.addReview(lesson.id, subject.id, reviewAt)
        assertEquals(1, repo.reviewsForLesson(lesson.id).first().size)
        repo.completeReview(reviewId, null)
        assertEquals(1, repo.completedReviewsCount().first())

        // 3. Create and complete a task due today.
        val task =
            Task(
                id = newId(),
                planId = null,
                title = "Solve 10 problems",
                dueDay = StudyTime.today(),
                subjectId = subject.id,
                lessonId = lesson.id,
            )
        v2.saveTask(task)
        assertEquals(1, repo.dueTasks().first().size)
        repo.setTaskStatus(task.id, TaskStatus.DONE)
        assertEquals(0, repo.dueTasks().first().size)

        // 4. A plan spanning today creates a calendar event.
        val plan =
            StudyPlan(
                id = newId(),
                title = "Finish Chapter 2",
                subjectId = subject.id,
                startDay = StudyTime.today() - 1,
                endDay = StudyTime.today() + 5,
            )
        v2.savePlan(plan)

        // 5. Calendar day view surfaces session and plan.
        val events = repo.eventsOnDay(StudyTime.today()).first()
        assertTrue(events.any { it.kind == CalendarEventKind.SESSION })
        assertTrue(events.any { it.kind == CalendarEventKind.PLAN })
        // The completed task is hidden; its review counterpart remains when pending.
        val range = repo.eventsForRange(StudyTime.today() - 2, StudyTime.today() + 3).first()
        assertTrue(range.any { it.kind == CalendarEventKind.REVIEW })

        // 6. Subject progress is explainable (one lesson, not started => 0).
        val progress = repo.subjectProgress().first()
        val physics = progress.single { it.subjectId == subject.id }
        assertEquals(1, physics.lessonCount)
        assertEquals(0, physics.percent)

        // 7. Continue-studying state.
        repo.recordOpen(subject.id, lesson.parentId, lesson.id)
        val state = repo.continueState().first()!!
        assertEquals("Lecture 4", state.lessonTitle)
        assertEquals("Chapter 2", state.folderTitle)
        repo.recordPage(lesson.id, null, 27)
        assertEquals(27, repo.continueState().first()!!.page)
    }

    @Test
    fun focusPresetsAreSeededAndEditable() = runBlocking {
        repo.ensureBuiltInPresets()
        val presets = repo.presets().first()
        assertEquals(4, presets.size)
        // 25/5 quick, 50/10 standard, 90/20 deep, 30/5 review.
        val quick = presets.first { it.id == "preset-quick" }
        assertEquals(25, quick.focusMinutes)
        assertEquals(5, quick.shortBreakMinutes)
        val custom =
            FocusPreset(
                id = newId(),
                title = "امتحان",
                focusMinutes = 45,
                shortBreakMinutes = 8,
                longBreakMinutes = 20,
                sessionsCount = 3,
                longBreakInterval = 3,
                builtIn = false,
                position = -1,
            )
        v2.savePreset(custom)
        assertEquals(5, repo.presets().first().size)
        v2.deletePreset(custom.id)
        assertEquals(4, repo.presets().first().size)
    }

    @Test
    fun invalidPresetRejected() = runBlocking {
        repo.ensureBuiltInPresets()
        try {
            v2.savePreset(
                FocusPreset(
                    id = newId(),
                    title = "bad",
                    focusMinutes = 0,
                    shortBreakMinutes = 0,
                    longBreakMinutes = 0,
                    sessionsCount = 0,
                    longBreakInterval = 1,
                )
            )
            fail("invalid preset accepted")
        } catch (_: StudyException) {
        }
    }

    @Test
    fun dataSurvivesRestart() = runBlocking {
        val (subject, _, lesson) = seedLesson()
        v2.saveSession(session(lesson, 60 * 60L, SessionResult.PARTIALLY))
        v2.saveTask(
            Task(newId(), null, "Task one", dueDay = StudyTime.today() + 1, subjectId = subject.id)
        )
        v2.savePlan(
            StudyPlan(
                newId(),
                "Goal",
                startDay = StudyTime.today(),
                endDay = StudyTime.today() + 3,
            )
        )
        db.close()

        db =
            Room.databaseBuilder(context, StudyDatabase::class.java, name)
                .allowMainThreadQueries()
                .addCallback(StudyDatabase.constraints)
                .addMigrations(StudyDatabase.MIGRATION_1_2)
                .build()
        val reopened = RoomV2Repository(db)
        val reopenedLegacy = RoomStudyRepository(db)
        assertEquals(3600L, reopened.secondsBetween(0, Long.MAX_VALUE))
        assertEquals(1, reopened.looseTasks().first().size)
        assertEquals(1, reopened.plans().first().size)
        // V1 data is intact.
        assertEquals("Physics", reopenedLegacy.get(Kind.SUBJECT, subject.id)?.title)
        assertEquals("Lecture 4", reopenedLegacy.get(Kind.LESSON, lesson.id)?.title)
    }

    @Test
    fun deletingSubjectKeepsSessionHistory() = runBlocking {
        val (subject, _, lesson) = seedLesson()
        v2.saveSession(session(lesson, 900L, SessionResult.YES))
        actions.delete(subject)
        val distribution = repo.observeSubjectSeconds(0, Long.MAX_VALUE).first()
        // History survives with a null subject; analytics never silently drops time.
        assertEquals(900L, distribution.firstOrNull { it.subjectId == null }?.seconds)
    }

    @Test
    fun plansCalculateTaskProgressAndCascadeDelete() = runBlocking {
        val (subject, _, _) = seedLesson()
        val plan =
            StudyPlan(
                newId(),
                "Finish Physics Chapter 1",
                subjectId = subject.id,
                startDay = StudyTime.today(),
                endDay = StudyTime.today() + 8,
            )
        v2.savePlan(plan)
        val t1 = Task(newId(), plan.id, "Lecture 1", dueDay = StudyTime.today())
        val t2 = Task(newId(), plan.id, "Lecture 2", dueDay = StudyTime.today() + 1)
        v2.saveTask(t1)
        v2.saveTask(t2)
        repo.setTaskStatus(t1.id, TaskStatus.DONE)
        val stored = repo.plans().first().single()
        assertEquals(2, stored.taskCount)
        assertEquals(1, stored.doneCount)
        v2.deletePlan(plan.id)
        assertTrue(repo.planTasks(plan.id).first().isEmpty())
    }
}
