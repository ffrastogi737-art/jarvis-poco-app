package com.personal.workoutcoach.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.personal.workoutcoach.AppContainer
import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.equipment.Compatibility
import com.personal.workoutcoach.core.equipment.fmt
import com.personal.workoutcoach.core.model.SessionMode
import com.personal.workoutcoach.core.timer.RestTimer
import com.personal.workoutcoach.data.db.ExerciseWithSets
import com.personal.workoutcoach.data.db.SessionWithExercises
import com.personal.workoutcoach.data.db.SetPerformanceEntity
import com.personal.workoutcoach.notifications.Notifications
import com.personal.workoutcoach.ui.coach.FeedbackDialog
import com.personal.workoutcoach.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class WorkoutViewModel(private val c: AppContainer) : ViewModel() {
    val active = c.training.activeSession
    val timer = c.timer.state
    val settings = c.settings.settings
    val clock = c.timer.clock
    val loads = MutableStateFlow<List<Double>>(emptyList())
    val previous = MutableStateFlow<ExerciseWithSets?>(null)
    val substitutes = MutableStateFlow<List<String>>(emptyList())
    val finishedSessionId = MutableStateFlow<Long?>(null)

    fun loadExerciseInfo(ex: ExerciseWithSets, sessionId: Long) = viewModelScope.launch {
        val def = ExerciseCatalog.get(ex.exercise.exerciseId)
        loads.value = def?.let { (c.training.inventory().check(it) as? Compatibility.Ok)?.loadOptionsKg }.orEmpty()
            .ifEmpty { listOfNotNull(ex.exercise.plannedLoadKg) }
        previous.value = c.training.previousPerformance(ex.exercise.exerciseId, sessionId)
    }

    fun loadSubstitutes(s: SessionWithExercises, ex: ExerciseWithSets) = viewModelScope.launch {
        substitutes.value = c.training.substitutes(ex.exercise.exerciseId, s.exercises.map { it.exercise.exerciseId }.toSet())
    }

    fun start(mode: SessionMode) = viewModelScope.launch { c.training.startSession(c.training.plan(mode)) }

    fun logSet(ex: ExerciseWithSets, weight: Double?, reps: Int?, seconds: Int?) = viewModelScope.launch {
        c.training.logSet(ex.exercise.id, weight, reps, seconds)
        c.timer.start(ex.exercise.restSeconds)
    }

    fun deleteSet(s: SetPerformanceEntity) = viewModelScope.launch { c.training.deleteSet(s) }
    fun setIndex(sessionId: Long, i: Int) = viewModelScope.launch { c.training.setCurrentIndex(sessionId, i) }
    fun skipExercise(ex: ExerciseWithSets) = viewModelScope.launch { c.training.skipExercise(ex.exercise.id) }
    fun substitute(ex: ExerciseWithSets, newId: String) = viewModelScope.launch { c.training.substitute(ex.exercise.id, newId) }

    fun pause(id: Long) = viewModelScope.launch { c.training.pause(id); c.timer.pause() }
    fun resume(id: Long) = viewModelScope.launch { c.training.resume(id); c.timer.resume() }

    fun finish(id: Long) = viewModelScope.launch {
        c.training.finish(id)
        c.timer.clear()
        finishedSessionId.value = id
    }

    fun saveFeedback(sessionId: Long, e: Int?, m: Int?, s: Int?, notes: String) = viewModelScope.launch { c.training.addFeedback(e, m, s, notes, sessionId) }

    fun timerPause() { c.timer.pause() }
    fun timerResume() { c.timer.resume() }
    fun timerReset() { c.timer.reset() }
    fun timerSkip() { c.timer.skip() }
    fun timerAdjust(d: Int) { c.timer.adjust(d) }
    fun timerStartIdle() { c.timer.state.value?.takeIf { it.status == RestTimer.Status.IDLE }?.let { c.timer.start((it.durationMs / 1000).toInt()) } }
    fun tick() { c.timer.tick() }
}

@Composable
fun WorkoutScreen(onFinished: () -> Unit) {
    val vm = appViewModel { WorkoutViewModel(it) }
    val active by vm.active.collectAsStateWithLifecycle(null)
    val finished by vm.finishedSessionId.collectAsStateWithLifecycle()

    finished?.let { id ->
        FeedbackDialog(onDismiss = { vm.finishedSessionId.value = null; onFinished() }) { e, m, s, n ->
            vm.saveFeedback(id, e, m, s, n); vm.finishedSessionId.value = null; onFinished()
        }
    }

    val a = active
    if (a == null) {
        ScreenColumn {
            Text("No active workout", style = MaterialTheme.typography.headlineMedium)
            Text("Pick a session length. Minimum counts as a real session.")
            SessionMode.entries.forEach { m ->
                Button(onClick = { vm.start(m) }, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("start_${m.name.lowercase()}")) {
                    Text(when (m) { SessionMode.MINIMUM -> "Minimum (~10–15 min)"; SessionMode.STANDARD -> "Standard (~20–35 min)"; SessionMode.EXTENDED -> "Extended (~35–50 min)" })
                }
            }
        }
        return
    }
    ActiveWorkout(a, vm, onFinished)
}

