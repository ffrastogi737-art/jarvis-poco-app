package com.personal.workoutcoach.core.plan

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Scheduling rules. A missed session is a scheduling event: it is recorded, and the user is offered
 * a realistic new slot that keeps a rest day between sessions. Sessions are never doubled up.
 */
object ScheduleEngine {
    val defaultTrainingDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

    fun hasConsecutiveDays(days: Set<DayOfWeek>): Boolean = days.any { it.plus(1) in days }

    fun plannedDates(days: Set<DayOfWeek>, from: LocalDate, toInclusive: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(toInclusive) }.filter { it.dayOfWeek in days }.toList()

    /**
     * Past planned dates that were neither completed nor already resolved (rescheduled/skipped/missed).
     * [today] itself is never reported as missed.
     */
    fun findMissed(planned: Collection<LocalDate>, completed: Set<LocalDate>, resolved: Set<LocalDate>, today: LocalDate): List<LocalDate> =
        planned.filter { it.isBefore(today) && it !in completed && it !in resolved }.sorted()

    /**
     * Up to [max] free dates from today within the next [windowDays]. Never the day before/after a
     * completed session (recovery). Dates with no planned neighbour come first; a date next to a
     * planned session is offered only as a fallback (the user can always skip instead).
     * Empty means: just continue with the next planned session.
     */
    fun rescheduleOptions(today: LocalDate, occupied: Set<LocalDate>, completed: Set<LocalDate> = emptySet(), windowDays: Int = 4, max: Int = 2): List<LocalDate> {
        fun near(d: LocalDate, set: Set<LocalDate>) = d.minusDays(1) in set || d.plusDays(1) in set
        return (0 until windowDays).map { today.plusDays(it.toLong()) }
            .filter { it !in occupied && it !in completed && !near(it, completed) }
            .sortedWith(compareBy({ near(it, occupied) }, { it }))
            .take(max)
    }
}
