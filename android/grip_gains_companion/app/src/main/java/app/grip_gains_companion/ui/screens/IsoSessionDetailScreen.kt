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
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val session by sessionRepository.getIsoSessionById(sessionId).collectAsState(initial = null)
    val reps by sessionRepository.getIsoRepsForSession(sessionId).collectAsState(initial = emptyList())
    val allIsoSessions by sessionRepository.getAllIsoSessions().collectAsState(initial = emptyList())
    val recentEquipment = remember(allIsoSessions) { allIsoSessions.map { it.gripperType }.distinct().sorted() }

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
                    IconButton(onClick = {
                        session?.let {
                            editEquipment = it.gripperType
                            editSide = it.side
                            showEditDialog = true
                        }
                    }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }

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
                                String.format("%.1f", if (useLbs) firstTargetWeight * AppConstants.KG_TO_LBS else firstTargetWeight)
                            } else "-"

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                MetricItem("Reps", "${reps.size}")
                                MetricItem("Duration", "${String.format("%.1f", totalDuration)}s")
                                MetricItem("Target", targetWeightText)
                            }
                        }
                    }
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

        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit Isometric Session") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                        // --- EQUIPMENT EDIT ---
                        Column(modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(200))) {
                            OutlinedTextField(
                                value = editEquipment,
                                onValueChange = { editEquipment = it },
                                label = { Text("Equipment (e.g. Micro)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    if (editEquipment.isNotEmpty()) {
                                        IconButton(onClick = { editEquipment = ""; focusManager.clearFocus() }) { Icon(Icons.Default.Clear, "Clear") }
                                    }
                                },
                            )

                            // Revert Equipment Chip
                            val scrapedEquip = session?.scrapedGripper
                            AnimatedVisibility(
                                visible = !scrapedEquip.isNullOrBlank() && scrapedEquip != editEquipment,
                                enter = fadeIn(tween(200)),
                                exit = fadeOut(tween(150))
                            ) {
                                Column {
                                    AssistChip(
                                        onClick = { editEquipment = scrapedEquip ?: ""; focusManager.clearFocus() },
                                        label = { Text("Revert to: $scrapedEquip") },
                                        leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            val filteredOptions = recentEquipment.filter { it.contains(editEquipment, true) && !it.equals(editEquipment, true) }
                            AnimatedVisibility(
                                visible = editEquipment.isNotEmpty() && filteredOptions.isNotEmpty(),
                                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column {
                                        filteredOptions.take(3).forEach { equipment ->
                                            ListItem(
                                                headlineContent = { Text(equipment, fontWeight = FontWeight.Bold) },
                                                modifier = Modifier.clickable { editEquipment = equipment; focusManager.clearFocus() },
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // --- SIDE EDIT ---
                        // Wrap the side section in its own animated content block
                        Column(modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(200))) {
                            Text("Side", style = MaterialTheme.typography.labelMedium)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(selected = editSide == "Left", onClick = { editSide = "Left" }, label = { Text("Left") })
                                FilterChip(selected = editSide == "Bilateral", onClick = { editSide = "Bilateral" }, label = { Text("Bilateral") })
                                FilterChip(selected = editSide == "Right", onClick = { editSide = "Right" }, label = { Text("Right") })
                            }

                            // Revert Side Chip
                            val scrapedS = session?.scrapedSide
                            AnimatedVisibility(
                                visible = !scrapedS.isNullOrBlank() && scrapedS != editSide,
                                enter = fadeIn(tween(200)),
                                exit = fadeOut(tween(150))
                            ) {
                                Column {
                                    AssistChip(
                                        onClick = { editSide = scrapedS ?: "" },
                                        label = { Text("Revert side to: $scrapedS") },
                                        leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        coroutineScope.launch {
                            session?.let {
                                sessionRepository.insertIsoSession(it.copy(gripperType = editEquipment, side = editSide))
                                showEditDialog = false
                            }
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
            )
        }
    }
}