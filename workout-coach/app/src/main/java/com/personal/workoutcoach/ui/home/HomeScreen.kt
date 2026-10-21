package com.personal.workoutcoach.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.personal.workoutcoach.AppContainer
import com.personal.workoutcoach.core.equipment.fmt
import com.personal.workoutcoach.core.model.Language
import com.personal.workoutcoach.core.model.MissReason
import com.personal.workoutcoach.core.model.ScheduleStatus
import com.personal.workoutcoach.core.model.SessionMode
import com.personal.workoutcoach.core.model.TrainingPhase
import com.personal.workoutcoach.core.plan.PlannedWorkout
import com.personal.workoutcoach.core.progress.ProgressCalculator
import com.personal.workoutcoach.core.progress.ScheduleEntryPoint
import com.personal.workoutcoach.data.db.ScheduleEntryEntity
import com.personal.workoutcoach.data.db.SessionWithExercises
import com.personal.workoutcoach.data.repo.languageEnum
import com.personal.workoutcoach.data.repo.toSummary
import com.personal.workoutcoach.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HomeState(
    val loading: Boolean = true,
    val language: Language = Language.ENGLISH,
    val today: LocalDate = LocalDate.now(),
    val todayEntry: ScheduleEntryEntity? = null,
    val missed: List<ScheduleEntryEntity> = emptyList(),
    val rescheduleOptions: List<LocalDate> = emptyList(),
    val plans: Map<SessionMode, PlannedWorkout> = emptyMap(),
    val active: SessionWithExercises? = null,
    val recent: List<SessionWithExercises> = emptyList(),
    val weekCompleted: Int = 0,
    val weekPlanned: Int = 0,
    val weekStreak: Int = 0,
    val phase: TrainingPhase = TrainingPhase.COMEBACK,
    val error: String? = null,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val state = MutableStateFlow(HomeState())
    val mode = MutableStateFlow(SessionMode.STANDARD)

    init {
        viewModelScope.launch {
            combine(c.training.profile, c.training.equipment, c.training.schedule, c.training.history, c.training.activeSession) { _, _, _, history, active -> history to active }
                .collectLatest { (history, active) -> load(history, active) }
        }
    }

    private suspend fun load(history: List<SessionWithExercises>, active: SessionWithExercises?) {
        try {
            val t = c.training
            val profile = t.profileOrDefault()
            val today = t.today()
            val schedule = c.db.schedule().all()
            val summaries = history.map { it.toSummary() }
            val week = ProgressCalculator.consistency(summaries, schedule.map { ScheduleEntryPoint(LocalDate.ofEpochDay(it.date), ScheduleStatus.valueOf(it.status)) }, today, 1).single()
            state.value = HomeState(
                loading = false,
                language = profile.languageEnum,
                today = today,
                todayEntry = t.todayEntry(),
                missed = t.unresolvedMissed(),
                rescheduleOptions = t.rescheduleOptions(),
                plans = SessionMode.entries.associateWith { t.plan(it) },
                active = active,
                recent = history.take(3),
                weekCompleted = week.completed,
                weekPlanned = week.planned,
                weekStreak = ProgressCalculator.weekStreak(summaries, today),
                phase = t.currentPhase(),
            )
        } catch (e: Exception) {
            state.value = state.value.copy(loading = false, error = "Couldn't load your plan: ${e.message}")
        }
    }

    fun start(onStarted: () -> Unit) = viewModelScope.launch {
        val plan = state.value.plans[mode.value] ?: return@launch
        c.training.startSession(plan)
        onStarted()
    }

    fun reschedule(entryId: Long, date: LocalDate, reason: MissReason) = viewModelScope.launch { c.training.reschedule(entryId, date, reason) }
    fun skip(entryId: Long, reason: MissReason) = viewModelScope.launch { c.training.skipMissed(entryId, reason) }
}

