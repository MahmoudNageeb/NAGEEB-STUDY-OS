package com.nageebstudyos.study.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities =
        [
            Subject::class,
            Folder::class,
            Lesson::class,
            StudyFile::class,
            StudyLink::class,
            Note::class,
            Tag::class,
            LessonTag::class,
            FileTag::class,
            NoteTag::class,
            AppSetting::class,
            PendingDeletion::class,
        ],
    version = 1,
    exportSchema = true,
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun dao(): StudyDao

    companion object {
        val constraints =
            object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    for (operation in listOf("INSERT", "UPDATE")) {
                        db.execSQL(
                            "CREATE TRIGGER note_owner_${operation.lowercase()} BEFORE $operation ON Note WHEN ((NEW.subjectId IS NOT NULL) + (NEW.folderId IS NOT NULL) + (NEW.lessonId IS NOT NULL) + (NEW.fileId IS NOT NULL)) != 1 BEGIN SELECT RAISE(ABORT, 'invalid note owner'); END"
                        )
                        db.execSQL(
                            "CREATE TRIGGER lesson_progress_${operation.lowercase()} BEFORE $operation ON Lesson WHEN NEW.understanding NOT BETWEEN 0 AND 100 OR NEW.application NOT BETWEEN 0 AND 100 OR NEW.revision NOT BETWEEN 0 AND 100 OR NEW.status NOT IN ('NOT_STARTED','IN_PROGRESS','NEEDS_REVIEW','MASTERED','WEAK') BEGIN SELECT RAISE(ABORT, 'invalid lesson progress'); END"
                        )
                        db.execSQL(
                            "CREATE TRIGGER file_storage_${operation.lowercase()} BEFORE $operation ON StudyFile WHEN NEW.storageType NOT IN ('LINKED','IMPORTED') BEGIN SELECT RAISE(ABORT, 'invalid storage type'); END"
                        )
                    }
                }
            }
    }
}
