package com.example.nelexiumlauncher

internal class TripClock {
    var startedAt = 0L
        private set

    private var below5Since = 0L
    private var runningSince = 0L
    private var elapsedBeforeCurrentRun = 0L

    val isActive: Boolean get() = startedAt > 0L

    fun onSpeed(now: Long, speedKph: Float): Boolean {
        val reset = tick(now)
        if (!isActive) {
            if (speedKph >= 5f) {
                startedAt = now
                runningSince = now
            }
        } else if (speedKph >= 5f) {
            below5Since = 0L
            if (runningSince == 0L) runningSince = now
        } else if (below5Since == 0L) {
            below5Since = now
        }
        return reset
    }

    fun tick(now: Long): Boolean {
        if (!isActive || below5Since == 0L) return false

        val pauseAt = below5Since + 5 * 60_000L
        if (runningSince > 0L && now >= pauseAt) {
            elapsedBeforeCurrentRun += pauseAt - runningSince
            runningSince = 0L
        }
        if (now - below5Since >= 3_600_000L) {
            reset()
            return true
        }
        return false
    }

    fun elapsed(now: Long): Long = elapsedBeforeCurrentRun +
        if (runningSince == 0L) 0L else {
            val countedUntil = if (below5Since == 0L) now else minOf(now, below5Since + 5 * 60_000L)
            (countedUntil - runningSince).coerceAtLeast(0L)
        }

    private fun reset() {
        startedAt = 0L
        below5Since = 0L
        runningSince = 0L
        elapsedBeforeCurrentRun = 0L
    }
}
