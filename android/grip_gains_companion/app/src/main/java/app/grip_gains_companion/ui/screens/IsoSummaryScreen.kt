package app.grip_gains_companion.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.config.AppConstants
import app.grip_gains_companion.database.IsoRepEntity
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsoSummaryScreen(
    reps: List<IsoRepEntity>,
    useLbs: Boolean,
    initialGripper: String = "",
    initialSide: String = "Bilateral",
    recentEquipment: List<String> = emptyList(), // Added for autocomplete
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var targetGripper by remember(initialGripper) { mutableStateOf(initialGripper) }
    var bodySide by remember(initialSide) { mutableStateOf(initialSide) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    BackHandler { showDiscardDialog = true }

    val totalDuration = reps.sumOf { it.duration }
    val firstTargetWeight = reps.firstOrNull { it.targetWeight != null }?.targetWeight

    val targetWeightText = if (firstTargetWeight != null) {
        String.format(java.util.Locale.US, "%.1f", if (useLbs) firstTargetWeight * AppConstants.KG_TO_LBS else firstTargetWeight)
    } else "-"

    val hasScrapedData = initialGripper.isNotBlank()
    val isModified = hasScrapedData && (targetGripper != initialGripper || bodySide != initialSide)

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Session Complete", fontWeight = FontWeight.Bold) }) },
        bottomBar = {
            Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).navigationBarsPadding()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { showDiscardDialog = true }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = { onSave(targetGripper, bodySide) },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = targetGripper.isNotBlank()
                    ) { Text("Save Set", fontWeight = FontWeight.Bold) }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Isometric Overview", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            MetricItem("Reps", "${reps.size}")
                            MetricItem("Duration", "${String.format(java.util.Locale.US, "%.1f", totalDuration)}s")
                            MetricItem("Target", targetWeightText)
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    // FIX: Parent column gets animateContentSize() to stop the snap!
                    Column(modifier = Modifier.padding(24.dp).animateContentSize()) {

                        // --- INLINE AUTOCOMPLETE ---
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = targetGripper,
                                onValueChange = { targetGripper = it },
                                label = { Text("Equipment (e.g. Micro)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    if (targetGripper.isNotEmpty()) {
                                        IconButton(onClick = { targetGripper = ""; focusManager.clearFocus() }) { Icon(Icons.Default.Clear, "Clear") }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                            )

                            val filteredOptions = if (targetGripper.isEmpty()) {
                                emptyList()
                            } else {
                                recentEquipment.filter { it.contains(targetGripper, ignoreCase = true) && !it.equals(targetGripper, ignoreCase = true) }
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
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        cachedOptions.take(4).forEach { equipment ->
                                            ListItem(
                                                headlineContent = { Text(equipment, fontWeight = FontWeight.Bold) },
                                                modifier = Modifier.clickable {
                                                    targetGripper = equipment
                                                    focusManager.clearFocus()
                                                },
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Side", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))

                        // FIX: Transparent segment buttons for clean UI parity
                        val segColors = SegmentedButtonDefaults.colors(
                            inactiveContainerColor = Color.Transparent,
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = bodySide == "Left",
                                onClick = { bodySide = "Left" },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                colors = segColors
                            ) { Text("Left") }
                            SegmentedButton(
                                selected = bodySide == "Bilateral",
                                onClick = { bodySide = "Bilateral" },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                colors = segColors
                            ) { Text("Bilateral") }
                            SegmentedButton(
                                selected = bodySide == "Right",
                                onClick = { bodySide = "Right" },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                colors = segColors
                            ) { Text("Right") }
                        }

                        // --- ANIMATED REVERT BOX ---
                        AnimatedVisibility(
                            visible = isModified,
                            enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
                        ) {
                            Surface(
                                onClick = {
                                    targetGripper = initialGripper
                                    bodySide = initialSide
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
                                        text = "Revert to: $initialGripper ($initialSide)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            itemsIndexed(reps) { index, rep ->
                IsoRepCard(repNum = index + 1, rep = rep, useLbs = useLbs)
            }
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("Discard Session?") },
                text = { Text("Are you sure you want to delete this data? It cannot be recovered.") },
                confirmButton = {
                    Button(onClick = { showDiscardDialog = false; onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Discard")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

// --- GLOBALLY SHARED UI COMPONENTS FOR ISOMETRIC SCREENS ---

@Composable
fun IsoRepCard(repNum: Int, rep: IsoRepEntity, useLbs: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Rep $repNum", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (rep.samples.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("No Tension Data Available", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            } else {
                SummaryForceGraph(
                    samples = rep.samples,
                    useLbs = useLbs,
                    modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = 12.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCol("Duration", "${String.format(java.util.Locale.US, "%.1f", rep.duration)}s")
                StatCol("Median", if (rep.samples.isEmpty()) "-" else formatWeightHistory(rep.median, useLbs))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCol("Mean", if (rep.samples.isEmpty()) "-" else formatWeightHistory(rep.mean, useLbs))
                StatCol("Std Dev", if (rep.samples.isEmpty()) "-" else formatWeightHistory(rep.stdDev, useLbs))
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SummaryForceGraph(samples: List<Double>, useLbs: Boolean, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas

        val processedSamples = if (useLbs) samples.map { it * AppConstants.KG_TO_LBS } else samples

        val maxVal = (processedSamples.maxOrNull() ?: 0.0) * 1.1
        val minVal = 0.0

        val width = size.width
        val height = size.height

        drawLine(axisColor, Offset(0f, height), Offset(width, height), strokeWidth = 2f)
        drawLine(axisColor, Offset(0f, 0f), Offset(0f, height), strokeWidth = 2f)

        if (processedSamples.size > 1 && maxVal > 0) {
            val path = Path()
            processedSamples.forEachIndexed { index, value ->
                val x = (index.toFloat() / (processedSamples.size - 1)) * width
                val y = height - ((value - minVal) / (maxVal - minVal) * height).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = lineColor, style = Stroke(width = 6f))
        }
    }
}

fun formatWeightHistory(kg: Double, useLbs: Boolean, showSign: Boolean = false): String {
    val displayValue = if (useLbs) kg * AppConstants.KG_TO_LBS else kg
    val sign = if (showSign && displayValue > 0) "+" else ""
    return String.format(java.util.Locale.US, "%s%.1f %s", sign, displayValue, if (useLbs) "lbs" else "kg")
}