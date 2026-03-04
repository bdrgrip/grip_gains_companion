package app.grip_gains_companion.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.service.SessionResult
import app.grip_gains_companion.ui.components.RawLineChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawSummaryScreen(
    result: SessionResult,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var targetMuscle by remember { mutableStateOf("") }
    var bodySide by remember { mutableStateOf("Bilateral") }
    var showDiscardDialog by remember { mutableStateOf(false) }

    BackHandler { showDiscardDialog = true }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error

    Scaffold(
        topBar = { TopAppBar(title = { Text("Session Complete", fontWeight = FontWeight.Bold) }) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(onClick = { showDiscardDialog = true }, modifier = Modifier.weight(1f)) { Text("Discard") }
                    Button(
                        onClick = {
                            onSave(targetMuscle, bodySide)
                            Toast.makeText(context, "Session Saved", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = targetMuscle.isNotBlank()
                    ) { Text("Save Set") }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Card(modifier = Modifier.padding(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Work", style = MaterialTheme.typography.labelMedium)
                        Text(result.mechanicalWork.toInt().toString(), style = MaterialTheme.typography.titleLarge, color = primaryColor)
                    }
                }
                Card(modifier = Modifier.padding(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Score", style = MaterialTheme.typography.labelMedium)
                        Text(result.workoutScore.toInt().toString(), style = MaterialTheme.typography.titleLarge, color = primaryColor)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = targetMuscle,
                        onValueChange = { targetMuscle = it },
                        label = { Text("Target Muscle") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Side", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummarySideButton("Left", bodySide, Modifier.weight(1f)) { bodySide = it }
                        SummarySideButton("Bilateral", bodySide, Modifier.weight(1f)) { bodySide = it }
                        SummarySideButton("Right", bodySide, Modifier.weight(1f)) { bodySide = it }
                    }
                }
            }

            SummaryGraphCard(title = "Tension & Magnitude", legends = listOf("Tension" to primaryColor, "Magnitude" to tertiaryColor)) {
                RawLineChart(times = result.timeSeries, primary = result.tensionSeries, secondary = result.magnitudeSeries, pColor = primaryColor, sColor = tertiaryColor)
            }

            SummaryGraphCard(title = "3s Density & Inst. Power", legends = listOf("Density" to errorColor, "Power" to secondaryColor)) {
                RawLineChart(times = result.timeSeries, primary = result.densitySeries, secondary = result.powerSeries, pColor = errorColor, sColor = secondaryColor)
            }

            SummaryGraphCard(title = "Accumulated Mechanical Work", legends = emptyList()) {
                RawLineChart(times = result.timeSeries, primary = result.workSeries, secondary = null, pColor = primaryColor, connectGaps = true)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("Discard Session?") },
                text = { Text("Are you sure you want to delete this data? It cannot be recovered.") },
                confirmButton = {
                    Button(onClick = { showDiscardDialog = false; onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = errorColor)) { Text("Discard") }
                },
                dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel") } }
            )
        }
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
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
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