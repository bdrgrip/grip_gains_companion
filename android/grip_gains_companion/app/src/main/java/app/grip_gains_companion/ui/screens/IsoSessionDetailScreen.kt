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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Isometric Session Details", fontWeight = FontWeight.Bold) },
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

                                    // FIX: Make the inactive buttons transparent so they don't look jet-black
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
                                        // Top padding inside the visibility block shrinks seamlessly
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
                                    MetricItem("Reps", "${reps.size}")
                                    MetricItem("Duration", "${String.format(Locale.US, "%.1f", totalDuration)}s")
                                    MetricItem("Target", targetWeightText)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Reps (${reps.size})", style = MaterialTheme.typography.titleMedium)
                }

                itemsIndexed(reps) { index, rep ->
                    IsoRepCard(repNum = index + 1, rep = rep, useLbs = useLbs)
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