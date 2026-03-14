package app.grip_gains_companion.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.database.RawSessionEntity
import app.grip_gains_companion.database.SessionRepository
import app.grip_gains_companion.database.SessionType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class HistoryFilter { ALL, RAW, ISOMETRIC }
enum class SideFilter { ALL, LEFT, BILATERAL, RIGHT }
enum class SortOrder { NEWEST, OLDEST }

data class HistoryItem(
    val id: String,
    val timestamp: Long,
    val type: SessionType,
    val label: String,
    val side: String,
    val score: Double
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    sessionRepository: SessionRepository,
    onBack: () -> Unit,
    enableIsotonicMode: Boolean,
    onViewSession: (String, SessionType) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedMuscleFilter by remember { mutableStateOf<String?>(null) }

    // Core Filter States
    var selectedSideFilter by remember { mutableStateOf(SideFilter.ALL) }
    var selectedTypeFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var sortOrder by remember { mutableStateOf(SortOrder.NEWEST) }

    // UI Dropdown States
    var showTypeDropdown by remember { mutableStateOf(false) }
    var showSideDropdown by remember { mutableStateOf(false) }

    // Date Range Picker States
    var showDatePicker by remember { mutableStateOf(false) }
    var dateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val datePickerState = rememberDateRangePickerState()

    // --- MULTI-SELECT STATES ---
    val selectedSessionIds = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedSessionIds.isNotEmpty()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val rawSessions by sessionRepository.getAllRawSessions().collectAsState(initial = emptyList())
    val isoSessions by sessionRepository.getAllIsoSessions().collectAsState(initial = emptyList())

    val allEquipment = remember(rawSessions, isoSessions) {
        (rawSessions.map { it.targetMuscle } + isoSessions.map { it.gripperType })
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    val csv = generateRawCsvContent(rawSessions)
                    os.write(csv.toByteArray())
                }
                Toast.makeText(context, "Export Saved Successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filteredItems = remember(rawSessions, isoSessions, selectedMuscleFilter, selectedSideFilter, selectedTypeFilter, searchQuery, sortOrder, dateRange) {
        val mappedRaw = rawSessions.map { HistoryItem("RAW_${it.id}", it.timestamp, SessionType.ISOTONIC, it.targetMuscle, it.bodySide, it.workoutScore) }
        val mappedIso = isoSessions.map { HistoryItem("ISO_${it.id}", it.timestamp, SessionType.ISOMETRIC, it.gripperType, it.side, 0.0) }

        val items = (mappedRaw + mappedIso).filter { item ->
            val typeMatch = when (selectedTypeFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.RAW -> item.type == SessionType.ISOTONIC
                HistoryFilter.ISOMETRIC -> item.type == SessionType.ISOMETRIC
            }
            val sideMatch = when (selectedSideFilter) {
                SideFilter.ALL -> true
                else -> item.side.equals(selectedSideFilter.name, ignoreCase = true)
            }
            val muscleMatch = selectedMuscleFilter == null || item.label.equals(selectedMuscleFilter, ignoreCase = true)
            val searchMatch = item.label.contains(searchQuery, ignoreCase = true)

            val timeMatch = if (dateRange != null) {
                item.timestamp in dateRange!!.first..(dateRange!!.second + 86400000L) // Add 24 hours to include the end day
            } else true

            typeMatch && sideMatch && muscleMatch && searchMatch && timeMatch
        }

        if (sortOrder == SortOrder.NEWEST) items.sortedByDescending { it.timestamp } else items.sortedBy { it.timestamp }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedSessionIds.size} Selected", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Session History", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedSessionIds.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { createDocumentLauncher.launch("GripGains_Export.csv") }) {
                            Icon(Icons.Default.Download, contentDescription = "Export CSV")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = if (isSelectionMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SEARCH BAR WITH INLINE AUTOCOMPLETE ---
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (it.isEmpty()) selectedMuscleFilter = null
                            },
                            label = { Text("Filter muscle or equipment") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            trailingIcon = {
                                if (selectedMuscleFilter != null || searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        selectedMuscleFilter = null
                                        searchQuery = ""
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )

                        val filteredOptions = if (searchQuery.isEmpty()) {
                            emptyList()
                        } else {
                            allEquipment.filter { it.contains(searchQuery, ignoreCase = true) && !it.equals(selectedMuscleFilter, ignoreCase = true) }
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
                                    filteredOptions.take(5).forEach { equipment ->
                                        ListItem(
                                            headlineContent = { Text(equipment, fontWeight = FontWeight.Bold) },
                                            modifier = Modifier.clickable {
                                                selectedMuscleFilter = equipment
                                                searchQuery = equipment
                                                focusManager.clearFocus()
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- CONSOLIDATED STICKY FILTERS ---
            stickyHeader {
                Surface(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Sort Order
                        ElevatedFilterChip(
                            selected = sortOrder == SortOrder.OLDEST,
                            onClick = { sortOrder = if (sortOrder == SortOrder.NEWEST) SortOrder.OLDEST else SortOrder.NEWEST },
                            label = { Text(if (sortOrder == SortOrder.NEWEST) "Newest" else "Oldest") },
                            leadingIcon = { Icon(if (sortOrder == SortOrder.NEWEST) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )

                        // 2. Date Range
                        val shortDateFmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
                        ElevatedFilterChip(
                            selected = dateRange != null,
                            onClick = { showDatePicker = true },
                            label = {
                                if (dateRange != null) {
                                    Text("${shortDateFmt.format(Date(dateRange!!.first))} - ${shortDateFmt.format(Date(dateRange!!.second))}")
                                } else {
                                    Text("All Time")
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                if (dateRange != null) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear Date",
                                        modifier = Modifier.size(16.dp).clickable {
                                            dateRange = null
                                            datePickerState.setSelection(null, null)
                                        }
                                    )
                                }
                            }
                        )

                        // 3. Type Filter
                        Box {
                            val isActive = selectedTypeFilter != HistoryFilter.ALL
                            val typeName = if (selectedTypeFilter == HistoryFilter.RAW) "RAW" else "Isometric"
                            ElevatedFilterChip(
                                selected = isActive,
                                onClick = { showTypeDropdown = true },
                                label = { Text(if (isActive) "Type: $typeName" else "Type") },
                                trailingIcon = {
                                    if (isActive) {
                                        Icon(Icons.Default.Clear, null, Modifier.size(16.dp).clickable { selectedTypeFilter = HistoryFilter.ALL })
                                    } else {
                                        Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                                    }
                                }
                            )
                            DropdownMenu(expanded = showTypeDropdown, onDismissRequest = { showTypeDropdown = false }) {
                                DropdownMenuItem(text = { Text("RAW") }, onClick = { selectedTypeFilter = HistoryFilter.RAW; showTypeDropdown = false })
                                DropdownMenuItem(text = { Text("Isometric") }, onClick = { selectedTypeFilter = HistoryFilter.ISOMETRIC; showTypeDropdown = false })
                            }
                        }

                        // 4. Side Filter
                        Box {
                            val isActive = selectedSideFilter != SideFilter.ALL
                            val sideName = selectedSideFilter.name.lowercase().replaceFirstChar { it.uppercase() }
                            ElevatedFilterChip(
                                selected = isActive,
                                onClick = { showSideDropdown = true },
                                label = { Text(if (isActive) "Side: $sideName" else "Side") },
                                trailingIcon = {
                                    if (isActive) {
                                        Icon(Icons.Default.Clear, null, Modifier.size(16.dp).clickable { selectedSideFilter = SideFilter.ALL })
                                    } else {
                                        Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                                    }
                                }
                            )
                            DropdownMenu(expanded = showSideDropdown, onDismissRequest = { showSideDropdown = false }) {
                                DropdownMenuItem(text = { Text("Left") }, onClick = { selectedSideFilter = SideFilter.LEFT; showSideDropdown = false })
                                DropdownMenuItem(text = { Text("Right") }, onClick = { selectedSideFilter = SideFilter.RIGHT; showSideDropdown = false })
                                DropdownMenuItem(text = { Text("Bilateral") }, onClick = { selectedSideFilter = SideFilter.BILATERAL; showSideDropdown = false })
                            }
                        }
                    }
                }
            }

            // --- SCORE GRAPH HERO CARD ---
            item {
                val graphData = filteredItems.filter { it.type == SessionType.ISOTONIC }
                val showGraph = selectedMuscleFilter != null && graphData.isNotEmpty()

                AnimatedVisibility(
                    visible = showGraph,
                    enter = expandVertically(animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)),
                    exit = shrinkVertically(animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                val safeTitle = selectedMuscleFilter ?: "None"
                                Text(
                                    text = "Workout Scores: $safeTitle",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                if (selectedSideFilter == SideFilter.ALL) {
                                    val hasLeft = graphData.any { it.side.equals("left", ignoreCase = true) }
                                    val hasRight = graphData.any { it.side.equals("right", ignoreCase = true) }
                                    val hasBi = graphData.any { it.side.equals("bilateral", ignoreCase = true) }

                                    if (hasLeft) {
                                        Spacer(Modifier.width(12.dp))
                                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                        Spacer(Modifier.width(4.dp))
                                        Text("L", style = MaterialTheme.typography.labelMedium)
                                    }
                                    if (hasRight) {
                                        Spacer(Modifier.width(12.dp))
                                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                                        Spacer(Modifier.width(4.dp))
                                        Text("R", style = MaterialTheme.typography.labelMedium)
                                    }
                                    if (hasBi) {
                                        Spacer(Modifier.width(12.dp))
                                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                                        Spacer(Modifier.width(4.dp))
                                        Text("B", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    Spacer(Modifier.width(4.dp))
                                    Text("(${selectedSideFilter.name.lowercase().replaceFirstChar { it.uppercase() }})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            SimpleScoreLineChart(graphData)
                        }
                    }
                }
            }

            // --- LIST ITEMS ---
            items(filteredItems, key = { it.id }) { item ->
                val isSelected = selectedSessionIds.contains(item.id)

                HistoryCard(
                    item = item,
                    isSelected = isSelected,
                    onClick = {
                        if (isSelectionMode) {
                            if (isSelected) selectedSessionIds.remove(item.id) else selectedSessionIds.add(item.id)
                        } else {
                            onViewSession(item.id, item.type)
                        }
                    },
                    onLongClick = {
                        if (!isSelectionMode) {
                            selectedSessionIds.add(item.id)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem()
                )
            }
        }

        // --- DIALOGS ---

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Delete Sessions") },
                text = { Text("Are you sure you want to permanently delete ${selectedSessionIds.size} session(s)?") },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                selectedSessionIds.forEach { id ->
                                    if (id.startsWith("RAW_")) {
                                        val rawId = id.removePrefix("RAW_").toLongOrNull()
                                        // Update this method call if your Repository uses a different name
                                        rawId?.let { sessionRepository.deleteRawSessionById(it) }
                                    } else if (id.startsWith("ISO_")) {
                                        val isoId = id.removePrefix("ISO_")
                                        // Update this method call if your Repository uses a different name
                                        sessionRepository.deleteIsoSessionById(isoId)
                                    }
                                }
                                selectedSessionIds.clear()
                                showDeleteConfirmDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        dateRange = if (datePickerState.selectedStartDateMillis != null && datePickerState.selectedEndDateMillis != null) {
                            Pair(datePickerState.selectedStartDateMillis!!, datePickerState.selectedEndDateMillis!!)
                        } else null
                        showDatePicker = false
                    }) { Text("Apply Filter") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        datePickerState.setSelection(null, null)
                        dateRange = null
                        showDatePicker = false
                    }) { Text("Clear") }
                }
            ) {
                DateRangePicker(
                    state = datePickerState,
                    title = { Text("Select date range", modifier = Modifier.padding(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SimpleScoreLineChart(data: List<HistoryItem>) {
    if (data.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val sortedData = data.sortedBy { it.timestamp }
    val minTime = sortedData.first().timestamp
    val maxTime = sortedData.last().timestamp
    val timeRange = (maxTime - minTime).coerceAtLeast(1L).toFloat()

    val rawMin = sortedData.minOf { it.score.toFloat() }
    val rawMax = sortedData.maxOf { it.score.toFloat() }

    val minScore = if (rawMin == rawMax) (rawMin - 10f).coerceAtLeast(0f) else rawMin
    val maxScore = if (rawMin == rawMax) rawMax + 10f else rawMax
    val scoreRange = (maxScore - minScore).coerceAtLeast(1f)

    val formatter = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }
    val startDate = formatter.format(Date(minTime))
    val endDate = formatter.format(Date(maxTime))

    Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 36.dp, end = 8.dp, top = 16.dp, bottom = 24.dp)
        ) {
            val width = size.width
            val height = size.height

            val groups = sortedData.groupBy { it.side.lowercase() }

            groups.forEach { (side, items) ->
                val color = when (side) {
                    "left" -> primaryColor
                    "right" -> secondaryColor
                    "bilateral" -> tertiaryColor
                    else -> Color.Gray
                }

                if (items.size > 1) {
                    val path = Path().apply {
                        items.forEachIndexed { index, item ->
                            val x = if (timeRange == 0f) width / 2f else width * ((item.timestamp - minTime).toFloat() / timeRange)
                            val y = height - ((item.score.toFloat() - minScore) / scoreRange * height)
                            if (index == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    drawPath(path, color, style = Stroke(width = 4.dp.toPx()))
                }

                items.forEach { item ->
                    val x = if (timeRange == 0f) width / 2f else width * ((item.timestamp - minTime).toFloat() / timeRange)
                    val y = height - ((item.score.toFloat() - minScore) / scoreRange * height)
                    drawCircle(color = color, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                }
            }
        }

        Text(text = maxScore.toInt().toString(), style = MaterialTheme.typography.labelMedium, color = labelColor, modifier = Modifier.align(Alignment.TopStart).padding(top = 8.dp))
        Text(text = minScore.toInt().toString(), style = MaterialTheme.typography.labelMedium, color = labelColor, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 24.dp))
        Text(text = startDate, style = MaterialTheme.typography.labelMedium, color = labelColor, modifier = Modifier.align(Alignment.BottomStart).padding(start = 36.dp))
        Text(text = endDate, style = MaterialTheme.typography.labelMedium, color = labelColor, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryCard(
    item: HistoryItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault()) }
    val isRaw = item.type == SessionType.ISOTONIC
    val sideShort = item.side.take(1).uppercase()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = formatter.format(Date(item.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(text = sideShort, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Surface(color = if (isRaw) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(text = if (isRaw) "RAW" else "Isometric", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = if (isRaw) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}

fun generateRawCsvContent(sessions: List<RawSessionEntity>): String {
    val header = "Timestamp,Date,Muscle,Side,Score,Work,TUT,Reps\n"
    val csv = StringBuilder(header)
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    sessions.forEach { s ->
        csv.append("${s.timestamp},${fmt.format(Date(s.timestamp))},${s.targetMuscle},${s.bodySide},${s.workoutScore},${s.mechanicalWork},${s.durationSeconds},${s.repTimestamps.size}\n")
    }
    return csv.toString()
}