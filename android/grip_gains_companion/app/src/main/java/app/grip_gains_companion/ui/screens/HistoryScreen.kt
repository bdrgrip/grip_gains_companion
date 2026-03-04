package app.grip_gains_companion.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.grip_gains_companion.database.RawSessionEntity
import app.grip_gains_companion.database.RawSessionRepository
import app.grip_gains_companion.ui.components.RawLineChart
import app.grip_gains_companion.ui.components.RepMarkersOverlay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    rawRepository: RawSessionRepository,
    onBack: () -> Unit,
    onViewSession: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessions by rawRepository.allSessions.collectAsState(initial = emptyList())
    val errorColor = MaterialTheme.colorScheme.error

    var sessionToEdit by remember { mutableStateOf<RawSessionEntity?>(null) }
    var sessionToDelete by remember { mutableStateOf<RawSessionEntity?>(null) }
    var editMuscleText by remember { mutableStateOf("") }
    var editSideText by remember { mutableStateOf("Bilateral") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val muscleSideOptions = sessions.map { "${it.targetMuscle} (${it.bodySide})" }.distinct().sorted()
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val csvData = generateCsvData(sessions)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csvData.toByteArray()) }
                    Toast.makeText(context, "Export Successful!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                } finally { isExporting = false }
            }
        } else { isExporting = false }
    }

    LaunchedEffect(muscleSideOptions) {
        if (selectedFilter == null && muscleSideOptions.isNotEmpty()) { selectedFilter = muscleSideOptions.first() }
    }

    val filteredBySelection = sessions.filter { "${it.targetMuscle} (${it.bodySide})" == selectedFilter }
    val chronologicalSessions = filteredBySelection.sortedBy { it.timestamp }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session History") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, "Close") } },
                actions = {
                    if (sessions.isNotEmpty()) {
                        IconButton(onClick = { isExporting = true; csvLauncher.launch("GripGains_Export.csv") }, enabled = !isExporting) {
                            Icon(Icons.Default.Download, "CSV")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ExposedDropdownMenuBox(expanded = dropdownExpanded, onExpandedChange = { dropdownExpanded = it }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = selectedFilter ?: "Pick Muscle/Side", onValueChange = {}, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                    muscleSideOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { selectedFilter = option; dropdownExpanded = false })
                    }
                }
            }

            if (chronologicalSessions.size > 1) {
                Text("Score Trend: $selectedFilter", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
                ProgressionLineChart(chronologicalSessions)
            }

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredBySelection.sortedByDescending { it.timestamp }) { s ->
                    SessionHistoryCard(s, { onViewSession(s.id) }, { editMuscleText = s.targetMuscle; editSideText = s.bodySide; sessionToEdit = s }, { sessionToDelete = s })
                }
            }
        }

        if (sessionToEdit != null) {
            AlertDialog(
                onDismissRequest = { sessionToEdit = null },
                title = { Text("Edit Labels") },
                text = {
                    Column {
                        OutlinedTextField(value = editMuscleText, onValueChange = { editMuscleText = it }, label = { Text("Muscle") }, modifier = Modifier.fillMaxWidth())
                        Row(modifier = Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            HistorySideButton("Left", editSideText) { editSideText = it }
                            HistorySideButton("Bilateral", editSideText) { editSideText = it }
                            HistorySideButton("Right", editSideText) { editSideText = it }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { coroutineScope.launch { rawRepository.update(sessionToEdit!!.copy(targetMuscle = editMuscleText, bodySide = editSideText)); sessionToEdit = null } }) { Text("Update") }
                },
                dismissButton = { TextButton(onClick = { sessionToEdit = null }) { Text("Cancel") } }
            )
        }

        if (sessionToDelete != null) {
            AlertDialog(
                onDismissRequest = { sessionToDelete = null },
                title = { Text("Delete Entry?") },
                text = { Text("Permanent removal of ${sessionToDelete!!.targetMuscle} data.") },
                confirmButton = {
                    Button(onClick = { coroutineScope.launch { rawRepository.delete(sessionToDelete!!); sessionToDelete = null } }, colors = ButtonDefaults.buttonColors(containerColor = errorColor)) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { sessionToDelete = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
fun HistoricalGraphView(session: RawSessionEntity, modifier: Modifier = Modifier) {
    val tList = session.timeSeries ?: return
    if (tList.isEmpty()) { Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) { CircularProgressIndicator() }; return }

    val primaryColor = MaterialTheme.colorScheme.primary; val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary; val errorColor = MaterialTheme.colorScheme.error

    val hasReps = (session.repTimestamps?.size ?: 0) > 1
    val cadence = session.averageRepInterval ?: 0.0

    // CRASH FIX: This Column no longer has a verticalScroll modifier.
    Column(modifier = modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Workout Score", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(session.workoutScore.toInt().toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    MetricItem("TUT", "${String.format("%.1f", session.durationSeconds)}s")
                    MetricItem("Work", "${session.mechanicalWork.toInt()}")
                    if (hasReps && !cadence.isNaN() && !cadence.isInfinite()) { MetricItem("Cadence", "${String.format("%.1f", cadence)}s") }
                    MetricItem("Reps", "${session.repTimestamps?.size ?: 0}")
                }
            }
        }

        val minT = tList.firstOrNull { !it.isNaN() } ?: 0.0
        val maxT = tList.lastOrNull { !it.isNaN() } ?: 1.0

        GraphCard("Tension & Magnitude", listOf("Tension" to primaryColor, "Magnitude" to tertiaryColor)) {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                RepMarkersOverlay(session.repTimestamps, minT, maxT)
                RawLineChart(tList, session.tensionSeries, session.magnitudeSeries, session.restDurations, primaryColor, tertiaryColor)
            }
        }

        GraphCard("3s Density & Inst. Power", listOf("Density" to errorColor, "Power" to secondaryColor)) {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                RepMarkersOverlay(session.repTimestamps, minT, maxT)
                RawLineChart(tList, session.densitySeries, session.powerSeries, session.restDurations, errorColor, secondaryColor)
            }
        }

        GraphCard("Accumulated Mechanical Work", emptyList()) {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                RawLineChart(tList, session.workSeries, null, session.restDurations, primaryColor, Color.Gray, true)
            }
        }
        Spacer(Modifier.height(32.dp))

        // Added safe list access for min/max time
        val firstTime = session.timeSeries.firstOrNull { !it.isNaN() } ?: 0.0
        val lastTime = session.timeSeries.lastOrNull { !it.isNaN() } ?: 1.0

        GraphCard(title = "Tension & Magnitude", legends = listOf("Tension" to primaryColor, "Magnitude" to tertiaryColor)) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                RepMarkersOverlay(
                    repTimestamps = session.repTimestamps ?: emptyList(),
                    minTime = firstTime,
                    maxTime = lastTime
                )
                RawLineChart(times = session.timeSeries, primary = session.tensionSeries, secondary = session.magnitudeSeries, pColor = primaryColor, sColor = tertiaryColor)
            }
        }

        GraphCard(title = "3s Density & Inst. Power", legends = listOf("Density" to errorColor, "Power" to secondaryColor)) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                RepMarkersOverlay(
                    repTimestamps = session.repTimestamps ?: emptyList(),
                    minTime = firstTime,
                    maxTime = lastTime
                )
                RawLineChart(times = session.timeSeries, primary = session.densitySeries, secondary = session.powerSeries, pColor = errorColor, sColor = secondaryColor)
            }
        }

        GraphCard(title = "Accumulated Mechanical Work", legends = emptyList()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                RawLineChart(times = session.timeSeries, primary = session.workSeries, secondary = null, pColor = primaryColor, connectGaps = true)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

        GraphCard(title = "Tension & Magnitude", legends = listOf("Tension" to primaryColor, "Magnitude" to tertiaryColor)) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                RepMarkersOverlay(
                    repTimestamps = session.repTimestamps,
                    minTime = session.timeSeries.firstOrNull { !it.isNaN() } ?: 0.0,
                    maxTime = session.timeSeries.lastOrNull { !it.isNaN() } ?: 1.0
                )
                RawLineChart(times = session.timeSeries, primary = session.tensionSeries, secondary = session.magnitudeSeries, pColor = primaryColor, sColor = tertiaryColor)
            }
        }

        GraphCard(title = "3s Density & Inst. Power", legends = listOf("Density" to errorColor, "Power" to secondaryColor)) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                RepMarkersOverlay(
                    repTimestamps = session.repTimestamps,
                    minTime = session.timeSeries.firstOrNull { !it.isNaN() } ?: 0.0,
                    maxTime = session.timeSeries.lastOrNull { !it.isNaN() } ?: 1.0
                )
                RawLineChart(times = session.timeSeries, primary = session.densitySeries, secondary = session.powerSeries, pColor = errorColor, sColor = secondaryColor)
            }
        }

        GraphCard(title = "Accumulated Mechanical Work", legends = emptyList()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                RawLineChart(times = session.timeSeries, primary = session.workSeries, secondary = null, pColor = primaryColor, connectGaps = true)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }


@Composable
fun GraphCard(title: String, legends: List<Pair<String, Color>>, content: @Composable () -> Unit) {
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

@Composable
fun ProgressionLineChart(sessions: List<RawSessionEntity>) {
    val textMeasurer = rememberTextMeasurer()
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)

    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(bgColor)) {
        if (sessions.isEmpty()) return@Canvas

        val scores = sessions.map { it.workoutScore.toFloat() }
        val timestamps = sessions.map { it.timestamp }
        val maxScore = (scores.maxOrNull() ?: 1f).coerceAtLeast(10f)
        val yMax = maxScore * 1.1f

        val paddingLeft = 32.dp.toPx()
        val paddingBottom = 24.dp.toPx()
        val paddingTop = 16.dp.toPx()
        val paddingRight = 16.dp.toPx()

        val drawWidth = size.width - paddingLeft - paddingRight
        val drawHeight = size.height - paddingBottom - paddingTop

        val yLabels = listOf(0f, yMax / 2f, yMax)
        yLabels.forEach { value ->
            val text = value.toInt().toString()
            val layoutResult = textMeasurer.measure(text, labelStyle)
            val yPos = paddingTop + drawHeight - ((value / yMax) * drawHeight)

            drawText(
                textLayoutResult = layoutResult,
                topLeft = Offset(0f, yPos - (layoutResult.size.height / 2f))
            )
            drawLine(color = onSurfaceColor.copy(alpha = 0.1f), start = Offset(paddingLeft, yPos), end = Offset(size.width - paddingRight, yPos), strokeWidth = 1f)
        }

        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        if (timestamps.isNotEmpty()) {
            val startText = dateFormat.format(Date(timestamps.first()))
            val endText = dateFormat.format(Date(timestamps.last()))
            val textY = size.height - paddingBottom + 8.dp.toPx()

            drawText(textMeasurer = textMeasurer, text = startText, style = labelStyle, topLeft = Offset(paddingLeft, textY))
            if (timestamps.size > 1) {
                val endLayout = textMeasurer.measure(endText, labelStyle)
                drawText(textMeasurer = textMeasurer, text = endText, style = labelStyle, topLeft = Offset(size.width - paddingRight - endLayout.size.width, textY))
            }
        }

        val path = Path()
        val points = mutableListOf<Offset>()
        val stepX = if (scores.size > 1) drawWidth / (scores.size - 1) else drawWidth / 2f

        scores.forEachIndexed { i, score ->
            val x = paddingLeft + (i * stepX)
            val y = paddingTop + drawHeight - ((score / yMax) * drawHeight)
            points.add(Offset(x, y))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path = path, color = accentColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEach { point ->
            drawCircle(color = bgColor, radius = 6.dp.toPx(), center = point)
            drawCircle(color = accentColor, radius = 4.dp.toPx(), center = point)
        }
    }
}

