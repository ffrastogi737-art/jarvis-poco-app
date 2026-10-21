package com.personal.workoutcoach.core.plan

import com.personal.workoutcoach.core.catalog.Exercise
import com.personal.workoutcoach.core.catalog.Prescription
import com.personal.workoutcoach.core.model.SessionMode
import java.time.LocalDate

enum class PlanSource { PLANNER, AI_VALIDATED }

enum class ProgressionDecision { START, HOLD, INCREASE_REPS, ADD_SET, SUGGEST_HARDER_VARIATION, REDUCE_FOR_RECOVERY, REDUCE_FOR_PERFORMANCE }

data class PlannedExercise(
    val exercise: Exercise,
    val prescription: Prescription,
    val loadKg: Double?,
    val decision: ProgressionDecision = ProgressionDecision.START,
    val note: String? = null,
)

data class PlannedWorkout(
    val title: String,
    val mode: SessionMode,
    val templateKey: String?,
    val exercises: List<PlannedExercise>,
    val estimatedMinutes: Int,
    val minimumCriteria: String,
    val source: PlanSource = PlanSource.PLANNER,
    val notes: List<String> = emptyList(),
)

data class SetRecord(val reps: Int?, val seconds: Int?, val loadKg: Double?)

/** One past performance of an exercise: what was planned and what was actually done. */
data class ExerciseRecord(val exerciseId: String, val date: LocalDate, val target: Prescription, val sets: List<SetRecord>) {
    val load: Double? get() = sets.firstNotNullOfOrNull { it.loadKg }
}

/** Ratings are 1–5 when the user provided them. */
data class RecoverySignal(
    val soreness: Int? = null,
    val energy: Int? = null,
    val daysSinceLastWorkout: Int? = null,
    val reportedTooHard: Boolean = false,
) {
    val needsLighterSession: Boolean
        get() = (soreness ?: 0) >= 4 || (energy ?: 5) <= 2 || reportedTooHard || (daysSinceLastWorkout ?: 0) >= 10
}
