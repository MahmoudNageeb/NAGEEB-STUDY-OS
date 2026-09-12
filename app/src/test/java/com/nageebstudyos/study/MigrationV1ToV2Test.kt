package com.nageebstudyos.study

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.nageebstudyos.study.data.local.StudyDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves the V1 -> V2 path is additive: pre-existing subjects/lessons/settings survive and
 * every new table exists with the exact schema Room expects at version 2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationV1ToV2Test {
    private val name = "migration-v1-v2.db"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            StudyDatabase::class.java,
        )

    @Test
    fun v1DataRemainsIntactAndV2TablesAreCreated() {
        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                "INSERT INTO Subject(id,title,accent,position,createdAt,updatedAt) " +
                    "VALUES('s1','Mathematics',1,0,1000,1000)"
            )
            db.execSQL(
                "INSERT INTO Folder(id,subjectId,parentId,title,position,createdAt,updatedAt) " +
                    "VALUES('f1','s1',NULL,'Calculus',0,1000,1000)"
            )
            db.execSQL(
                "INSERT INTO Lesson(id,subjectId,folderId,title,position,status,understanding,application,revision,createdAt,updatedAt) " +
                    "VALUES('l1','s1','f1','Lecture 08',0,'IN_PROGRESS',40,30,20,1000,1000)"
            )
            db.execSQL(
                "INSERT INTO AppSetting(`key`,value,updatedAt) VALUES('theme','dark',1000)"
            )
        }

        helper
            .runMigrationsAndValidate(name, 2, true, StudyDatabase.MIGRATION_1_2)
            .use { db ->
                db.query("SELECT title FROM Subject").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Mathematics", cursor.getString(0))
                }
                db.query("SELECT title, status FROM Lesson").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Lecture 08", cursor.getString(0))
                    assertEquals("IN_PROGRESS", cursor.getString(1))
                }
                db.query("SELECT value FROM AppSetting WHERE `key`='theme'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("dark", cursor.getString(0))
                }
                listOf(
                    "Review",
                    "StudySession",
                    "Plan",
                    "Task",
                    "FocusPreset",
                    "LastActive",
                ).forEach { table ->
                    db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("table $table should start empty", 0, cursor.getInt(0))
                    }
                }
                // New validation triggers are live on migrated databases.
                db.execSQL(
                    "INSERT INTO Review(id,lessonId,subjectId,scheduledAt,scheduledDay,completedAt,status,note,createdAt,updatedAt) " +
                        "VALUES('r1','l1','s1',2000,2,NULL,'PENDING','',2000,2000)"
                )
                db.query("SELECT COUNT(*) FROM Review").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }
            }
    }
}
