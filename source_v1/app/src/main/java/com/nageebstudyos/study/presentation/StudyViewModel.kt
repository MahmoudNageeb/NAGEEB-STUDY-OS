package com.nageebstudyos.study.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nageebstudyos.study.AppContainer
import com.nageebstudyos.study.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LoadState<T>(val data: T, val loading: Boolean = true, val error: Problem? = null)

sealed interface Notice {
    data class Failure(val problem: Problem) : Notice

    data object Saved : Notice

    data object Deleted : Notice
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class StudyViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val mutex = Mutex()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _editor = MutableStateFlow<Entry?>(null)
    val editor = _editor.asStateFlow()
    private val _editorError = MutableStateFlow<Problem?>(null)
    val editorError = _editorError.asStateFlow()
    private val _library = MutableStateFlow(LoadState(Library()))
    val library = _library.asStateFlow()
    private var scope = Scope()
    private var libraryJob: Job? = null
    private val noticesChannel = Channel<Notice>(Channel.BUFFERED)
    val notices = noticesChannel.receiveAsFlow()
    private val _settings = MutableStateFlow<Map<String, String>?>(null)
    val settings = _settings.asStateFlow()
    private val _settingsError = MutableStateFlow<Problem?>(null)
    val settingsError = _settingsError.asStateFlow()
    private var settingsJob: Job? = null
    private val refresh = MutableStateFlow(0)

    fun refreshLists() {
        refresh.value += 1
    }

