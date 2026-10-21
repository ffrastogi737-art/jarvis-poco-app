package com.personal.workoutcoach.core

import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.catalog.Prescription
import com.personal.workoutcoach.core.equipment.*
import com.personal.workoutcoach.core.model.SessionMode
import com.personal.workoutcoach.core.model.TrainingPhase
import com.personal.workoutcoach.core.plan.*
import java.time.LocalDate
import kotlin.test.*

class PlannerTest {
    private val inv = Inventory(defaultInventory())
    private fun input(mode: SessionMode = SessionMode.STANDARD, t: String = "A", phase: TrainingPhase = TrainingPhase.COMEBACK, inventory: Inventory = inv) =
        PlannerInput(inventory, phase, mode, t)

    @Test fun defaultPlansRespectEquipmentAndComebackLimits() {
        for (t in WorkoutPlanner.templateKeys) for (mode in SessionMode.entries) {
            val p = WorkoutPlanner.plan(input(mode, t))
            assertTrue(p.exercises.isNotEmpty())
            for (e in p.exercises) {
                assertTrue(inv.isCompatible(e.exercise), "${e.exercise.id} not compatible")
                assertTrue(e.exercise.difficulty <= 2, "${e.exercise.id} too hard for comeback")
                assertTrue(e.prescription.sets <= 3)
                if (e.loadKg != null) assertEquals(17.5, e.loadKg)
            }
            assertEquals(p.exercises.size, p.exercises.map { it.exercise.id }.toSet().size, "no duplicates")
        }
    }

    @Test fun comebackStartsWithTwoSetsInStandard() {
        val p = WorkoutPlanner.plan(input())
        assertTrue(p.exercises.all { it.prescription.sets == 2 })
    }

    @Test fun modeDurations() {
        for (t in WorkoutPlanner.templateKeys) {
            val min = WorkoutPlanner.plan(input(SessionMode.MINIMUM, t))
            val std = WorkoutPlanner.plan(input(SessionMode.STANDARD, t))
            val ext = WorkoutPlanner.plan(input(SessionMode.EXTENDED, t))
            assertEquals(3, min.exercises.size)
            assertTrue(min.estimatedMinutes in 8..16, "minimum $t = ${min.estimatedMinutes}")
            assertTrue(std.estimatedMinutes in 18..35, "standard $t = ${std.estimatedMinutes}")
            assertTrue(ext.estimatedMinutes in 30..50, "extended $t = ${ext.estimatedMinutes}")
            assertTrue(ext.estimatedMinutes > std.estimatedMinutes)
        }
    }

    @Test fun timeLimitTrimsWorkout() {
        val p = WorkoutPlanner.plan(input().copy(maxMinutes = 12))
        assertEquals(SessionMode.MINIMUM, p.mode)
        assertTrue(p.estimatedMinutes <= 12, "${p.estimatedMinutes}")
    }

    @Test fun excludedExercisesAreReplaced() {
        val p = WorkoutPlanner.plan(input().copy(excludedExerciseIds = setOf("goblet_squat")))
        assertFalse(p.exercises.any { it.exercise.id == "goblet_squat" })
        assertTrue(p.exercises.any { it.exercise.id == "bodyweight_squat" })
    }

    @Test fun recalculatesWhenEquipmentChanges() {
        val bar = EquipmentItem(type = EquipmentType.PULL_UP_BAR, name = "Bar", mountType = MountType.DOORFRAME_LEVERAGE)
        val withBar = Inventory(defaultInventory() + bar)
        val before = WorkoutPlanner.plan(input(t = "B"))
        val after = WorkoutPlanner.plan(input(t = "B", inventory = withBar))
        assertFalse(before.exercises.any { it.exercise.id == "negative_pull_up" })
        assertTrue(after.exercises.any { it.exercise.id == "negative_pull_up" })
        // Bodyweight only still yields a full plan
        val bw = WorkoutPlanner.plan(input(inventory = Inventory(emptyList())))
        assertTrue(bw.exercises.size >= 4)
        assertTrue(bw.exercises.all { it.exercise.requires.isEmpty() })
    }

    @Test fun templateRotation() {
        assertEquals("A", WorkoutPlanner.nextTemplate(null))
        assertEquals("B", WorkoutPlanner.nextTemplate("A"))
        assertEquals("A", WorkoutPlanner.nextTemplate("C"))
    }

    @Test fun chooseLoadIsConservativeAndSticky() {
        assertEquals(5.0, WorkoutPlanner.chooseLoad(listOf(2.5, 5.0, 7.5, 10.0), emptyList()))
        val rec = ExerciseRecord("x", LocalDate.now(), Prescription(2, 8, 12, restSeconds = 60), listOf(SetRecord(10, null, 7.5)))
        assertEquals(7.5, WorkoutPlanner.chooseLoad(listOf(2.5, 5.0, 7.5, 10.0), listOf(rec)))
    }

    @Test fun recoveryReducesNextSessionUsingHistory() {
        val ex = ExerciseCatalog.get("goblet_squat")!!
        val rec = ExerciseRecord(ex.id, LocalDate.now(), Prescription(2, 8, 12, restSeconds = 90), List(2) { SetRecord(12, null, 17.5) })
        val p = WorkoutPlanner.plan(input().copy(history = mapOf(ex.id to listOf(rec)), recovery = RecoverySignal(soreness = 5)))
        val gs = p.exercises.first { it.exercise.id == ex.id }
        assertEquals(1, gs.prescription.sets)
        assertEquals(ProgressionDecision.REDUCE_FOR_RECOVERY, gs.decision)
    }
}
