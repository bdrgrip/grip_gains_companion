package app.grip_gains_companion.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.service.SessionResult
import app.grip_gains_companion.ui.components.RawLineChart
import app.grip_gains_companion.ui.components.RepMarkersOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawSummaryScreen(
    result: SessionResult,
    recentMuscles: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var targetMuscle by remember { mutableStateOf("") }
    var bodySide by remember { mutableStateOf("Bilateral") }
    var showDiscardDialog by remember { mutableStateOf(false) }

    BackHandler { showDiscardDialog = true }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Session Complete", fontWeight = FontWeight.Bold) }) },
        bottomBar = {
            Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).navigationBarsPadding()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { showDiscardDialog = true }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = { onSave(targetMuscle, bodySide) },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = targetMuscle.isNotBlank()
                    ) { Text("Save Set", fontWeight = FontWeight.Bold) }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                ) {
                    RawDashboardCard(score = result.workoutScore.toInt(), tut = result.timeUnderTension, work = result.mechanicalWork.toInt(), reps = result.repTimestamps.size, cadence = result.averageRepInterval)
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {

                        // --- INLINE AUTOCOMPLETE ---
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = targetMuscle,
                                onValueChange = { targetMuscle = it },
                                label = { Text("Target Muscle") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    if (targetMuscle.isNotEmpty()) {
                                        IconButton(onClick = { targetMuscle = ""; focusManager.clearFocus() }) { Icon(Icons.Default.Clear, "Clear") }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                            )

                            val filteredOptions = recentMuscles.filter { it.contains(targetMuscle, true) && !it.equals(targetMuscle, true) }

                            AnimatedVisibility(
                                visible = targetMuscle.isNotEmpty() && filteredOptions.isNotEmpty(),
                                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column {
                                        filteredOptions.take(4).forEach { muscle ->
                                            ListItem(
                                                headlineContent = { Text(muscle, fontWeight = FontWeight.Bold) },
                                                modifier = Modifier.clickable { targetMuscle = muscle; focusManager.clearFocus() },
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Side", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummarySideButton("Left", bodySide, Modifier.weight(1f)) { bodySide = it }
                            SummarySideButton("Bilateral", bodySide, Modifier.weight(1f)) { bodySide = it }
                            SummarySideButton("Right", bodySide, Modifier.weight(1f)) { bodySide = it }
                        }
                    }
                }
            }

            item {
                RawGraphStack(
                    timeSeries = result.timeSeries, tensionSeries = result.tensionSeries, magnitudeSeries = result.magnitudeSeries,
                    densitySeries = result.densitySeries, powerSeries = result.powerSeries, workSeries = result.workSeries,
                    restDurations = result.restDurations, repTimestamps = result.repTimestamps
                )
            }
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("Discard Session?") },
                text = { Text("Are you sure you want to delete this data? It cannot be recovered.") },
                confirmButton = {
                    Button(onClick = { showDiscardDialog = false; onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Discard") }
                },
                dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel") } }
            )
        }
    }
}

// ... GLOBALLY SHARED UI COMPONENTS BELOW STAY EXACTLY THE SAME ...

@Composable
fun RawDashboardCard(score: Int, tut: Double, work: Int, reps: Int, cadence: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Workout Score", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RawMetricItem("TUT", "${String.format("%.1f", tut)}s")
                RawMetricItem("Work", "$work")
                if (reps > 1 && !cadence.isNaN() && !cadence.isInfinite()) {
                    RawMetricItem("Cadence", "${String.format("%.1f", cadence)}s")
                }
                RawMetricItem("Reps", "$reps")
            }
        }
    }
}

@Composable
fun RawGraphStack(
    timeSeries: List<Double>, tensionSeries: List<Double>, magnitudeSeries: List<Double>?,
    densitySeries: List<Double>?, powerSeries: List<Double>?, workSeries: List<Double>,
    restDurations: List<Double>,
    repTimestamps: List<Double>
) {
    val pColor = MaterialTheme.colorScheme.primary
    val sColor = MaterialTheme.colorScheme.secondary
    val tColor = MaterialTheme.colorScheme.tertiary
    val eColor = MaterialTheme.colorScheme.error
    val validTimes = timeSeries.filter { !it.isNaN() }
    val minT = validTimes.firstOrNull() ?: 0.0
    val maxT = validTimes.lastOrNull() ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SummaryGraphCard("Tension & Magnitude", listOf("Tension" to pColor, "Magnitude" to tColor)) {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                RepMarkersOverlay(repTimestamps, minT, maxT, modifier = Modifier.fillMaxSize())
                RawLineChart(timeSeries, tensionSeries, magnitudeSeries, restDurations, pColor, tColor, modifier = Modifier.fillMaxSize())
            }
        }
        if (densitySeries != null && powerSeries != null) {
            SummaryGraphCard("Density & Power", listOf("Density" to eColor, "Power" to sColor)) {
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    RepMarkersOverlay(repTimestamps, minT, maxT, modifier = Modifier.fillMaxSize())
                    RawLineChart(timeSeries, densitySeries, powerSeries, restDurations, eColor, sColor, modifier = Modifier.fillMaxSize())
                }
            }
        }
        SummaryGraphCard("Accumulated Mechanical Work", emptyList()) {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                RawLineChart(timeSeries, workSeries, null, restDurations, pColor, Color.Gray, true, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun RawMetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

@Composable
private fun SummarySideButton(label: String, current: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val isSelected = label == current
    Button(
        onClick = { onSelect(label) }, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) { Text(label, maxLines = 1) }
}

@Composable
private fun SummaryGraphCard(title: String, legends: List<Pair<String, Color>>, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), // ADD THIS
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (legends.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    legends.forEach { (name, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            content()
        }
    }
}