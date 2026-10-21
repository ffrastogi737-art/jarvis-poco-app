package com.personal.workoutcoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import com.personal.workoutcoach.AppContainer
import com.personal.workoutcoach.WorkoutCoachApp

/** Periodic UI tickers (elapsed time, rest countdown). Tests switch them off so Compose can go idle. */
object UiClock { @Volatile var tickingEnabled = true }

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as WorkoutCoachApp).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val c = appContainer()
    return viewModel(factory = viewModelFactory { initializer { create(c) } })
}

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun InfoBanner(text: String, isError: Boolean = false, action: (@Composable () -> Unit)? = null) {
    val bg = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Number input with large +/- buttons (48dp touch targets). */
@Composable
fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, range: IntRange = 0..100, step: Int = 1, suffix: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step).coerceIn(range)) }, modifier = Modifier.semantics { contentDescription = "Decrease $label" }) {
            Icon(Icons.Default.Remove, null)
        }
        Text("$value$suffix", style = MaterialTheme.typography.titleLarge, modifier = Modifier.widthIn(min = 48.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + step).coerceIn(range)) }, modifier = Modifier.semantics { contentDescription = "Increase $label" }) {
            Icon(Icons.Default.Add, null)
        }
    }
}

/** 1–5 rating chips; tapping the selected value clears it. */
@Composable
fun RatingRow(label: String, value: Int?, onChange: (Int?) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..5) FilterChip(selected = value == i, onClick = { onChange(if (value == i) null else i) }, label = { Text("$i") })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialMinutes / 60, initialMinutes % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state) },
    )
}

fun formatMinutes(m: Int): String {
    val h = (m / 60) % 24
    val min = m % 60
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, min, ampm)
}

/** Grouped bars (e.g. completed vs planned per week). Real values only; empty input draws nothing. */
@Composable
fun BarChart(values: List<Pair<Int, Int>>, labels: List<String>, modifier: Modifier = Modifier, description: String) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.surfaceVariant
    val max = (values.maxOfOrNull { maxOf(it.first, it.second) } ?: 0).coerceAtLeast(1)
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barW = slot * 0.3f
            values.forEachIndexed { i, (a, b) ->
                val x = i * slot + slot * 0.15f
                val hb = size.height * b / max
                drawRect(secondary, Offset(x, size.height - hb), Size(barW, hb))
                val ha = size.height * a / max
                drawRect(primary, Offset(x + barW + 4f, size.height - ha), Size(barW, ha))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun LineChart(points: List<Double>, modifier: Modifier = Modifier, description: String, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = description }) {
        if (points.isEmpty()) return@Canvas
        val min = points.min()
        val max = points.max().let { if (it == min) min + 1 else it }
        fun pos(i: Int) = Offset(
            if (points.size == 1) size.width / 2 else size.width * i / (points.size - 1),
            size.height - ((points[i] - min) / (max - min) * size.height * 0.9 + size.height * 0.05).toFloat(),
        )
        for (i in 1 until points.size) drawLine(color, pos(i - 1), pos(i), strokeWidth = 6f, cap = StrokeCap.Round)
        for (i in points.indices) drawCircle(color, 8f, pos(i))
    }
}
