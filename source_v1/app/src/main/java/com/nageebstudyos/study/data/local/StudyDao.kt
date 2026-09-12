package com.nageebstudyos.study.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query(
        "SELECT Subject.*, (SELECT COUNT(*) FROM Lesson WHERE subjectId = Subject.id) AS lessonCount FROM Subject ORDER BY position, title"
    )
    fun subjects(): Flow<List<SubjectCount>>

    @Query("SELECT * FROM Subject WHERE id=:id") fun subjectFlow(id: String): Flow<Subject?>

    @Query("SELECT * FROM Folder WHERE id=:id") fun folderFlow(id: String): Flow<Folder?>

    @Query("SELECT * FROM Lesson WHERE id=:id") fun lessonFlow(id: String): Flow<Lesson?>

    @Query("SELECT * FROM StudyFile WHERE id=:id") fun fileFlow(id: String): Flow<StudyFile?>

    @Query("SELECT * FROM Folder WHERE subjectId=:subject ORDER BY position,title")
    fun folderTree(subject: String): Flow<List<Folder>>

    @Query("SELECT * FROM Folder WHERE subjectId=:subject ORDER BY position,title")
    suspend fun folders(subject: String): List<Folder>

    @Query(
        "SELECT * FROM Folder WHERE subjectId=:subject AND parentId IS :parent ORDER BY position,title"
    )
    fun children(subject: String, parent: String?): Flow<List<Folder>>

    @Query(
        "SELECT * FROM Lesson WHERE subjectId=:subject AND folderId IS :parent ORDER BY position,title"
    )
    fun lessons(subject: String, parent: String?): Flow<List<Lesson>>

    @Query("SELECT * FROM StudyFile WHERE lessonId=:id ORDER BY createdAt DESC")
    fun files(id: String): Flow<List<StudyFile>>

    @Query("SELECT * FROM StudyLink WHERE lessonId=:id ORDER BY createdAt DESC")
    fun links(id: String): Flow<List<StudyLink>>

    @Query(
        "SELECT id, subjectId, folderId, lessonId, fileId, title, '' AS body, createdAt, updatedAt FROM Note WHERE subjectId=:id OR folderId=:id OR lessonId=:id OR fileId=:id ORDER BY updatedAt DESC"
    )
    fun notes(id: String): Flow<List<Note>>

    @Query("SELECT * FROM Subject ORDER BY updatedAt DESC LIMIT 4")
    fun recentSubjects(): Flow<List<Subject>>

    @Query("SELECT * FROM Lesson ORDER BY updatedAt DESC LIMIT 5")
    fun recentLessons(): Flow<List<Lesson>>

    @Query("SELECT * FROM StudyFile ORDER BY updatedAt DESC LIMIT 4")
    fun recentFiles(): Flow<List<StudyFile>>

    @Query("SELECT * FROM Subject WHERE id=:id") suspend fun subject(id: String): Subject?

    @Query("SELECT COUNT(*) FROM Lesson WHERE subjectId=:id")
    suspend fun countLessons(id: String): Int

    @Query("SELECT * FROM Folder WHERE id=:id") suspend fun folder(id: String): Folder?

    @Query("SELECT * FROM Lesson WHERE id=:id") suspend fun lesson(id: String): Lesson?

    @Query("SELECT * FROM StudyFile WHERE id=:id") suspend fun file(id: String): StudyFile?

    @Query("SELECT * FROM StudyLink WHERE id=:id") suspend fun link(id: String): StudyLink?

    @Query("SELECT * FROM Note WHERE id=:id") suspend fun note(id: String): Note?

    @Query("SELECT * FROM Tag WHERE id=:id") suspend fun tag(id: String): Tag?

    @Query("SELECT * FROM Tag ORDER BY normalized") fun tags(): Flow<List<Tag>>

    @Query("SELECT * FROM AppSetting") fun settings(): Flow<List<AppSetting>>

    @Query("SELECT tagId FROM LessonTag WHERE lessonId=:id")
    fun lessonTags(id: String): Flow<List<String>>

    @Query("SELECT tagId FROM FileTag WHERE fileId=:id")
    fun fileTags(id: String): Flow<List<String>>

    @Query("SELECT tagId FROM NoteTag WHERE noteId=:id")
    fun noteTags(id: String): Flow<List<String>>

    @Upsert suspend fun put(value: Subject)

    @Upsert suspend fun put(value: Folder)

    @Upsert suspend fun put(value: Lesson)

    @Upsert suspend fun put(value: StudyFile)

    @Upsert suspend fun put(value: StudyLink)

    @Upsert suspend fun put(value: Note)

    @Upsert suspend fun put(value: Tag)

    @Upsert suspend fun put(value: AppSetting)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun put(value: LessonTag)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun put(value: FileTag)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun put(value: NoteTag)

    @Insert suspend fun enqueue(value: PendingDeletion)

    @Query("DELETE FROM Subject WHERE id=:id") suspend fun deleteSubject(id: String)

    @Query("DELETE FROM Folder WHERE id=:id") suspend fun deleteFolder(id: String)

    @Query("DELETE FROM Lesson WHERE id=:id") suspend fun deleteLesson(id: String)

    @Query("DELETE FROM StudyFile WHERE id=:id") suspend fun deleteFile(id: String)

    @Query("DELETE FROM StudyLink WHERE id=:id") suspend fun deleteLink(id: String)

    @Query("DELETE FROM Note WHERE id=:id") suspend fun deleteNote(id: String)

    @Query("DELETE FROM Tag WHERE id=:id") suspend fun deleteTag(id: String)

    @Query("DELETE FROM LessonTag WHERE lessonId=:id AND tagId=:tag")
    suspend fun detachLesson(id: String, tag: String)

    @Query("DELETE FROM FileTag WHERE fileId=:id AND tagId=:tag")
    suspend fun detachFile(id: String, tag: String)

    @Query("DELETE FROM NoteTag WHERE noteId=:id AND tagId=:tag")
    suspend fun detachNote(id: String, tag: String)

    @Query("SELECT * FROM PendingDeletion") suspend fun pending(): List<PendingDeletion>

    @Query("DELETE FROM PendingDeletion WHERE id=:id") suspend fun complete(id: String)

    @Query(
        "SELECT * FROM StudyFile WHERE storageType='IMPORTED' AND (id=:id OR lessonId=:id OR lessonId IN (SELECT id FROM Lesson WHERE subjectId=:id))"
    )
    suspend fun directCleanup(id: String): List<StudyFile>

    @Query(
        "WITH RECURSIVE descendants(id) AS (SELECT id FROM Folder WHERE id=:id UNION SELECT f.id FROM Folder f JOIN descendants d ON f.parentId=d.id) SELECT * FROM StudyFile WHERE storageType='IMPORTED' AND lessonId IN (SELECT id FROM Lesson WHERE folderId IN (SELECT id FROM descendants))"
    )
    suspend fun treeCleanup(id: String): List<StudyFile>

    @Query("SELECT COALESCE(MAX(position),-1)+1 FROM Subject") suspend fun nextSubject(): Int

    @Query(
        "SELECT COALESCE(MAX(position),-1)+1 FROM Folder WHERE subjectId=:subject AND parentId IS :parent"
    )
    suspend fun nextFolder(subject: String, parent: String?): Int

    @Query(
        "SELECT COALESCE(MAX(position),-1)+1 FROM Lesson WHERE subjectId=:subject AND folderId IS :parent"
    )
    suspend fun nextLesson(subject: String, parent: String?): Int

    @Query("UPDATE Subject SET updatedAt=:time WHERE id=:id")
    suspend fun touchSubject(id: String, time: Long)

    @Query("UPDATE Lesson SET updatedAt=:time WHERE id=:id")
    suspend fun touchLesson(id: String, time: Long)

    @Query(SEARCH_SQL) suspend fun search(pattern: String): List<SearchHit>

    @Query(SEARCH_SQL) fun observeSearch(pattern: String): Flow<List<SearchHit>>

    companion object {
        const val SEARCH_SQL =
            """SELECT id,title,'SUBJECT' AS kind,updatedAt FROM Subject WHERE title LIKE :pattern ESCAPE '\'
        UNION ALL SELECT id,title,'FOLDER',updatedAt FROM Folder WHERE title LIKE :pattern ESCAPE '\'
        UNION ALL SELECT id,title,'LESSON',updatedAt FROM Lesson WHERE title LIKE :pattern ESCAPE '\'
        UNION ALL SELECT id,title,'FILE',updatedAt FROM StudyFile WHERE title LIKE :pattern ESCAPE '\'
        UNION ALL SELECT id,title,'NOTE',updatedAt FROM Note WHERE title LIKE :pattern ESCAPE '\' OR body LIKE :pattern ESCAPE '\'
        UNION ALL SELECT id,title,'LINK',updatedAt FROM StudyLink WHERE title LIKE :pattern ESCAPE '\' OR url LIKE :pattern ESCAPE '\'
        UNION ALL SELECT id,title,'TAG',updatedAt FROM Tag WHERE title LIKE :pattern ESCAPE '\'
        ORDER BY updatedAt DESC LIMIT 200"""
    }
}
