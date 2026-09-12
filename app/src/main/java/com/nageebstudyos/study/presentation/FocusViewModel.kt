package com.nageebstudyos.study.presentation

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nageebstudyos.study.AppContainer
import com.nageebstudyos.study.domain.*
import com.nageebstudyos.study.focus.FocusService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FocusLinking(
    val subjectId: String? = null,
    val folderId: String? = null,
    val lessonId: String? = null,
    val subjectTitle: String = "",
    val folderTitle: String = "",
    val lessonTitle: String = "",
) {
    val linked get() = subjectId != null || lessonId != null
}

class FocusViewModel(app: Application) : AndroidViewModel(app) {
    private val container: AppContainer =
        (app.applicationContext as com.nageebstudyos.study.StudyApplication).container
    private val repo = container.v2Repository
    private val actions = container.v2Actions
    private val scheduler = container.scheduler
    private val legacy = container.repository
    private val controller = container.focusController

    val presets: StateFlow<List<FocusPreset>> =
        repo.presets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedPresetId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = selectedPresetId.asStateFlow()

    val draft = MutableStateFlow<FocusConfig?>(null)

    val goal = MutableStateFlow("")

    private val _linking = MutableStateFlow(FocusLinking())
    val linking: StateFlow<FocusLinking> = _linking.asStateFlow()

    val snapshot: StateFlow<FocusSnapshot?> = controller.snapshot
    val result: StateFlow<FocusResult?> = controller.result
    val savedId: StateFlow<String?> = controller.savedSessionId

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            presets.filter { it.isNotEmpty() }.first().let { list ->
                if (selectedPresetId.value == null) selectPreset(list.first().id)
            }
        }
    }

    fun selectPreset(id: String) {
        selectedPresetId.value = id
        val p = presets.value.firstOrNull { it.id == id } ?: return
        val current = _linking.value
        draft.value =
            FocusConfig(
                presetId = id,
                focusMinutes = p.focusMinutes,
                shortBreakMinutes = p.shortBreakMinutes,
                longBreakMinutes = p.longBreakMinutes,
                sessionsCount = p.sessionsCount,
                longBreakInterval = p.longBreakInterval,
                autoStart = p.autoStart,
                sound = p.sound,
                vibration = p.vibration,
                subjectId = current.subjectId,
                folderId = current.folderId,
                lessonId = current.lessonId,
                goal = goal.value,
            )
    }

    fun updateDraft(transform: (FocusConfig) -> FocusConfig) {
        val current = draft.value ?: return
        draft.value = transform(current)
    }

    fun setGoal(value: String) {
        goal.value = value
        updateDraft { it.copy(goal = value) }
    }

    fun linkTo(subjectId: String?, folderId: String?, lessonId: String?) {
        viewModelScope.launch {
            val lesson = lessonId?.let { legacy.get(Kind.LESSON, it) }
            val effectiveSubject = subjectId ?: lesson?.subjectId
            val effectiveFolder = folderId ?: lesson?.parentId
            val folder = effectiveFolder?.let { legacy.get(Kind.FOLDER, it) }
            val linking =
                FocusLinking(
                    subjectId = effectiveSubject,
                    folderId = effectiveFolder,
                    lessonId = lessonId,
                    subjectTitle = effectiveSubject?.let { legacy.get(Kind.SUBJECT, it)?.title }
                        .orEmpty(),
                    folderTitle = folder?.title.orEmpty(),
                    lessonTitle = lesson?.title.orEmpty(),
                )
            _linking.value = linking
            updateDraft {
                it.copy(
                    subjectId = linking.subjectId,
                    folderId = linking.folderId,
                    lessonId = linking.lessonId,
                )
            }
        }
    }

    fun clearLinking() {
        _linking.value = FocusLinking()
        updateDraft { it.copy(subjectId = null, folderId = null, lessonId = null) }
    }

    fun start(context: Context) {
        val cfg = draft.value ?: return
        val withGoal = cfg.copy(goal = goal.value)
        draft.value = withGoal
        FocusService.start(context.applicationContext, withGoal)
    }

    fun pause(context: Context) = FocusService.action(context.applicationContext, FocusService.ACTION_PAUSE)

    fun resume(context: Context) = FocusService.action(context.applicationContext, FocusService.ACTION_RESUME)

    fun skip(context: Context) = FocusService.action(context.applicationContext, FocusService.ACTION_SKIP)

    fun stop(context: Context) = FocusService.action(context.applicationContext, FocusService.ACTION_STOP)

    /** Persists the user-confirmed result; updates the auto-saved row when one exists. */
    fun saveResult(outcome: SessionResult?, notes: String, page: Int?, afterSave: () -> Unit) {
        val finished = result.value ?: return
        viewModelScope.launch {
            val built =
                actions.sessionFromResult(
                    finished,
                    goal = goal.value,
                    outcome = outcome,
                    notes = notes,
                    page = page,
                )
            val existingId = savedId.value
            val id =
                if (existingId != null) {
                    actions.saveSession(built.copy(id = existingId))
                } else {
                    actions.saveSession(built)
                }
            controller.onSessionEdited(id)
            // Refresh continue-studying state.
            val cfg = finished.config
            if (cfg.lessonId != null) {
                repo.recordOpen(cfg.subjectId, cfg.folderId, cfg.lessonId)
                repo.recordPage(cfg.lessonId, null, page)
            }
            _saved.value = true
            afterSave()
        }
    }

    fun dismissWithoutSaving() {
        controller.reset()
        _saved.value = false
    }

    /** One-tap review scheduling from the post-session flow; returns true if scheduled. */
    fun scheduleReview(plusDays: Long, afterDone: () -> Unit) {
        val finished = result.value ?: return
        val cfg = finished.config
        val lessonId = cfg.lessonId ?: run {
            controller.reset()
            afterDone()
            return
        }
        val subjectId = cfg.subjectId ?: return
        viewModelScope.launch {
            val at = actions.suggestReviewAt(StudyTime.today(), plusDays)
            val id = actions.addReview(lessonId, subjectId, at)
            scheduler.scheduleReview(id, at)
            controller.reset()
            _saved.value = false
            afterDone()
        }
    }

    fun resetPrompt() {
        controller.reset()
        _saved.value = false
    }

    fun savePreset(preset: FocusPreset) =
        viewModelScope.launch {
            runCatching { actions.savePreset(preset) }
        }

    fun deletePreset(preset: FocusPreset) =
        viewModelScope.launch {
            runCatching { actions.deletePreset(preset.id) }
        }
}
