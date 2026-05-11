package app.grip_gains_companion.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grip_gains_companion.config.AppConstants
import app.grip_gains_companion.database.SessionRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsoSessionDetailScreen(
    sessionId: String,
    sessionRepository: SessionRepository,
    useLbs: Boolean,
    recentEquipment: List<String>,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val session by sessionRepository.getIsoSessionById(sessionId).collectAsState(initial = null)
    val reps by sessionRepository.getIsoRepsForSession(sessionId).collectAsState(initial = emptyList())

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editEquipment by remember { mutableStateOf("") }
    var editSide by remember { mutableStateOf("") }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val isIsotonic = session?.isIsotonic == true
    val sessionTitle = if (isIsotonic) "Isotonic Session Details" else "Isometric Session Details"

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(sessionTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (!showEditDialog) {
                        IconButton(onClick = {
                            session?.let {
                                editEquipment = it.gripperType
                                editSide = it.side
                                showEditDialog = true
                            }
                        }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    }

                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Session", tint = MaterialTheme.colorScheme.error)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        if (session == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val totalDuration = reps.sumOf { it.duration }
            val totalIsotonicReps = ceil(totalDuration / 3.0).toInt()
            val firstTargetWeight = reps.firstOrNull { it.targetWeight != null }?.targetWeight
            val formatter = remember { SimpleDateFormat("MMM dd, yyyy - h:mm a", Locale.getDefault()) }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (showEditDialog) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
                                Text("Edit Details", style = MaterialTheme.typography.titleMedium)

                                // --- SMART AUTOCOMPLETE ---
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                    OutlinedTextField(
                                        value = editEquipment,
                                        onValueChange = { editEquipment = it },
                                        label = { Text("Equipment (e.g. Micro)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        trailingIcon = {
                                            if (editEquipment.isNotEmpty()) {
                                                IconButton(onClick = { editEquipment = ""; focusManager.clearFocus() }) { Icon(Icons.Default.Clear, "Clear") }
                                            }
                                        }
                                    )

                                    val filteredOptions = if (editEquipment.isEmpty()) {
                                        emptyList()
                                    } else {
                                        recentEquipment.filter { it.contains(editEquipment, ignoreCase = true) && !it.equals(editEquipment, ignoreCase = true) }
                                    }

                                    var cachedOptions by remember { mutableStateOf(emptyList<String>()) }
                                    LaunchedEffect(filteredOptions) {
                                        if (filteredOptions.isNotEmpty()) {
                                            cachedOptions = filteredOptions
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = filteredOptions.isNotEmpty(),
                                        enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                                        exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
                                    ) {
                                        ElevatedCard(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                cachedOptions.take(5).forEach { eq ->
                                                    ListItem(
                                                        headlineContent = { Text(eq, fontWeight = FontWeight.Bold) },
                                                        modifier = Modifier.clickable {
                                                            editEquipment = eq
                                                            focusManager.clearFocus()
                                                        },
                                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                    Text("Side", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))

                                    val segColors = SegmentedButtonDefaults.colors(
                                        inactiveContainerColor = Color.Transparent,
                                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedButton(
                                            selected = editSide == "Left",
                                            onClick = { editSide = "Left" },
                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                            colors = segColors
                                        ) { Text("Left") }
                                        SegmentedButton(
                                            selected = editSide == "Bilateral",
                                            onClick = { editSide = "Bilateral" },
                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                            colors = segColors
                                        ) { Text("Bilateral") }
                                        SegmentedButton(
                                            selected = editSide == "Right",
                                            onClick = { editSide = "Right" },
                                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                            colors = segColors
                                        ) { Text("Right") }
                                    }
                                }

                                // --- ANIMATED REVERT BOX ---
                                val scrapedEquip = session?.scrapedGripper
                                val scrapedSide = session?.scrapedSide
                                val hasScrapedData = !scrapedEquip.isNullOrBlank()
                                val isDifferentFromScraped = editEquipment != scrapedEquip || editSide != scrapedSide

                                AnimatedVisibility(
                                    visible = hasScrapedData && isDifferentFromScraped,
                                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
                                ) {
                                    Surface(
                                        onClick = {
                                            editEquipment = scrapedEquip ?: ""
                                            editSide = scrapedSide ?: "Bilateral"
                                            focusManager.clearFocus()
                                        },
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = "Revert", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Revert to: $scrapedEquip ($scrapedSide)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = {
                                        coroutineScope.launch {
                                            session?.let {
                                                sessionRepository.insertIsoSession(it.copy(gripperType = editEquipment, side = editSide))
                                                showEditDialog = false
                                            }
                                        }
                                    }) { Text("Save") }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(32.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatter.format(Date(session!!.timestamp)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                Text("${session!!.gripperType} - ${session!!.side}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)

                                Spacer(modifier = Modifier.height(24.dp))

                                val targetWeightText = if (firstTargetWeight != null) {
                                    String.format(Locale.US, "%.1f", if (useLbs) firstTargetWeight * AppConstants.KG_TO_LBS else firstTargetWeight)
                                } else "-"

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    if (isIsotonic) {
                                        MetricItem("Sets", "${reps.size}")
                                        MetricItem("Iso Reps", "$totalIsotonicReps")
                                    } else {
                                        MetricItem("Reps", "${reps.size}")
                                    }
                                    MetricItem("Duration", "${String.format(Locale.US, "%.1f", totalDuration)}s")
                                    MetricItem("Target", targetWeightText)
                                }
                            }
                        }
                    }
                }

                item {
                    val listHeader = if (isIsotonic) "Sets (${reps.size})" else "Reps (${reps.size})"
                    Text(listHeader, style = MaterialTheme.typography.titleMedium)
                }

                itemsIndexed(reps) { index, rep ->
                    IsoRepCard(repNum = index + 1, rep = rep, useLbs = useLbs, isIsotonic = isIsotonic)
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Session?") },
                text = { Text("This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                session?.let { sessionRepository.deleteIsoSession(it) }
                                showDeleteConfirm = false
                                onBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
            )
        }
    }
}

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
                RawMetricItem("TUT", "${String.format(java.util.Locale.US, "%.1f", tut)}s")
                RawMetricItem("Work", "$work")
                if (reps > 1 && !cadence.isNaN() && !cadence.isInfinite()) {
                    RawMetricItem("Cadence", "${String.format(java.util.Locale.US, "%.1f", cadence)}s")
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
                app.grip_gains_companion.ui.components.RepMarkersOverlay(repTimestamps, minT, maxT, modifier = Modifier.fillMaxSize())
                app.grip_gains_companion.ui.components.RawLineChart(timeSeries, tensionSeries, magnitudeSeries, restDurations, pColor, tColor, modifier = Modifier.fillMaxSize())
            }
        }
        if (densitySeries != null && powerSeries != null) {
            SummaryGraphCard("Density & Power", listOf("Density" to eColor, "Power" to sColor)) {
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    app.grip_gains_companion.ui.components.RepMarkersOverlay(repTimestamps, minT, maxT, modifier = Modifier.fillMaxSize())
                    app.grip_gains_companion.ui.components.RawLineChart(timeSeries, densitySeries, powerSeries, restDurations, eColor, sColor, modifier = Modifier.fillMaxSize())
                }
            }
        }
        SummaryGraphCard("Accumulated Mechanical Work", emptyList()) {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                app.grip_gains_companion.ui.components.RawLineChart(timeSeries, workSeries, null, restDurations, pColor, Color.Gray, true, modifier = Modifier.fillMaxSize())
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
private fun SummaryGraphCard(title: String, legends: List<Pair<String, Color>>, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (legends.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    legends.forEach { (name, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
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