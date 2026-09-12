package com.nageebstudyos.study

import com.nageebstudyos.study.domain.FocusConfig
import com.nageebstudyos.study.domain.FocusPhase
import com.nageebstudyos.study.domain.FocusStatus
import com.nageebstudyos.study.focus.FocusEngine
import com.nageebstudyos.study.focus.FocusEvent
import org.junit.Assert.*
import org.junit.Test

class FocusEngineTest {
    private var clock = 0L

    private fun engine(config: FocusConfig) = FocusEngine(config) { clock }

    private fun config(
        focus: Int = 1,
        short: Int = 1,
        long: Int = 2,
        rounds: Int = 2,
        interval: Int = 2,
        auto: Boolean = false,
    ) = FocusConfig(
        presetId = null,
        focusMinutes = focus,
        shortBreakMinutes = short,
        longBreakMinutes = long,
        sessionsCount = rounds,
        longBreakInterval = interval,
        autoStart = auto,
        sound = false,
        vibration = false,
    )

    @Test
    fun runsFullCycleAcrossFocusAndBreaks() {
        clock = 0L
        val e = engine(config())
        e.start(wallClock = 1_000)
        assertEquals(FocusStatus.RUNNING, e.status)
        assertEquals(FocusPhase.FOCUS, e.phase)

        // End focus round 1 -> short break, waiting because auto-start is off.
        clock += 60_000
        val afterFocus1 = e.advance()
        assertEquals(1, e.completedRounds)
        assertTrue(afterFocus1.any { it is FocusEvent.FocusRoundFinished })
        assertEquals(FocusPhase.SHORT_BREAK, e.phase)
        assertEquals(FocusStatus.PAUSED, e.status)

        // Resume and finish the break -> next focus waits as well.
        e.resume()
        clock += 60_000
        e.advance()
        assertEquals(2, e.round)
        assertEquals(FocusPhase.FOCUS, e.phase)
        assertEquals(FocusStatus.PAUSED, e.status)

        // Finish focus round 2 -> whole run completes.
        e.resume()
        clock += 60_000
        val events = e.advance()
        assertTrue(events.any { it is FocusEvent.AllFinished })
        assertEquals(FocusStatus.FINISHED, e.status)

        val result = e.stop(wallClock = 200_000)!!
        assertEquals(120L, result.focusedSeconds)
        assertEquals(2, result.completedRounds)
    }

    @Test
    fun pauseShiftsDeadlineAndDoesNotLeakTime() {
        clock = 0L
        val e = engine(config(focus = 1, rounds = 1))
        e.start()
        clock += 20_000
        e.pause()
        assertEquals(FocusStatus.PAUSED, e.status)
        // Wall-clock keeps moving while paused; no transition may fire.
        clock += 100_000
        assertTrue(e.advance().isEmpty())
        assertEquals(FocusPhase.FOCUS, e.phase)
        e.resume()
        clock += 39_000
        assertTrue(e.advance().isEmpty())
        clock += 2_000
        e.advance()
        assertEquals(FocusStatus.FINISHED, e.status)
        assertEquals(60L, e.stop()!!.focusedSeconds)
    }

    @Test
    fun skipBanksPartialFocusTime() {
        clock = 0L
        val e = engine(config(focus = 1, rounds = 2))
        e.start()
        clock += 30_000
        e.skip()
        val result = e.stop()!!
        assertEquals(30L, result.focusedSeconds)
        assertEquals(1, result.completedRounds)
    }

    @Test
    fun autoStartChainsPhasesWithoutPausing() {
        clock = 0L
        val e = engine(config(focus = 1, short = 1, rounds = 2, interval = 2, auto = true))
        e.start()
        clock += 60_000
        e.advance()
        assertEquals(FocusPhase.SHORT_BREAK, e.phase)
        assertEquals(FocusStatus.RUNNING, e.status)
        clock += 60_000
        e.advance()
        assertEquals(2, e.round)
        assertEquals(FocusStatus.RUNNING, e.status)
        clock += 60_000
        e.advance()
        assertEquals(FocusStatus.FINISHED, e.status)
    }

    @Test
    fun longBreakFiresAtConfiguredInterval() {
        clock = 0L
        val e = engine(config(focus = 1, short = 1, long = 1, rounds = 4, interval = 2, auto = true))
        e.start()
        // Round 1 -> short break, round 2 -> long break.
        clock += 60_000
        e.advance()
        assertEquals(FocusPhase.SHORT_BREAK, e.phase)
        clock += 60_000
        e.advance()
        assertEquals(2, e.round)
        clock += 60_000
        e.advance()
        assertEquals(FocusPhase.LONG_BREAK, e.phase)
    }
}
