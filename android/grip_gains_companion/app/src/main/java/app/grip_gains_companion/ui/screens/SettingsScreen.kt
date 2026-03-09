package app.grip_gains_companion.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grip_gains_companion.data.PreferencesRepository
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.model.KinematicsSource
import app.grip_gains_companion.service.ble.BluetoothManager
import app.grip_gains_companion.service.web.WebViewBridge
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import app.grip_gains_companion.ui.components.DataSourceCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesRepository: PreferencesRepository,
    bluetoothManager: BluetoothManager,
    webViewBridge: WebViewBridge,
    activeKinematicsSource: KinematicsSource,
    currentManualWeight: Double,
    onKinematicsChange: (KinematicsSource) -> Unit,
    onWeightChange: (Double) -> Unit,
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit,
    onConnectDevice: () -> Unit,
    onRecalibrate: () -> Unit,
    onViewLogs: () -> Unit,
    onViewHistory: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val useLbs by preferencesRepository.useLbs.collectAsStateWithLifecycle(initialValue = false)
    val expandedForceBar by preferencesRepository.expandedForceBar.collectAsStateWithLifecycle(initialValue = true)
    val showForceGraph by preferencesRepository.showForceGraph.collectAsStateWithLifecycle(initialValue = true)
    val forceGraphWindow by preferencesRepository.forceGraphWindow.collectAsStateWithLifecycle(initialValue = 5)
    val weightTolerance by preferencesRepository.weightTolerance.collectAsStateWithLifecycle(initialValue = 0.5)
    val enableHaptics by preferencesRepository.enableHaptics.collectAsStateWithLifecycle(initialValue = true)
    val enableTargetSound by preferencesRepository.enableTargetSound.collectAsStateWithLifecycle(initialValue = true)
    val enableCalibration by preferencesRepository.enableCalibration.collectAsStateWithLifecycle(initialValue = true)
    val backgroundTimeSync by preferencesRepository.backgroundTimeSync.collectAsStateWithLifecycle(initialValue = true)

    val enableIsotonicMode by preferencesRepository.enableIsotonicMode.collectAsStateWithLifecycle(initialValue = false)

    val autoFailRep by preferencesRepository.autoFailRep.collectAsStateWithLifecycle(initialValue = false)
    val failThreshold by preferencesRepository.failThreshold.collectAsStateWithLifecycle(initialValue = 0.50)
    val enableEndSessionOnEarlyFail by preferencesRepository.enableEndSessionOnEarlyFail.collectAsStateWithLifecycle(initialValue = false)
    val earlyFailThresholdPercent by preferencesRepository.earlyFailThresholdPercent.collectAsStateWithLifecycle(initialValue = 0.50)

    val connectionState by bluetoothManager.connectionState.collectAsStateWithLifecycle()
    val connectedDeviceName by bluetoothManager.connectedDeviceName.collectAsStateWithLifecycle()
    val discoveredDevices by bluetoothManager.discoveredDevices.collectAsStateWithLifecycle()

    val enableAnalytics by preferencesRepository.enableAnalytics.collectAsStateWithLifecycle(initialValue = true)
    val showIsoSummary by preferencesRepository.showIsoSummary.collectAsStateWithLifecycle(initialValue = true)
    val showRawSummary by preferencesRepository.showRawSummary.collectAsStateWithLifecycle(initialValue = true)

    var showTensionSheet by remember { mutableStateOf(false) }
    var showKinematicsSheet by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    var weightInput by remember {
        val initialDisplay = if (useLbs) 20.0 else 20.0
        mutableStateOf(initialDisplay.toInt().toString())
    }

    LaunchedEffect(useLbs) {
        weightInput = "20"
        onWeightChange(if (useLbs) 20.0 / 2.20462 else 20.0)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- TENSION DATA CARD ---
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Tension Data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    DataSourceCard(
                        title = "Scale Connection",
                        icon = Icons.Default.FitnessCenter,
                        activeSource = if (connectionState == ConnectionState.Connected) connectedDeviceName ?: "Bluetooth Scale" else "[No Device]",
                        statusColor = if (connectionState == ConnectionState.Connected) Color.Green else Color.LightGray,
                        onClick = {
                            bluetoothManager.startScanning()
                            showTensionSheet = true
                        }
                    )

                    if (connectionState != ConnectionState.Connected) {
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            label = { Text("Manual Target (${if (useLbs) "lbs" else "kg"})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    weightInput.toDoubleOrNull()?.let { typedVal ->
                                        val weightInKg = if (useLbs) typedVal / 2.20462 else typedVal
                                        onWeightChange(weightInKg)
                                        weightInput = String.format(java.util.Locale.US, "%.1f", typedVal)
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    val unitLabel = if (useLbs) "lbs" else "kg"
                    Text("Target Tolerance: ${String.format(java.util.Locale.US, "%.1f", weightTolerance)} $unitLabel", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = weightTolerance.toFloat(),
                        onValueChange = { coroutineScope.launch { preferencesRepository.setWeightTolerance(it.toDouble()) } },
                        valueRange = 0.5f..5.0f,
                        steps = 8
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Auto-Calibrate on Connect")
                        Switch(checked = enableCalibration, onCheckedChange = { coroutineScope.launch { preferencesRepository.setEnableCalibration(it) } })
                    }
                }
            }

            // --- ISOTONICS CARD ---
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Isotonics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text("Isotonic (RAW) Mode", style = MaterialTheme.typography.bodyLarge)
                                Text("Treat Basic Timer as an Isotonic session with accelerometer data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = enableIsotonicMode,
                                onCheckedChange = { coroutineScope.launch { preferencesRepository.setEnableIsotonicMode(it) } }
                            )
                        }

                        AnimatedVisibility(visible = enableIsotonicMode) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                DataSourceCard(
                                    title = "Kinematics Data",
                                    icon = Icons.Default.Speed,
                                    activeSource = if (activeKinematicsSource == KinematicsSource.PHONE) "Phone Accelerometer" else "M5StickC Plus 2",
                                    statusColor = if (activeKinematicsSource == KinematicsSource.PHONE) Color.Cyan else Color(0xFFE65100),
                                    onClick = { showKinematicsSheet = true }
                                )
                            }
                        }
                    }
                }
            }

            // --- ANALYTICS & HISTORY CARD ---
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Analytics & History", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    // Wrapper Column
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Enable Analytics", style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = enableAnalytics, onCheckedChange = { coroutineScope.launch { preferencesRepository.setEnableAnalytics(it) } })
                        }

                        AnimatedVisibility(visible = enableAnalytics) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Isometric Session Popups")
                                        Switch(checked = showIsoSummary, onCheckedChange = { coroutineScope.launch { preferencesRepository.setShowIsoSummary(it) } })
                                    }
                                    Text("If off, isometric sessions auto-save to History without a summary screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Basic Timer Popups")
                                        Switch(checked = showRawSummary, onCheckedChange = { coroutineScope.launch { preferencesRepository.setShowRawSummary(it) } })
                                    }
                                    Text("If off, nothing will be saved from Basic Timer sessions. A popup is required to input your equipment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // --- DISPLAY & FEEDBACK CARD ---
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Display & Feedback", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Use Imperial Units (lbs)")
                        Switch(checked = useLbs, onCheckedChange = { coroutineScope.launch { preferencesRepository.setUseLbs(it) } })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Expanded Force Bar")
                        Switch(checked = expandedForceBar, onCheckedChange = { coroutineScope.launch { preferencesRepository.setExpandedForceBar(it) } })
                    }

                    // Wrapper Column
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Show Force Graph")
                            Switch(checked = showForceGraph, onCheckedChange = { coroutineScope.launch { preferencesRepository.setShowForceGraph(it) } })
                        }

                        AnimatedVisibility(visible = showForceGraph) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Force Graph Window: ${forceGraphWindow}s", style = MaterialTheme.typography.bodyMedium)
                                Slider(
                                    value = forceGraphWindow.toFloat(),
                                    onValueChange = { coroutineScope.launch { preferencesRepository.setForceGraphWindow(it.toInt()) } },
                                    valueRange = 2f..15f,
                                    steps = 12
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Haptic Feedback")
                        Switch(checked = enableHaptics, onCheckedChange = { coroutineScope.launch { preferencesRepository.setEnableHaptics(it) } })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Target Weight Sounds")
                        Switch(checked = enableTargetSound, onCheckedChange = { coroutineScope.launch { preferencesRepository.setEnableTargetSound(it) } })
                    }
                }
            }

            // --- AUTOMATION CARD ---
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Advanced", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Background Timer Sync")
                        Switch(checked = backgroundTimeSync, onCheckedChange = { coroutineScope.launch { preferencesRepository.setBackgroundTimeSync(it) } })
                    }

                    // Wrapper Column
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Auto-Fail Rep on Tension Drop")
                            Switch(checked = autoFailRep, onCheckedChange = { coroutineScope.launch { preferencesRepository.setAutoFailRep(it) } })
                        }
                        AnimatedVisibility(visible = autoFailRep) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Ends the rep if tension drops below the threshold (only arms after reaching target weight once).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("Drop Threshold: ${(failThreshold * 100).roundToInt()}% of Target", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                                Slider(
                                    value = failThreshold.toFloat(),
                                    onValueChange = { coroutineScope.launch { preferencesRepository.setFailThreshold(it.toDouble()) } },
                                    valueRange = 0.2f..0.9f,
                                    steps = 13
                                )
                            }
                        }
                    }

                    // Wrapper Column
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Abort Fatigued Session Early")
                            Switch(checked = enableEndSessionOnEarlyFail, onCheckedChange = { coroutineScope.launch { preferencesRepository.setEnableEndSessionOnEarlyFail(it) } })
                        }
                        AnimatedVisibility(visible = enableEndSessionOnEarlyFail) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Ends the entire session if rep time falls too far below the target prediction.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("Abort Threshold: ${(earlyFailThresholdPercent * 100).roundToInt()}% of Target Time", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                                Slider(
                                    value = earlyFailThresholdPercent.toFloat(),
                                    onValueChange = { coroutineScope.launch { preferencesRepository.setEarlyFailThresholdPercent(it.toDouble()) } },
                                    valueRange = 0.2f..0.9f,
                                    steps = 13
                                )
                            }
                        }
                    }
                }
            }

            // --- SYSTEM COMMANDS CARD ---
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("System Commands", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    Button(onClick = { webViewBridge.reloadPage(); onDismiss() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh Website")
                    }

                    Button(onClick = { webViewBridge.clearWebsiteData(); onDismiss() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Website Data")
                    }

                    if (connectionState == ConnectionState.Connected) {
                        Button(onClick = onRecalibrate, modifier = Modifier.fillMaxWidth()) {
                            Text("Zero Connected Scale")
                        }
                    }
                }
            }

            // --- RESET BUTTON ---
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showResetConfirmation = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Warning, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset to Defaults")
            }
            Spacer(modifier = Modifier.height(48.dp))
        }

        // Dialogs and BottomSheets remain exactly the same...
        if (showResetConfirmation) {
            AlertDialog(
                onDismissRequest = { showResetConfirmation = false },
                title = { Text("Reset to Defaults") },
                text = { Text("This will restore all settings to their recommended values.") },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch { preferencesRepository.resetToDefaults() }
                            showResetConfirmation = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Reset") }
                },
                dismissButton = { TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") } }
            )
        }

        if (showTensionSheet) {
            ModalBottomSheet(onDismissRequest = { showTensionSheet = false }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Tension Source", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Bluetooth Crane Scales", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (connectionState == ConnectionState.Connected) {
                        TextButton(onClick = { onDisconnect(); showTensionSheet = false }) {
                            Text("Disconnect Current Scale", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (discoveredDevices.isEmpty() && connectionState != ConnectionState.Connected) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                    } else {
                        LazyColumn {
                            items(discoveredDevices) { device ->
                                @SuppressLint("MissingPermission")
                                ListItem(
                                    headlineContent = { Text(device.name ?: "Unknown Device") },
                                    supportingContent = { Text(device.address) },
                                    leadingContent = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                        bluetoothManager.connect(device)
                                        showTensionSheet = false
                                    }.background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (showKinematicsSheet) {
            ModalBottomSheet(onDismissRequest = { showKinematicsSheet = false }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Kinematics Source", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    ListItem(
                        headlineContent = { Text("Phone Accelerometer") },
                        supportingContent = { Text("Uses internal gravity sensors. Requires phone strapped to body.") },
                        leadingContent = { Icon(Icons.Default.Smartphone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            onKinematicsChange(KinematicsSource.PHONE)
                            showKinematicsSheet = false
                        }.background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ListItem(
                        headlineContent = { Text("M5StickC Plus 2") },
                        supportingContent = { Text("External Bluetooth IMU. Straps directly to the body.") },
                        leadingContent = { Icon(Icons.Default.Watch, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            onKinematicsChange(KinematicsSource.M5STICK)
                            showKinematicsSheet = false
                        }.background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}