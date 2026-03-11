package app.grip_gains_companion.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*

import androidx.compose.runtime.* import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.database.RawSessionRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    rawRepository: RawSessionRepository,
    recentMuscles: List<String>,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val session by rawRepository.getSessionById(sessionId).collectAsState(initial = null)

    val coroutineScope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editMuscle by remember { mutableStateOf("") }
    var editSide by remember { mutableStateOf("") }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("RAW Session Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        session?.let {
                            editMuscle = it.targetMuscle
                            editSide = it.bodySide
                            showEditDialog = true
                        }
                    }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }

                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        if (session != null) {
            val s = session!!
            val tList = s.timeSeries

            if (tList.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.targetMuscle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(s.bodySide, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        val dateText = SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(s.timestamp))
                        Text(dateText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        RawDashboardCard(
                            score = s.workoutScore.toInt(), tut = s.durationSeconds, work = s.mechanicalWork.toInt(),
                            reps = s.repTimestamps.size, cadence = s.averageRepInterval
                        )
                    }
                }

                item {
                    RawGraphStack(
                        timeSeries = tList, tensionSeries = s.tensionSeries, magnitudeSeries = s.magnitudeSeries,
                        densitySeries = s.densitySeries, powerSeries = s.powerSeries, workSeries = s.workSeries,
                        restDurations = s.restDurations, repTimestamps = s.repTimestamps
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }

            // DIALOGS
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete Session") },
                    text = { Text("Are you sure? This cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    rawRepository.delete(s)
                                    showDeleteDialog = false
                                    onBack()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
                )
            }

            if (showEditDialog) {
                AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    title = { Text("Edit RAW Session") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // --- INLINE AUTOCOMPLETE ---
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = editMuscle,
                                    onValueChange = { editMuscle = it },
                                    label = { Text("Target Muscle") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        if (editMuscle.isNotEmpty()) {
                                            IconButton(onClick = { editMuscle = ""; focusManager.clearFocus() }) { Icon(Icons.Default.Clear, "Clear") }
                                        }
                                    }
                                )

                                val filteredOptions = if (editMuscle.isEmpty()) {
                                    emptyList()
                                } else {
                                    recentMuscles.filter { it.contains(editMuscle, ignoreCase = true) && !it.equals(editMuscle, ignoreCase = true) }
                                }

                                AnimatedVisibility(
                                    visible = filteredOptions.isNotEmpty(),
                                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
                                ) {
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            filteredOptions.take(5).forEach { muscle ->
                                                ListItem(
                                                    headlineContent = { Text(muscle, fontWeight = FontWeight.Bold) },
                                                    modifier = Modifier.clickable {
                                                        editMuscle = muscle
                                                        focusManager.clearFocus()
                                                    },
                                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Side", style = MaterialTheme.typography.labelMedium)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(selected = editSide == "Left", onClick = { editSide = "Left" }, label = { Text("Left") })
                                FilterChip(selected = editSide == "Bilateral", onClick = { editSide = "Bilateral" }, label = { Text("Bilateral") })
                                FilterChip(selected = editSide == "Right", onClick = { editSide = "Right" }, label = { Text("Right") })
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            coroutineScope.launch {
                                rawRepository.update(s.copy(targetMuscle = editMuscle, bodySide = editSide))
                                showEditDialog = false
                            }
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
                )
            }
        }
    }
}