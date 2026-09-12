package com.nageebstudyos.study.focus

import com.nageebstudyos.study.domain.FocusConfig
import com.nageebstudyos.study.domain.FocusResult
import com.nageebstudyos.study.domain.FocusSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bridge between [FocusService] (which owns the ticking engine) and Compose.
 * Held by [com.nageebstudyos.study.AppContainer]; survives configuration changes.
 */
class FocusController {
    private val _config = MutableStateFlow<FocusConfig?>(null)
    val config: StateFlow<FocusConfig?> = _config.asStateFlow()

    private val _snapshot = MutableStateFlow<FocusSnapshot?>(null)
    val snapshot: StateFlow<FocusSnapshot?> = _snapshot.asStateFlow()

    private val _result = MutableStateFlow<FocusResult?>(null)
    val result: StateFlow<FocusResult?> = _result.asStateFlow()

    /** Session id once the service auto-persisted the finished run (may still be edited). */
    private val _savedSessionId = MutableStateFlow<String?>(null)
    val savedSessionId: StateFlow<String?> = _savedSessionId.asStateFlow()

    fun start(config: FocusConfig) {
        _config.value = config
        _result.value = null
        _savedSessionId.value = null
        _snapshot.value = null
    }

    fun publish(snapshot: FocusSnapshot?) {
        _snapshot.value = snapshot
    }

    fun finished(result: FocusResult, savedSessionId: String?) {
        _result.value = result
        _savedSessionId.value = savedSessionId
    }

    fun onSessionEdited(id: String) {
        _savedSessionId.value = id
    }

    fun reset() {
        _config.value = null
        _snapshot.value = null
        _result.value = null
        _savedSessionId.value = null
    }
}
