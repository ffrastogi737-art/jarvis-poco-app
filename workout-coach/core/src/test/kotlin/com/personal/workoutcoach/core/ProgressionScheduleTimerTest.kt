package com.personal.workoutcoach.core

import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.catalog.Prescription
import com.personal.workoutcoach.core.model.TrainingPhase
import com.personal.workoutcoach.core.plan.*
import com.personal.workoutcoach.core.timer.RestTimer
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.*

class ProgressionTest {
    private val squat = ExerciseCatalog.get("goblet_squat")!!
    private val plank = ExerciseCatalog.get("plank")!!
    private val d = LocalDate.of(2026, 1, 5)
    private fun rec(target: Prescription, vararg reps: Int) = ExerciseRecord(squat.id, d, target, reps.map { SetRecord(it, null, 17.5) })

    @Test fun startUsesComebackBase() {
        val r = Progression.next(squat, TrainingPhase.COMEBACK, emptyList(), RecoverySignal())
        assertEquals(ProgressionDecision.START, r.decision)
        assertEquals(2, r.prescription.sets)
    }

    @Test fun hittingTopIncreasesRepsOnly() {
        val t = Prescription(2, 8, 12, restSeconds = 90)
        val r = Progression.next(squat, TrainingPhase.COMEBACK, listOf(rec(t, 12, 12)), RecoverySignal())
        assertEquals(ProgressionDecision.INCREASE_REPS, r.decision)
        assertEquals(t.copy(repsMin = 10, repsMax = 14), r.prescription) // sets & rest unchanged
    }

    @Test fun atRepCapAddsSetAndResetsReps() {
        val t = Prescription(2, 13, 15, restSeconds = 90)
        val r = Progression.next(squat, TrainingPhase.COMEBACK, listOf(rec(t, 15, 15)), RecoverySignal())
        assertEquals(ProgressionDecision.ADD_SET, r.decision)
        assertEquals(3, r.prescription.sets)
        assertEquals(squat.start.repsMin, r.prescription.repsMin)
    }

    @Test fun atAllCapsSuggestsHarderVariation() {
        val t = Prescription(3, 13, 15, restSeconds = 90)
        val r = Progression.next(squat, TrainingPhase.COMEBACK, listOf(rec(t, 15, 15, 15)), RecoverySignal())
        assertEquals(ProgressionDecision.SUGGEST_HARDER_VARIATION, r.decision)
        assertEquals(t, r.prescription)
    }

    @Test fun partialPerformanceHolds() {
        val t = Prescription(2, 8, 12, restSeconds = 90)
        assertEquals(ProgressionDecision.HOLD, Progression.next(squat, TrainingPhase.COMEBACK, listOf(rec(t, 12, 10)), RecoverySignal()).decision)
    }

    @Test fun twoMissedSessionsReduceTarget() {
        val t = Prescription(2, 8, 12, restSeconds = 90)
        val r = Progression.next(squat, TrainingPhase.COMEBACK, listOf(rec(t, 6, 5), rec(t, 7)), RecoverySignal())
        assertEquals(ProgressionDecision.REDUCE_FOR_PERFORMANCE, r.decision)
        assertEquals(6, r.prescription.repsMin)
    }

    @Test fun longGapReducesInsteadOfProgressing() {
        val t = Prescription(2, 8, 12, restSeconds = 90)
        val r = Progression.next(squat, TrainingPhase.COMEBACK, listOf(rec(t, 12, 12)), RecoverySignal(daysSinceLastWorkout = 14))
        assertEquals(ProgressionDecision.REDUCE_FOR_RECOVERY, r.decision)
        assertEquals(1, r.prescription.sets)
    }

    @Test fun timedProgression() {
        val t = Prescription(2, seconds = 20, restSeconds = 45)
        val r = Progression.next(plank, TrainingPhase.COMEBACK, listOf(ExerciseRecord(plank.id, d, t, listOf(SetRecord(null, 20, null), SetRecord(null, 25, null)))), RecoverySignal())
        assertEquals(30, r.prescription.seconds)
    }

