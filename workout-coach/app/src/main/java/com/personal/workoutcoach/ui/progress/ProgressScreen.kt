package com.personal.workoutcoach.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.personal.workoutcoach.AppContainer
import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.equipment.fmt
import com.personal.workoutcoach.core.model.Language
import com.personal.workoutcoach.core.model.ScheduleStatus
import com.personal.workoutcoach.core.progress.*
import com.personal.workoutcoach.data.db.ProgressMeasurementEntity
import com.personal.workoutcoach.data.db.SessionWithExercises
import com.personal.workoutcoach.data.repo.languageEnum
import com.personal.workoutcoach.data.repo.toSummary
import com.personal.workoutcoach.ui.components.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ProgressState(
    val report: WeeklyReport? = null,
    val language: Language = Language.ENGLISH,
    val consistency: List<WeekConsistency> = emptyList(),
    val history: List<SessionWithExercises> = emptyList(),
    val summaries: List<SessionSummary> = emptyList(),
    val missedTotal: Int = 0,
    val rescheduledTotal: Int = 0,
    val measurements: List<ProgressMeasurementEntity> = emptyList(),
)

class ProgressViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<ProgressState> = combine(
        c.training.history, c.training.schedule, c.training.feedback, c.training.measurements, c.training.profile,
    ) { history, schedule, feedback, measurements, profile ->
        val today = c.training.today()
        val lang = profile?.languageEnum ?: Language.ENGLISH
        val summaries = history.map { it.toSummary() }
        val sched = schedule.map { ScheduleEntryPoint(LocalDate.ofEpochDay(it.date), ScheduleStatus.valueOf(it.status)) }
        val fb = feedback.map { FeedbackPoint(Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate(), it.energy, it.soreness, it.motivation) }
        ProgressState(
            report = ProgressCalculator.weeklyReport(ProgressCalculator.weekStart(today), summaries, sched, fb, lang),
            language = lang,
            consistency = ProgressCalculator.consistency(summaries, sched, today, 8),
            history = history,
            summaries = summaries,
            missedTotal = schedule.count { it.status == ScheduleStatus.MISSED.name },
            rescheduledTotal = schedule.count { it.status == ScheduleStatus.RESCHEDULED.name },
            measurements = measurements,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressState())

    fun deleteSession(id: Long) = viewModelScope.launch { c.training.deleteSession(id) }
    fun addMeasurement(type: String, value: Double, unit: String) = viewModelScope.launch { c.training.addMeasurement(type, value, unit) }
    fun deleteMeasurement(id: Long) = viewModelScope.launch { c.training.deleteMeasurement(id) }
}

