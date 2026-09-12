package com.nageebstudyos.study

import com.nageebstudyos.study.domain.StreakRules
import org.junit.Assert.*
import org.junit.Test

class StreakRulesTest {
    private fun bucket(day: Int, seconds: Long, positive: Int = 1) =
        StreakRules.Bucket(day, seconds, positive)

    @Test
    fun studyDayRequiresMinuteAndPositiveOutcome() {
        assertFalse(StreakRules.qualifies(59, 1))
        assertFalse(StreakRules.qualifies(3_600, 0))
        assertTrue(StreakRules.qualifies(60, 1))
        assertTrue(StreakRules.qualifies(7_200, 3))
    }

    @Test
    fun currentStreakCountsConsecutiveDaysIncludingToday() {
        val today = 100
        val buckets =
            listOf(
                bucket(today, 1_800),
                bucket(today - 1, 3_600),
                bucket(today - 2, 30), // under a minute: gap
                bucket(today - 3, 900),
                bucket(today - 10, 5_000),
            )
        assertEquals(2, StreakRules.current(buckets, today))
    }

    @Test
    fun currentStreakStartsYesterdayWhenTodayEmpty() {
        val today = 100
        val buckets = listOf(bucket(today - 1, 600), bucket(today - 2, 900))
        assertEquals(2, StreakRules.current(buckets, today))
    }

    @Test
    fun negativeOnlyDayNeverStartsStreak() {
        val today = 100
        val buckets =
            listOf(
                bucket(today, 900, positive = 0),
                bucket(today - 1, 3_600, positive = 1),
            )
        assertEquals(1, StreakRules.current(buckets, today))
    }

    @Test
    fun bestStreakFindsLongestRun() {
        val today = 50
        val buckets =
            listOf(
                bucket(today - 9, 900),
                bucket(today - 8, 900),
                bucket(today - 8 + 3, 900), // isolated
                bucket(today - 1, 900),
                bucket(today, 900),
            )
        assertEquals(2, StreakRules.best(buckets))
    }

    @Test
    fun emptyHistoryHasNoStreak() {
        assertEquals(0, StreakRules.current(emptyList()))
        assertEquals(0, StreakRules.best(emptyList()))
    }
}
