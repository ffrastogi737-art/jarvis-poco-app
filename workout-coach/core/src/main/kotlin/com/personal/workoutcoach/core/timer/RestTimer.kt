package com.personal.workoutcoach.core.timer

/**
 * Immutable rest-timer state driven by a monotonic clock (Android: SystemClock.elapsedRealtime,
 * which keeps counting while the app is backgrounded or the process is recreated).
 * Remaining time is always derived from the end timestamp, so the countdown cannot drift.
 */
data class RestTimer(
    val durationMs: Long,
    val status: Status = Status.IDLE,
    /** Monotonic timestamp when the countdown reaches zero (RUNNING only). */
    val endsAtMs: Long = 0,
    /** Remaining time while PAUSED/IDLE. */
    val pausedRemainingMs: Long = durationMs,
) {
    enum class Status { IDLE, RUNNING, PAUSED, FINISHED }

    fun remaining(nowMs: Long): Long = when (status) {
        Status.RUNNING -> (endsAtMs - nowMs).coerceAtLeast(0)
        Status.FINISHED -> 0
        else -> pausedRemainingMs
    }

    fun isFinished(nowMs: Long) = status == Status.FINISHED || (status == Status.RUNNING && nowMs >= endsAtMs)

    /** Collapses an elapsed RUNNING timer into FINISHED. */
    fun tick(nowMs: Long): RestTimer = if (status == Status.RUNNING && nowMs >= endsAtMs) copy(status = Status.FINISHED, pausedRemainingMs = 0) else this

    fun start(nowMs: Long): RestTimer = copy(status = Status.RUNNING, endsAtMs = nowMs + durationMs, pausedRemainingMs = durationMs)

    fun pause(nowMs: Long): RestTimer = if (status == Status.RUNNING) copy(status = Status.PAUSED, pausedRemainingMs = remaining(nowMs)).tickPaused() else this

    private fun tickPaused() = if (pausedRemainingMs <= 0) copy(status = Status.FINISHED, pausedRemainingMs = 0) else this

    fun resume(nowMs: Long): RestTimer = if (status == Status.PAUSED) copy(status = Status.RUNNING, endsAtMs = nowMs + pausedRemainingMs) else this

    fun reset(): RestTimer = RestTimer(durationMs)

    fun skip(): RestTimer = copy(status = Status.FINISHED, pausedRemainingMs = 0)

    /** Adds (or removes, if negative) time; the total never goes below 5 s or above 10 min. */
    fun adjust(deltaMs: Long, nowMs: Long): RestTimer {
        val newDuration = (durationMs + deltaMs).coerceIn(MIN_MS, MAX_MS)
        return when (status) {
            Status.RUNNING -> copy(durationMs = newDuration, endsAtMs = (endsAtMs + deltaMs).coerceAtLeast(nowMs))
            Status.PAUSED -> copy(durationMs = newDuration, pausedRemainingMs = (pausedRemainingMs + deltaMs).coerceAtLeast(0))
            Status.IDLE -> RestTimer(newDuration)
            Status.FINISHED -> this
        }
    }

    companion object {
        const val MIN_MS = 5_000L
        const val MAX_MS = 600_000L
    }
}