    @Test fun phasePolicy() {
        assertEquals(TrainingPhase.COMEBACK, PhasePolicy.phase(0, null, null))
        assertEquals(TrainingPhase.COMEBACK, PhasePolicy.phase(8, 2, 2.0))
        assertEquals(TrainingPhase.BUILD, PhasePolicy.phase(9, 2, 2.0))
        assertEquals(TrainingPhase.COMEBACK, PhasePolicy.phase(30, 25, 2.0))
        assertEquals(TrainingPhase.COMEBACK, PhasePolicy.phase(30, 2, 4.5))
    }
}

class ScheduleTest {
    private val mon = LocalDate.of(2026, 10, 19) // Monday

    @Test fun defaultDaysAreNonConsecutive() {
        assertFalse(ScheduleEngine.hasConsecutiveDays(ScheduleEngine.defaultTrainingDays))
        assertTrue(ScheduleEngine.hasConsecutiveDays(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)))
    }

    @Test fun plannedDates() {
        assertEquals(listOf(mon, mon.plusDays(2), mon.plusDays(4)), ScheduleEngine.plannedDates(ScheduleEngine.defaultTrainingDays, mon, mon.plusDays(6)))
    }

    @Test fun missedDetectionIgnoresTodayAndResolved() {
        val planned = listOf(mon, mon.plusDays(2), mon.plusDays(4))
        val missed = ScheduleEngine.findMissed(planned, completed = setOf(mon), resolved = emptySet(), today = mon.plusDays(4))
        assertEquals(listOf(mon.plusDays(2)), missed)
        assertTrue(ScheduleEngine.findMissed(planned, setOf(mon), setOf(mon.plusDays(2)), mon.plusDays(4)).isEmpty())
    }

    @Test fun rescheduleKeepsRestDaysAndNeverDoublesUp() {
        // Missed Wed; today Thu; Fri is planned. Thu is next to Fri → not offered; Sat is next to Fri → not offered; Sun ok.
        val thu = mon.plusDays(3)
        val opts = ScheduleEngine.rescheduleOptions(thu, occupied = setOf(mon, mon.plusDays(4)))
        assertEquals(listOf(mon.plusDays(6)), opts)
        for (o in opts) assertFalse(o == mon.plusDays(4))
    }

    @Test fun noRealisticSlotMeansContinueWithNextSession() {
        val today = mon
        val occupied = (0L..5L).map { mon.plusDays(it) }.filter { it.dayOfMonth % 2 == 1 }.toSet() + mon.plusDays(1)
        assertTrue(ScheduleEngine.rescheduleOptions(today, occupied, windowDays = 3).isEmpty())
    }
}

class RestTimerTest {
    @Test fun countdownDerivedFromEndTimeNoDrift() {
        val t = RestTimer(90_000).start(1_000)
        assertEquals(90_000, t.remaining(1_000))
        assertEquals(30_000, t.remaining(61_000))
        assertTrue(t.isFinished(91_000))
        assertEquals(RestTimer.Status.FINISHED, t.tick(200_000).status)
        assertEquals(0, t.remaining(500_000))
    }

    @Test fun pauseResume() {
        val paused = RestTimer(60_000).start(0).pause(20_000)
        assertEquals(RestTimer.Status.PAUSED, paused.status)
        assertEquals(40_000, paused.remaining(999_999)) // time frozen while paused
        val resumed = paused.resume(100_000)
        assertEquals(40_000, resumed.remaining(100_000))
        assertEquals(10_000, resumed.remaining(130_000))
    }

    @Test fun resetSkipAdjust() {
        val t = RestTimer(60_000).start(0)
        assertEquals(RestTimer.Status.IDLE, t.reset().status)
        assertEquals(60_000, t.reset().remaining(10))
        assertEquals(0, t.skip().remaining(0))
        val adj = t.adjust(15_000, 10_000)
        assertEquals(65_000, adj.remaining(10_000))
        assertEquals(75_000, adj.durationMs)
        assertEquals(RestTimer.MIN_MS, RestTimer(10_000).adjust(-60_000, 0).durationMs)
    }
}
