package com.personal.workoutcoach.core.plan

import com.personal.workoutcoach.core.catalog.Exercise
import com.personal.workoutcoach.core.catalog.Prescription
import com.personal.workoutcoach.core.model.TrainingPhase

/**
 * Deterministic double progression for fixed-weight home training.
 * Changes at most one variable per session: reps first, then sets, then a harder variation.
 * Never progresses because a workout was missed; reduces demand on poor recovery.
 */
object Progression {
    fun maxSets(phase: TrainingPhase) = if (phase == TrainingPhase.COMEBACK) 3 else 4
    fun startSets(phase: TrainingPhase, ex: Exercise) = if (phase == TrainingPhase.COMEBACK) minOf(ex.start.sets, 2) else ex.start.sets
    fun repCap(ex: Exercise) = if (ex.isCompound) 15 else 20
    const val SECONDS_CAP = 60

    fun base(ex: Exercise, phase: TrainingPhase) = ex.start.copy(sets = startSets(phase, ex))

    data class Result(val prescription: Prescription, val decision: ProgressionDecision, val note: String?)

    /**
     * @param comparable past records of this exercise at the same load, oldest first.
     */
    fun next(ex: Exercise, phase: TrainingPhase, comparable: List<ExerciseRecord>, recovery: RecoverySignal): Result {
        val start = base(ex, phase)
        val last = comparable.lastOrNull() ?: return Result(start, ProgressionDecision.START, null)
        val lastTarget = last.target.copy(sets = last.target.sets.coerceIn(1, maxSets(phase)))

        if (recovery.needsLighterSession) {
            return Result(
                lastTarget.copy(sets = (lastTarget.sets - 1).coerceAtLeast(1)),
                ProgressionDecision.REDUCE_FOR_RECOVERY,
                "One set fewer today so you can recover.",
            )
        }
        val recent = comparable.takeLast(2)
        if (recent.size == 2 && recent.all { missedTarget(it) }) {
            val reduced = if (lastTarget.isTimed) lastTarget.copy(seconds = (lastTarget.seconds!! - 10).coerceAtLeast(10))
            else lastTarget.copy(repsMin = (lastTarget.repsMin - 2).coerceAtLeast(3), repsMax = (lastTarget.repsMax - 2).coerceAtLeast(5))
            return Result(reduced, ProgressionDecision.REDUCE_FOR_PERFORMANCE, "Target lowered slightly after two tough sessions.")
        }
        if (!hitTop(last)) return Result(lastTarget, ProgressionDecision.HOLD, "Same target as last time — aim to beat your reps.")

        if (lastTarget.isTimed) {
            if (lastTarget.seconds!! < SECONDS_CAP) return Result(lastTarget.copy(seconds = lastTarget.seconds + 10), ProgressionDecision.INCREASE_REPS, "+10 seconds.")
        } else if (lastTarget.repsMax < repCap(ex)) {
            return Result(lastTarget.copy(repsMin = lastTarget.repsMin + 2, repsMax = lastTarget.repsMax + 2), ProgressionDecision.INCREASE_REPS, "You hit the top of the range — rep target +2.")
        }
        if (lastTarget.sets < maxSets(phase)) {
            return Result(lastTarget.copy(sets = lastTarget.sets + 1, repsMin = start.repsMin, repsMax = start.repsMax, seconds = start.seconds), ProgressionDecision.ADD_SET, "Adding a set; reps reset to the base range.")
        }
        val harder = ex.harder.firstOrNull()
        return Result(lastTarget, ProgressionDecision.SUGGEST_HARDER_VARIATION, if (harder != null) "Ready to try a harder variation." else "Slow the lowering phase (3 s) to make it harder.")
    }

    fun hitTop(r: ExerciseRecord): Boolean {
        if (r.sets.size < r.target.sets) return false
        return r.sets.all { s -> if (r.target.isTimed) (s.seconds ?: 0) >= r.target.seconds!! else (s.reps ?: 0) >= r.target.repsMax }
    }

    fun missedTarget(r: ExerciseRecord): Boolean {
        if (r.sets.size < r.target.sets) return true
        return r.sets.any { s -> if (r.target.isTimed) (s.seconds ?: 0) < r.target.seconds!! else (s.reps ?: 0) < r.target.repsMin }
    }
}

object PhasePolicy {
    const val COMEBACK_SESSIONS = 9

    /** Comeback lasts ~3 weeks of completed sessions; a long break (≥21 days) returns to comeback. */
    fun phase(completedSessions: Int, daysSinceLastWorkout: Int?, recentSorenessAvg: Double?): TrainingPhase = when {
        daysSinceLastWorkout != null && daysSinceLastWorkout >= 21 -> TrainingPhase.COMEBACK
        completedSessions < COMEBACK_SESSIONS -> TrainingPhase.COMEBACK
        recentSorenessAvg != null && recentSorenessAvg >= 4.0 -> TrainingPhase.COMEBACK
        else -> TrainingPhase.BUILD
    }
}
