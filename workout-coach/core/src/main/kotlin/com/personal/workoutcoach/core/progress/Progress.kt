package com.personal.workoutcoach.core.progress

import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.model.Language
import com.personal.workoutcoach.core.model.ScheduleStatus
import com.personal.workoutcoach.core.model.SessionStatus
import com.personal.workoutcoach.core.plan.ExerciseRecord
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class SessionSummary(
    val date: LocalDate,
    val status: SessionStatus,
    val durationMinutes: Int,
    val exercises: List<ExerciseRecord>,
) {
    /** Minimum and partial sessions with at least one recorded set count as real sessions. */
    val counts: Boolean get() = (status == SessionStatus.COMPLETED || status == SessionStatus.PARTIAL) && exercises.any { it.sets.isNotEmpty() }
}

data class FeedbackPoint(val date: LocalDate, val energy: Int?, val soreness: Int?, val motivation: Int?)

data class ScheduleEntryPoint(val date: LocalDate, val status: ScheduleStatus)

/** A like-for-like comparison: same exercise, same recorded load. */
data class ExerciseChange(val exerciseId: String, val name: String, val loadKg: Double?, val previousBest: Int, val currentBest: Int, val unit: String) {
    val delta get() = currentBest - previousBest
}

data class WeeklyReport(
    val weekStart: LocalDate,
    val completedSessions: Int,
    val trainingDays: Int,
    val totalMinutes: Int,
    val exercisesCompleted: List<String>,
    val changes: List<ExerciseChange>,
    val missed: Int,
    val avgEnergy: Double?,
    val avgSoreness: Double?,
    val insufficientData: Boolean,
    val recommendation: String,
)

data class WeekConsistency(val weekStart: LocalDate, val completed: Int, val planned: Int, val missed: Int)

object ProgressCalculator {
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun best(r: ExerciseRecord): Int? = r.sets.mapNotNull { if (r.target.isTimed) it.seconds else it.reps }.maxOrNull()

    fun weeklyReport(
        weekStart: LocalDate,
        sessions: List<SessionSummary>,
        schedule: List<ScheduleEntryPoint>,
        feedback: List<FeedbackPoint>,
        language: Language,
    ): WeeklyReport {
        val end = weekStart.plusDays(7)
        fun inWeek(d: LocalDate) = !d.isBefore(weekStart) && d.isBefore(end)
        val week = sessions.filter { it.counts && inWeek(it.date) }
        val before = sessions.filter { it.counts && it.date.isBefore(weekStart) }
        val missed = schedule.count { inWeek(it.date) && it.status == ScheduleStatus.MISSED }
        val fb = feedback.filter { inWeek(it.date) }

        val records = week.flatMap { it.exercises }.filter { it.sets.isNotEmpty() }
        val changes = records.groupBy { it.exerciseId to it.load }.mapNotNull { (key, now) ->
            val (id, load) = key
            val prev = before.flatMap { it.exercises }.filter { it.exerciseId == id && it.load == load }.mapNotNull(::best).maxOrNull() ?: return@mapNotNull null
            val cur = now.mapNotNull(::best).maxOrNull() ?: return@mapNotNull null
            ExerciseChange(id, ExerciseCatalog.get(id)?.name ?: id, load, prev, cur, if (now.first().target.isTimed) "s" else "reps")
        }
        val avgEnergy = fb.mapNotNull { it.energy }.takeIf { it.isNotEmpty() }?.average()
        val avgSoreness = fb.mapNotNull { it.soreness }.takeIf { it.isNotEmpty() }?.average()
        val completed = week.size
        return WeeklyReport(
            weekStart = weekStart,
            completedSessions = completed,
            trainingDays = week.map { it.date }.distinct().size,
            totalMinutes = week.sumOf { it.durationMinutes },
            exercisesCompleted = records.map { ExerciseCatalog.get(it.exerciseId)?.name ?: it.exerciseId }.distinct(),
            changes = changes,
            missed = missed,
            avgEnergy = avgEnergy,
            avgSoreness = avgSoreness,
            insufficientData = completed == 0 && missed == 0,
            recommendation = recommend(completed, missed, avgSoreness, avgEnergy, language),
        )
    }

    fun recommend(completed: Int, missed: Int, soreness: Double?, energy: Double?, l: Language): String {
        val hi = l == Language.HINGLISH
        return when {
            completed == 0 && missed == 0 -> if (hi) "Abhi data kam hai. Ek Minimum session se shuru karo, phir report better hogi." else "Not enough records yet. Start with one Minimum session and the report will fill in."
            soreness != null && soreness >= 4 -> if (hi) "Soreness high hai. Next week volume thoda kam rakho aur sleep pe dhyan do." else "Soreness was high. Keep next week's volume a bit lower and prioritise sleep."
            missed > completed -> if (hi) "Plan schedule mein fit nahi ho raha. Next week 2 sessions ya Minimum Mode try karo, ya reminder time badlo." else "The plan isn't fitting your week. Try 2 sessions or Minimum Mode next week, or move your reminder time."
            energy != null && energy <= 2 -> if (hi) "Energy low rahi. Shorter sessions rakho jab tak recovery better na ho." else "Energy was low. Keep sessions shorter until recovery improves."
            completed >= 3 -> if (hi) "Solid week! Same schedule continue karo; reps target automatically badhenge jab tum top range hit karoge." else "Solid week! Keep the same schedule; rep targets go up automatically once you hit the top of the range."
            else -> if (hi) "Achha start. Next week ek aur session add karne ka try karo, chahe Minimum hi ho." else "Good start. Next week, try to add one more session, even a Minimum one."
        }
    }

    fun consistency(sessions: List<SessionSummary>, schedule: List<ScheduleEntryPoint>, today: LocalDate, weeks: Int): List<WeekConsistency> =
        (weeks - 1 downTo 0).map { i ->
            val ws = weekStart(today).minusWeeks(i.toLong())
            val we = ws.plusDays(7)
            fun inW(d: LocalDate) = !d.isBefore(ws) && d.isBefore(we)
            WeekConsistency(
                ws,
                completed = sessions.count { it.counts && inW(it.date) },
                planned = schedule.count { inW(it.date) && it.status != ScheduleStatus.RESCHEDULED },
                missed = schedule.count { inW(it.date) && it.status == ScheduleStatus.MISSED },
            )
        }

    /** Total recorded reps (or seconds) per session for one exercise, for trend charts. Grouped by load. */
    fun exerciseTrend(sessions: List<SessionSummary>, exerciseId: String): Map<Double?, List<Pair<LocalDate, Int>>> =
        sessions.filter { it.counts }.sortedBy { it.date }.flatMap { s ->
            s.exercises.filter { it.exerciseId == exerciseId && it.sets.isNotEmpty() }
                .map { r -> r.load to (s.date to r.sets.sumOf { (if (r.target.isTimed) it.seconds else it.reps) ?: 0 }) }
        }.groupBy({ it.first }, { it.second })

    /** Consecutive weeks (ending this or last week) with at least one counted session. Real data only. */
    fun weekStreak(sessions: List<SessionSummary>, today: LocalDate): Int {
        val weeks = sessions.filter { it.counts }.map { weekStart(it.date) }.toSet()
        var w = weekStart(today).let { if (it in weeks) it else it.minusWeeks(1) }
        var n = 0
        while (w in weeks) { n++; w = w.minusWeeks(1) }
        return n
    }
}
