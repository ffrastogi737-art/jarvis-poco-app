package com.personal.workoutcoach.core.plan

import com.personal.workoutcoach.core.catalog.Exercise
import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.catalog.Prescription
import com.personal.workoutcoach.core.equipment.Compatibility
import com.personal.workoutcoach.core.equipment.Inventory
import com.personal.workoutcoach.core.model.SessionMode
import com.personal.workoutcoach.core.model.TrainingPhase
import kotlin.math.roundToInt

data class PlannerInput(
    val inventory: Inventory,
    val phase: TrainingPhase,
    val mode: SessionMode,
    val templateKey: String,
    val excludedExerciseIds: Set<String> = emptySet(),
    /** Exercise id → past records, oldest first. */
    val history: Map<String, List<ExerciseRecord>> = emptyMap(),
    val recovery: RecoverySignal = RecoverySignal(),
    /** Hard time limit in minutes, e.g. "I only have 15 minutes". */
    val maxMinutes: Int? = null,
)

/**
 * Full-body 3-day comeback program (templates A/B/C). Each slot lists candidates in order of
 * preference; the first one that the current equipment supports, that the user hasn't excluded and
 * that fits the phase's difficulty limit is used.
 */
object WorkoutPlanner {
    val templateKeys = listOf("A", "B", "C")

    private val templates: Map<String, List<List<String>>> = mapOf(
        "A" to listOf(
            listOf("goblet_squat", "kb_goblet_squat", "bodyweight_squat"),
            listOf("bench_db_press", "db_floor_press", "db_floor_press_single", "push_up", "incline_push_up"),
            listOf("db_row_one_arm", "band_row", "barbell_row", "prone_y_t_raise"),
            listOf("db_rdl", "kb_deadlift", "barbell_rdl", "glute_bridge"),
            listOf("dead_bug", "plank"),
        ),
        "B" to listOf(
            listOf("db_rdl", "kb_deadlift", "barbell_rdl", "glute_bridge"),
            listOf("db_shoulder_press", "db_shoulder_press_single", "pike_push_up"),
            listOf("pull_up", "negative_pull_up", "db_bent_over_row", "db_row_one_arm", "band_row", "prone_y_t_raise"),
            listOf("split_squat", "reverse_lunge", "db_split_squat"),
            listOf("hammer_curl", "db_curl", "band_curl"),
            listOf("plank", "dead_bug"),
        ),
        "C" to listOf(
            listOf("goblet_squat", "db_split_squat", "reverse_lunge", "bodyweight_squat"),
            listOf("push_up", "incline_push_up", "db_floor_press"),
            listOf("db_row_one_arm", "band_row", "prone_y_t_raise"),
            listOf("db_rear_delt_fly", "band_pull_apart", "prone_y_t_raise"),
            listOf("db_overhead_triceps_ext", "close_grip_push_up", "dips"),
            listOf("standing_calf_raise"),
            listOf("russian_twist", "side_plank", "dead_bug"),
        ),
    )

    private val extendedAccessories = listOf(
        listOf("db_lateral_raise", "band_lateral_raise", "band_pull_apart", "prone_y_t_raise"),
        listOf("hammer_curl", "db_curl", "band_curl"),
        listOf("db_overhead_triceps_ext", "close_grip_push_up"),
        listOf("standing_calf_raise", "db_single_leg_calf_raise"),
        listOf("side_plank", "hanging_knee_raise", "plank"),
    )

    fun nextTemplate(lastCompleted: String?): String =
        templateKeys.getOrElse((templateKeys.indexOf(lastCompleted) + 1) % templateKeys.size) { "A" }

    fun maxDifficulty(phase: TrainingPhase) = if (phase == TrainingPhase.COMEBACK) 2 else 3

    /** First usable exercise in [candidates]; also used to substitute exercises. */
    fun pick(candidates: List<String>, input: PlannerInput, used: Set<String>, allowAnyDifficulty: Boolean = false): Pair<Exercise, Compatibility.Ok>? {
        for (id in candidates) {
            if (id in used || id in input.excludedExerciseIds) continue
            val ex = ExerciseCatalog.get(id) ?: continue
            if (!allowAnyDifficulty && ex.difficulty > maxDifficulty(input.phase)) continue
            val c = input.inventory.check(ex) as? Compatibility.Ok ?: continue
            return ex to c
        }
        return null
    }

