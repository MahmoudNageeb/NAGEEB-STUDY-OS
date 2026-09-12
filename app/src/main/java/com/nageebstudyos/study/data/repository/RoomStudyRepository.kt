package com.nageebstudyos.study.data.repository

import androidx.room.withTransaction
import com.nageebstudyos.study.data.local.*
import com.nageebstudyos.study.domain.*
import java.util.Locale
import kotlinx.coroutines.flow.*

class RoomStudyRepository(private val db: StudyDatabase) : StudyRepository {
    private val dao = db.dao()

    override fun library(scope: Scope): Flow<Library> {
        if (scope.kind == null)
            return dao.subjects().map {
                Library(rows = it.map { s -> s.subject.entry(s.lessonCount) })
            }
        val id = requireNotNull(scope.id)
        return when (scope.kind) {
            Kind.SUBJECT,
            Kind.FOLDER -> {
                val subject = scope.subjectId ?: id
                val parent = id.takeIf { scope.kind == Kind.FOLDER }
                val current =
                    if (scope.kind == Kind.SUBJECT) dao.subjectFlow(id).map { it?.entry() }
                    else dao.folderFlow(id).map { it?.entry() }
                combine(
                    current,
                    dao.children(subject, parent),
                    dao.lessons(subject, parent),
                    dao.notes(id),
                    dao.folderTree(subject),
                ) { c, f, l, n, tree ->
                    Library(
                        c,
                        f.map { it.entry() } + l.map { it.entry() } + n.map { it.entry() },
                        tree.map { it.node() },
                        dao.subject(subject)?.title.orEmpty(),
                        c?.parentId?.let { dao.folder(it)?.title }.orEmpty(),
                    )
                }
            }
            Kind.LESSON ->
                combine(dao.lessonFlow(id), dao.files(id), dao.links(id), dao.notes(id)) {
                    c,
                    f,
                    l,
                    n ->
                    Library(
                        c?.entry(),
                        f.map { it.entry() } + l.map { it.entry() } + n.map { it.entry() },
                        subjectTitle = c?.subjectId?.let { dao.subject(it)?.title }.orEmpty(),
                        parentTitle = c?.folderId?.let { dao.folder(it)?.title }.orEmpty(),
                    )
                }
            Kind.FILE ->
                combine(dao.fileFlow(id), dao.notes(id)) { f, n ->
                    Library(f?.entry(), n.map { it.entry() })
                }
            else -> flowOf(Library())
        }
    }

    override fun recent(): Flow<List<Entry>> =
        combine(dao.subjects(), dao.recentLessons(), dao.recentFiles()) { s, l, f ->
            s.sortedByDescending { it.subject.updatedAt }
                .take(4)
                .map { it.subject.entry(it.lessonCount) } +
                l.map { it.entry() } +
                f.map { it.entry() }
        }

    override fun settings() = dao.settings().map { list -> list.associate { it.key to it.value } }

    override fun tags() = dao.tags().map { list -> list.map { TagItem(it.id, it.title) } }

    override fun attachedTags(kind: Kind, id: String): Flow<Set<String>> =
        when (kind) {
            Kind.LESSON -> dao.lessonTags(id)
            Kind.FILE -> dao.fileTags(id)
            Kind.NOTE -> dao.noteTags(id)
            else -> flowOf(emptyList())
        }.map { it.toSet() }

    override suspend fun get(kind: Kind, id: String): Entry? =
        when (kind) {
            Kind.SUBJECT -> dao.subject(id)?.entry(dao.countLessons(id))
            Kind.FOLDER -> dao.folder(id)?.entry()
            Kind.LESSON -> dao.lesson(id)?.entry()
            Kind.FILE -> dao.file(id)?.entry()
            Kind.LINK -> dao.link(id)?.entry()
            Kind.NOTE -> dao.note(id)?.entry()
            Kind.TAG -> dao.tag(id)?.entry()
        }

