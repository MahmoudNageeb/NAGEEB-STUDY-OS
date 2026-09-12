package com.nageebstudyos.study.data.local

import androidx.room.*

@Entity(indices = [Index("position"), Index("updatedAt")])
data class Subject(
    @PrimaryKey val id: String,
    val title: String,
    val accent: Int = 0,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Subject::class,
                parentColumns = ["id"],
                childColumns = ["subjectId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Folder::class,
                parentColumns = ["subjectId", "id"],
                childColumns = ["subjectId", "parentId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
        ],
    indices =
        [
            Index("subjectId", "id", unique = true),
            Index("subjectId", "parentId"),
            Index("parentId"),
            Index("position"),
        ],
)
data class Folder(
    @PrimaryKey val id: String,
    val subjectId: String,
    val parentId: String?,
    val title: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Subject::class,
                parentColumns = ["id"],
                childColumns = ["subjectId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Folder::class,
                parentColumns = ["subjectId", "id"],
                childColumns = ["subjectId", "folderId"],
                onDelete = ForeignKey.CASCADE,
                deferred = true,
            ),
        ],
    indices =
        [Index("subjectId", "folderId"), Index("folderId"), Index("updatedAt"), Index("position")],
)
data class Lesson(
    @PrimaryKey val id: String,
    val subjectId: String,
    val folderId: String?,
    val title: String,
    val position: Int,
    val status: String,
    val understanding: Int,
    val application: Int,
    val revision: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Lesson::class,
                parentColumns = ["id"],
                childColumns = ["lessonId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("lessonId"), Index("updatedAt")],
)
data class StudyFile(
    @PrimaryKey val id: String,
    val lessonId: String,
    val title: String,
    val location: String,
    val mime: String,
    val size: Long,
    val storageType: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Lesson::class,
                parentColumns = ["id"],
                childColumns = ["lessonId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("lessonId")],
)
data class StudyLink(
    @PrimaryKey val id: String,
    val lessonId: String,
    val title: String,
    val url: String,
    val type: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Subject::class,
                parentColumns = ["id"],
                childColumns = ["subjectId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Folder::class,
                parentColumns = ["id"],
                childColumns = ["folderId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Lesson::class,
                parentColumns = ["id"],
                childColumns = ["lessonId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = StudyFile::class,
                parentColumns = ["id"],
                childColumns = ["fileId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices =
        [
            Index("subjectId"),
            Index("folderId"),
            Index("lessonId"),
            Index("fileId"),
            Index("updatedAt"),
        ],
)
data class Note(
    @PrimaryKey val id: String,
    val subjectId: String?,
    val folderId: String?,
    val lessonId: String?,
    val fileId: String?,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(indices = [Index("normalized", unique = true)])
data class Tag(
    @PrimaryKey val id: String,
    val title: String,
    val normalized: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Lesson::class,
                parentColumns = ["id"],
                childColumns = ["lessonId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Tag::class,
                parentColumns = ["id"],
                childColumns = ["tagId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("lessonId", "tagId", unique = true), Index("tagId")],
)
data class LessonTag(@PrimaryKey val id: String, val lessonId: String, val tagId: String)

@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = StudyFile::class,
                parentColumns = ["id"],
                childColumns = ["fileId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Tag::class,
                parentColumns = ["id"],
                childColumns = ["tagId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("fileId", "tagId", unique = true), Index("tagId")],
)
data class FileTag(@PrimaryKey val id: String, val fileId: String, val tagId: String)

@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Note::class,
                parentColumns = ["id"],
                childColumns = ["noteId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Tag::class,
                parentColumns = ["id"],
                childColumns = ["tagId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("noteId", "tagId", unique = true), Index("tagId")],
)
data class NoteTag(@PrimaryKey val id: String, val noteId: String, val tagId: String)

@Entity data class AppSetting(@PrimaryKey val key: String, val value: String, val updatedAt: Long)

@Entity data class PendingDeletion(@PrimaryKey val id: String, val path: String)

data class SearchHit(val id: String, val title: String, val kind: String, val updatedAt: Long)

data class SubjectCount(@Embedded val subject: Subject, val lessonCount: Int)
