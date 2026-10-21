package com.personal.workoutcoach.data.repo

import com.personal.workoutcoach.core.catalog.Prescription
import com.personal.workoutcoach.core.equipment.*
import com.personal.workoutcoach.core.model.CoachStyle
import com.personal.workoutcoach.core.model.Language
import com.personal.workoutcoach.core.model.SessionStatus
import com.personal.workoutcoach.core.model.TrainingPhase
import com.personal.workoutcoach.core.plan.ExerciseRecord
import com.personal.workoutcoach.core.plan.SetRecord
import com.personal.workoutcoach.core.progress.SessionSummary
import com.personal.workoutcoach.data.db.*
import java.time.DayOfWeek
import java.time.LocalDate

private inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? = this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() }
private inline fun <reified E : Enum<E>> String.toEnumSet(): Set<E> = split(',').mapNotNull { it.trim().ifEmpty { null }.toEnumOrNull<E>() }.toSet()

fun EquipmentEntity.toItem() = EquipmentItem(
    id = id,
    type = type.toEnumOrNull<EquipmentType>() ?: EquipmentType.CUSTOM,
    name = name, enabled = enabled, quantity = quantity, weightKg = weightKg,
    minWeightKg = minWeightKg, maxWeightKg = maxWeightKg, incrementKg = incrementKg,
    bandType = bandType.toEnumOrNull<BandType>(), resistanceLabel = resistanceLabel, resistanceKg = resistanceKg,
    anchor = anchor.toEnumOrNull<BandAnchor>(), benchPositions = benchPositions.toEnumSet(),
    mountType = mountType.toEnumOrNull<MountType>(), customCapabilities = customCapabilities.toEnumSet(), notes = notes,
)

fun EquipmentItem.toEntity(createdAt: Long = System.currentTimeMillis()) = EquipmentEntity(
    id = id, type = type.name, name = name.trim(), enabled = enabled, quantity = quantity, weightKg = weightKg,
    minWeightKg = minWeightKg, maxWeightKg = maxWeightKg, incrementKg = incrementKg,
    bandType = bandType?.name, resistanceLabel = resistanceLabel?.trim(), resistanceKg = resistanceKg, anchor = anchor?.name,
    benchPositions = benchPositions.joinToString(",") { it.name }, mountType = mountType?.name,
    customCapabilities = customCapabilities.joinToString(",") { it.name }, notes = notes,
    createdAt = createdAt, updatedAt = System.currentTimeMillis(),
)

val UserProfileEntity.languageEnum get() = language.toEnumOrNull<Language>() ?: Language.HINGLISH
val UserProfileEntity.styleEnum get() = coachStyle.toEnumOrNull<CoachStyle>() ?: CoachStyle.BALANCED
val UserProfileEntity.phaseEnum get() = phase.toEnumOrNull<TrainingPhase>() ?: TrainingPhase.COMEBACK
val UserProfileEntity.days: Set<DayOfWeek> get() = trainingDays.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { d -> d in 1..7 }?.let(DayOfWeek::of) }.toSet()
val UserProfileEntity.excluded: Set<String> get() = excludedExercises.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
fun Set<DayOfWeek>.toStored() = sorted().joinToString(",") { it.value.toString() }

fun ExerciseWithSets.toRecord(date: LocalDate) = ExerciseRecord(
    exerciseId = exercise.exerciseId,
    date = date,
    target = Prescription(exercise.plannedSets, exercise.repsMin, exercise.repsMax, exercise.seconds, exercise.restSeconds),
    sets = sets.sortedBy { it.setNumber }.map { SetRecord(it.reps, it.seconds, it.weightKg) },
)

fun SessionWithExercises.toSummary() = SessionSummary(
    date = LocalDate.ofEpochDay(session.plannedDate),
    status = session.status.toEnumOrNull<SessionStatus>() ?: SessionStatus.ABANDONED,
    durationMinutes = (session.activeDurationMs(System.currentTimeMillis()) / 60_000).toInt(),
    exercises = orderedExercises.map { it.toRecord(LocalDate.ofEpochDay(session.plannedDate)) },
)
