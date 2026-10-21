package com.personal.workoutcoach.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.personal.workoutcoach.WorkoutCoachApp
import com.personal.workoutcoach.core.model.ScheduleStatus
import com.personal.workoutcoach.core.reminders.ReminderContext
import com.personal.workoutcoach.core.reminders.ReminderDecision
import com.personal.workoutcoach.core.reminders.ReminderPolicy
import com.personal.workoutcoach.core.reminders.ReminderType
import com.personal.workoutcoach.data.db.ReminderConfigEntity
import com.personal.workoutcoach.data.repo.languageEnum
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One unique WorkManager job per reminder type, re-armed after each run. WorkManager survives
 * reboots; timing is approximate (Doze/battery optimisation can delay it by minutes).
 */
class ReminderScheduler(private val context: Context) {
    fun schedule(config: ReminderConfigEntity, now: LocalDateTime = LocalDateTime.now()) {
        val name = "reminder_${config.type}"
        val wm = WorkManager.getInstance(context)
        if (!config.enabled) { wm.cancelUniqueWork(name); return }
        val next = ReminderPolicy.nextTrigger(now, LocalTime.of(config.minuteOfDay / 60, config.minuteOfDay % 60))
        wm.enqueueUniqueWork(
            name, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(Duration.between(now, next))
                .setInputData(workDataOf(ReminderWorker.KEY_TYPE to config.type))
                .addTag("reminder")
                .build(),
        )
    }

    fun scheduleAll(configs: List<ReminderConfigEntity>) = configs.forEach { schedule(it) }

    fun cancelAll() = WorkManager.getInstance(context).cancelAllWorkByTag("reminder")
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object { const val KEY_TYPE = "type" }

    override suspend fun doWork(): Result {
        val c = (applicationContext as WorkoutCoachApp).container
        val typeName = inputData.getString(KEY_TYPE) ?: return Result.success()
        val type = runCatching { ReminderType.valueOf(typeName) }.getOrNull() ?: return Result.success()
        val config = c.db.reminders().get(typeName) ?: return Result.success()
        val profile = c.db.profile().get()
        if (profile == null || !profile.onboarded) return Result.success()

        c.training.ensureSchedule()
        val s = c.settings.current()
        c.settings.updateIgnoredStreak()
        val today = c.training.today()
        val state = c.settings.reminderState(today.toEpochDay())
        val entry = c.training.todayEntry()
        val decision = ReminderPolicy.decide(
            ReminderContext(
                now = LocalDateTime.now(), type = type, enabled = config.enabled, remindersPaused = s.remindersPaused,
                quietStart = minutesToTime(s.quietStartMinutes), quietEnd = minutesToTime(s.quietEndMinutes),
                maxPerDay = s.maxNotificationsPerDay, sentToday = state.sentToday, ignoredStreak = state.ignoredStreak,
                lastSentDate = state.lastSentDay?.let { java.time.LocalDate.ofEpochDay(it) },
                isTrainingDay = entry != null && entry.status != ScheduleStatus.RESCHEDULED.name && entry.status != ScheduleStatus.SKIPPED.name,
                workoutDoneToday = entry?.status == ScheduleStatus.DONE.name,
                hasUnresolvedMissedSession = c.training.unresolvedMissed().isNotEmpty(),
            ),
            profile.languageEnum,
        )
        if (decision is ReminderDecision.Send && Notifications.show(applicationContext, 2000 + type.ordinal, Notifications.CHANNEL_REMINDERS, decision.title, decision.body)) {
            c.settings.recordReminderSent(today.toEpochDay(), System.currentTimeMillis())
        }
        // Re-arm for the next occurrence (at least a minute ahead so it can't loop).
        c.reminderScheduler.schedule(config, LocalDateTime.now().plusMinutes(1))
        return Result.success()
    }

    private fun minutesToTime(m: Int) = LocalTime.of((m / 60) % 24, m % 60)
}
