package com.personal.workoutcoach.core.coach

import com.personal.workoutcoach.core.catalog.Exercise
import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.equipment.Compatibility
import com.personal.workoutcoach.core.equipment.Inventory
import com.personal.workoutcoach.core.model.TrainingPhase
import com.personal.workoutcoach.core.plan.ExerciseRecord
import com.personal.workoutcoach.core.plan.PlanSource
import com.personal.workoutcoach.core.plan.PlannedExercise
import com.personal.workoutcoach.core.plan.PlannedWorkout
import com.personal.workoutcoach.core.plan.Progression
import com.personal.workoutcoach.core.plan.WorkoutPlanner

data class ValidationResult(val workout: PlannedWorkout?, val warnings: List<String>)

/**
 * Turns an untrusted [WorkoutRecommendation] (from an AI model or a stored chat message) into a
 * [PlannedWorkout] that only uses catalogue exercises the current equipment supports, within the
 * phase's limits. Loads are always chosen locally from the inventory, never taken from the AI.
 */
class RecommendationValidator(
    private val inventory: Inventory,
    private val phase: TrainingPhase,
    private val excludedExerciseIds: Set<String> = emptySet(),
    private val history: Map<String, List<ExerciseRecord>> = emptyMap(),
) {
    companion object {
        const val MAX_EXERCISES = 10
        const val MAX_TOTAL_SETS = 30
    }

    fun validate(rec: WorkoutRecommendation): ValidationResult {
        val warnings = mutableListOf<String>()
        if (rec.exercises.size > MAX_EXERCISES) warnings += "Only the first $MAX_EXERCISES exercises were kept."
        val result = mutableListOf<PlannedExercise>()
        for (item in rec.exercises.take(MAX_EXERCISES)) {
            val requested = ExerciseCatalog.get(item.exerciseId)
            if (requested == null) { warnings += "Unknown exercise '${item.exerciseId.take(40)}' was removed."; continue }
            val (ex, compat) = resolve(requested) ?: run {
                warnings += "${requested.name} was removed: not possible with your equipment or settings."
                null
            } ?: continue
            if (ex.id != requested.id) warnings += "${requested.name} was replaced with ${ex.name}."
            if (result.any { it.exercise.id == ex.id }) { warnings += "Duplicate ${ex.name} removed."; continue }
            if (result.sumOf { it.prescription.sets } >= MAX_TOTAL_SETS) { warnings += "Workout trimmed to $MAX_TOTAL_SETS total sets."; break }
            result += PlannedExercise(ex, sanitize(item, ex, warnings), WorkoutPlanner.chooseLoad(compat.loadOptionsKg, history[ex.id].orEmpty()))
        }
        if (result.isEmpty()) return ValidationResult(null, warnings + "No valid exercises remained, so the suggestion was not used.")
        val minutes = WorkoutPlanner.estimateMinutes(result, rec.mode)
        return ValidationResult(
            PlannedWorkout(
                title = rec.title.take(60).ifBlank { "${WorkoutPlanner.titleFor(rec.mode)} · Coach plan" },
                mode = rec.mode,
                templateKey = null,
                exercises = result,
                estimatedMinutes = minutes,
                minimumCriteria = WorkoutPlanner.minimumCriteria(rec.mode),
                source = PlanSource.AI_VALIDATED,
                notes = rec.safetyNotes.take(3).map { it.take(200) },
            ),
            warnings,
        )
    }

    /** The requested exercise, or its first usable easier alternative. */
    private fun resolve(ex: Exercise): Pair<Exercise, Compatibility.Ok>? {
        for (candidate in listOf(ex) + ex.easier.mapNotNull(ExerciseCatalog::get)) {
            if (candidate.id in excludedExerciseIds) continue
            if (candidate.difficulty > WorkoutPlanner.maxDifficulty(phase)) continue
            val c = inventory.check(candidate) as? Compatibility.Ok ?: continue
            return candidate to c
        }
        return null
    }

    private fun sanitize(item: RecommendedExercise, ex: Exercise, warnings: MutableList<String>): com.personal.workoutcoach.core.catalog.Prescription {
        val maxSets = Progression.maxSets(phase)
        val sets = item.sets.coerceIn(1, maxSets)
        if (sets != item.sets) warnings += "${ex.name}: sets limited to $sets."
        val rest = (item.restSeconds ?: ex.start.restSeconds).coerceIn(15, 300)
        return if (ex.start.isTimed) {
            ex.start.copy(sets = sets, seconds = (item.seconds ?: ex.start.seconds!!).coerceIn(5, 120), restSeconds = rest)
        } else {
            val min = (item.repsMin ?: ex.start.repsMin).coerceIn(1, 30)
            val max = (item.repsMax ?: maxOf(min, ex.start.repsMax)).coerceIn(min, 30)
            ex.start.copy(sets = sets, repsMin = min, repsMax = max, restSeconds = rest)
        }
    }
}

fun PlannedWorkout.toRecommendation(explanation: String = ""): WorkoutRecommendation = WorkoutRecommendation(
    mode = mode,
    title = title,
    explanation = explanation,
    exercises = exercises.map {
        RecommendedExercise(it.exercise.id, it.prescription.sets, it.prescription.repsMin.takeIf { _ -> !it.prescription.isTimed },
            it.prescription.repsMax.takeIf { _ -> !it.prescription.isTimed }, it.prescription.seconds, it.prescription.restSeconds)
    },
    estimatedMinutes = estimatedMinutes,
    safetyNotes = notes,
)

fun PlannedWorkout.toPlanOption() = PlanOption(mode, title, estimatedMinutes, toRecommendation().exercises)
