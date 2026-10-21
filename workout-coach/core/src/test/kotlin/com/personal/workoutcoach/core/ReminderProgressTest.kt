package com.personal.workoutcoach.core

import com.personal.workoutcoach.core.catalog.Prescription
import com.personal.workoutcoach.core.model.Language
import com.personal.workoutcoach.core.model.ScheduleStatus
import com.personal.workoutcoach.core.model.SessionStatus
import com.personal.workoutcoach.core.plan.ExerciseRecord
import com.personal.workoutcoach.core.plan.SetRecord
import com.personal.workoutcoach.core.progress.*
import com.personal.workoutcoach.core.reminders.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.*

class ReminderPolicyTest {
    private val now = LocalDateTime.of(2026, 10, 19, 18, 0)
    private val base = ReminderContext(now, ReminderType.TRAINING_DAY, enabled = true, remindersPaused = false,
        quietStart = LocalTime.of(22, 0), quietEnd = LocalTime.of(7, 0), maxPerDay = 2, sentToday = 0, ignoredStreak = 0,
        lastSentDate = null, isTrainingDay = true, workoutDoneToday = false, hasUnresolvedMissedSession = false)

    @Test fun sendsOnTrainingDay() {
        val d = ReminderPolicy.decide(base, Language.HINGLISH)
        assertIs<ReminderDecision.Send>(d)
        assertTrue(d.body.contains("15-minute session bhi enough"))
        assertTrue((ReminderPolicy.decide(base, Language.ENGLISH) as ReminderDecision.Send).body.startsWith("It's workout time"))
    }

    @Test fun skipsWhenDisabledPausedRestDayDoneOrLimit() {
        assertIs<ReminderDecision.Skip>(ReminderPolicy.decide(base.copy(enabled = false), Language.ENGLISH))
        assertIs<ReminderDecision.Skip>(ReminderPolicy.decide(base.copy(remindersPaused = true), Language.ENGLISH))
        assertIs<ReminderDecision.Skip>(ReminderPolicy.decide(base.copy(isTrainingDay = false), Language.ENGLISH))
        assertIs<ReminderDecision.Skip>(ReminderPolicy.decide(base.copy(workoutDoneToday = true), Language.ENGLISH))
        assertIs<ReminderDecision.Skip>(ReminderPolicy.decide(base.copy(sentToday = 2), Language.ENGLISH))
    }

    @Test fun quietHoursAcrossMidnight() {
        assertTrue(ReminderPolicy.inQuietHours(LocalTime.of(23, 30), LocalTime.of(22, 0), LocalTime.of(7, 0)))
        assertTrue(ReminderPolicy.inQuietHours(LocalTime.of(6, 59), LocalTime.of(22, 0), LocalTime.of(7, 0)))
        assertFalse(ReminderPolicy.inQuietHours(LocalTime.of(7, 0), LocalTime.of(22, 0), LocalTime.of(7, 0)))
        assertTrue(ReminderPolicy.inQuietHours(LocalTime.of(13, 0), LocalTime.of(12, 0), LocalTime.of(14, 0)))
        assertIs<ReminderDecision.Skip>(ReminderPolicy.decide(base.copy(now = now.withHour(23)), Language.ENGLISH))
    }

    @Test fun backsOffInsteadOfEscalatingWhenIgnored() {
        val ignored = base.copy(ignoredStreak = 3, lastSentDate = now.toLocalDate().minusDays(1))
        assertIs<ReminderDecision.Skip>(ReminderPolicy.decide(ignored, Language.ENGLISH))
        val next = ReminderPolicy.decide(ignored.copy(lastSentDate = now.toLocalDate().minusDays(2)), Language.ENGLISH)
        assertIs<ReminderDecision.Send>(next)
        assertTrue(next.body.contains("change it in Settings"))
    }

    @Test fun missedFollowUpOnlyWhenSomethingMissed() {
        assertIs<ReminderDecision.Skip>(ReminderPolicy.decide(base.copy(type = ReminderType.MISSED_FOLLOWUP), Language.ENGLISH))
        assertIs<ReminderDecision.Send>(ReminderPolicy.decide(base.copy(type = ReminderType.MISSED_FOLLOWUP, hasUnresolvedMissedSession = true), Language.ENGLISH))
    }