    override suspend fun save(entry: Entry): String =
        db.withTransaction {
            val time = System.currentTimeMillis()
            val old = get(entry.kind, entry.id)
            val created = old?.createdAt ?: time
            val e = entry.copy(title = StudyRules.title(entry.title))
            StudyRules.progress(e.understanding, e.application, e.revision)
            when (e.kind) {
                Kind.SUBJECT ->
                    dao.put(
                        Subject(
                            e.id,
                            e.title,
                            e.accent,
                            old?.position ?: dao.nextSubject(),
                            created,
                            time,
                        )
                    )
                Kind.FOLDER -> {
                    val subject = e.subjectId ?: throw StudyException(Problem.INVALID_OWNER)
                    if (!StudyRules.canMove(e.id, subject, e.parentId, folders(subject)))
                        throw StudyException(Problem.INVALID_MOVE)
                    dao.put(
                        Folder(
                            e.id,
                            subject,
                            e.parentId,
                            e.title,
                            old?.position ?: dao.nextFolder(subject, e.parentId),
                            created,
                            time,
                        )
                    )
                }
                Kind.LESSON ->
                    dao.put(
                        Lesson(
                            e.id,
                            requireNotNull(e.subjectId),
                            e.parentId,
                            e.title,
                            old?.position ?: dao.nextLesson(e.subjectId, e.parentId),
                            e.status.name,
                            e.understanding,
                            e.application,
                            e.revision,
                            created,
                            time,
                        )
                    )
                Kind.FILE ->
                    dao.put(
                        StudyFile(
                            e.id,
                            requireNotNull(e.ownerId),
                            e.title,
                            e.location,
                            e.mime,
                            e.size,
                            e.storageType.name,
                            created,
                            time,
                        )
                    )
                Kind.LINK ->
                    dao.put(
                        StudyLink(
                            e.id,
                            requireNotNull(e.ownerId),
                            e.title,
                            StudyRules.url(e.url),
                            e.linkType,
                            created,
                            time,
                        )
                    )
                Kind.NOTE -> {
                    if (
                        e.ownerId == null ||
                            e.ownerKind !in setOf(Kind.SUBJECT, Kind.FOLDER, Kind.LESSON, Kind.FILE)
                    )
                        throw StudyException(Problem.INVALID_OWNER)
                    dao.put(
                        Note(
                            e.id,
                            e.ownerId.takeIf { e.ownerKind == Kind.SUBJECT },
                            e.ownerId.takeIf { e.ownerKind == Kind.FOLDER },
                            e.ownerId.takeIf { e.ownerKind == Kind.LESSON },
                            e.ownerId.takeIf { e.ownerKind == Kind.FILE },
                            e.title,
                            e.body,
                            created,
                            time,
                        )
                    )
                }
                Kind.TAG -> {
                    val normalized = e.title.lowercase(Locale.ROOT)
                    if (dao.tags().first().any { it.normalized == normalized && it.id != e.id })
                        throw StudyException(Problem.DUPLICATE_TAG)
                    dao.put(Tag(e.id, e.title, normalized, created, time))
                }
            }
            touch(e, time)
            e.id
        }

    override suspend fun touch(kind: Kind, id: String) {
        val e = get(kind, id) ?: return
        touch(e, System.currentTimeMillis())
    }

    private suspend fun touch(e: Entry, time: Long) {
        when {
            e.kind == Kind.LESSON -> {
                dao.touchLesson(e.id, time)
                e.subjectId?.let { dao.touchSubject(it, time) }
            }
            e.subjectId != null -> dao.touchSubject(e.subjectId, time)
            e.ownerKind == Kind.SUBJECT && e.ownerId != null -> dao.touchSubject(e.ownerId, time)
            e.ownerKind == Kind.FOLDER && e.ownerId != null ->
                dao.folder(e.ownerId)?.let { dao.touchSubject(it.subjectId, time) }
            e.ownerKind == Kind.LESSON && e.ownerId != null ->
                dao.lesson(e.ownerId)?.let {
                    dao.touchLesson(it.id, time)
                    dao.touchSubject(it.subjectId, time)
                }
            e.ownerKind == Kind.FILE && e.ownerId != null ->
                dao.file(e.ownerId)?.let { f ->
                    dao.lesson(f.lessonId)?.let { l ->
                        dao.touchLesson(l.id, time)
                        dao.touchSubject(l.subjectId, time)
                    }
                }
        }
    }

    override suspend fun delete(kind: Kind, id: String) =
        db.withTransaction {
            val item = get(kind, id) ?: throw StudyException(Problem.MISSING_ITEM)
            val files = if (kind == Kind.FOLDER) dao.treeCleanup(id) else dao.directCleanup(id)
            files.forEach { dao.enqueue(PendingDeletion(newId(), it.location)) }
            touch(item, System.currentTimeMillis())
            when (kind) {
                Kind.SUBJECT -> {
                    deleteFoldersBottomUp(id, null)
                    dao.deleteSubject(id)
                }
                Kind.FOLDER -> {
                    deleteFoldersBottomUp(requireNotNull(item.subjectId), id)
                }
                Kind.LESSON -> dao.deleteLesson(id)
                Kind.FILE -> dao.deleteFile(id)
                Kind.LINK -> dao.deleteLink(id)
                Kind.NOTE -> dao.deleteNote(id)
                Kind.TAG -> dao.deleteTag(id)
            }
        }

    private suspend fun deleteFoldersBottomUp(subjectId: String, rootId: String?) {
        // Iterative post-order avoids SQLite's recursive cascade depth limit.
        val all = dao.folders(subjectId)
        val children = all.groupBy { it.parentId }
        val stack = java.util.ArrayDeque<String>()
        if (rootId != null) stack.push(rootId)
        else children[null].orEmpty().forEach { stack.push(it.id) }
        val ordered = mutableListOf<String>()
        val seen = hashSetOf<String>()
        while (stack.isNotEmpty()) {
            val next = stack.pop()
            if (!seen.add(next)) continue
            ordered.add(next)
            children[next].orEmpty().forEach { stack.push(it.id) }
        }
        ordered.asReversed().forEach { dao.deleteFolder(it) }
    }

