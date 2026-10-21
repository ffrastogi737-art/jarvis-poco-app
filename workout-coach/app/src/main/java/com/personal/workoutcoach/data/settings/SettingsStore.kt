package com.personal.workoutcoach.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.workoutcoach.core.coach.RemoteProvider
import com.personal.workoutcoach.core.timer.RestTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

data class AppSettings(
    val aiEnabled: Boolean = false,
    val backendUrl: String = "",
    /** Token for the user's own backend — not a provider API key. */
    val appToken: String = "",
    val provider: RemoteProvider = RemoteProvider.GEMINI,
    val shareHistoryWithAi: Boolean = true,
    val quietStartMinutes: Int = 22 * 60,
    val quietEndMinutes: Int = 7 * 60,
    val maxNotificationsPerDay: Int = 2,
    val remindersPaused: Boolean = false,
    val timerSound: Boolean = true,
    val timerVibrate: Boolean = true,
    val aiRequestsDay: Long = 0,
    val aiRequestsToday: Int = 0,
    val aiTokensToday: Int = 0,
)

/** Small, non-relational preferences. Never holds provider API keys. */
class SettingsStore(private val context: Context) {
    private object K {
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val backendUrl = stringPreferencesKey("backend_url")
        val appToken = stringPreferencesKey("app_token")
        val provider = stringPreferencesKey("provider")
        val shareHistory = booleanPreferencesKey("share_history")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val maxPerDay = intPreferencesKey("max_per_day")
        val paused = booleanPreferencesKey("reminders_paused")
        val timerSound = booleanPreferencesKey("timer_sound")
        val timerVibrate = booleanPreferencesKey("timer_vibrate")
        val aiDay = longPreferencesKey("ai_day")
        val aiReq = intPreferencesKey("ai_requests")
        val aiTok = intPreferencesKey("ai_tokens")
        // reminder bookkeeping
        val notifDay = longPreferencesKey("notif_day")
        val notifCount = intPreferencesKey("notif_count")
        val lastSentMs = longPreferencesKey("last_sent_ms")
        val lastSentDay = longPreferencesKey("last_sent_day")
        val lastOpenMs = longPreferencesKey("last_open_ms")
        val ignoredStreak = intPreferencesKey("ignored_streak")
        // rest timer (monotonic clock values)
        val tStatus = stringPreferencesKey("timer_status")
        val tDuration = longPreferencesKey("timer_duration")
        val tEnds = longPreferencesKey("timer_ends")
        val tPaused = longPreferencesKey("timer_paused_remaining")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            aiEnabled = p[K.aiEnabled] ?: false,
            backendUrl = p[K.backendUrl] ?: "",
            appToken = p[K.appToken] ?: "",
            provider = RemoteProvider.fromWire(p[K.provider]) ?: RemoteProvider.GEMINI,
            shareHistoryWithAi = p[K.shareHistory] ?: true,
            quietStartMinutes = p[K.quietStart] ?: (22 * 60),
            quietEndMinutes = p[K.quietEnd] ?: (7 * 60),
            maxNotificationsPerDay = p[K.maxPerDay] ?: 2,
            remindersPaused = p[K.paused] ?: false,
            timerSound = p[K.timerSound] ?: true,
            timerVibrate = p[K.timerVibrate] ?: true,
            aiRequestsDay = p[K.aiDay] ?: 0,
            aiRequestsToday = p[K.aiReq] ?: 0,
            aiTokensToday = p[K.aiTok] ?: 0,
        )
    }

    suspend fun current() = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val n = transform(current())
        context.dataStore.edit { p ->
            p[K.aiEnabled] = n.aiEnabled
            p[K.backendUrl] = n.backendUrl.trim()
            p[K.appToken] = n.appToken.trim()
            p[K.provider] = n.provider.wireName
            p[K.shareHistory] = n.shareHistoryWithAi
            p[K.quietStart] = n.quietStartMinutes
            p[K.quietEnd] = n.quietEndMinutes
            p[K.maxPerDay] = n.maxNotificationsPerDay.coerceIn(1, 5)
            p[K.paused] = n.remindersPaused
            p[K.timerSound] = n.timerSound
            p[K.timerVibrate] = n.timerVibrate
        }
    }

    suspend fun recordAiUsage(today: Long, tokens: Int) = context.dataStore.edit { p ->
        if (p[K.aiDay] != today) { p[K.aiDay] = today; p[K.aiReq] = 0; p[K.aiTok] = 0 }
        p[K.aiReq] = (p[K.aiReq] ?: 0) + 1
        p[K.aiTok] = (p[K.aiTok] ?: 0) + tokens
    }

    // ---- Reminder bookkeeping

    data class ReminderState(val sentToday: Int, val ignoredStreak: Int, val lastSentDay: Long?)

    suspend fun reminderState(today: Long): ReminderState {
        val p = context.dataStore.data.first()
        return ReminderState(if (p[K.notifDay] == today) p[K.notifCount] ?: 0 else 0, p[K.ignoredStreak] ?: 0, p[K.lastSentDay])
    }

    /** Called before deciding: a previous reminder with no app open since counts as ignored. */
    suspend fun updateIgnoredStreak() = context.dataStore.edit { p ->
        val sent = p[K.lastSentMs] ?: return@edit
        val opened = p[K.lastOpenMs] ?: 0
        p[K.ignoredStreak] = if (opened < sent) (p[K.ignoredStreak] ?: 0) + 1 else 0
        p.remove(K.lastSentMs) // evaluate each sent reminder once
    }

    suspend fun recordReminderSent(today: Long, nowMs: Long) = context.dataStore.edit { p ->
        if (p[K.notifDay] != today) { p[K.notifDay] = today; p[K.notifCount] = 0 }
        p[K.notifCount] = (p[K.notifCount] ?: 0) + 1
        p[K.lastSentMs] = nowMs
        p[K.lastSentDay] = today
    }

    suspend fun recordAppOpen(nowMs: Long) = context.dataStore.edit { p ->
        p[K.lastOpenMs] = nowMs
        p[K.ignoredStreak] = 0
    }

    // ---- Rest timer persistence (survives process death)

    val restTimer: Flow<RestTimer?> = context.dataStore.data.map { p ->
        val status = p[K.tStatus]?.let { runCatching { RestTimer.Status.valueOf(it) }.getOrNull() } ?: return@map null
        RestTimer(p[K.tDuration] ?: 90_000, status, p[K.tEnds] ?: 0, p[K.tPaused] ?: 0)
    }

    suspend fun saveTimer(t: RestTimer?) = context.dataStore.edit { p ->
        if (t == null) { p.remove(K.tStatus); return@edit }
        p[K.tStatus] = t.status.name
        p[K.tDuration] = t.durationMs
        p[K.tEnds] = t.endsAtMs
        p[K.tPaused] = t.pausedRemainingMs
    }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
