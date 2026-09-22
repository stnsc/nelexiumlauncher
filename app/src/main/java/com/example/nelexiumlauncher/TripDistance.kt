package com.example.nelexiumlauncher

import android.location.Location

/** Odometer distance along successive fixes, rather than displacement from the start. */
internal class TripDistance {
    var meters = 0.0
        private set
    private var previous: Location? = null

    fun update(location: Location, moving: Boolean) {
        if (moving) previous?.let {
            val segment = it.distanceTo(location).toDouble()
            if (segment.isFinite() && segment >= 0.0) meters += segment
        }
        // Advance even while stopped so ignored GPS drift is not added on resuming.
        previous = Location(location)
    }

    fun reset() {
        meters = 0.0
        previous = null
    }
}
