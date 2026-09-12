package com.nageebstudyos.study.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities =
        [
            // V1
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
            // V2
            ReviewEntity::class,
            StudySessionEntity::class,
            PlanEntity::class,
            TaskEntity::class,
            FocusPresetEntity::class,
            LastActiveEntity::class,
        ],
    version = 2,
    exportSchema = true,
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun dao(): StudyDao

    abstract fun v2Dao(): V2Dao

    companion object {
        /**
         * Additive, non-destructive migration. Every V1 table is untouched; the new V2
         * tables are created with the exact columns/indices Room expects at version 2 and
         * the same CHECK triggers that a fresh install receives.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    V2_SCHEMA.forEach(db::execSQL)
                    V2_TRIGGERS.forEach(db::execSQL)
                }
            }

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
                    V2_TRIGGERS.forEach(db::execSQL)
                }
            }

        // Exact DDL Room generates from the entities (order and nullability matter).
        private val V2_SCHEMA =
            listOf(
                """
                CREATE TABLE IF NOT EXISTS `Review` (
                    `id` TEXT NOT NULL,
                    `lessonId` TEXT NOT NULL,
                    `subjectId` TEXT NOT NULL,
                    `scheduledAt` INTEGER NOT NULL,
                    `scheduledDay` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    `status` TEXT NOT NULL,
                    `note` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`lessonId`) REFERENCES `Lesson`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
                "CREATE INDEX IF NOT EXISTS `index_Review_lessonId` ON `Review` (`lessonId`)",
                "CREATE INDEX IF NOT EXISTS `index_Review_subjectId` ON `Review` (`subjectId`)",
                "CREATE INDEX IF NOT EXISTS `index_Review_scheduledDay` ON `Review` (`scheduledDay`)",
                "CREATE INDEX IF NOT EXISTS `index_Review_status_scheduledDay` ON `Review` (`status`, `scheduledDay`)",
                """
                CREATE TABLE IF NOT EXISTS `StudySession` (
                    `id` TEXT NOT NULL,
                    `subjectId` TEXT,
                    `folderId` TEXT,
                    `lessonId` TEXT,
                    `startTime` INTEGER NOT NULL,
                    `endTime` INTEGER NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `goal` TEXT NOT NULL,
                    `result` TEXT,
                    `notes` TEXT NOT NULL,
                    `lastPage` INTEGER,
                    `dayIndex` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`subjectId`) REFERENCES `Subject`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`lessonId`) REFERENCES `Lesson`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
                "CREATE INDEX IF NOT EXISTS `index_StudySession_subjectId` ON `StudySession` (`subjectId`)",
                "CREATE INDEX IF NOT EXISTS `index_StudySession_lessonId` ON `StudySession` (`lessonId`)",
                "CREATE INDEX IF NOT EXISTS `index_StudySession_startTime` ON `StudySession` (`startTime`)",
                "CREATE INDEX IF NOT EXISTS `index_StudySession_dayIndex` ON `StudySession` (`dayIndex`)",
                """
                CREATE TABLE IF NOT EXISTS `Plan` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `subjectId` TEXT,
                    `startDay` INTEGER NOT NULL,
                    `endDay` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`subjectId`) REFERENCES `Subject`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
                "CREATE INDEX IF NOT EXISTS `index_Plan_subjectId` ON `Plan` (`subjectId`)",
                "CREATE INDEX IF NOT EXISTS `index_Plan_startDay` ON `Plan` (`startDay`)",
                "CREATE INDEX IF NOT EXISTS `index_Plan_endDay` ON `Plan` (`endDay`)",
                """
                CREATE TABLE IF NOT EXISTS `Task` (
                    `id` TEXT NOT NULL,
                    `planId` TEXT,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `day` INTEGER,
                    `dueDay` INTEGER,
                    `subjectId` TEXT,
                    `lessonId` TEXT,
                    `status` TEXT NOT NULL,
                    `priority` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`planId`) REFERENCES `Plan`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`subjectId`) REFERENCES `Subject`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`lessonId`) REFERENCES `Lesson`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
                "CREATE INDEX IF NOT EXISTS `index_Task_planId` ON `Task` (`planId`)",
                "CREATE INDEX IF NOT EXISTS `index_Task_subjectId` ON `Task` (`subjectId`)",
                "CREATE INDEX IF NOT EXISTS `index_Task_lessonId` ON `Task` (`lessonId`)",
                "CREATE INDEX IF NOT EXISTS `index_Task_status` ON `Task` (`status`)",
                "CREATE INDEX IF NOT EXISTS `index_Task_day` ON `Task` (`day`)",
                "CREATE INDEX IF NOT EXISTS `index_Task_dueDay` ON `Task` (`dueDay`)",
                """
                CREATE TABLE IF NOT EXISTS `FocusPreset` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `focusMinutes` INTEGER NOT NULL,
                    `shortBreakMinutes` INTEGER NOT NULL,
                    `longBreakMinutes` INTEGER NOT NULL,
                    `sessionsCount` INTEGER NOT NULL,
                    `longBreakInterval` INTEGER NOT NULL,
                    `autoStart` INTEGER NOT NULL,
                    `sound` INTEGER NOT NULL,
                    `vibration` INTEGER NOT NULL,
                    `builtIn` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_FocusPreset_position` ON `FocusPreset` (`position`)",
                """
                CREATE TABLE IF NOT EXISTS `LastActive` (
                    `id` INTEGER NOT NULL,
                    `subjectId` TEXT,
                    `folderId` TEXT,
                    `lessonId` TEXT,
                    `fileId` TEXT,
                    `page` INTEGER,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )

        private val V2_TRIGGERS =
            listOf(
                """
                CREATE TRIGGER review_valid_insert BEFORE INSERT ON Review WHEN NEW.status NOT IN ('PENDING','DONE','SKIPPED') OR NEW.scheduledDay IS NULL BEGIN SELECT RAISE(ABORT, 'invalid review'); END
                """.trimIndent(),
                """
                CREATE TRIGGER review_valid_update BEFORE UPDATE ON Review WHEN NEW.status NOT IN ('PENDING','DONE','SKIPPED') OR NEW.scheduledDay IS NULL BEGIN SELECT RAISE(ABORT, 'invalid review'); END
                """.trimIndent(),
                """
                CREATE TRIGGER session_valid_insert BEFORE INSERT ON StudySession WHEN NEW.duration < 0 OR (NEW.result IS NOT NULL AND NEW.result NOT IN ('YES','PARTIALLY','NO')) OR NEW.dayIndex IS NULL BEGIN SELECT RAISE(ABORT, 'invalid session'); END
                """.trimIndent(),
                """
                CREATE TRIGGER session_valid_update BEFORE UPDATE ON StudySession WHEN NEW.duration < 0 OR (NEW.result IS NOT NULL AND NEW.result NOT IN ('YES','PARTIALLY','NO')) OR NEW.dayIndex IS NULL BEGIN SELECT RAISE(ABORT, 'invalid session'); END
                """.trimIndent(),
                """
                CREATE TRIGGER task_valid_insert BEFORE INSERT ON Task WHEN NEW.status NOT IN ('PENDING','IN_PROGRESS','DONE') OR NEW.priority NOT IN ('LOW','NORMAL','HIGH') BEGIN SELECT RAISE(ABORT, 'invalid task'); END
                """.trimIndent(),
                """
                CREATE TRIGGER task_valid_update BEFORE UPDATE ON Task WHEN NEW.status NOT IN ('PENDING','IN_PROGRESS','DONE') OR NEW.priority NOT IN ('LOW','NORMAL','HIGH') BEGIN SELECT RAISE(ABORT, 'invalid task'); END
                """.trimIndent(),
            )
    }
}
