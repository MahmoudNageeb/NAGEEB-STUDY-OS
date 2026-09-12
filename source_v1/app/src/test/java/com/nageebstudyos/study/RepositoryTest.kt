package com.nageebstudyos.study

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.nageebstudyos.study.data.local.*
import com.nageebstudyos.study.data.repository.RoomStudyRepository
import com.nageebstudyos.study.data.storage.DocumentStorage
import com.nageebstudyos.study.domain.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class RepositoryTest {
    private lateinit var context: Context
    private lateinit var db: StudyDatabase
    private lateinit var repo: RoomStudyRepository
    private lateinit var actions: StudyActions
    private val name = "test-study.db"

    @Before
    fun before() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
        openDb()
    }

    private fun openDb() {
        db =
            Room.databaseBuilder(context, StudyDatabase::class.java, name)
                .allowMainThreadQueries()
                .addCallback(StudyDatabase.constraints)
                .build()
        repo = RoomStudyRepository(db)
        actions = StudyActions(repo)
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(name)
    }

    private suspend fun seed(): List<Entry> {
        val s = Entry(newId(), "Mathematics", Kind.SUBJECT)
        val f = Entry(newId(), "Calculus", Kind.FOLDER, subjectId = s.id)
        val child =
            Entry(newId(), "Differentiation", Kind.FOLDER, subjectId = s.id, parentId = f.id)
        val lesson =
            Entry(newId(), "Lecture 01", Kind.LESSON, subjectId = s.id, parentId = child.id)
        listOf(s, f, child, lesson).forEach { actions.save(it) }
        return listOf(s, f, child, lesson)
    }

    @Test
    fun acceptanceDataFlowPersistsAfterDatabaseReopen() =
        runBlocking<Unit> {
            val (subject, folder, child, lesson) = seed()
            val original =
                File(context.cacheDir, "lecture.pdf").apply {
                    writeBytes("%PDF-1.4\n%%EOF\n".toByteArray())
                }
            val storage = DocumentStorage(context)
            val imported = storage.attach(Uri.fromFile(original), lesson.id, true, "Lecture PDF")
            actions.save(imported)
            val note =
                Entry(
                    newId(),
                    "Rules",
                    Kind.NOTE,
                    ownerId = lesson.id,
                    ownerKind = Kind.LESSON,
                    body = "Differentiate powers carefully",
                )
            val link =
                Entry(
                    newId(),
                    "Reference",
                    Kind.LINK,
                    ownerId = lesson.id,
                    ownerKind = Kind.LESSON,
                    url = "https://example.org/calculus",
                )
            val tag = Entry(newId(), "Exam", Kind.TAG)
            listOf(note, link, tag).forEach { actions.save(it) }
            repo.setTag(Kind.LESSON, lesson.id, tag.id, true)
            actions.save(
                lesson.copy(
                    status = LessonStatus.IN_PROGRESS,
                    understanding = 60,
                    application = 25,
                    revision = 10,
                )
            )
            repo.setSetting("language", "ar")
            repo.setSetting("theme", "dark")
            assertEquals(lesson.id, repo.search("Lecture 01").first { it.kind == Kind.LESSON }.id)
            db.close()
            openDb()
            assertEquals("Mathematics", repo.get(Kind.SUBJECT, subject.id)?.title)
            assertEquals(folder.id, repo.get(Kind.FOLDER, child.id)?.parentId)
            assertEquals(60, repo.get(Kind.LESSON, lesson.id)?.understanding)
            assertEquals(note.body, repo.get(Kind.NOTE, note.id)?.body)
            assertEquals(link.url, repo.get(Kind.LINK, link.id)?.url)
            assertEquals(setOf(tag.id), repo.attachedTags(Kind.LESSON, lesson.id).first())
            assertEquals("ar", repo.settings().first()["language"])
            assertEquals("dark", repo.settings().first()["theme"])
            assertArrayEquals(original.readBytes(), File(imported.location).readBytes())
            assertEquals(1, repo.library(Scope()).first().rows.single().lessonCount)
            assertEquals(
                3,
                repo.library(Scope(Kind.LESSON, lesson.id, subject.id)).first().rows.size,
            )
            actions.delete(subject)
            storage.cleanPending(repo)
            original.delete()
        }

    @Test
    fun folderMoveRejectsCyclesAndPreservesContents() =
        runBlocking<Unit> {
            val (s, f, c, l) = seed()
            try {
                actions.move(f, c.id)
                fail("cycle allowed")
            } catch (e: StudyException) {
                assertEquals(Problem.INVALID_MOVE, e.problem)
            }
            assertNull(repo.get(Kind.FOLDER, f.id)?.parentId)
            actions.move(c, null)
            assertNull(repo.get(Kind.FOLDER, c.id)?.parentId)
            assertEquals(c.id, repo.get(Kind.LESSON, l.id)?.parentId)
            val other = Entry(newId(), "Physics", Kind.SUBJECT)
            actions.save(other)
            val otherFolder = Entry(newId(), "Chapter", Kind.FOLDER, subjectId = other.id)
            actions.save(otherFolder)
            try {
                actions.move(c, otherFolder.id)
                fail("cross-subject move allowed")
            } catch (_: StudyException) {}
            assertEquals(s.id, repo.get(Kind.FOLDER, c.id)?.subjectId)
        }

    @Test
    fun allSevenEntityTypesAreSearchable() =
        runBlocking<Unit> {
            val s = Entry(newId(), "Needle subject", Kind.SUBJECT)
            actions.save(s)
            val f = Entry(newId(), "Needle folder", Kind.FOLDER, subjectId = s.id)
            actions.save(f)
            val l = Entry(newId(), "Needle lesson", Kind.LESSON, subjectId = s.id, parentId = f.id)
            actions.save(l)
            listOf(
                    Entry(
                        newId(),
                        "Needle file",
                        Kind.FILE,
                        ownerId = l.id,
                        ownerKind = Kind.LESSON,
                        location = "content://provider/file",
                    ),
                    Entry(
                        newId(),
                        "A note",
                        Kind.NOTE,
                        ownerId = l.id,
                        ownerKind = Kind.LESSON,
                        body = "needle in body",
                    ),
                    Entry(
                        newId(),
                        "Needle link",
                        Kind.LINK,
                        ownerId = l.id,
                        ownerKind = Kind.LESSON,
                        url = "https://example.org",
                    ),
                    Entry(newId(), "Needle tag", Kind.TAG),
                )
                .forEach { actions.save(it) }
            assertEquals(Kind.entries.toSet(), repo.search("needle").map { it.kind }.toSet())
        }

    @Test
    fun wildcardSearchIsLiteralAndArabicSearchWorks() =
        runBlocking<Unit> {
            actions.save(Entry(newId(), "الفصل الأول 50%", Kind.SUBJECT))
            actions.save(Entry(newId(), "Other", Kind.SUBJECT))
            assertEquals(1, repo.search("%").size)
            assertEquals(1, repo.search("الفصل").size)
            assertEquals(0, repo.search("_").size)
        }

    @Test
    fun deletingLinkedRecordNeverDeletesOriginalOrQueuesIt() =
        runBlocking<Unit> {
            val lesson = seed().last()
            val original = File(context.cacheDir, "original.txt").apply { writeText("keep") }
            val linked =
                Entry(
                    newId(),
                    "Linked",
                    Kind.FILE,
                    ownerId = lesson.id,
                    ownerKind = Kind.LESSON,
                    location = Uri.fromFile(original).toString(),
                )
            actions.save(linked)
            actions.delete(linked)
            assertTrue(original.exists())
            assertEquals("keep", original.readText())
            assertTrue(repo.pendingFiles().isEmpty())
            original.delete()
        }

    @Test
    fun cascadeQueuesOnlyPrivateCopiesAndSurvivesRestart() =
        runBlocking<Unit> {
            val (s, f, c, l) = seed()
            val copy =
                File(context.filesDir, "imports/${newId()}").apply {
                    parentFile!!.mkdirs()
                    writeText("private")
                }
            val imported =
                Entry(
                    newId(),
                    "Copy",
                    Kind.FILE,
                    ownerId = l.id,
                    ownerKind = Kind.LESSON,
                    location = copy.path,
                    storageType = StorageType.IMPORTED,
                )
            val linked =
                Entry(
                    newId(),
                    "Original",
                    Kind.FILE,
                    ownerId = l.id,
                    ownerKind = Kind.LESSON,
                    location = "content://provider/original",
                )
            listOf(imported, linked).forEach { actions.save(it) }
            actions.delete(f)
            assertNull(repo.get(Kind.FOLDER, c.id))
            assertNull(repo.get(Kind.LESSON, l.id))
            assertNotNull(repo.get(Kind.SUBJECT, s.id))
            assertEquals(listOf(copy.path), repo.pendingFiles().map { it.second })
            assertTrue(copy.exists())
            db.close()
            openDb()
            DocumentStorage(context).cleanPending(repo)
            assertFalse(copy.exists())
            assertTrue(repo.pendingFiles().isEmpty())
        }

    @Test
    fun tagAttachmentsAreIdempotentAndCascadeSafely() =
        runBlocking<Unit> {
            val lesson = seed().last()
            val tag = Entry(newId(), "Review", Kind.TAG)
            actions.save(tag)
            repo.setTag(Kind.LESSON, lesson.id, tag.id, true)
            repo.setTag(Kind.LESSON, lesson.id, tag.id, true)
            assertEquals(1, repo.attachedTags(Kind.LESSON, lesson.id).first().size)
            actions.delete(tag)
            assertTrue(repo.attachedTags(Kind.LESSON, lesson.id).first().isEmpty())
            assertNotNull(repo.get(Kind.LESSON, lesson.id))
        }

    @Test
    fun fileAndNoteTagAssignmentsPersist() =
        runBlocking<Unit> {
            val lesson = seed().last()
            val file =
                Entry(
                    newId(),
                    "File",
                    Kind.FILE,
                    ownerId = lesson.id,
                    ownerKind = Kind.LESSON,
                    location = "content://provider/document",
                )
            val note =
                Entry(newId(), "Note", Kind.NOTE, ownerId = lesson.id, ownerKind = Kind.LESSON)
            val tag = Entry(newId(), "Important", Kind.TAG)
            listOf(file, note, tag).forEach { actions.save(it) }
            listOf(file, note).forEach {
                repo.setTag(it.kind, it.id, tag.id, true)
                assertEquals(setOf(tag.id), repo.attachedTags(it.kind, it.id).first())
            }
        }

    @Test
    fun duplicateTagsRejectedCaseInsensitively() =
        runBlocking<Unit> {
            actions.save(Entry(newId(), "Exam", Kind.TAG))
            try {
                actions.save(Entry(newId(), " exam ", Kind.TAG))
                fail("duplicate allowed")
            } catch (e: StudyException) {
                assertEquals(Problem.DUPLICATE_TAG, e.problem)
            }
            assertEquals(1, repo.tags().first().size)
        }

    @Test
    fun renameDoesNotCascadeOrChangeId() =
        runBlocking<Unit> {
            val (s, f, c, l) = seed()
            actions.save(s.copy(title = "Math"))
            actions.save(f.copy(title = "Analysis"))
            assertEquals("Math", repo.get(Kind.SUBJECT, s.id)?.title)
            assertEquals(f.id, repo.get(Kind.FOLDER, c.id)?.parentId)
            assertNotNull(repo.get(Kind.LESSON, l.id))
        }

    @Test
    fun reorderSwapsOnlySiblings() =
        runBlocking<Unit> {
            val (s, f, c, l) = seed()
            val second = Entry(newId(), "Other folder", Kind.FOLDER, subjectId = s.id)
            actions.save(second)
            actions.reorder(second, -1)
            val rows =
                repo.library(Scope(Kind.SUBJECT, s.id, s.id)).first().rows.filter {
                    it.kind == Kind.FOLDER
                }
            assertEquals(listOf(second.id, f.id), rows.map { it.id })
            assertEquals(f.id, repo.get(Kind.FOLDER, c.id)?.parentId)
            assertNotNull(repo.get(Kind.LESSON, l.id))
        }

    @Test
    fun noteCanAttachToEachSupportedOwner() =
        runBlocking<Unit> {
            val (s, f, _, l) = seed()
            val file =
                Entry(
                    newId(),
                    "File",
                    Kind.FILE,
                    ownerId = l.id,
                    ownerKind = Kind.LESSON,
                    location = "content://provider/file",
                )
            actions.save(file)
            for (owner in listOf(s, f, l, file)) {
                val note =
                    Entry(
                        newId(),
                        "Note ${owner.kind}",
                        Kind.NOTE,
                        ownerId = owner.id,
                        ownerKind = owner.kind,
                        body = "body",
                    )
                actions.save(note)
                assertEquals(owner.id, repo.get(Kind.NOTE, note.id)?.ownerId)
            }
        }

    @Test
    fun databaseRejectsInvalidProgressAndOwner() =
        runBlocking<Unit> {
            val (s, _, c, l) = seed()
            try {
                db.dao()
                    .put(Lesson(newId(), s.id, c.id, "Invalid", 0, "NOT_STARTED", 101, 0, 0, 1, 1))
                fail("invalid progress inserted")
            } catch (_: android.database.sqlite.SQLiteException) {}
            try {
                db.dao().put(Note(newId(), s.id, null, l.id, null, "Invalid", "", 1, 1))
                fail("two owners inserted")
            } catch (_: android.database.sqlite.SQLiteException) {}
        }

    @Test
    fun databaseRejectsCrossSubjectFolderRelationship() =
        runBlocking<Unit> {
            val (_, f, _, _) = seed()
            val other = Entry(newId(), "Physics", Kind.SUBJECT)
            actions.save(other)
            try {
                db.dao().put(Folder(newId(), other.id, f.id, "Invalid", 0, 1, 1))
                fail("cross-subject parent inserted")
            } catch (_: android.database.sqlite.SQLiteException) {}
        }

    @Test
    fun failedImportDoesNotCreateRecord() =
        runBlocking<Unit> {
            val lesson = seed().last()
            try {
                DocumentStorage(context)
                    .attach(
                        Uri.parse("content://missing.provider/file"),
                        lesson.id,
                        true,
                        "Missing",
                    )
                fail("import succeeded")
            } catch (e: StudyException) {
                assertEquals(Problem.IMPORT_FAILED, e.problem)
            }
            assertTrue(
                repo.library(Scope(Kind.LESSON, lesson.id, lesson.subjectId)).first().rows.isEmpty()
            )
        }

    @Test
    fun searchFlowRefreshesAfterWritesWithoutChangingQuery() =
        runBlocking<Unit> {
            val started = CompletableDeferred<Unit>()
            val result =
                async(Dispatchers.IO) {
                    withTimeout(10_000) {
                        repo
                            .observeSearch("Fresh")
                            .onEach { started.complete(Unit) }
                            .first { it.any { row -> row.title == "Fresh lesson" } }
                    }
                }
            withTimeout(10_000) { started.await() }
            val s = Entry(newId(), "Fresh lesson", Kind.SUBJECT)
            actions.save(s)
            assertEquals(s.id, result.await().single().id)
        }

    @Test
    fun deletesHierarchyDeeperThanSqliteCascadeLimit() =
        runBlocking<Unit> {
            val s = Entry(newId(), "Deep subject", Kind.SUBJECT)
            actions.save(s)
            db.withTransaction {
                repeat(1100) { i ->
                    db.dao()
                        .put(
                            Folder(
                                "deep-$i",
                                s.id,
                                if (i == 0) null else "deep-${i-1}",
                                "Level $i",
                                i,
                                1,
                                1,
                            )
                        )
                }
            }
            assertEquals(1100, repo.folders(s.id).size)
            actions.delete(s)
            assertTrue(repo.folders(s.id).isEmpty())
            assertNull(repo.get(Kind.SUBJECT, s.id))
        }

    @Test
    fun thousandsOfMetadataRowsRemainSearchableAndBounded() =
        runBlocking<Unit> {
            val s = Entry(newId(), "Large library", Kind.SUBJECT)
            actions.save(s)
            db.withTransaction {
                repeat(2500) { i ->
                    val id = "lesson-$i"
                    db.dao()
                        .put(Lesson(id, s.id, null, "Lecture $i", i, "NOT_STARTED", 0, 0, 0, 1, 1))
                    db.dao()
                        .put(
                            StudyFile(
                                "file-$i",
                                id,
                                "File $i",
                                "content://provider/$i",
                                "application/pdf",
                                1024,
                                "LINKED",
                                1,
                                1,
                            )
                        )
                }
            }
            assertEquals(2500, repo.library(Scope()).first().rows.single().lessonCount)
            assertEquals(200, repo.search("Lecture").size)
            assertEquals(200, repo.search("File").size)
            assertEquals(1, repo.search("Lecture 2499").size)
        }

    @Test
    fun cleanupCannotDeleteOutsidePrivateImportsDirectory() =
        runBlocking<Unit> {
            val original = File(context.cacheDir, "do-not-delete").apply { writeText("original") }
            try {
                DocumentStorage(context).removeCopy(original.path)
                fail("unsafe delete allowed")
            } catch (_: StudyException) {}
            assertTrue(original.exists())
            original.delete()
        }
}
