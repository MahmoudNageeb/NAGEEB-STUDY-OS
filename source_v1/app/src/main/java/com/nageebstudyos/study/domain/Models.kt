package com.nageebstudyos.study.domain

import java.net.URI
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class Kind {
    SUBJECT,
    FOLDER,
    LESSON,
    FILE,
    LINK,
    NOTE,
    TAG,
}

enum class LessonStatus {
    NOT_STARTED,
    IN_PROGRESS,
    NEEDS_REVIEW,
    MASTERED,
    WEAK,
}

enum class StorageType {
    LINKED,
    IMPORTED,
}

data class Entry(
    val id: String,
    val title: String,
    val kind: Kind,
    val subjectId: String? = null,
    val parentId: String? = null,
    val ownerId: String? = null,
    val ownerKind: Kind? = null,
    val position: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val body: String = "",
    val url: String = "",
    val linkType: String = "web",
    val location: String = "",
    val mime: String = "application/octet-stream",
    val storageType: StorageType = StorageType.LINKED,
    val size: Long = -1,
    val status: LessonStatus = LessonStatus.NOT_STARTED,
    val understanding: Int = 0,
    val application: Int = 0,
    val revision: Int = 0,
    val accent: Int = 0,
    val lessonCount: Int = 0,
)

data class Scope(val kind: Kind? = null, val id: String? = null, val subjectId: String? = null)

data class FolderNode(
    val id: String,
    val subjectId: String,
    val parentId: String?,
    val title: String,
    val position: Int,
)

data class TagItem(val id: String, val title: String)

data class Library(
    val current: Entry? = null,
    val rows: List<Entry> = emptyList(),
    val folders: List<FolderNode> = emptyList(),
    val subjectTitle: String = "",
    val parentTitle: String = "",
)

enum class Problem {
    TITLE_REQUIRED,
    INVALID_URL,
    INVALID_MOVE,
    INVALID_PROGRESS,
    INVALID_OWNER,
    MISSING_ITEM,
    FILE_ACCESS,
    IMPORT_FAILED,
    DATABASE,
    DUPLICATE_TAG,
}

class StudyException(val problem: Problem, cause: Throwable? = null) :
    Exception(problem.name, cause)

fun newId(): String = UUID.randomUUID().toString()

interface StudyRepository {
    fun library(scope: Scope): Flow<Library>

    fun recent(): Flow<List<Entry>>

    fun settings(): Flow<Map<String, String>>

    fun tags(): Flow<List<TagItem>>

    fun attachedTags(kind: Kind, id: String): Flow<Set<String>>

    suspend fun get(kind: Kind, id: String): Entry?

    suspend fun save(entry: Entry): String

    suspend fun delete(kind: Kind, id: String)

    suspend fun move(kind: Kind, id: String, targetFolderId: String?)

    suspend fun reorder(kind: Kind, id: String, delta: Int)

    suspend fun search(query: String): List<Entry>

    fun observeSearch(query: String): Flow<List<Entry>>

    suspend fun setSetting(key: String, value: String)

    suspend fun setTag(kind: Kind, id: String, tagId: String, attached: Boolean)

    suspend fun folders(subjectId: String): List<FolderNode>

    suspend fun pendingFiles(): List<Pair<String, String>>

    suspend fun completeDeletion(id: String)
}

object StudyRules {
    fun title(value: String): String =
        value.trim().also { if (it.isBlank()) throw StudyException(Problem.TITLE_REQUIRED) }

    fun url(value: String): String {
        val clean = value.trim()
        val uri =
            try {
                URI(clean)
            } catch (_: Exception) {
                throw StudyException(Problem.INVALID_URL)
            }
        if (
            uri.scheme?.lowercase() !in setOf("http", "https") ||
                uri.host.isNullOrBlank() ||
                uri.userInfo != null
        )
            throw StudyException(Problem.INVALID_URL)
        return clean
    }

    fun progress(vararg values: Int) {
        if (values.any { it !in 0..100 }) throw StudyException(Problem.INVALID_PROGRESS)
    }

    fun canMove(
        id: String,
        subjectId: String,
        target: String?,
        folders: List<FolderNode>,
    ): Boolean {
        val byId = folders.associateBy { it.id }
        var cursor = target
        val visited = hashSetOf<String>()
        while (cursor != null) {
            if (cursor == id || !visited.add(cursor)) return false
            val node = byId[cursor] ?: return false
            if (node.subjectId != subjectId) return false
            cursor = node.parentId
        }
        return true
    }

    fun ancestors(id: String?, folders: List<FolderNode>): List<FolderNode> {
        val byId = folders.associateBy { it.id }
        val result = mutableListOf<FolderNode>()
        val seen = hashSetOf<String>()
        var cursor = id
        while (cursor != null && seen.add(cursor)) {
            val item = byId[cursor] ?: break
            result.add(item)
            cursor = item.parentId
        }
        return result.asReversed()
    }

    fun escapeLike(query: String): String =
        query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

class StudyActions(private val repository: StudyRepository) {
    suspend fun save(entry: Entry): String {
        val clean = entry.copy(title = StudyRules.title(entry.title))
        StudyRules.progress(clean.understanding, clean.application, clean.revision)
        if (
            clean.kind == Kind.NOTE &&
                (clean.ownerId == null ||
                    clean.ownerKind !in setOf(Kind.SUBJECT, Kind.FOLDER, Kind.LESSON, Kind.FILE))
        )
            throw StudyException(Problem.INVALID_OWNER)
        if (clean.kind == Kind.LINK)
            return repository.save(clean.copy(url = StudyRules.url(clean.url)))
        return repository.save(clean)
    }

    suspend fun move(entry: Entry, folderId: String?) {
        if (entry.kind !in setOf(Kind.FOLDER, Kind.LESSON))
            throw StudyException(Problem.INVALID_MOVE)
        val subject = entry.subjectId ?: throw StudyException(Problem.INVALID_MOVE)
        if (!StudyRules.canMove(entry.id, subject, folderId, repository.folders(subject)))
            throw StudyException(Problem.INVALID_MOVE)
        repository.move(entry.kind, entry.id, folderId)
    }

    suspend fun delete(entry: Entry) = repository.delete(entry.kind, entry.id)

    suspend fun reorder(entry: Entry, delta: Int) =
        repository.reorder(entry.kind, entry.id, delta.coerceIn(-1, 1))
}
