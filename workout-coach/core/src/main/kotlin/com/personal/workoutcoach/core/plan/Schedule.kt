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
     * Up to [max] dates from today within the next [windowDays] that are free and not directly next to
     * another training session. Empty means: just continue with the next planned session.
     */
    fun rescheduleOptions(today: LocalDate, occupied: Set<LocalDate>, windowDays: Int = 4, max: Int = 2): List<LocalDate> =
        (0 until windowDays).map { today.plusDays(it.toLong()) }
            .filter { it !in occupied && it.minusDays(1) !in occupied && it.plusDays(1) !in occupied }
            .take(max)
}