    override suspend fun move(kind: Kind, id: String, targetFolderId: String?) =
        db.withTransaction {
            val e = get(kind, id) ?: throw StudyException(Problem.MISSING_ITEM)
            val subject = e.subjectId ?: throw StudyException(Problem.INVALID_MOVE)
            if (!StudyRules.canMove(id, subject, targetFolderId, folders(subject)))
                throw StudyException(Problem.INVALID_MOVE)
            val time = System.currentTimeMillis()
            when (kind) {
                Kind.FOLDER ->
                    dao.folder(id)?.let {
                        dao.put(
                            it.copy(
                                parentId = targetFolderId,
                                position = dao.nextFolder(subject, targetFolderId),
                                updatedAt = time,
                            )
                        )
                    }
                Kind.LESSON ->
                    dao.lesson(id)?.let {
                        dao.put(
                            it.copy(
                                folderId = targetFolderId,
                                position = dao.nextLesson(subject, targetFolderId),
                                updatedAt = time,
                            )
                        )
                    }
                else -> throw StudyException(Problem.INVALID_MOVE)
            }
            dao.touchSubject(subject, time)
        }

    override suspend fun reorder(kind: Kind, id: String, delta: Int) =
        db.withTransaction {
            val e = get(kind, id) ?: throw StudyException(Problem.MISSING_ITEM)
            val items =
                when (kind) {
                    Kind.SUBJECT -> dao.subjects().first().map { it.subject.entry() }
                    Kind.FOLDER ->
                        dao.children(requireNotNull(e.subjectId), e.parentId).first().map {
                            it.entry()
                        }
                    Kind.LESSON ->
                        dao.lessons(requireNotNull(e.subjectId), e.parentId).first().map {
                            it.entry()
                        }
                    else -> return@withTransaction
                }.toMutableList()
            val from = items.indexOfFirst { it.id == id }
            val to = from + delta
            if (from < 0 || to !in items.indices) return@withTransaction
            items.add(to, items.removeAt(from))
            val time = System.currentTimeMillis()
            items.forEachIndexed { index, row ->
                when (kind) {
                    Kind.SUBJECT ->
                        dao.subject(row.id)?.let {
                            dao.put(it.copy(position = index, updatedAt = time))
                        }
                    Kind.FOLDER ->
                        dao.folder(row.id)?.let {
                            dao.put(it.copy(position = index, updatedAt = time))
                        }
                    Kind.LESSON ->
                        dao.lesson(row.id)?.let {
                            dao.put(it.copy(position = index, updatedAt = time))
                        }
                    else -> Unit
                }
            }
        }

    override suspend fun search(query: String): List<Entry> {
        if (query.isBlank()) return emptyList()
        return searchEntries(dao.search("%${StudyRules.escapeLike(query.trim())}%"))
    }

    override fun observeSearch(query: String): Flow<List<Entry>> =
        if (query.isBlank()) flowOf(emptyList())
        else dao.observeSearch("%${StudyRules.escapeLike(query.trim())}%").map { searchEntries(it) }

    private suspend fun searchEntries(hits: List<SearchHit>): List<Entry> =
        hits.mapNotNull { hit ->
            // Note bodies are never materialized for search-result rows.
            if (hit.kind == Kind.NOTE.name)
                Entry(hit.id, hit.title, Kind.NOTE, updatedAt = hit.updatedAt)
            else get(Kind.valueOf(hit.kind), hit.id)
        }

    override suspend fun setSetting(key: String, value: String) =
        dao.put(AppSetting(key, value, System.currentTimeMillis()))

    override suspend fun setTag(kind: Kind, id: String, tagId: String, attached: Boolean) =
        db.withTransaction {
            when (kind) {
                Kind.LESSON ->
                    if (attached) dao.put(LessonTag(newId(), id, tagId))
                    else dao.detachLesson(id, tagId)
                Kind.FILE ->
                    if (attached) dao.put(FileTag(newId(), id, tagId))
                    else dao.detachFile(id, tagId)
                Kind.NOTE ->
                    if (attached) dao.put(NoteTag(newId(), id, tagId))
                    else dao.detachNote(id, tagId)
                else -> throw StudyException(Problem.INVALID_OWNER)
            }
            get(kind, id)?.let { touch(it, System.currentTimeMillis()) }
            Unit
        }

    override suspend fun folders(subjectId: String) = dao.folders(subjectId).map { it.node() }

    override suspend fun pendingFiles() = dao.pending().map { it.id to it.path }

    override suspend fun completeDeletion(id: String) = dao.complete(id)
}
