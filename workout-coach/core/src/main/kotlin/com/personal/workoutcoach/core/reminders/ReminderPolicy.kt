package com.personal.workoutcoach.core.reminders

import com.personal.workoutcoach.core.model.Language
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ReminderType { TRAINING_DAY, DAILY_CHECKIN, MISSED_FOLLOWUP }

data class ReminderContext(
    val now: LocalDateTime,
    val type: ReminderType,
    val enabled: Boolean,
    val remindersPaused: Boolean,
    val quietStart: LocalTime?,
    val quietEnd: LocalTime?,
    val maxPerDay: Int,
    val sentToday: Int,
    /** Consecutive reminders sent with no app open afterwards. */
    val ignoredStreak: Int,
    val lastSentDate: LocalDate?,
    val isTrainingDay: Boolean,
    val workoutDoneToday: Boolean,
    val hasUnresolvedMissedSession: Boolean,
)

sealed interface ReminderDecision {
    data class Send(val title: String, val body: String) : ReminderDecision
    data class Skip(val reason: String) : ReminderDecision
}

/**
 * Decides whether a reminder may be shown. Never escalates when ignored: after 3 ignored reminders it
 * sends at most every other day and suggests changing the time instead.
 */
object ReminderPolicy {
    const val IGNORED_THRESHOLD = 3

    fun inQuietHours(t: LocalTime, start: LocalTime?, end: LocalTime?): Boolean {
        if (start == null || end == null || start == end) return false
        return if (start < end) t >= start && t < end else t >= start || t < end // spans midnight
    }

    fun decide(c: ReminderContext, language: Language): ReminderDecision {
        if (!c.enabled) return ReminderDecision.Skip("disabled")
        if (c.remindersPaused) return ReminderDecision.Skip("paused")
        if (inQuietHours(c.now.toLocalTime(), c.quietStart, c.quietEnd)) return ReminderDecision.Skip("quiet hours")
        if (c.sentToday >= c.maxPerDay) return ReminderDecision.Skip("daily limit")
        when (c.type) {
            ReminderType.TRAINING_DAY -> {
                if (!c.isTrainingDay) return ReminderDecision.Skip("rest day")
                if (c.workoutDoneToday) return ReminderDecision.Skip("already trained")
            }
            ReminderType.MISSED_FOLLOWUP -> if (!c.hasUnresolvedMissedSession) return ReminderDecision.Skip("nothing missed")
            ReminderType.DAILY_CHECKIN -> if (c.workoutDoneToday) return ReminderDecision.Skip("already trained")
        }
        val backingOff = c.ignoredStreak >= IGNORED_THRESHOLD
        if (backingOff && c.lastSentDate == c.now.toLocalDate().minusDays(1)) return ReminderDecision.Skip("backing off after ignored reminders")
        return ReminderDecision.Send(title(c.type, language), body(c.type, language, backingOff))
    }

    fun nextTrigger(now: LocalDateTime, time: LocalTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun title(type: ReminderType, l: Language) = when (type) {
        ReminderType.TRAINING_DAY -> if (l == Language.HINGLISH) "Workout ka time" else "Workout time"
        ReminderType.DAILY_CHECKIN -> if (l == Language.HINGLISH) "Quick check-in" else "Quick check-in"
        ReminderType.MISSED_FOLLOWUP -> if (l == Language.HINGLISH) "Chalo, wapas track pe" else "Let's get back on track"
    }

    private fun body(type: ReminderType, l: Language, suggestRetime: Boolean): String {
        val base = when (type) {
            ReminderType.TRAINING_DAY -> if (l == Language.HINGLISH) "Workout ka time hai. Aaj 15-minute session bhi enough hai to get started. Ready?"
            else "It's workout time. Even a 15-minute session today counts. Ready?"
            ReminderType.DAILY_CHECKIN -> if (l == Language.HINGLISH) "Aaj energy kaisi hai? 10 second mein check-in kar lo."
            else "How's your energy today? A 10-second check-in helps plan your next session."
            ReminderType.MISSED_FOLLOWUP -> if (l == Language.HINGLISH) "Pichla session miss hua, koi baat nahi. Next realistic slot choose karte hain."
            else "Last session got missed — no problem. Let's pick the next realistic slot."
        }
        val retime = if (l == Language.HINGLISH) " (Yeh time suit nahi kar raha? Settings mein reminder time badal lo.)"
        else " (Is this time not working? You can change it in Settings.)"
        return if (suggestRetime) base + retime else base
    }
}
