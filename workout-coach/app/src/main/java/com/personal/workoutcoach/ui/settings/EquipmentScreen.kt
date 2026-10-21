package com.personal.workoutcoach.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.personal.workoutcoach.AppContainer
import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.equipment.*
import com.personal.workoutcoach.data.db.CoachMemoryEntity
import com.personal.workoutcoach.data.repo.toItem
import com.personal.workoutcoach.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EquipmentViewModel(private val c: AppContainer) : ViewModel() {
    val items = c.training.equipment.map { list -> list.map { it.toItem() } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val errors = MutableStateFlow<List<String>>(emptyList())

    fun save(item: EquipmentItem, onDone: () -> Unit) = viewModelScope.launch {
        val e = c.training.saveEquipment(item)
        errors.value = e
        if (e.isEmpty()) onDone()
    }

    fun toggle(item: EquipmentItem) = save(item.copy(enabled = !item.enabled)) {}
    fun delete(id: Long) = viewModelScope.launch { c.training.deleteEquipment(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentScreen(onBack: () -> Unit) {
    val vm = appViewModel { EquipmentViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EquipmentItem?>(null) }
    val inv = remember(items) { Inventory(items) }
    val available = remember(inv) { ExerciseCatalog.all.count { inv.isCompatible(it) } }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Equipment") }, navigationIcon = {
                IconButton(onClick = onBack, Modifier.semantics { contentDescription = "Back" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editing = EquipmentItem(type = EquipmentType.FIXED_DUMBBELL, name = "") }, Modifier.testTag("add_equipment")) {
                Icon(Icons.Default.Add, null); Text("Add")
            }
        },
    ) { pad ->
        ScreenColumn(Modifier.padding(pad)) {
            InfoBanner("$available of ${ExerciseCatalog.all.size} catalogue exercises are possible with your enabled equipment. Plans update automatically; past workouts are never changed.")
            if (items.isEmpty()) EmptyState("No equipment — bodyweight exercises only.")
            items.forEach { item ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.label(), style = MaterialTheme.typography.titleMedium)
                            Text(item.type.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(item.enabled, { vm.toggle(item) }, Modifier.semantics { contentDescription = "Enable ${item.name}" })
                    }
                    if (item.type == EquipmentType.RESISTANCE_BAND && item.anchor != BandAnchor.DOOR_ANCHOR && item.anchor != BandAnchor.SECURE_POST) {
                        Text("No safe anchor recorded: anchored band exercises won't be suggested.", style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        TextButton(onClick = { editing = item }) { Text("Edit") }
                        TextButton(onClick = { vm.delete(item.id) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    editing?.let { EquipmentDialog(it, vm, onDismiss = { editing = null; vm.errors.value = emptyList() }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquipmentDialog(initial: EquipmentItem, vm: EquipmentViewModel, onDismiss: () -> Unit) {
    var item by remember { mutableStateOf(initial) }
    val errors by vm.errors.collectAsStateWithLifecycle()
    fun num(s: String) = s.replace(',', '.').toDoubleOrNull()

    @Composable
    fun NumField(label: String, value: Double?, set: (Double?) -> Unit) {
        var text by remember(label) { mutableStateOf(value?.let(::fmt) ?: "") }
        OutlinedTextField(text, { text = it.take(7); set(num(it)) }, Modifier.fillMaxWidth(), label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add equipment" else "Edit equipment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial.id == 0L) ChoiceChips(EquipmentType.entries.filter { it != EquipmentType.BODYWEIGHT }, item.type, { it.name.lowercase().replace('_', ' ') }) { item = item.copy(type = it) }
                OutlinedTextField(item.name, { item = item.copy(name = it.take(60)) }, Modifier.fillMaxWidth().testTag("equipment_name"), label = { Text("Name") }, singleLine = true)
                Stepper("Quantity", item.quantity, { item = item.copy(quantity = it) }, 1..50)
                when (item.type) {
                    EquipmentType.FIXED_DUMBBELL, EquipmentType.KETTLEBELL, EquipmentType.WEIGHT_PLATE, EquipmentType.ANKLE_WEIGHTS ->
                        NumField("Weight per item (kg)", item.weightKg) { item = item.copy(weightKg = it) }
                    EquipmentType.BARBELL -> NumField("Bar weight (kg)", item.weightKg) { item = item.copy(weightKg = it) }
                    EquipmentType.ADJUSTABLE_DUMBBELL -> {
                        NumField("Minimum (kg)", item.minWeightKg) { item = item.copy(minWeightKg = it) }
                        NumField("Maximum (kg)", item.maxWeightKg) { item = item.copy(maxWeightKg = it) }
                        NumField("Increment (kg)", item.incrementKg) { item = item.copy(incrementKg = it) }
                    }
                    EquipmentType.RESISTANCE_BAND -> {
                        ChoiceChips(BandType.entries, item.bandType, { it.name.lowercase().replace('_', ' ') }) { item = item.copy(bandType = it) }
                        OutlinedTextField(item.resistanceLabel ?: "", { item = item.copy(resistanceLabel = it.take(40)) }, Modifier.fillMaxWidth(), label = { Text("Resistance level / colour (as labelled)") })
                        NumField("Rated resistance in kg (optional)", item.resistanceKg) { item = item.copy(resistanceKg = it) }
                        Text("Anchor", style = MaterialTheme.typography.labelLarge)
                        ChoiceChips(BandAnchor.entries, item.anchor ?: BandAnchor.NONE, {
                            when (it) { BandAnchor.NONE -> "none"; BandAnchor.DOOR_ANCHOR -> "purpose-made door anchor"; BandAnchor.SECURE_POST -> "secure fixed post"; BandAnchor.OTHER -> "other / improvised" }
                        }) { item = item.copy(anchor = it) }
                        Text("Improvised anchors (handles, furniture) aren't used for anchored exercises.", style = MaterialTheme.typography.bodySmall)
                    }
                    EquipmentType.BENCH -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BenchPosition.entries.forEach { b ->
                            FilterChip(b in item.benchPositions, { item = item.copy(benchPositions = if (b in item.benchPositions) item.benchPositions - b else item.benchPositions + b) }, label = { Text(b.name.lowercase()) })
                        }
                    }
                    EquipmentType.PULL_UP_BAR -> ChoiceChips(MountType.entries, item.mountType, { it.name.lowercase().replace('_', ' ') }) { item = item.copy(mountType = it) }
                    EquipmentType.CUSTOM -> {
                        Text("What can it be used as? (leave empty if unsure)", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Capability.entries.filter { it != Capability.DUMBBELL_PAIR }.forEach { cap ->
                                FilterChip(cap in item.customCapabilities, {
                                    item = item.copy(customCapabilities = if (cap in item.customCapabilities) item.customCapabilities - cap else item.customCapabilities + cap)
                                }, label = { Text(cap.name.lowercase().replace('_', ' ')) })
                            }
                        }
                    }
                    else -> Unit
                }
                OutlinedTextField(item.notes, { item = item.copy(notes = it.take(200)) }, Modifier.fillMaxWidth(), label = { Text("Notes") })
                errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { vm.save(item, onDismiss) }, Modifier.testTag("save_equipment")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------- Coach memory (inspect, edit, delete)

class MemoryViewModel(private val c: AppContainer) : ViewModel() {
    val memory = c.coach.memory
    fun add(category: String, content: String) = viewModelScope.launch { c.coach.addMemory(category, content) }
    fun update(m: CoachMemoryEntity) = viewModelScope.launch { c.coach.updateMemory(m) }
    fun delete(id: Long) = viewModelScope.launch { c.coach.deleteMemory(id) }
    fun clearChat() = viewModelScope.launch { c.coach.clearChat() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val vm = appViewModel { MemoryViewModel(it) }
    val memory by vm.memory.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<CoachMemoryEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val categories = listOf("PROFILE", "TRAINING", "BEHAVIOR", "CONVERSATION")
    Scaffold(topBar = {
        TopAppBar(title = { Text("Coach memory") }, navigationIcon = {
            IconButton(onClick = onBack, Modifier.semantics { contentDescription = "Back" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        })
    }) { pad ->
        ScreenColumn(Modifier.padding(pad)) {
            Text("Notes the coach uses for context. Workout history and your profile are read directly from your records. Edit or delete anything.", style = MaterialTheme.typography.bodySmall)
            if (memory.isEmpty()) EmptyState("Nothing remembered yet. Add things like \"I prefer evening workouts\".")
            memory.forEach { m ->
                SectionCard {
                    Text("${m.category.lowercase()} · ${if (m.source == "COACH") "suggested by coach" else "added by you"}", style = MaterialTheme.typography.labelSmall)
                    Text(m.content)
                    Row {
                        TextButton(onClick = { editing = m }) { Text("Edit") }
                        TextButton(onClick = { vm.delete(m.id) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Button(onClick = { editing = CoachMemoryEntity(category = "BEHAVIOR", content = "") }, Modifier.fillMaxWidth()) { Text("Add a note") }
            OutlinedButton(onClick = { confirmClear = true }, Modifier.fillMaxWidth()) { Text("Clear chat history") }
        }
    }
    editing?.let { m ->
        var text by remember(m.id) { mutableStateOf(m.content) }
        var cat by remember(m.id) { mutableStateOf(m.category) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (m.id == 0L) "Add note" else "Edit note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChips(categories, cat, { it.lowercase() }) { cat = it }
                    OutlinedTextField(text, { text = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Note") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) { if (m.id == 0L) vm.add(cat, text) else vm.update(m.copy(category = cat, content = text)) }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear chat history?") },
        text = { Text("Deletes all coach conversations. Workouts and memory notes are kept.") },
        confirmButton = { TextButton(onClick = { vm.clearChat(); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}
