package com.example.nelexiumlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripClockTest {
    private val minute = 60_000L
    private val start = 1_000L

    @Test fun startsAtFiveAndPausesAfterFiveMinutesBelowFive() {
        val clock = TripClock()
        clock.onSpeed(start, 4.9f)
        assertFalse(clock.isActive)

        clock.onSpeed(start + minute, 5f)
        assertTrue(clock.isActive)
        clock.onSpeed(start + 2 * minute, 4.9f)
        clock.tick(start + 6 * minute)
        assertEquals(5 * minute, clock.elapsed(start + 6 * minute))

        clock.tick(start + 20 * minute)
        assertEquals(6 * minute, clock.elapsed(start + 20 * minute))
    }

    @Test fun resumesAtFiveAndCancelsTheResetCountdown() {
        val clock = TripClock()
        clock.onSpeed(start, 5f)
        clock.onSpeed(start + minute, 0f)
        clock.tick(start + 20 * minute)
        assertEquals(6 * minute, clock.elapsed(start + 20 * minute))

        clock.onSpeed(start + 30 * minute, 5f)
        clock.onSpeed(start + 40 * minute, 0f)
        assertFalse(clock.tick(start + 70 * minute))
        assertEquals(21 * minute, clock.elapsed(start + 70 * minute))
    }

    @Test fun briefSlowdownKeepsTheTimerRunning() {
        val clock = TripClock()
        clock.onSpeed(start, 5f)
        clock.onSpeed(start + minute, 0f)
        clock.onSpeed(start + 4 * minute, 5f)
        assertEquals(10 * minute, clock.elapsed(start + 10 * minute))
        assertFalse(clock.tick(start + 70 * minute))
    }

    @Test fun resetsOnlyAfterAnHourContinuouslyBelowFive() {
        val clock = TripClock()
        clock.onSpeed(start, 5f)
        clock.onSpeed(start + minute, 0f)
        assertFalse(clock.tick(start + 61 * minute - 1))
        assertTrue(clock.tick(start + 61 * minute))
        assertFalse(clock.isActive)
        assertEquals(0L, clock.elapsed(start + 61 * minute))
    }
}
