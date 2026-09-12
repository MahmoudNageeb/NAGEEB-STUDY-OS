package com.nageebstudyos.study.focus

import com.nageebstudyos.study.domain.FocusConfig
import com.nageebstudyos.study.domain.FocusPhase
import com.nageebstudyos.study.domain.FocusResult
import com.nageebstudyos.study.domain.FocusSnapshot
import com.nageebstudyos.study.domain.FocusStatus

/**
 * Real pomodoro-style focus state machine.
 *
 * Timing is driven externally by a monotonic clock ([now], typically
 * [android.os.SystemClock.elapsedRealtime]); the engine is a pure Kotlin class so it can be
 * unit-tested with a virtual clock. The foreground service ticks it once per second and
 * renders [snapshot]; transitions produce explicit [FocusEvent]s for sound, vibration and
 * notifications. Pauses are exact (deadline is shifted by the paused duration), and skipping
 * a focus round banks the focused portion of that round.
 */
sealed class FocusEvent {
    data class FocusRoundFinished(val round: Int, val longBreakNext: Boolean) : FocusEvent()

    data class BreakStarted(val long: Boolean, val autoStarted: Boolean) : FocusEvent()

    data class AwaitingNext(val phase: FocusPhase, val round: Int) : FocusEvent()

    data object AllFinished : FocusEvent()
}

