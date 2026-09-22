package com.example.nelexiumlauncher

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripDistanceTest {
    private fun fix(longitude: Double, latitude: Double = 0.0) = Location("gps").apply {
        this.longitude = longitude
        this.latitude = latitude
    }

    @Test fun returningToStartAddsDistanceInsteadOfSubtractingIt() {
        val trip = TripDistance()
        trip.update(fix(0.0), true)
        trip.update(fix(0.001), true)
        val outbound = trip.meters
        assertTrue(outbound > 100.0)
        trip.update(fix(0.0005), true)
        assertTrue(trip.meters > outbound)
        trip.update(fix(0.0), true)
        assertEquals(2 * outbound, trip.meters, 0.1)
    }

    @Test fun turnsAccumulateEveryLegWithoutDoubleCounting() {
        val trip = TripDistance()
        trip.update(fix(0.0), true)
        trip.update(fix(0.001), true)
        trip.update(fix(0.001, 0.001), true)
        trip.update(fix(0.0, 0.001), true)
        trip.update(fix(0.0), true)
        assertEquals(443.8, trip.meters, 1.0)
    }

    @Test fun stoppedFixesDoNotAddDistanceAndResetClearsThePreviousFix() {
        val trip = TripDistance()
        trip.update(fix(0.0), true)
        trip.update(fix(0.001), false)
        trip.update(fix(0.001), true)
        assertEquals(0.0, trip.meters, 0.0)
        trip.update(fix(0.002), true)
        assertTrue(trip.meters > 100.0)
        trip.reset()
        trip.update(fix(1.0), true)
        assertEquals(0.0, trip.meters, 0.0)
    }
}
