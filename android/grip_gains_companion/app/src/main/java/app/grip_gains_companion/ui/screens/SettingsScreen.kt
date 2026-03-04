package app.grip_gains_companion.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grip_gains_companion.config.AppConstants
import app.grip_gains_companion.data.PreferencesRepository
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.service.ble.BluetoothManager
import app.grip_gains_companion.service.web.WebViewBridge
import app.grip_gains_companion.util.WeightFormatter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    // 1. Collect all preferences
    val useLbs by preferencesRepository.useLbs.collectAsStateWithLifecycle(initialValue = false)
    val showStatusBar by preferencesRepository.showStatusBar.collectAsStateWithLifecycle(initialValue = true)
    val expandedForceBar by preferencesRepository.expandedForceBar.collectAsStateWithLifecycle(initialValue = true)
    val showForceGraph by preferencesRepository.showForceGraph.collectAsStateWithLifecycle(initialValue = true)
    val forceGraphWindow by preferencesRepository.forceGraphWindow.collectAsStateWithLifecycle(initialValue = 5)
    val enableTargetWeight by preferencesRepository.enableTargetWeight.collectAsStateWithLifecycle(initialValue = true)
    val useManualTarget by preferencesRepository.useManualTarget.collectAsStateWithLifecycle(initialValue = false)
    val weightTolerance by preferencesRepository.weightTolerance.collectAsStateWithLifecycle(initialValue = 0.5)
    val enableHaptics by preferencesRepository.enableHaptics.collectAsStateWithLifecycle(initialValue = true)
    val enableTargetSound by preferencesRepository.enableTargetSound.collectAsStateWithLifecycle(initialValue = true)
    val enableCalibration by preferencesRepository.enableCalibration.collectAsStateWithLifecycle(initialValue = true)
    val showGripStats by preferencesRepository.showGripStats.collectAsStateWithLifecycle(initialValue = true)
    val showSetReview by preferencesRepository.showSetReview.collectAsStateWithLifecycle(initialValue = false)
    val backgroundTimeSync by preferencesRepository.backgroundTimeSync.collectAsStateWithLifecycle(initialValue = true)
    val enableLiveActivity by preferencesRepository.enableLiveActivity.collectAsStateWithLifecycle(initialValue = true)
    val autoSelectWeight by preferencesRepository.autoSelectWeight.collectAsStateWithLifecycle(initialValue = true)
    val enableEndSessionOnEarlyFail by preferencesRepository.enableEndSessionOnEarlyFail.collectAsStateWithLifecycle(initialValue = false)
    val earlyFailThresholdPercent by preferencesRepository.earlyFailThresholdPercent.collectAsStateWithLifecycle(initialValue = 0.50)

    val connectionState by bluetoothManager.connectionState.collectAsStateWithLifecycle()
    val connectedDeviceName by bluetoothManager.connectedDeviceName.collectAsStateWithLifecycle()
    val discoveredDevices by bluetoothManager.discoveredDevices.collectAsStateWithLifecycle()

    var showTensionSheet by remember { mutableStateOf(false) }
    var showKinematicsSheet by remember { mutableStateOf(false) }
    var localManualWeight by remember { mutableStateOf(currentManualWeight.toString()) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // --- DATA SOURCES ---
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Data Sources", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                DataSourceCard(
                    title = "Tension Data",
                    icon = Icons.Default.FitnessCenter,
                    activeSource = if (connectionState == ConnectionState.Connected) {
                        connectedDeviceName ?: "Bluetooth Scale"
                    } else {
                        "Basic Timer ($localManualWeight)"
                    },
                    statusColor = if (connectionState == ConnectionState.Connected) Color.Green else Color.LightGray,
                    onClick = {
                        bluetoothManager.startScanning()
                        showTensionSheet = true
                    }
                )

                DataSourceCard(
                    title = "Kinematics Data",
                    icon = Icons.Default.Speed,
                    activeSource = if (activeKinematicsSource == KinematicsSource.PHONE) "Phone Accelerometer" else "M5StickC Plus 2",
                    statusColor = if (activeKinematicsSource == KinematicsSource.PHONE) Color.Cyan else Color(0xFFE65100),
                    onClick = { showKinematicsSheet = true }
                )
            }

            HorizontalDivider()

            // --- DISPLAY ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Display & Feedback", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Use Imperial Units (lbs)")
                    Switch(checked = useLbs, onCheckedChange = { coroutineScope.launch { preferencesRepository.setUseLbs(it) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Show Status Bar")
                    Switch(checked = showStatusBar, onCheckedChange = { coroutineScope.launch { preferencesRepository.setShowStatusBar(it) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Expanded Force Bar")
                    Switch(checked = expandedForceBar, onCheckedChange = { coroutineScope.launch { preferencesRepository.setExpandedForceBar(it) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Show Force Graph")
                    Switch(checked = showForceGraph, onCheckedChange = { coroutineScope.launch { preferencesRepository.setShowForceGraph(it) } })
                }

                if (showForceGraph) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Force Graph Window: ${forceGraphWindow}s", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = forceGraphWindow.toFloat(),
                        onValueChange = { coroutineScope.launch { preferencesRepository.setForceGraphWindow(it.toInt()) } },
                        valueRange = 2f..15f,
                        steps = 12
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Haptic Feedback")
                    Switch(checked = enableHaptics, onCheckedChange = { coroutineScope.launch { preferencesRepository.setEnableHaptics(it) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Target Weight Sounds")
                    Switch(checked = enableTargetSound, onCheckedChange = { coroutineScope.launch { preferencesRepository.setEnableTargetSound(it) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Grip Statistics")
                    Switch(checked = showGripStats, onCheckedChange = { coroutineScope.launch { preferencesRepository.setShowGripStats(it) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("End-of-Set Summary")
                    Switch(checked = showSetReview, onCheckedChange = { coroutineScope.launch { preferencesRepository.setShowSetReview(it) } })
                }
            }

            HorizontalDivider()

            // --- BEHAVIOR ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Behavior & Targets", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Enable Target Weight")
                    Switch(checked = enableTargetWeight, onCheckedChange = { coroutineScope.launch { preferencesRepository.setEnableTargetWeight(it) } })
                }

                if (enableTargetWeight) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Use Manual Target Weight")
                        Switch(checked = useManualTarget, onCheckedChange = { coroutineScope.launch { preferencesRepository.setUseManualTarget(it) } })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Auto-set Target Weight")
                        Switch(checked = autoSelectWeight, onCheckedChange = { coroutineScope.launch { preferencesRepository.setAutoSelectWeight(it) } })
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    val unitLabel = if (useLbs) "lbs" else "kg"
                    Text("Target Tolerance: ${String.format("%.1f", weightTolerance)} $unitLabel", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = weightTolerance.toFloat(),
                        onValueChange = { coroutineScope.launch { preferencesRepository.setWeightTolerance(it.toDouble()) } },
                        valueRange = 0.5f..5.0f,
                        steps = 8
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Auto-Calibrate on Connect")
                    Switch(checked = enableCalibration, onCheckedChange = { coroutineScope.launch { preferencesRepository.setEnableCalibration(it) } })
                }
            }

            HorizontalDivider()

            // --- EXPERIMENTAL ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Experimental", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Background Timer Sync")
                    Switch(checked = backgroundTimeSync, onCheckedChange = { coroutineScope.launch { preferencesRepository.setBackgroundTimeSync(it) } })
                }

                if (backgroundTimeSync) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Background Notification")
                        Switch(checked = enableLiveActivity, onCheckedChange = { coroutineScope.launch { preferencesRepository.setEnableLiveActivity(it) } })
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Balrog Avoidance (Early Fail)")
                    Switch(checked = enableEndSessionOnEarlyFail, onCheckedChange = { coroutineScope.launch { preferencesRepository.setEnableEndSessionOnEarlyFail(it) } })
                }

                if (enableEndSessionOnEarlyFail) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Fail Threshold: ${(earlyFailThresholdPercent * 100).roundToInt()}%")
                        Row {
                            IconButton(onClick = {
                                val newVal = (earlyFailThresholdPercent - 0.05).coerceAtLeast(AppConstants.MIN_EARLY_FAIL_THRESHOLD_PERCENT)
                                coroutineScope.launch { preferencesRepository.setEarlyFailThresholdPercent(newVal) }
                            }) { Icon(Icons.Default.Remove, "Decrease") }
                            IconButton(onClick = {
                                val newVal = (earlyFailThresholdPercent + 0.05).coerceAtMost(AppConstants.MAX_EARLY_FAIL_THRESHOLD_PERCENT)
                                coroutineScope.launch { preferencesRepository.setEarlyFailThresholdPercent(newVal) }
                            }) { Icon(Icons.Default.Add, "Increase") }
                        }
                    }
                    Text(
                        text = "Abort session if grip fails before this % of target duration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // --- WEBSITE & HARDWARE COMMANDS ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("System Commands", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

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
                        Text("Zero Progressor Scale")
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
            Spacer(modifier = Modifier.height(32.dp))
        }

        // --- RESET DIALOG ---
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
                dismissButton = {
                    TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") }
                }
            )
        }

        // --- TENSION BOTTOM SHEET ---
        if (showTensionSheet) {
            ModalBottomSheet(onDismissRequest = { showTensionSheet = false }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Tension Source", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Basic Web Timer (Manual Weight)", style = MaterialTheme.typography.titleMedium)
                            }
                            OutlinedTextField(
                                value = localManualWeight,
                                onValueChange = {
                                    localManualWeight = it
                                    it.toDoubleOrNull()?.let { weight -> onWeightChange(weight) }
                                },
                                label = { Text("Target Weight") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Bluetooth Crane Scales", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (connectionState == ConnectionState.Connected) {
                            TextButton(onClick = { onDisconnect(); showTensionSheet = false }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
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
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            bluetoothManager.connect(device)
                                            showTensionSheet = false
                                        }
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        // --- KINEMATICS BOTTOM SHEET ---
        if (showKinematicsSheet) {
            ModalBottomSheet(onDismissRequest = { showKinematicsSheet = false }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Kinematics Source", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))

                    ListItem(
                        headlineContent = { Text("Phone Accelerometer") },
                        supportingContent = { Text("Uses internal gravity sensors. Requires phone strapped to body.") },
                        leadingContent = { Icon(Icons.Default.Smartphone, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onKinematicsChange(KinematicsSource.PHONE)
                                showKinematicsSheet = false
                            }
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ListItem(
                        headlineContent = { Text("M5StickC Plus 2") },
                        supportingContent = { Text("External Bluetooth IMU. Straps directly to the body.") },
                        leadingContent = { Icon(Icons.Default.Watch, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onKinematicsChange(KinematicsSource.M5STICK)
                                showKinematicsSheet = false
                            }
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}