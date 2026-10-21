package com.personal.workoutcoach.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personal.workoutcoach.MainActivity
import com.personal.workoutcoach.R

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_TIMER = "rest_timer"
    private const val ID_TIMER = 1001

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Training-day reminders and check-ins. Configure times and quiet hours in Settings."
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TIMER, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tells you when your rest period is over."
        })
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun openAppIntent(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Returns false if notifications are not permitted (nothing is shown). */
    fun show(context: Context, id: Int, channel: String, title: String, body: String): Boolean {
        if (!canPost(context)) return false
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true) // always dismissible
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            return false
        }
        return true
    }

    fun showRestOver(context: Context) = show(context, ID_TIMER, CHANNEL_TIMER, "Rest over", "Time for your next set. Log it when you've done it.")
    fun cancelRestOver(context: Context) = NotificationManagerCompat.from(context).cancel(ID_TIMER)

    /** In-app alert when the rest timer ends while the screen is visible. */
    fun restOverAlert(context: Context, sound: Boolean, vibrate: Boolean) {
        if (vibrate) {
            val v = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        }
        if (sound) runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tg.release() }, 600)
        }
    }
}

/**
 * Background alert for the rest timer. Inexact-but-idle-safe alarm (no exact-alarm permission needed);
 * Android may deliver it a few seconds late in Doze. The in-app countdown itself never drifts.
 */
object RestTimerAlarm {
    private fun pi(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, RestTimerReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(context: Context, endsAtElapsedMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAtElapsedMs.coerceAtLeast(SystemClock.elapsedRealtime()), pi(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pi(context))
    }
}

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifications.showRestOver(context)
    }
}