class FocusEngine(
    val config: FocusConfig,
    private val now: () -> Long,
) {
    var status: FocusStatus = FocusStatus.IDLE
        private set
    var phase: FocusPhase = FocusPhase.IDLE
        private set
    var round: Int = 0
        private set
    var completedRounds: Int = 0
        private set

    private var phaseLengthMs: Long = 0
    private var endAt: Long = 0
    private var remainingPausedMs: Long = 0
    private var completedFocusMs: Long = 0
    private var startElapsed: Long = 0
    private var startWallClock: Long = 0

    private val focusMs get() = config.focusMs
    private val shortMs get() = config.shortBreakMs
    private val longMs get() = config.longBreakMs

    fun start(wallClock: Long = System.currentTimeMillis()): List<FocusEvent> {
        if (status != FocusStatus.IDLE) return emptyList()
        startElapsed = now()
        startWallClock = wallClock
        return beginFocus(round = 1, autoStarted = false)
    }

    fun pause() {
        if (status != FocusStatus.RUNNING) return
        remainingPausedMs = (endAt - now()).coerceAtLeast(0)
        status = FocusStatus.PAUSED
    }

    fun resume() {
        if (status != FocusStatus.PAUSED) return
        endAt = now() + remainingPausedMs
        status = FocusStatus.RUNNING
    }

    /** Advance the clock; returns any boundary events that fired. Safe to call every tick. */
    fun advance(): List<FocusEvent> {
        val events = mutableListOf<FocusEvent>()
        // Loops only when a zero-length break auto-starts into the next zero-length phase.
        while (status == FocusStatus.RUNNING && now() >= endAt) {
            when (phase) {
                FocusPhase.FOCUS -> finishFocusRound(events)
                FocusPhase.SHORT_BREAK,
                FocusPhase.LONG_BREAK -> finishBreak(events)
                else -> return events
            }
            if (status == FocusStatus.FINISHED) break
        }
        return events
    }

    /** Skip the current phase; focused time within a focus round is still banked. */
    fun skip(): List<FocusEvent> {
        if (status == FocusStatus.FINISHED || status == FocusStatus.IDLE) return emptyList()
        val events = mutableListOf<FocusEvent>()
        when (phase) {
            FocusPhase.FOCUS -> {
                completedFocusMs += phaseLengthMs - remaining()
                finishFocusRound(events, skipped = true)
            }
            FocusPhase.SHORT_BREAK,
            FocusPhase.LONG_BREAK -> finishBreak(events, skipped = true)
            else -> Unit
        }
        return events
    }

    /** End the run now, banking partial work. Returns null if it was never started. */
    fun stop(wallClock: Long = System.currentTimeMillis()): FocusResult? {
        if (status == FocusStatus.IDLE) return null
        if (phase == FocusPhase.FOCUS && status != FocusStatus.FINISHED)
            completedFocusMs += phaseLengthMs - remaining()
        val focusedSeconds = (completedFocusMs / 1000).coerceAtLeast(0)
        status = FocusStatus.FINISHED
        phase = FocusPhase.FINISHED
        return FocusResult(
            config = config,
            focusedSeconds = focusedSeconds,
            completedRounds = completedRounds,
            startWallClock = startWallClock,
            endWallClock = wallClock,
        )
    }

    fun snapshot(): FocusSnapshot? {
        if (status == FocusStatus.IDLE) return null
        return FocusSnapshot(
            status = status,
            phase = phase,
            round = round.coerceAtLeast(1),
            totalRounds = config.sessionsCount,
            phaseLengthMs = phaseLengthMs,
            remainingMs = if (status == FocusStatus.FINISHED) 0 else remaining(),
            focusedMs =
                completedFocusMs +
                    if (phase == FocusPhase.FOCUS && status != FocusStatus.FINISHED)
                        phaseLengthMs - remaining()
                    else 0,
            startedAtElapsed = startElapsed,
        )
    }

    private fun remaining(): Long =
        when (status) {
            FocusStatus.RUNNING -> (endAt - now()).coerceAtLeast(0)
            FocusStatus.PAUSED -> remainingPausedMs.coerceAtLeast(0)
            else -> 0
        }

    private fun beginFocus(round: Int, autoStarted: Boolean): List<FocusEvent> {
        this.round = round
        phase = FocusPhase.FOCUS
        phaseLengthMs = focusMs
        remainingPausedMs = phaseLengthMs
        return if (config.autoStart || !autoStarted) {
            status = FocusStatus.RUNNING
            endAt = now() + phaseLengthMs
            emptyList()
        } else {
            status = FocusStatus.PAUSED
            listOf(FocusEvent.AwaitingNext(FocusPhase.FOCUS, round))
        }
    }

    private fun beginBreak(long: Boolean, autoStarted: Boolean): List<FocusEvent> {
        phase = if (long) FocusPhase.LONG_BREAK else FocusPhase.SHORT_BREAK
        phaseLengthMs = if (long) longMs else shortMs
        remainingPausedMs = phaseLengthMs
        val events = mutableListOf<FocusEvent>()
        if (phaseLengthMs <= 0) {
            // No break configured: continue immediately.
            return finishBreak(events)
        }
        return if (config.autoStart) {
            status = FocusStatus.RUNNING
            endAt = now() + phaseLengthMs
            events.add(FocusEvent.BreakStarted(long, autoStarted = true))
            events
        } else {
            status = FocusStatus.PAUSED
            events.add(FocusEvent.BreakStarted(long, autoStarted = false))
            events
        }
    }

    private fun finishFocusRound(events: MutableList<FocusEvent>, skipped: Boolean = false) {
        if (!skipped) completedFocusMs += phaseLengthMs
        completedRounds += 1
        if (completedRounds >= config.sessionsCount) {
            status = FocusStatus.FINISHED
            phase = FocusPhase.FINISHED
            events.add(FocusEvent.AllFinished)
            return
        }
        val longNext = completedRounds % config.longBreakInterval == 0
        events.add(FocusEvent.FocusRoundFinished(completedRounds, longNext))
        beginBreak(longNext, autoStarted = true).also { events.addAll(it) }
    }

    private fun finishBreak(events: MutableList<FocusEvent>, skipped: Boolean = false) {
        if (!skipped) events.add(FocusEvent.AwaitingNext(phase, round + 1))
        beginFocus(round + 1, autoStarted = true).also { events.addAll(it) }
    }
}
