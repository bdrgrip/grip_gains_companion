package app.grip_gains_companion.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.service.SessionResult
import app.grip_gains_companion.ui.components.RawLineChart // NEW IMPORT

@Composable
fun RawSummaryScreen(
    result: SessionResult,
    onDismiss: () -> Unit,
    onSave: (muscle: String, side: String) -> Unit
) {
    val scrollState = rememberScrollState()
    var targetMuscle by remember { mutableStateOf("") }
    var bodySide by remember { mutableStateOf("Bilateral") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Session Analysis", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
            MetricRow(result)

            OutlinedTextField(
                value = targetMuscle,
                onValueChange = { targetMuscle = it },
                label = { Text("Target Muscle") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                SideButton("Left", bodySide) { bodySide = it }
                SideButton("Bilateral", bodySide) { bodySide = it }
                SideButton("Right", bodySide) { bodySide = it }
            }

            ChartLabel("Tension (Cyan) & Magnitude (Magenta)")
            RawLineChart(result.timeSeries, result.tensionSeries, result.magnitudeSeries, Color.Cyan, Color.Magenta)
            ChartLabel("Inst. Power (Green) & 3s Flux (Yellow)")
            RawLineChart(result.timeSeries, result.powerSeries, result.fluxSeries, Color.Green, Color.Yellow)
            ChartLabel("Accumulated Mechanical Work")
            RawLineChart(result.timeSeries, result.workSeries, null, Color(0xFFE65100))

            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = onDismiss) { Text("Discard") }
                Button(onClick = { onSave(targetMuscle, bodySide) }) { Text("Save Set") }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SideButton(label: String, current: String, onSelect: (String) -> Unit) {
    val isSelected = label == current
    Button(
        onClick = { onSelect(label) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray
        )
    ) {
        Text(label, color = Color.White)
    }
}

@Composable
private fun MetricRow(result: SessionResult) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        MetricCard("Work", result.mechanicalWork.toInt().toString())
        MetricCard("Score", result.workoutScore.toInt().toString())
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Card(modifier = Modifier.padding(8.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ChartLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}