    fun plan(input: PlannerInput): PlannedWorkout {
        val mode = if (input.maxMinutes != null && input.maxMinutes <= 15) SessionMode.MINIMUM else input.mode
        val slots = templates[input.templateKey] ?: templates.getValue("A")
        val slotsForMode = if (mode == SessionMode.MINIMUM) slots.take(3) else slots

        val used = mutableSetOf<String>()
        val planned = mutableListOf<PlannedExercise>()
        for (slot in slotsForMode) {
            val (ex, compat) = pick(slot, input, used) ?: continue
            used += ex.id
            planned += prescribe(ex, compat, input, mode)
        }
        if (mode == SessionMode.EXTENDED) {
            var added = 0
            for (slot in extendedAccessories) {
                if (added == 2) break
                val (ex, compat) = pick(slot, input, used) ?: continue
                if (planned.any { it.exercise.pattern == ex.pattern }) continue
                used += ex.id
                planned += prescribe(ex, compat, input, mode)
                added++
            }
        }

        var exercises: List<PlannedExercise> = planned
        input.maxMinutes?.let { limit -> while (exercises.size > 1 && estimateMinutes(exercises, mode) > limit) exercises = exercises.dropLast(1) }

        val notes = buildList {
            if (input.recovery.needsLighterSession) add("Recovery looks limited, so the volume is reduced today.")
            if (input.phase == TrainingPhase.COMEBACK) add("Comeback phase: stop each set with 2–3 reps left in the tank. No training to failure.")
        }
        return PlannedWorkout(
            title = "${titleFor(mode)} · Full Body ${input.templateKey}",
            mode = mode,
            templateKey = input.templateKey,
            exercises = exercises,
            estimatedMinutes = estimateMinutes(exercises, mode),
            minimumCriteria = minimumCriteria(mode),
            notes = notes,
        )
    }

    fun titleFor(mode: SessionMode) = when (mode) {
        SessionMode.MINIMUM -> "Minimum"
        SessionMode.STANDARD -> "Standard"
        SessionMode.EXTENDED -> "Extended"
    }

    fun minimumCriteria(mode: SessionMode) = when (mode) {
        SessionMode.MINIMUM -> "Done = at least 1 set of each exercise. That counts as a real session."
        else -> "Done = at least 1 set of every exercise; anything more is a bonus."
    }

    private fun prescribe(ex: Exercise, compat: Compatibility.Ok, input: PlannerInput, mode: SessionMode): PlannedExercise {
        val records = input.history[ex.id].orEmpty()
        val load = chooseLoad(compat.loadOptionsKg, records)
        val comparable = records.filter { it.load == load }
        val r = Progression.next(ex, input.phase, comparable, input.recovery)
        var rx = r.prescription
        when (mode) {
            SessionMode.MINIMUM -> rx = rx.copy(sets = minOf(rx.sets, 2), restSeconds = minOf(rx.restSeconds, 60))
            SessionMode.EXTENDED -> if (ex.isCompound && !input.recovery.needsLighterSession) rx = rx.copy(sets = minOf(rx.sets + 1, Progression.maxSets(input.phase)))
            SessionMode.STANDARD -> Unit
        }
        return PlannedExercise(ex, rx, load, r.decision, r.note)
    }

    /** Reuse the last used load if still available; otherwise start conservatively in the lower third. */
    fun chooseLoad(options: List<Double>, records: List<ExerciseRecord>): Double? {
        if (options.isEmpty()) return null
        records.lastOrNull()?.load?.let { if (it in options) return it }
        return options[(options.size - 1) / 3]
    }

    fun estimateSeconds(rx: Prescription, perSide: Boolean): Int {
        val work = (rx.seconds ?: (((rx.repsMin + rx.repsMax) / 2.0) * 4).roundToInt()) * (if (perSide) 2 else 1)
        return rx.sets * work + (rx.sets - 1).coerceAtLeast(0) * rx.restSeconds + 45 // + setup/transition
    }

    fun estimateMinutes(exercises: List<PlannedExercise>, mode: SessionMode): Int {
        val warmup = if (mode == SessionMode.MINIMUM) 120 else 240
        val total = warmup + exercises.sumOf { estimateSeconds(it.prescription, it.exercise.perSide) + it.prescription.restSeconds / 2 }
        return (total / 60.0).roundToInt().coerceAtLeast(1)
    }
}