@Composable
private fun ActiveWorkout(a: SessionWithExercises, vm: WorkoutViewModel, onLeave: () -> Unit) {
    val s = a.session
    val exercises = a.orderedExercises
    val index = s.currentIndex.coerceIn(0, (exercises.size - 1).coerceAtLeast(0))
    val ex = exercises.getOrNull(index) ?: return
    val def = remember(ex.exercise.exerciseId) { ExerciseCatalog.get(ex.exercise.exerciseId) }
    val paused = s.status == "PAUSED"
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var showSwap by remember { mutableStateOf(false) }
    var showHow by remember { mutableStateOf(false) }

    LaunchedEffect(ex.exercise.id, ex.exercise.exerciseId) { vm.loadExerciseInfo(ex, s.id) }
    val loads by vm.loads.collectAsStateWithLifecycle()
    val previous by vm.previous.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    BackHandler { confirmLeave = true }

    val timed = ex.exercise.seconds != null
    var weight by remember(ex.exercise.id) { mutableStateOf(ex.sets.lastOrNull()?.weightKg ?: ex.exercise.plannedLoadKg) }
    var reps by remember(ex.exercise.id) { mutableIntStateOf(ex.sets.lastOrNull()?.reps ?: ex.exercise.repsMax) }
    var secs by remember(ex.exercise.id) { mutableIntStateOf(ex.sets.lastOrNull()?.seconds ?: (ex.exercise.seconds ?: 30)) }

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleLarge)
                val ms = s.activeDurationMs(now)
                Text("%d:%02d elapsed".format(ms / 60000, (ms / 1000) % 60), style = MaterialTheme.typography.bodySmall)
            }
            if (paused) Button(onClick = { vm.resume(s.id) }, Modifier.testTag("resume")) { Text("Resume") }
            else OutlinedButton(onClick = { vm.pause(s.id) }, Modifier.testTag("pause")) { Text("Pause") }
        }
        if (paused) InfoBanner("Workout paused. Everything is saved — resume whenever you're ready.")

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.setIndex(s.id, index - 1) }, enabled = index > 0) { Text("‹ Prev") }
            Text("Exercise ${index + 1} of ${exercises.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { vm.setIndex(s.id, index + 1) }, enabled = index < exercises.size - 1, modifier = Modifier.testTag("next_exercise")) { Text("Next ›") }
        }

        SectionCard {
            Text(ex.exercise.exerciseName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("exercise_name"))
            def?.let { Text(it.muscles.joinToString(" · "), style = MaterialTheme.typography.bodySmall) }
            val target = if (timed) "${ex.exercise.plannedSets} × ${ex.exercise.seconds}s" else "${ex.exercise.plannedSets} × ${ex.exercise.repsMin}–${ex.exercise.repsMax} reps"
            Text("Target: $target" + (ex.exercise.plannedLoadKg?.let { " @ ${fmt(it)} kg" } ?: "") + if (def?.perSide == true) " (each side)" else "", style = MaterialTheme.typography.titleMedium)
            Text("Rest ${ex.exercise.restSeconds}s · keep 2–3 reps in reserve", style = MaterialTheme.typography.bodySmall)
            ex.exercise.progressionNote?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            ex.exercise.substitutedFrom?.let { Text("Swapped from ${ExerciseCatalog.get(it)?.name ?: it}", style = MaterialTheme.typography.bodySmall) }
            if (ex.exercise.status == "SKIPPED") Text("Skipped", color = MaterialTheme.colorScheme.error)
            previous?.let { p ->
                Text("Last time: " + p.sets.sortedBy { it.setNumber }.joinToString(", ") { (it.reps ?: it.seconds ?: 0).toString() + if (it.seconds != null) "s" else "" } +
                    (p.sets.firstOrNull()?.weightKg?.let { " @ ${fmt(it)} kg" } ?: ""), style = MaterialTheme.typography.bodyMedium)
            } ?: Text("No previous record for this exercise.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showHow = !showHow }) { Text(if (showHow) "Hide instructions" else "How to do it") }
            if (showHow && def != null) {
                def.instructions.forEachIndexed { i, t -> Text("${i + 1}. $t") }
                Text("Cues: " + def.cues.joinToString("; "), style = MaterialTheme.typography.bodySmall)
                Text("Avoid: " + def.commonErrors.joinToString("; "), style = MaterialTheme.typography.bodySmall)
                def.safetyNotes.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }

        SectionCard("Sets") {
            ex.sets.sortedBy { it.setNumber }.forEach { st ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Set ${st.setNumber}: " + (if (st.seconds != null) "${st.seconds}s" else "${st.reps} reps") + (st.weightKg?.let { " @ ${fmt(it)} kg" } ?: ""), Modifier.weight(1f))
                    IconButton(onClick = { vm.deleteSet(st) }, Modifier.semantics { contentDescription = "Delete set ${st.setNumber}" }) { Icon(Icons.Default.Delete, null) }
                }
            }
            if (ex.exercise.status != "SKIPPED") {
                if (loads.isNotEmpty()) ChoiceChips(loads, weight, { "${fmt(it)} kg" }) { weight = it }
                else Text("Bodyweight", style = MaterialTheme.typography.bodySmall)
                if (timed) Stepper("Seconds held", secs, { secs = it }, 0..600, 5) else Stepper("Reps done", reps, { reps = it }, 0..100)
                Button(
                    onClick = { vm.logSet(ex, weight.takeIf { loads.isNotEmpty() }, if (timed) null else reps, if (timed) secs else null) },
                    enabled = !paused,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("log_set"),
                ) { Text("Log set ${ex.sets.size + 1}" + if (ex.sets.size >= ex.exercise.plannedSets) " (extra)" else "") }
            }
        }

        RestTimerCard(vm)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.skipExercise(ex); if (index < exercises.size - 1) vm.setIndex(s.id, index + 1) }, enabled = ex.exercise.status != "SKIPPED", modifier = Modifier.weight(1f)) { Text("Skip") }
            OutlinedButton(onClick = { vm.loadSubstitutes(a, ex); showSwap = true }, enabled = ex.sets.isEmpty(), modifier = Modifier.weight(1f).testTag("swap")) { Text("Swap") }
        }
        Button(onClick = { confirmFinish = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("finish_workout"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("Finish workout") }
    }

    if (showSwap) {
        val subs by vm.substitutes.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showSwap = false },
            title = { Text("Swap ${ex.exercise.exerciseName}") },
            text = {
                Column {
                    if (subs.isEmpty()) Text("No alternatives available with your current equipment.")
                    subs.forEach { id ->
                        TextButton(onClick = { vm.substitute(ex, id); showSwap = false }) { Text(ExerciseCatalog.get(id)?.name ?: id) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSwap = false }) { Text("Close") } },
        )
    }
    if (confirmFinish) {
        val started = exercises.count { it.sets.isNotEmpty() }
        val required = exercises.count { it.exercise.status != "SKIPPED" }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish workout?") },
            text = {
                Text(
                    when {
                        started == 0 -> "No sets logged yet. Finishing now records nothing as completed."
                        started >= required -> "Every exercise has at least one set — this counts as a completed session."
                        else -> "$started of $required exercises have sets. It will be saved as a partial session — still counts."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { confirmFinish = false; vm.finish(s.id) }, Modifier.testTag("confirm_finish")) { Text("Finish") } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going") } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the workout screen?") },
            text = { Text("Your session stays saved and active. Continue it any time from Home.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onLeave() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun RestTimerCard(vm: WorkoutViewModel) {
    val timer by vm.timer.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle(null)
    val context = LocalContext.current
    val t = timer ?: return
    var nowMs by remember { mutableLongStateOf(vm.clock()) }
    LaunchedEffect(t.status, t.endsAtMs) {
        while (t.status == RestTimer.Status.RUNNING) {
            nowMs = vm.clock()
            if (t.isFinished(nowMs)) {
                vm.tick()
                settings?.let { Notifications.restOverAlert(context, it.timerSound, it.timerVibrate) }
                break
            }
            delay(200)
        }
        nowMs = vm.clock()
    }
    val remaining = t.remaining(nowMs)
    SectionCard("Rest timer") {
        Text(
            if (t.status == RestTimer.Status.FINISHED) "Rest over" else "%d:%02d".format(remaining / 60000, (remaining / 1000) % 60),
            style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("timer_text"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (t.status) {
                RestTimer.Status.RUNNING -> OutlinedButton(onClick = vm::timerPause, Modifier.testTag("timer_pause")) { Text("Pause") }
                RestTimer.Status.PAUSED -> OutlinedButton(onClick = vm::timerResume, Modifier.testTag("timer_resume")) { Text("Resume") }
                RestTimer.Status.IDLE -> OutlinedButton(onClick = { vm.timerStartIdle() }) { Text("Start") }
                RestTimer.Status.FINISHED -> Unit
            }
            TextButton(onClick = { vm.timerAdjust(-15) }) { Text("−15s") }
            TextButton(onClick = { vm.timerAdjust(15) }, Modifier.testTag("timer_plus")) { Text("+15s") }
            TextButton(onClick = vm::timerReset) { Text("Reset") }
            TextButton(onClick = vm::timerSkip, Modifier.testTag("timer_skip")) { Text("Skip") }
        }
        Text("The timer never logs a set for you — log each set yourself.", style = MaterialTheme.typography.bodySmall)
    }
}
