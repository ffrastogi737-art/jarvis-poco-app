package com.personal.workoutcoach.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.personal.workoutcoach.AppContainer
import com.personal.workoutcoach.BuildConfig
import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.coach.RemoteProvider
import com.personal.workoutcoach.core.model.CoachStyle
import com.personal.workoutcoach.core.model.Language
import com.personal.workoutcoach.core.plan.ScheduleEngine
import com.personal.workoutcoach.core.reminders.ReminderType
import com.personal.workoutcoach.data.db.ReminderConfigEntity
import com.personal.workoutcoach.data.db.UserProfileEntity
import com.personal.workoutcoach.data.repo.days
import com.personal.workoutcoach.data.repo.excluded
import com.personal.workoutcoach.data.repo.toStored
import com.personal.workoutcoach.data.settings.AppSettings
import com.personal.workoutcoach.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val profile = c.training.profile
    val settings = c.settings.settings
    val reminders = c.db.reminders().observe()
    val message = MutableStateFlow<String?>(null)

    fun saveProfile(p: UserProfileEntity) = viewModelScope.launch {
        c.training.updateProfile(p)
        c.db.reminders().get(ReminderType.TRAINING_DAY.name)?.let { if (it.minuteOfDay != p.preferredTimeMinutes) saveReminder(it.copy(minuteOfDay = p.preferredTimeMinutes)) }
        message.value = "Profile saved"
    }

    fun saveSettings(s: AppSettings) = viewModelScope.launch {
        c.settings.update { s }
        if (s.remindersPaused) c.reminderScheduler.cancelAll() else c.reminderScheduler.scheduleAll(c.db.reminders().all())
    }

    fun saveReminder(r: ReminderConfigEntity) = viewModelScope.launch {
        c.db.reminders().upsert(r)
        if (!c.settings.current().remindersPaused) c.reminderScheduler.schedule(r)
    }

    fun export(uri: android.net.Uri, context: android.content.Context) = viewModelScope.launch {
        try {
            val json = c.data.exportJson()
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            message.value = "Data exported"
        } catch (e: Exception) {
            message.value = "Export failed: ${e.message}"
        }
    }

    fun deleteAll() = c.scope.launch {
        c.reminderScheduler.cancelAll()
        c.timer.clear()
        c.data.deleteEverything()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onEquipment: () -> Unit, onMemory: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val profile by vm.profile.collectAsStateWithLifecycle(null)
    val settings by vm.settings.collectAsStateWithLifecycle(null)
    val reminders by vm.reminders.collectAsStateWithLifecycle(emptyList())
    val message by vm.message.collectAsStateWithLifecycle()
    val p = profile ?: return
    val st = settings ?: return
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.export(it, context) } }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var confirmDelete by remember { mutableStateOf(false) }
    var timeEdit by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var excludedDialog by remember { mutableStateOf(false) }

    // Local editable copy of the profile
    var draft by remember(p) { mutableStateOf(p) }

    ScreenColumn {
        Text("Profile & Settings", style = MaterialTheme.typography.headlineMedium)
        message?.let { InfoBanner(it) { TextButton(onClick = { vm.message.value = null }) { Text("OK") } } }

        SectionCard("Coach") {
            Text("Language", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(Language.entries, Language.valueOf(draft.language), { if (it == Language.HINGLISH) "Hinglish" else "English" }) { draft = draft.copy(language = it.name) }
            Text("Style", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(CoachStyle.entries, CoachStyle.valueOf(draft.coachStyle), { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { draft = draft.copy(coachStyle = it.name) }
        }
        SectionCard("Training") {
            Text("Goal: ${draft.goal}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { d ->
                    val days = draft.days
                    FilterChip(d in days, { draft = draft.copy(trainingDays = (if (d in days) days - d else days + d).toStored()) }, label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) })
                }
            }
            if (ScheduleEngine.hasConsecutiveDays(draft.days)) Text("Consecutive full-body days leave less time to recover.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Preferred time: ${formatMinutes(draft.preferredTimeMinutes)}", Modifier.weight(1f))
                TextButton(onClick = { timeEdit = "profile" to draft.preferredTimeMinutes }) { Text("Change") }
            }
            Stepper("Preferred length", draft.preferredMinutes, { draft = draft.copy(preferredMinutes = it) }, 10..60, 5, " min")
            OutlinedTextField(draft.restrictions, { draft = draft.copy(restrictions = it.take(300)) }, Modifier.fillMaxWidth(), label = { Text("Restrictions / injuries (shared with coach)") })
            Text("Excluded exercises: " + (draft.excluded.mapNotNull { ExerciseCatalog.get(it)?.name }.joinToString().ifEmpty { "none" }), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { excludedDialog = true }) { Text("Choose exercises to avoid") }
            Button(onClick = { vm.saveProfile(draft) }, enabled = draft != p && draft.days.isNotEmpty(), modifier = Modifier.testTag("save_profile")) { Text("Save profile") }
        }

        SectionCard("Equipment & memory") {
            OutlinedButton(onClick = onEquipment, Modifier.fillMaxWidth()) { Text("Manage equipment") }
            OutlinedButton(onClick = onMemory, Modifier.fillMaxWidth()) { Text("What the coach remembers") }
        }

        SectionCard("Reminders") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pause all coaching reminders", Modifier.weight(1f))
                Switch(st.remindersPaused, { vm.saveSettings(st.copy(remindersPaused = it)) }, Modifier.testTag("pause_reminders"))
            }
            ReminderType.entries.forEach { type ->
                val r = reminders.firstOrNull { it.type == type.name } ?: ReminderConfigEntity(type.name, 18 * 60, false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(when (type) { ReminderType.TRAINING_DAY -> "Training-day reminder"; ReminderType.DAILY_CHECKIN -> "Daily check-in"; ReminderType.MISSED_FOLLOWUP -> "Missed-session follow-up" })
                        TextButton(onClick = { timeEdit = type.name to r.minuteOfDay }) { Text(formatMinutes(r.minuteOfDay)) }
                    }
                    Switch(r.enabled, { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        vm.saveReminder(r.copy(enabled = on))
                    }, Modifier.testTag("reminder_${type.name}"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quiet hours: ${formatMinutes(st.quietStartMinutes)} – ${formatMinutes(st.quietEndMinutes)}", Modifier.weight(1f))
            }
            Row {
                TextButton(onClick = { timeEdit = "quietStart" to st.quietStartMinutes }) { Text("Set start") }
                TextButton(onClick = { timeEdit = "quietEnd" to st.quietEndMinutes }) { Text("Set end") }
            }
            Stepper("Max notifications per day", st.maxNotificationsPerDay, { vm.saveSettings(st.copy(maxNotificationsPerDay = it)) }, 1..5)
            Text("Ignored reminders are sent less often, never more. Every notification can be dismissed.", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard("Rest timer") {
            Row { Text("Sound", Modifier.weight(1f)); Switch(st.timerSound, { vm.saveSettings(st.copy(timerSound = it)) }) }
            Row { Text("Vibration", Modifier.weight(1f)); Switch(st.timerVibrate, { vm.saveSettings(st.copy(timerVibrate = it)) }) }
        }

        AiSection(st, vm)

        SectionCard("Your data") {
            Text("Everything is stored only on this phone. No account, no analytics, no ads.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { exportLauncher.launch("workout-coach-export.json") }, Modifier.fillMaxWidth()) { Text("Export all data (JSON)") }
            OutlinedButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth().testTag("delete_all")) { Text("Delete all data", color = MaterialTheme.colorScheme.error) }
        }

        SectionCard("Privacy") {
            Text(
                "• Workouts, chat, memory and measurements stay in a local database and are excluded from cloud backup.\n" +
                    "• The offline coach runs entirely on the phone.\n" +
                    "• Only if you enable remote AI are your message and a coaching context sent to the backend URL you configure, which forwards them to the AI provider you choose. Turn off \"share history\" to send only the message, equipment and plan options.\n" +
                    "• Provider API keys never live in this app.\n" +
                    "• General fitness guidance only — not medical advice.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.labelSmall)
    }

    timeEdit?.let { (key, minutes) ->
        TimePickerDialog(minutes, { timeEdit = null }) { m ->
            when (key) {
                "profile" -> draft = draft.copy(preferredTimeMinutes = m)
                "quietStart" -> vm.saveSettings(st.copy(quietStartMinutes = m))
                "quietEnd" -> vm.saveSettings(st.copy(quietEndMinutes = m))
                else -> reminders.firstOrNull { it.type == key }?.let { vm.saveReminder(it.copy(minuteOfDay = m)) }
                    ?: vm.saveReminder(ReminderConfigEntity(key, m, true))
            }
            timeEdit = null
        }
    }
    if (excludedDialog) {
        var sel by remember { mutableStateOf(draft.excluded) }
        AlertDialog(
            onDismissRequest = { excludedDialog = false },
            title = { Text("Exercises to avoid") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ExerciseCatalog.all.forEach { ex ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(ex.id in sel, { sel = if (it) sel + ex.id else sel - ex.id })
                            Text(ex.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { draft = draft.copy(excludedExercises = sel.joinToString(",")); excludedDialog = false }) { Text("Done") } },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete all data?") },
        text = { Text("This permanently deletes your profile, workouts, chat, memory, measurements and settings from this phone. Export first if you want a copy.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteAll() }, Modifier.testTag("confirm_delete_all")) { Text("Delete everything", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun AiSection(st: AppSettings, vm: SettingsViewModel) {
    var url by remember(st.backendUrl) { mutableStateOf(st.backendUrl) }
    var token by remember(st.appToken) { mutableStateOf(st.appToken) }
    SectionCard("AI coach (optional)") {
        Text(
            "Off by default. The offline rule-based coach always works. To use a language model, deploy the included backend (see README), " +
                "then enter its HTTPS URL and app token here. The backend holds the provider key and enforces rate and spending limits.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use remote AI", Modifier.weight(1f))
            Switch(st.aiEnabled, { vm.saveSettings(st.copy(aiEnabled = it)) }, Modifier.testTag("ai_toggle"))
        }
        OutlinedTextField(url, { url = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Backend URL (https://…)") }, singleLine = true)
        OutlinedTextField(token, { token = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("App token") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        val urlOk = url.isBlank() || url.startsWith("https://")
        if (!urlOk) Text("The URL must start with https://", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.saveSettings(st.copy(backendUrl = url, appToken = token)) }, enabled = urlOk && (url != st.backendUrl || token != st.appToken)) { Text("Save connection") }
        Text("Provider", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(RemoteProvider.entries, st.provider, { it.displayName }) { vm.saveSettings(st.copy(provider = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Share training history & memory")
                Text("Off = only your message, equipment and plan options are sent.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(st.shareHistoryWithAi, { vm.saveSettings(st.copy(shareHistoryWithAi = it)) })
        }
        val today = java.time.LocalDate.now().toEpochDay()
        val (req, tok) = if (st.aiRequestsDay == today) st.aiRequestsToday to st.aiTokensToday else 0 to 0
        Text("Usage today: $req AI requests, $tok tokens (as reported by the backend).", style = MaterialTheme.typography.bodySmall)
    }
}
