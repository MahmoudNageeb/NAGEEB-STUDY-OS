package com.nageebstudyos.study.data.repository

import com.nageebstudyos.study.data.local.*
import com.nageebstudyos.study.domain.*

internal fun Subject.entry(count: Int = 0) =
    Entry(
        id,
        title,
        Kind.SUBJECT,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt,
        accent = accent,
        lessonCount = count,
    )

internal fun Folder.entry() =
    Entry(
        id,
        title,
        Kind.FOLDER,
        subjectId,
        parentId,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

internal fun Folder.node() = FolderNode(id, subjectId, parentId, title, position)

internal fun Lesson.entry() =
    Entry(
        id,
        title,
        Kind.LESSON,
        subjectId,
        folderId,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status =
            runCatching { LessonStatus.valueOf(status) }.getOrDefault(LessonStatus.NOT_STARTED),
        understanding = understanding,
        application = application,
        revision = revision,
    )

internal fun StudyFile.entry() =
    Entry(
        id,
        title,
        Kind.FILE,
        ownerId = lessonId,
        ownerKind = Kind.LESSON,
        createdAt = createdAt,
        updatedAt = updatedAt,
        location = location,
        mime = mime,
        size = size,
        storageType = StorageType.valueOf(storageType),
    )

internal fun StudyLink.entry() =
    Entry(
        id,
        title,
        Kind.LINK,
        ownerId = lessonId,
        ownerKind = Kind.LESSON,
        createdAt = createdAt,
        updatedAt = updatedAt,
        url = url,
        linkType = type,
    )

internal fun Note.entry(): Entry {
    val owner =
        when {
            subjectId != null -> Kind.SUBJECT to subjectId
            folderId != null -> Kind.FOLDER to folderId
            lessonId != null -> Kind.LESSON to lessonId
            else -> Kind.FILE to fileId
        }
    return Entry(
        id,
        title,
        Kind.NOTE,
        ownerId = owner.second,
        ownerKind = owner.first,
        createdAt = createdAt,
        updatedAt = updatedAt,
        body = body,
    )
}

internal fun Tag.entry() = Entry(id, title, Kind.TAG, createdAt = createdAt, updatedAt = updatedAt)