@Composable
fun ProgressScreen() {
    val vm = appViewModel { ProgressViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<SessionWithExercises?>(null) }
    var addMeasurement by remember { mutableStateOf(false) }
    val fmtDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    ScreenColumn {
        Text("Progress", style = MaterialTheme.typography.headlineMedium)
        s.report?.let { r ->
            SectionCard("This week", Modifier.testTag("weekly_report")) {
                if (r.insufficientData) Text(r.recommendation)
                else {
                    Text("${r.completedSessions} sessions · ${r.trainingDays} training days · ${r.totalMinutes} min · ${r.missed} missed")
                    if (r.exercisesCompleted.isNotEmpty()) Text("Exercises: " + r.exercisesCompleted.joinToString(), style = MaterialTheme.typography.bodySmall)
                    r.avgEnergy?.let { Text("Avg energy %.1f/5".format(it)) }
                    r.avgSoreness?.let { Text("Avg soreness %.1f/5".format(it)) }
                    if (r.changes.isEmpty()) Text("No like-for-like comparisons yet (same exercise and weight needed).", style = MaterialTheme.typography.bodySmall)
                    r.changes.forEach { ch ->
                        Text("${ch.name}${ch.loadKg?.let { " @ ${fmt(it)} kg" } ?: ""}: best set ${ch.previousBest} → ${ch.currentBest} ${ch.unit}")
                    }
                    Text(r.recommendation, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        SectionCard("Consistency (last 8 weeks)") {
            BarChart(
                s.consistency.map { it.completed to it.planned },
                s.consistency.map { it.weekStart.format(DateTimeFormatter.ofPattern("d/M")) },
                description = "Completed versus planned sessions per week: " + s.consistency.joinToString { "${it.completed} of ${it.planned}" },
            )
            Text("Dark bar = completed, light bar = planned.", style = MaterialTheme.typography.bodySmall)
            Text("Completed sessions: ${s.summaries.count { it.counts }} · Missed: ${s.missedTotal} · Rescheduled: ${s.rescheduledTotal}")
        }

        ExerciseTrend(s.summaries)

        SectionCard("Body measurements") {
            val weights = s.measurements.filter { it.type == "BODY_WEIGHT" }
            if (weights.size >= 2) LineChart(weights.map { it.value }, description = "Body weight trend")
            if (s.measurements.isEmpty()) Text("Nothing recorded. Measurements are only shown if you enter them.")
            s.measurements.takeLast(6).asReversed().forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${Instant.ofEpochMilli(m.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().format(fmtDate)} · ${m.type.lowercase().replace('_', ' ')}: ${fmt(m.value)} ${m.unit}", Modifier.weight(1f))
                    IconButton(onClick = { vm.deleteMeasurement(m.id) }, Modifier.semantics { contentDescription = "Delete measurement" }) { Icon(Icons.Default.Delete, null) }
                }
            }
            OutlinedButton(onClick = { addMeasurement = true }) { Text("Add measurement") }
        }

        SectionCard("Workout history") {
            if (s.history.isEmpty()) Text("No sessions recorded yet.")
            s.history.forEach { h ->
                Text(
                    "${LocalDate.ofEpochDay(h.session.plannedDate).format(fmtDate)} · ${h.session.title} · ${h.session.status.lowercase()} · ${h.session.activeDurationMs(System.currentTimeMillis()) / 60000} min",
                    Modifier.fillMaxWidth().clickable { detail = h }.padding(vertical = 8.dp),
                )
            }
        }
    }

    detail?.let { d ->
        var confirmDelete by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(d.session.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    d.orderedExercises.forEach { e ->
                        Text(e.exercise.exerciseName + if (e.exercise.status == "SKIPPED") " (skipped)" else "", style = MaterialTheme.typography.titleSmall)
                        Text(if (e.sets.isEmpty()) "No sets" else e.sets.sortedBy { it.setNumber }.joinToString(", ") {
                            (if (it.seconds != null) "${it.seconds}s" else "${it.reps}") + (it.weightKg?.let { kg -> "@${fmt(kg)}" } ?: "")
                        })
                    }
                    if (confirmDelete) Text("Delete this session permanently? Use this only to fix a wrong record.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = { if (confirmDelete) { vm.deleteSession(d.session.id); detail = null } else confirmDelete = true }) {
                    Text(if (confirmDelete) "Confirm delete" else "Delete record", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    if (addMeasurement) MeasurementDialog({ addMeasurement = false }) { type, v, unit -> vm.addMeasurement(type, v, unit); addMeasurement = false }
}

@Composable
private fun ExerciseTrend(summaries: List<SessionSummary>) {
    val ids = remember(summaries) { summaries.filter { it.counts }.flatMap { s -> s.exercises.filter { it.sets.isNotEmpty() }.map { it.exerciseId } }.distinct() }
    var selected by remember(ids) { mutableStateOf(ids.firstOrNull()) }
    SectionCard("Exercise trends") {
        if (ids.isEmpty()) { Text("Complete a session to see trends."); return@SectionCard }
        ChoiceChips(ids, selected, { ExerciseCatalog.get(it)?.name ?: it }) { selected = it }
        val trend = selected?.let { ProgressCalculator.exerciseTrend(summaries, it) }.orEmpty()
        trend.forEach { (load, pts) ->
            Text((load?.let { "@ ${fmt(it)} kg" } ?: "Bodyweight") + " — total reps/seconds per session", style = MaterialTheme.typography.labelLarge)
            LineChart(pts.map { it.second.toDouble() }, description = "Trend: " + pts.joinToString { "${it.first}: ${it.second}" })
            Text(pts.takeLast(5).joinToString("  ") { "${it.first.format(DateTimeFormatter.ofPattern("d/M"))}: ${it.second}" }, style = MaterialTheme.typography.bodySmall)
        }
        Text("Only sessions with the same weight are compared.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MeasurementDialog(onDismiss: () -> Unit, onSave: (String, Double, String) -> Unit) {
    val types = listOf("BODY_WEIGHT" to "kg", "WAIST" to "cm", "CHEST" to "cm", "ARM" to "cm")
    var type by remember { mutableStateOf(types[0]) }
    var text by remember { mutableStateOf("") }
    val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 500 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add measurement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChips(types, type, { it.first.lowercase().replace('_', ' ') }) { type = it }
                OutlinedTextField(text, { text = it.take(8) }, label = { Text("Value (${type.second})") }, isError = text.isNotEmpty() && value == null)
            }
        },
        confirmButton = { TextButton(onClick = { value?.let { onSave(type.first, it, type.second) } }, enabled = value != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