@Composable
fun SessionHistoryCard(session: RawSessionEntity, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val date = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(session.timestamp))

    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(date, style = MaterialTheme.typography.labelSmall)
                Row {
                    IconButton(onClick = onEdit, Modifier.size(20.dp)) { Icon(Icons.Default.Edit, null, tint = primaryColor) }
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = onDelete, Modifier.size(20.dp)) { Icon(Icons.Default.Delete, null, tint = errorColor) }
                }
            }
            Text("${session.targetMuscle} (${session.bodySide})", style = MaterialTheme.typography.titleMedium)
            Text("Score: ${session.workoutScore.toInt()} pts", color = primaryColor)
        }
    }
}

@Composable
fun HistorySideButton(label: String, current: String, onSelect: (String) -> Unit) {
    val selected = label == current
    Button(
        onClick = { onSelect(label) },
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) { Text(label, fontSize = 10.sp) }
}

private fun generateCsvData(sessions: List<RawSessionEntity>): String {
    val sb = StringBuilder("Date,Muscle,Side,Weight,Work,Score\n")
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    sessions.forEach { s ->
        sb.append("${df.format(Date(s.timestamp))},${s.targetMuscle},${s.bodySide},${s.targetWeight},${s.mechanicalWork},${s.workoutScore}\n")
    }
    return sb.toString()
}