    val recent =
        refresh
            .flatMapLatest {
                repository
                    .recent()
                    .map { LoadState(it, false) }
                    .onStart { emit(LoadState(emptyList())) }
                    .catch { emit(LoadState(emptyList(), false, Problem.DATABASE)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadState(emptyList()))
    val tagState =
        refresh
            .flatMapLatest {
                repository
                    .tags()
                    .map { LoadState(it, false) }
                    .onStart { emit(LoadState(emptyList())) }
                    .catch { emit(LoadState(emptyList(), false, Problem.DATABASE)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadState(emptyList()))
    val tags =
        tagState
            .map { it.data }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val query = MutableStateFlow("")
    val search =
        combine(query, refresh) { text, revision -> text to revision }
            .debounce(250)
            .flatMapLatest { (text, _) ->
                repository
                    .observeSearch(text)
                    .map { LoadState(it, false) }
                    .onStart { emit(LoadState(emptyList(), text.isNotBlank())) }
                    .catch { emit(LoadState(emptyList(), false, Problem.DATABASE)) }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                LoadState(emptyList(), false),
            )
    private val _tagTarget = MutableStateFlow<Entry?>(null)
    val tagTarget = _tagTarget.asStateFlow()
    val attachedTags =
        _tagTarget
            .flatMapLatest { e ->
                if (e == null) flowOf(emptySet())
                else
                    repository
                        .attachedTags(e.kind, e.id)
                        .onStart { emit(emptySet()) }
                        .catch { noticesChannel.send(Notice.Failure(Problem.DATABASE)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    private val _moveTarget = MutableStateFlow<Entry?>(null)
    val moveTarget = _moveTarget.asStateFlow()
    private val _folders = MutableStateFlow<List<FolderNode>>(emptyList())
    val folders = _folders.asStateFlow()

    init {
        retrySettings()
        selectScope(Scope())
        operation(notify = false) { container.storage.cleanPending(repository) }
    }

    fun retrySettings() {
        settingsJob?.cancel()
        _settingsError.value = null
        settingsJob =
            viewModelScope.launch {
                repository
                    .settings()
                    .catch { _settingsError.value = Problem.DATABASE }
                    .collect { _settings.value = it }
            }
    }

    fun selectScope(value: Scope) {
        scope = value
        libraryJob?.cancel()
        _library.value = LoadState(Library())
        libraryJob =
            viewModelScope.launch {
                repository
                    .library(value)
                    .catch { _library.value = LoadState(Library(), false, Problem.DATABASE) }
                    .collect { result ->
                        _library.value =
                            LoadState(
                                result,
                                false,
                                if (value.id != null && result.current == null) Problem.MISSING_ITEM
                                else null,
                            )
                    }
            }
    }

    fun retry() = selectScope(scope)

    private fun operation(notify: Boolean = true, block: suspend () -> Unit) =
        viewModelScope.launch {
            mutex.withLock {
                _busy.value = true
                try {
                    block()
                    if (notify) noticesChannel.send(Notice.Saved)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    noticesChannel.send(
                        Notice.Failure((e as? StudyException)?.problem ?: Problem.DATABASE)
                    )
                } finally {
                    _busy.value = false
                }
            }
        }

    fun edit(entry: Entry) =
        operation(false) {
            _editorError.value = null
            _editor.value =
                repository.get(entry.kind, entry.id) ?: throw StudyException(Problem.MISSING_ITEM)
        }

    fun create(kind: Kind, context: Scope = scope) {
        _editorError.value = null
        _editor.value =
            Entry(
                newId(),
                "",
                kind,
                subjectId =
                    when {
                        kind !in setOf(Kind.FOLDER, Kind.LESSON) -> null
                        context.kind == Kind.SUBJECT -> context.id
                        else -> context.subjectId
                    },
                parentId =
                    context.id.takeIf {
                        context.kind == Kind.FOLDER && kind in setOf(Kind.FOLDER, Kind.LESSON)
                    },
                ownerId = context.id.takeIf { kind in setOf(Kind.NOTE, Kind.LINK, Kind.FILE) },
                ownerKind = context.kind.takeIf { kind in setOf(Kind.NOTE, Kind.LINK, Kind.FILE) },
            )
    }

    fun dismissEditor() {
        if (!_busy.value) {
            _editor.value = null
            _editorError.value = null
        }
    }

    fun save(entry: Entry) =
        viewModelScope.launch {
            mutex.withLock {
                _busy.value = true
                _editorError.value = null
                try {
                    container.actions.save(entry)
                    _editor.value = null
                    noticesChannel.send(Notice.Saved)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _editorError.value = (e as? StudyException)?.problem ?: Problem.DATABASE
                } finally {
                    _busy.value = false
                }
            }
        }

    fun delete(entry: Entry, after: () -> Unit) =
        operation(false) {
            container.actions.delete(entry)
            after()
            noticesChannel.send(Notice.Deleted)
            container.storage.cleanPending(repository)
        }

    fun reorder(entry: Entry, delta: Int) = operation { container.actions.reorder(entry, delta) }

    fun prepareMove(entry: Entry) =
        operation(false) {
            _folders.value = repository.folders(requireNotNull(entry.subjectId))
            _moveTarget.value = entry
        }

    fun dismissMove() {
        _moveTarget.value = null
    }

    fun move(entry: Entry, parentId: String?) = operation {
        container.actions.move(entry, parentId)
        _moveTarget.value = null
    }

    fun setting(key: String, value: String) = operation(false) { repository.setSetting(key, value) }

    fun showTags(entry: Entry?) {
        _tagTarget.value = entry
    }

    fun assignTag(entry: Entry, tagId: String, attached: Boolean) =
        operation(false) { repository.setTag(entry.kind, entry.id, tagId, attached) }

    fun attach(uri: Uri, lessonId: String, imported: Boolean, fallbackTitle: String) = operation {
        val file = container.storage.attach(uri, lessonId, imported, fallbackTitle)
        try {
            container.actions.save(file)
        } catch (e: Exception) {
            if (file.storageType == StorageType.IMPORTED)
                withContext(NonCancellable) { container.storage.removeCopy(file.location) }
            throw e
        }
    }

    fun openFile(entry: Entry) = operation(false) { container.storage.open(entry) }

    fun openLink(entry: Entry) = operation(false) { container.storage.openLink(entry.url) }
}
