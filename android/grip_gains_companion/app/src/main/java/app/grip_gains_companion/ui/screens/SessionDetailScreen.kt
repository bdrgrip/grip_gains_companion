package app.grip_gains_companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.database.RawSessionRepository
import app.grip_gains_companion.ui.components.RawLineChart
import app.grip_gains_companion.ui.components.RepMarkersOverlay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    rawRepository: RawSessionRepository,
    onBack: () -> Unit
) {
    val session by rawRepository.getSessionById(sessionId).collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        if (session != null) {
            val s = session!!
            val tList = s.timeSeries ?: emptyList()

            if (tList.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }

            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.secondary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary
            val errorColor = MaterialTheme.colorScheme.error

            val hasReps = (s.repTimestamps?.size ?: 0) > 1
            val cadence = s.averageRepInterval ?: 0.0
            val minT = tList.firstOrNull { !it.isNaN() } ?: 0.0
            val maxT = tList.lastOrNull { !it.isNaN() } ?: 1.0

            // LAZYCOLUMN: Completely prevents the infinite height crash
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // --- HEADER ---
                item {
                    Text(s.targetMuscle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = primaryColor)
                    Text(s.bodySide, style = MaterialTheme.typography.titleMedium, color = secondaryColor)
                    val dateText = SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(s.timestamp))
                    Text(dateText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                }

                // --- DASHBOARD ---
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Workout Score", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(s.workoutScore.toInt().toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                                DetailMetricItem("TUT", "${String.format("%.1f", s.durationSeconds)}s")
                                DetailMetricItem("Work", "${s.mechanicalWork.toInt()}")
                                if (hasReps && !cadence.isNaN() && !cadence.isInfinite()) { DetailMetricItem("Cadence", "${String.format("%.1f", cadence)}s") }
                                DetailMetricItem("Reps", "${s.repTimestamps?.size ?: 0}")
                            }
                        }
                    }
                }

                // --- TENSION GRAPH ---
                item {
                    DetailGraphCard("Tension & Magnitude", listOf("Tension" to primaryColor, "Magnitude" to tertiaryColor)) {
                        Box(Modifier.fillMaxWidth().height(200.dp)) {
                            RepMarkersOverlay(s.repTimestamps, minT, maxT)
                            RawLineChart(tList, s.tensionSeries, s.magnitudeSeries, s.restDurations, primaryColor, tertiaryColor)
                        }
                    }
                }

                // --- POWER GRAPH ---
                item {
                    DetailGraphCard("3s Density & Inst. Power", listOf("Density" to errorColor, "Power" to secondaryColor)) {
                        Box(Modifier.fillMaxWidth().height(200.dp)) {
                            RepMarkersOverlay(s.repTimestamps, minT, maxT)
                            RawLineChart(tList, s.densitySeries, s.powerSeries, s.restDurations, errorColor, secondaryColor)
                        }
                    }
                }

                // --- WORK GRAPH ---
                item {
                    DetailGraphCard("Accumulated Mechanical Work", emptyList()) {
                        Box(Modifier.fillMaxWidth().height(200.dp)) {
                            RawLineChart(tList, s.workSeries, null, s.restDurations, primaryColor, Color.Gray, true)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailMetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

@Composable
private fun DetailGraphCard(title: String, legends: List<Pair<String, Color>>, content: @Composable () -> Unit) {
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