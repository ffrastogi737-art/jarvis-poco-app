package com.personal.workoutcoach

import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.personal.workoutcoach.core.timer.RestTimer
import com.personal.workoutcoach.data.db.AppDatabase
import com.personal.workoutcoach.data.repo.CoachRepository
import com.personal.workoutcoach.data.repo.DataRepository
import com.personal.workoutcoach.data.repo.TrainingRepository
import com.personal.workoutcoach.data.settings.SettingsStore
import com.personal.workoutcoach.notifications.Notifications
import com.personal.workoutcoach.notifications.ReminderScheduler
import com.personal.workoutcoach.notifications.RestTimerAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Manual dependency injection: the app is small enough that a framework would add more than it saves. */
class AppContainer(context: Context, val db: AppDatabase = AppDatabase.build(context)) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(appContext)
    val training = TrainingRepository(db)
    val coach = CoachRepository(db, training, settings)
    val data = DataRepository(db, settings)
    val reminderScheduler = ReminderScheduler(appContext)
    val timer = RestTimerController(settings, scope)
}

/** Holds the rest timer in memory (for synchronous access) and persists every change. */
class RestTimerController(private val settings: SettingsStore, private val scope: CoroutineScope, val clock: () -> Long = SystemClock::elapsedRealtime) {
    private val _state = MutableStateFlow<RestTimer?>(null)
    val state: StateFlow<RestTimer?> = _state

    init { scope.launch { _state.value = settings.restTimer.first()?.tick(clock()) } }

    private fun set(t: RestTimer?) { _state.value = t; scope.launch { settings.saveTimer(t) } }

    fun start(seconds: Int) = set(RestTimer(seconds * 1000L).start(clock()))
    fun pause() = _state.value?.let { set(it.pause(clock())) }
    fun resume() = _state.value?.let { set(it.resume(clock())) }
    fun reset() = _state.value?.let { set(it.reset()) }
    fun skip() = _state.value?.let { set(it.skip()) }
    fun adjust(deltaSeconds: Int) = _state.value?.let { set(it.adjust(deltaSeconds * 1000L, clock())) }
    fun clear() = set(null)
    fun tick() = _state.value?.let { val t = it.tick(clock()); if (t != it) set(t) }

    /** Called when the app goes to the background / returns. */
    fun onBackground(context: Context) {
        val t = _state.value ?: return
        if (t.status == RestTimer.Status.RUNNING) RestTimerAlarm.schedule(context, t.endsAtMs)
    }

    fun onForeground(context: Context) {
        RestTimerAlarm.cancel(context)
        Notifications.cancelRestOver(context)
        tick()
    }
}

class WorkoutCoachApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}