@Composable
fun HomeScreen(onOpenWorkout: () -> Unit, onOpenCoach: () -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val hi = s.language == Language.HINGLISH
    fun t(en: String, h: String) = if (hi) h else en

    if (s.loading) { Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.padding(32.dp)) }; return }

    ScreenColumn {
        val hour = LocalTime.now().hour
        val greet = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
        Text(greet, style = MaterialTheme.typography.headlineMedium)
        s.error?.let { InfoBanner(it, isError = true) }

        // One clear next action
        val todayStatus = s.todayEntry?.status
        val nextAction = when {
            s.active != null -> t("Next: finish the workout you started.", "Next: jo workout shuru kiya tha, use complete karo.")
            todayStatus == ScheduleStatus.DONE.name -> t("Done for today. Recovery is part of training.", "Aaj ka ho gaya. Recovery bhi training ka part hai.")
            s.missed.isNotEmpty() -> t("Next: choose a new slot for your missed session — no need to double up.", "Next: missed session ke liye naya slot chuno — double karne ki zarurat nahi.")
            todayStatus == ScheduleStatus.PLANNED.name -> t("Next: start today's session. Even Minimum Mode counts.", "Next: aaj ka session start karo. Minimum Mode bhi count hota hai.")
            else -> t("Rest day. Optional: a 10-minute walk or light stretching.", "Rest day hai. Optional: 10 minute walk ya halki stretching.")
        }
        InfoBanner(nextAction)

        s.active?.let { a ->
            SectionCard(t("Workout in progress", "Workout chal raha hai")) {
                Text(a.session.title)
                val done = a.exercises.count { it.sets.isNotEmpty() }
                Text("$done / ${a.exercises.size} exercises started · ${if (a.session.status == "PAUSED") "paused" else "active"}")
                Button(onClick = onOpenWorkout, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("continue_workout")) { Text(t("Continue workout", "Workout continue karo")) }
            }
        }

        if (s.missed.isNotEmpty() && s.active == null) MissedCard(s, vm, ::t)

        if (s.active == null) {
            val plan = s.plans[mode]
            SectionCard(t("Recommended workout", "Aaj ka recommended workout")) {
                ChoiceChips(SessionMode.entries, mode, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { vm.mode.value = it }
                if (plan == null || plan.exercises.isEmpty()) {
                    Text(t("No exercises match your current equipment. Check Profile → Equipment.", "Current equipment se koi exercise match nahi hui. Profile → Equipment check karo."))
                } else {
                    Text("${plan.title} · ~${plan.estimatedMinutes} min", style = MaterialTheme.typography.titleMedium)
                    plan.exercises.forEach { pe ->
                        Text("• ${pe.exercise.name} — ${pe.prescription.describe()}" + (pe.loadKg?.let { " @ ${fmt(it)} kg" } ?: ""))
                    }
                    Text(plan.minimumCriteria, style = MaterialTheme.typography.bodySmall)
                    plan.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    Button(onClick = { vm.start(onOpenWorkout) }, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("start_workout")) { Text(t("Start workout", "Workout start karo")) }
                }
            }
        }

        SectionCard(t("This week", "Is hafte")) {
            Text(t("${s.weekCompleted} of ${s.weekPlanned} planned sessions done", "${s.weekPlanned} mein se ${s.weekCompleted} sessions ho gaye"), style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { if (s.weekPlanned == 0) 0f else (s.weekCompleted.toFloat() / s.weekPlanned).coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (s.weekStreak > 0) Text(t("${s.weekStreak}-week streak of training at least once.", "${s.weekStreak} hafte se lagatar training."))
            Text(
                t("Phase: ", "Phase: ") + if (s.phase == TrainingPhase.COMEBACK) t("Comeback — gentle volume, technique first", "Comeback — halka volume, technique pehle") else t("Build — gradually more volume", "Build — dheere dheere zyada volume"),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard(t("Recent workouts", "Recent workouts")) {
            if (s.recent.isEmpty()) Text(t("No workouts recorded yet.", "Abhi tak koi workout record nahi hua."))
            s.recent.forEach { r ->
                val date = LocalDate.ofEpochDay(r.session.plannedDate).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
                Text("$date · ${r.session.title} · ${r.session.status.lowercase()} · ${r.session.activeDurationMs(System.currentTimeMillis()) / 60000} min")
            }
        }
        OutlinedButton(onClick = onOpenCoach, Modifier.fillMaxWidth()) { Text(t("Talk to your coach", "Coach se baat karo")) }
    }
}

@Composable
private fun MissedCard(s: HomeState, vm: HomeViewModel, t: (String, String) -> String) {
    val entry = s.missed.first()
    var reason by remember(entry.id) { mutableStateOf(MissReason.UNKNOWN) }
    val date = LocalDate.ofEpochDay(entry.date).format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH))
    SectionCard(t("Missed session", "Missed session")) {
        Text(t("$date's session didn't happen. That's a scheduling event, not a failure.", "$date ka session nahi ho paya. Yeh schedule ki baat hai, failure nahi."))
        Text(t("What got in the way? (optional)", "Kya problem aayi? (optional)"), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(MissReason.entries.filter { it != MissReason.UNKNOWN }, reason, { it.name.lowercase().replace('_', ' ') }) { reason = if (reason == it) MissReason.UNKNOWN else it }
        if (reason == MissReason.ILL_OR_INJURED) Text(t("Rest until you feel better. Restart with a Minimum session.", "Theek hone tak rest karo. Phir Minimum session se restart."), color = MaterialTheme.colorScheme.primary)
        s.rescheduleOptions.forEach { d ->
            OutlinedButton(onClick = { vm.reschedule(entry.id, d, reason) }, Modifier.fillMaxWidth()) {
                Text(t("Move to ", "Shift karo: ") + d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)))
            }
        }
        TextButton(onClick = { vm.skip(entry.id, reason) }, Modifier.fillMaxWidth()) {
            Text(t("Skip it — continue with the next planned session", "Skip karo — next planned session se continue"))
        }
    }
}
