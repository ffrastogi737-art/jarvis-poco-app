package com.personal.workoutcoach.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.personal.workoutcoach.AppContainer
import com.personal.workoutcoach.core.model.CoachStyle
import com.personal.workoutcoach.core.model.Language
import com.personal.workoutcoach.core.plan.ScheduleEngine
import com.personal.workoutcoach.data.db.UserProfileEntity
import com.personal.workoutcoach.data.repo.toStored
import com.personal.workoutcoach.ui.components.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    fun finish(p: UserProfileEntity, reminders: Boolean) = c.scope.launch { // app scope: this screen disappears once onboarded
        c.training.completeOnboarding(p)
        val configs = c.db.reminders().all().map { if (!reminders) it.copy(enabled = false) else it }
        configs.forEach { c.db.reminders().upsert(it) }
        c.reminderScheduler.scheduleAll(configs)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen() {
    val vm = appViewModel { OnboardingViewModel(it) }
    var language by remember { mutableStateOf(Language.HINGLISH) }
    var style by remember { mutableStateOf(CoachStyle.BALANCED) }
    var days by remember { mutableStateOf(ScheduleEngine.defaultTrainingDays) }
    var time by remember { mutableIntStateOf(18 * 60) }
    var minutes by remember { mutableIntStateOf(25) }
    var restrictions by remember { mutableStateOf("") }
    var reminders by remember { mutableStateOf(true) }
    var showTime by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Scaffold { pad ->
        ScreenColumn(Modifier.padding(pad)) {
            Text("Welcome back to training", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Your personal home-workout coach. We'll restart gently: 3 short full-body sessions a week, " +
                    "built around your dumbbells and your schedule. You can change everything later.",
            )
            SectionCard("Coach language") {
                ChoiceChips(Language.entries, language, { if (it == Language.HINGLISH) "Hinglish" else "English" }) { language = it }
            }
            SectionCard("Coaching style") {
                ChoiceChips(CoachStyle.entries, style, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { style = it }
                Text(
                    when (style) {
                        CoachStyle.FRIENDLY -> "Always encouraging, never pushy."
                        CoachStyle.BALANCED -> "Friendly by default; more direct if you keep postponing."
                        CoachStyle.STRICT -> "Direct accountability, always respectful."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SectionCard("Training days") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(selected = d in days, onClick = { days = if (d in days) days - d else days + d },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) })
                    }
                }
                if (ScheduleEngine.hasConsecutiveDays(days)) Text("Tip: full-body sessions recover best with a rest day in between.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (days.size > 4) Text("For the comeback, 3 days is plenty.", style = MaterialTheme.typography.bodySmall)
                Row { Text("Preferred time: ${formatMinutes(time)}", Modifier.weight(1f).padding(top = 12.dp)); TextButton(onClick = { showTime = true }) { Text("Change") } }
                Stepper("Session length", minutes, { minutes = it }, 10..60, 5, " min")
            }
            SectionCard("Equipment") {
                Text("Starting with: 2 × 17.5 kg fixed dumbbells + bodyweight. Add bands, a bar, a bench and more under Profile → Equipment.")
            }
            SectionCard("Anything to avoid?") {
                OutlinedTextField(restrictions, { restrictions = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Injuries or exercises to avoid (optional)") })
            }
            SectionCard("Reminders") {
                Row { Text("Training-day reminders", Modifier.weight(1f)); Switch(reminders, { reminders = it }) }
                Text("Max 2 a day, never during quiet hours (10 PM–7 AM by default).", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    if (reminders && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    vm.finish(
                        UserProfileEntity(
                            language = language.name, coachStyle = style.name, trainingDays = days.ifEmpty { ScheduleEngine.defaultTrainingDays }.toStored(),
                            preferredTimeMinutes = time, preferredMinutes = minutes, restrictions = restrictions.trim(),
                        ),
                        reminders,
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("get_started"),
            ) { Text("Get started") }
            Text(
                "This app gives general fitness guidance, not medical advice. Stop if you feel sharp pain, dizziness or chest pain.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showTime) TimePickerDialog(time, { showTime = false }) { time = it; showTime = false }
}