    @Test fun nextTrigger() {
        assertEquals(now.withHour(19), ReminderPolicy.nextTrigger(now, LocalTime.of(19, 0)))
        assertEquals(now.plusDays(1).withHour(7), ReminderPolicy.nextTrigger(now, LocalTime.of(7, 0)))
    }
}

class ProgressTest {
    private val ws = LocalDate.of(2026, 10, 19)
    private fun session(date: LocalDate, load: Double?, vararg reps: Int, status: SessionStatus = SessionStatus.COMPLETED) =
        SessionSummary(date, status, 20, listOf(ExerciseRecord("goblet_squat", date, Prescription(2, 8, 12, restSeconds = 90), reps.map { SetRecord(it, null, load) })))

    @Test fun insufficientData() {
        val r = ProgressCalculator.weeklyReport(ws, emptyList(), emptyList(), emptyList(), Language.ENGLISH)
        assertTrue(r.insufficientData)
        assertEquals(0, r.completedSessions)
        assertTrue(r.recommendation.contains("Not enough records"))
    }

    @Test fun weeklyReportFromRealRecords() {
        val sessions = listOf(
            session(ws.minusDays(5), 17.5, 8, 8),
            session(ws, 17.5, 10, 9),
            session(ws.plusDays(2), 17.5, 11, 10),
            session(ws.plusDays(3), null, status = SessionStatus.ABANDONED),
        )
        val schedule = listOf(ScheduleEntryPoint(ws.plusDays(4), ScheduleStatus.MISSED))
        val fb = listOf(FeedbackPoint(ws, 3, 2, 4), FeedbackPoint(ws.plusDays(2), 4, 3, null))
        val r = ProgressCalculator.weeklyReport(ws, sessions, schedule, fb, Language.ENGLISH)
        assertEquals(2, r.completedSessions)
        assertEquals(40, r.totalMinutes)
        assertEquals(1, r.missed)
        assertEquals(3.5, r.avgEnergy)
        val change = r.changes.single()
        assertEquals(8, change.previousBest); assertEquals(11, change.currentBest)
        assertFalse(r.insufficientData)
    }

    @Test fun differentLoadsAreNotCompared() {
        val sessions = listOf(session(ws.minusDays(3), 10.0, 15, 15), session(ws, 17.5, 8, 8))
        assertTrue(ProgressCalculator.weeklyReport(ws, sessions, emptyList(), emptyList(), Language.ENGLISH).changes.isEmpty())
    }

    @Test fun consistencyAndStreakUseOnlyRealSessions() {
        val sessions = listOf(session(ws, 17.5, 8), session(ws.minusWeeks(1), 17.5, 8), session(ws.minusWeeks(3), 17.5, 8))
        assertEquals(2, ProgressCalculator.weekStreak(sessions, ws.plusDays(2)))
        val c = ProgressCalculator.consistency(sessions, listOf(ScheduleEntryPoint(ws, ScheduleStatus.DONE), ScheduleEntryPoint(ws.plusDays(2), ScheduleStatus.MISSED)), ws.plusDays(3), 4)
        assertEquals(4, c.size)
        assertEquals(1, c.last().completed); assertEquals(2, c.last().planned); assertEquals(1, c.last().missed)
        assertEquals(0, c[1].completed)
    }

    @Test fun minimumAndPartialSessionsCount() {
        assertTrue(session(ws, 17.5, 5, status = SessionStatus.PARTIAL).counts)
        assertFalse(SessionSummary(ws, SessionStatus.COMPLETED, 3, emptyList()).counts)
    }

    @Test fun trendGroupedByLoad() {
        val t = ProgressCalculator.exerciseTrend(listOf(session(ws, 17.5, 8, 9), session(ws.plusDays(2), 10.0, 15)), "goblet_squat")
        assertEquals(listOf(ws to 17), t[17.5])
        assertEquals(listOf(ws.plusDays(2) to 15), t[10.0])
    }

    @Test fun hinglishRecommendation() {
        assertTrue(ProgressCalculator.recommend(0, 0, null, null, Language.HINGLISH).contains("Minimum session"))
    }
}
