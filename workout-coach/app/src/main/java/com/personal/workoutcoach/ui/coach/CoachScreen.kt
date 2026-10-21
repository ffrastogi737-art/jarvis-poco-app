package com.personal.workoutcoach.ui.coach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.personal.workoutcoach.core.coach.AiResponseParser
import com.personal.workoutcoach.core.coach.QuickAction
import com.personal.workoutcoach.core.coach.ReplySourceKind
import com.personal.workoutcoach.data.db.ChatMessageEntity
import com.personal.workoutcoach.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class CoachViewModel(private val c: AppContainer) : ViewModel() {
    val messages = c.coach.messages
    val settings = c.settings.settings
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val feedbackSaved = MutableStateFlow(false)

    fun send(text: String) {
        if (text.isBlank() || busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { c.coach.send(text) } catch (e: Exception) { error.value = "Couldn't get a reply: ${e.message}" } finally { busy.value = false }
        }
    }

    fun retry(id: Long) = viewModelScope.launch {
        busy.value = true
        try { c.coach.retry(id) } catch (e: Exception) { error.value = "Retry failed: ${e.message}" } finally { busy.value = false }
    }

    /** Starts the validated workout attached to a coach message. Re-validated against current equipment. */
    fun startFromMessage(m: ChatMessageEntity, onStarted: () -> Unit) = viewModelScope.launch {
        val rec = m.structuredJson?.let(AiResponseParser::decodeStored)?.workout ?: return@launch
        val plan = c.training.validator().validate(rec).workout
        if (plan == null) { error.value = "This workout no longer matches your equipment."; return@launch }
        c.training.startSession(plan)
        onStarted()
    }

    fun startMinimum(onStarted: () -> Unit) = viewModelScope.launch {
        c.training.startSession(c.training.plan(com.personal.workoutcoach.core.model.SessionMode.MINIMUM))
        onStarted()
    }

    fun saveFeedback(energy: Int?, motivation: Int?, soreness: Int?, notes: String) = viewModelScope.launch {
        c.training.addFeedback(energy, motivation, soreness, notes)
        feedbackSaved.value = true
    }
}

val QUICK_PROMPTS = listOf(
    "Plan my workout today", "I have only 15 minutes", "I have no motivation today", "I am tired",
    "I missed my last workout", "Show my progress", "Make today's workout easier", "Explain goblet squat", "Help me get back on track",
)

@Composable
fun CoachScreen(onOpenWorkout: () -> Unit, onOpenProgress: () -> Unit, onOpenHome: () -> Unit) {
    val vm = appViewModel { CoachViewModel(it) }
    val messages by vm.messages.collectAsStateWithLifecycle(emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle(null)
    var input by remember { mutableStateOf("") }
    var showFeedback by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1 + if (busy) 1 else 0) }

    Column(Modifier.fillMaxSize().imePadding()) {
        val aiOn = settings?.let { it.aiEnabled && it.backendUrl.isNotBlank() && it.appToken.isNotBlank() } == true
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                if (aiOn) "Remote AI on (${settings!!.provider.displayName}). Messages are sent to your backend." else "Offline coach (rule-based). Enable remote AI in Profile → AI.",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
            )
        }
        error?.let { InfoBanner(it, isError = true) { TextButton(onClick = { vm.error.value = null }) { Text("Dismiss") } } }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("chat_list"), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) item { EmptyState("Ask your coach anything about today's training, or tap a prompt below.") }
            items(messages, key = { it.id }) { m ->
                MessageBubble(m, busy,
                    onRetry = { vm.retry(m.id) },
                    onStart = { vm.startFromMessage(m, onOpenWorkout) },
                    onAction = { a ->
                        when (a) {
                            QuickAction.START_WORKOUT -> vm.startFromMessage(m, onOpenWorkout)
                            QuickAction.START_MINIMUM -> vm.startMinimum(onOpenWorkout)
                            QuickAction.RESCHEDULE -> onOpenHome()
                            QuickAction.VIEW_PROGRESS -> onOpenProgress()
                            QuickAction.LOG_FEEDBACK -> showFeedback = true
                        }
                    })
            }
            if (busy) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("coach_loading")) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Coach is thinking…")
                }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(QUICK_PROMPTS) { p -> SuggestionChip(onClick = { vm.send(p) }, label = { Text(p) }, enabled = !busy) }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it.take(2000) }, Modifier.weight(1f).testTag("chat_input"), placeholder = { Text("Message your coach") }, maxLines = 4)
            IconButton(
                onClick = { vm.send(input); input = "" },
                enabled = input.isNotBlank() && !busy,
                modifier = Modifier.semantics { contentDescription = "Send" }.testTag("send"),
            ) { Icon(Icons.AutoMirrored.Filled.Send, null) }
        }
    }
    if (showFeedback) FeedbackDialog(onDismiss = { showFeedback = false }) { e, m, s, n -> vm.saveFeedback(e, m, s, n); showFeedback = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(m: ChatMessageEntity, busy: Boolean, onRetry: () -> Unit, onStart: () -> Unit, onAction: (QuickAction) -> Unit) {
    val mine = m.role == "user"
    val reply = remember(m.structuredJson) { m.structuredJson?.let(AiResponseParser::decodeStored) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (!mine) Text(m.sourceLabel ?: "Coach", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            color = when {
                mine -> MaterialTheme.colorScheme.primary
                m.source == ReplySourceKind.LOCAL_SAFETY.name -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(m.content)
                reply?.safetyNotes?.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
                if (reply?.workout != null && reply.workout!!.exercises.isNotEmpty()) {
                    Button(onClick = onStart, modifier = Modifier.testTag("start_from_chat")) { Text("Start this workout") }
                }
                m.warnings?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        m.errorNotice?.let { notice ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 340.dp)) {
                Text(notice, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (m.retryText != null) TextButton(onClick = onRetry, enabled = !busy, modifier = Modifier.testTag("retry")) { Text("Retry AI") }
            }
        }
        val actions = reply?.actions.orEmpty().filter { it != QuickAction.START_WORKOUT || reply?.workout == null }
        if (!mine && actions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            actions.forEach { a ->
                AssistChip(onClick = { onAction(a) }, label = {
                    Text(when (a) {
                        QuickAction.START_WORKOUT -> "Start workout"
                        QuickAction.START_MINIMUM -> "Start Minimum session"
                        QuickAction.RESCHEDULE -> "Reschedule"
                        QuickAction.LOG_FEEDBACK -> "Log how I feel"
                        QuickAction.VIEW_PROGRESS -> "View progress"
                    })
                })
            }
        }
    }
}

@Composable
fun FeedbackDialog(onDismiss: () -> Unit, onSave: (Int?, Int?, Int?, String) -> Unit) {
    var energy by remember { mutableStateOf<Int?>(null) }
    var motivation by remember { mutableStateOf<Int?>(null) }
    var soreness by remember { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How do you feel?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingRow("Energy (1 low – 5 high)", energy) { energy = it }
                RatingRow("Motivation", motivation) { motivation = it }
                RatingRow("Soreness (1 none – 5 very sore)", soreness) { soreness = it }
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("Notes (optional)") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(energy, motivation, soreness, notes) }, modifier = Modifier.testTag("save_feedback")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Skip") } },
    )
}
