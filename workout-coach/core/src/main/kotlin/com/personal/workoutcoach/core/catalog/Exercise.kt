package com.personal.workoutcoach.core.catalog

import com.personal.workoutcoach.core.equipment.Capability

enum class Pattern { SQUAT, HINGE, LUNGE, HORIZONTAL_PUSH, VERTICAL_PUSH, HORIZONTAL_PULL, VERTICAL_PULL, BICEPS, TRICEPS, SHOULDER_ISOLATION, CALF, CORE }

enum class LoadKind { BODYWEIGHT, DUMBBELL_SINGLE, DUMBBELL_PAIR, KETTLEBELL, BAND, BARBELL, ANKLE_WEIGHT }

/** A prescription target. Timed exercises use [seconds]; rep ranges otherwise. */
data class Prescription(
    val sets: Int,
    val repsMin: Int = 0,
    val repsMax: Int = 0,
    val seconds: Int? = null,
    val restSeconds: Int,
) {
    val isTimed get() = seconds != null
    fun describe(): String = if (seconds != null) "$sets × ${seconds}s" else "$sets × $repsMin–$repsMax"
}

data class Exercise(
    val id: String,
    val name: String,
    val muscles: List<String>,
    val pattern: Pattern,
    val requires: Set<Capability>,
    val loadKind: LoadKind,
    /** 1 = beginner friendly, 3 = demanding. Comeback phase uses ≤ 2. */
    val difficulty: Int,
    val perSide: Boolean = false,
    val isCompound: Boolean,
    /** Heaviest load (per dumbbell / total) we consider controllable for this movement. */
    val maxLoadKg: Double? = null,
    val start: Prescription,
    val instructions: List<String>,
    val cues: List<String>,
    val commonErrors: List<String>,
    val easier: List<String> = emptyList(),
    val harder: List<String> = emptyList(),
    val safetyNotes: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
)
