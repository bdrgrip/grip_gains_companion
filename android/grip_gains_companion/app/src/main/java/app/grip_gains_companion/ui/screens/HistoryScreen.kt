package app.grip_gains_companion.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.database.RawSessionEntity
import app.grip_gains_companion.database.RawSessionRepository
import app.grip_gains_companion.database.calculateRetroactiveScore
import app.grip_gains_companion.ui.components.RawLineChart // NEW IMPORT
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    rawRepository: RawSessionRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val sessions by rawRepository.allSessions.collectAsState(initial = emptyList())
    var selectedSession by remember { mutableStateOf<RawSessionEntity?>(null) }

    // Dialog States
    var sessionToEdit by remember { mutableStateOf<RawSessionEntity?>(null) }
    var sessionToDelete by remember { mutableStateOf<RawSessionEntity?>(null) }
    var editMuscleText by remember { mutableStateOf("") }
    var editSideText by remember { mutableStateOf("Bilateral") }

    // Filter State
    val uniqueMuscles = sessions.map { it.targetMuscle }.distinct().sorted()
    var selectedMuscleFilter by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uniqueMuscles) {
        if (selectedMuscleFilter == null && uniqueMuscles.isNotEmpty()) {
            selectedMuscleFilter = sessions.firstOrNull()?.targetMuscle
        }
    }

    val filteredSessions = sessions.filter { it.targetMuscle == selectedMuscleFilter }
    val chronologicalSessions = filteredSessions.sortedBy { it.timestamp }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedSession == null) "Session History" else "Session Details") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedSession != null) selectedSession = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (selectedSession != null) {
            HistoricalGraphView(session = selectedSession!!, modifier = Modifier.padding(paddingValues))
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (sessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No RAW sessions saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedMuscleFilter ?: "Select Muscle",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Target Muscle Filter") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            uniqueMuscles.forEach { muscle ->
                                DropdownMenuItem(
                                    text = { Text(muscle) },
                                    onClick = {
                                        selectedMuscleFilter = muscle
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (chronologicalSessions.size > 1) {
                        Text("Progression Over Time", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        ProgressionLineChart(chronologicalSessions)
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredSessions) { session ->
                            SessionHistoryCard(
                                session = session,
                                onClick = { selectedSession = session },
                                onEdit = {
                                    editMuscleText = session.targetMuscle
                                    editSideText = session.bodySide
                                    sessionToEdit = session
                                },
                                onDelete = { sessionToDelete = session }
                            )
                        }
                    }
                }
            }
        }

        if (sessionToEdit != null) {
            AlertDialog(
                onDismissRequest = { sessionToEdit = null },
                title = { Text("Edit Session Details") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = editMuscleText,
                            onValueChange = { editMuscleText = it },
                            label = { Text("Target Muscle") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            HistorySideButton("Left", editSideText) { editSideText = it }
                            HistorySideButton("Bilateral", editSideText) { editSideText = it }
                            HistorySideButton("Right", editSideText) { editSideText = it }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        coroutineScope.launch {
                            val updatedSession = sessionToEdit!!.copy(
                                targetMuscle = editMuscleText,
                                bodySide = editSideText
                            )
                            rawRepository.update(updatedSession)
                            selectedMuscleFilter = editMuscleText
                            sessionToEdit = null
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { sessionToEdit = null }) { Text("Cancel") }
                }
            )
        }

        if (sessionToDelete != null) {
            AlertDialog(
                onDismissRequest = { sessionToDelete = null },
                title = { Text("Delete Session") },
                text = { Text("Are you sure you want to permanently delete this session data?") },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                rawRepository.delete(sessionToDelete!!)
                                sessionToDelete = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { sessionToDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun ProgressionLineChart(sessions: List<RawSessionEntity>) {
    val scores = sessions.map { it.calculateRetroactiveScore() }
    val maxScore = (scores.maxOrNull() ?: 1.0).toFloat()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 16.dp)
            .background(Color.Black.copy(alpha = 0.2f))
    ) {
        val path = Path()
        val width = size.width
        val height = size.height
        val stepX = width / (scores.size - 1).coerceAtLeast(1).toFloat()

        scores.forEachIndexed { i, score ->
            val x = i * stepX
            val y = height - ((score.toFloat() / maxScore) * height)
            val adjustedY = y.coerceAtMost(height - 4f).coerceAtLeast(4f)

            if (i == 0) path.moveTo(x, adjustedY) else path.lineTo(x, adjustedY)
        }
        drawPath(path = path, color = Color(0xFFE65100), style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
fun SessionHistoryCard(
    session: RawSessionEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    val dateString = formatter.format(Date(session.timestamp))
    val dynamicScore = session.calculateRetroactiveScore().toInt()

    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(dateString, style = MaterialTheme.typography.labelMedium)
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("${session.targetMuscle} (${session.bodySide})", style = MaterialTheme.typography.titleMedium)
                    Text("Target: ${session.targetWeight} lbs", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Score: $dynamicScore pts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Work: ${session.mechanicalWork.toInt()} J", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun HistoricalGraphView(session: RawSessionEntity, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val dynamicScore = session.calculateRetroactiveScore().toInt()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Card(modifier = Modifier.padding(8.dp)) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Work", style = MaterialTheme.typography.labelMedium)
                    Text(session.mechanicalWork.toInt().toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Card(modifier = Modifier.padding(8.dp)) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Score", style = MaterialTheme.typography.labelMedium)
                    Text(dynamicScore.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Text("Tension (Cyan) & Magnitude (Magenta)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        RawLineChart(session.timeSeries, session.tensionSeries, session.magnitudeSeries, Color.Cyan, Color.Magenta)

        Text("Inst. Power (Green) & 3s Flux (Yellow)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        RawLineChart(session.timeSeries, session.powerSeries, session.fluxSeries, Color.Green, Color.Yellow)

        Text("Accumulated Mechanical Work", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        RawLineChart(session.timeSeries, session.workSeries, null, Color(0xFFE65100))

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun HistorySideButton(label: String, current: String, onSelect: (String) -> Unit) {
    val isSelected = label == current
    Button(
        onClick = { onSelect(label) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}