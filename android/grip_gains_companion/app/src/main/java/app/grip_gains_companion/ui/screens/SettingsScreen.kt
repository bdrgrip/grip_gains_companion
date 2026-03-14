package app.grip_gains_companion.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
    m5ConnectionState: ConnectionState,
    m5Data: Triple<Float, Float, Float>,
    onKinematicsChange: (KinematicsSource) -> Unit,
    onWeightChange: (Double) -> Unit,
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit,
    onConnectDevice: () -> Unit,
    onRecalibrate: () -> Unit,
    onViewLogs: () -> Unit,
    onViewHistory: () -> Unit
) {
    // --- INITIALIZE CONTEXT BEFORE CALLING SYSTEM SERVICES ---
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val btManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? AndroidBluetoothManager }
    val btAdapter = btManager?.adapter
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    val useLbs by preferencesRepository.useLbs.collectAsStateWithLifecycle(initialValue = false)
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
                        statusColor = if (connectionState == ConnectionState.Connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        onClick = {
                            val isBtOn = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter?.isEnabled == true
                            if (isBtOn) {
                                bluetoothManager.startScanning()
                                showTensionSheet = true
                            } else {
                                enableBluetoothLauncher.launch(android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }
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
                                    statusColor = if (activeKinematicsSource == KinematicsSource.PHONE) {
                                        MaterialTheme.colorScheme.primary
                                    } else if (m5ConnectionState == ConnectionState.Connected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    onClick = {
                                        val isBtOn = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter?.isEnabled == true
                                        if (isBtOn) {
                                            showKinematicsSheet = true
                                        } else {
                                            enableBluetoothLauncher.launch(android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                        }
                                    }
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

        // --- DIALOGS ---
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
            AlertDialog(
                onDismissRequest = { showTensionSheet = false },
                shape = RoundedCornerShape(28.dp),
                title = { Text("Select Tension Source", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        if (connectionState == ConnectionState.Connected) {
                            TextButton(
                                onClick = { bluetoothManager.disconnect(); showTensionSheet = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Disconnect Current Scale", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (discoveredDevices.isEmpty() && connectionState != ConnectionState.Connected) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(discoveredDevices) { device ->
                                    Surface(
                                        onClick = {
                                            bluetoothManager.connect(device)
                                            showTensionSheet = false
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        @SuppressLint("MissingPermission")
                                        ListItem(
                                            headlineContent = { Text(device.name ?: "Unknown Device", fontWeight = FontWeight.Bold) },
                                            supportingContent = { Text(device.address) },
                                            leadingContent = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTensionSheet = false }) { Text("Close") }
                }
            )
        }

        if (showKinematicsSheet) {
            AlertDialog(
                onDismissRequest = { showKinematicsSheet = false },
                shape = RoundedCornerShape(28.dp),
                title = { Text("Select Kinematics Source", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                        // 1. Phone Accelerometer Card
                        Surface(
                            onClick = {
                                onKinematicsChange(KinematicsSource.PHONE)
                                showKinematicsSheet = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = { Text("Phone Accelerometer", fontWeight = FontWeight.Bold) },
                                supportingContent = { Text("Uses internal gravity sensors.") },
                                leadingContent = { Icon(Icons.Default.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }

                        // 2. M5Stick Card with Live Status
                        Surface(
                            onClick = {
                                onKinematicsChange(KinematicsSource.M5STICK)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (activeKinematicsSource == KinematicsSource.M5STICK) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = { Text("M5StickC Plus 2", fontWeight = FontWeight.Bold) },
                                supportingContent = {
                                    Column {
                                        Text("External Bluetooth IMU.")
                                        if (activeKinematicsSource == KinematicsSource.M5STICK) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // THE MATERIAL YOU DOT
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(RoundedCornerShape(50)) // Circle
                                                        .background(if (m5ConnectionState == ConnectionState.Connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                if (m5ConnectionState == ConnectionState.Connected) {
                                                    Text(
                                                        "Live: X: ${String.format(java.util.Locale.US, "%.1f", m5Data.first)} Y: ${String.format(java.util.Locale.US, "%.1f", m5Data.second)} Z: ${String.format(java.util.Locale.US, "%.1f", m5Data.third)}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                } else {
                                                    Text("Disconnected / Scanning...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                },
                                leadingContent = { Icon(Icons.Default.Watch, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    headlineColor = if (activeKinematicsSource == KinematicsSource.M5STICK) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    supportingColor = if (activeKinematicsSource == KinematicsSource.M5STICK) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(8.dp))

                        val context = LocalContext.current
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

                        OutlinedButton(
                            onClick = {
                                val clip = android.content.ClipData.newPlainText("M5 Firmware", M5_FIRMWARE_CODE)
                                clipboardManager.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Firmware copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Firmware (C++)")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showKinematicsSheet = false }) { Text("Close") }
                }
            )
        }
    }
}

// Ensure the massive firmware string is kept at the bottom as usual!
// Ensure the massive firmware string is kept at the bottom as usual!
private const val M5_FIRMWARE_CODE = """#include <M5Unified.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <math.h>

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

struct AccelData {
  float x;
  float y;
  float z;
};

unsigned long lastUpdate = 0;
const int updateInterval = 20; 
const float dt = 0.02f;
unsigned long lastBatteryUpdate = 0;

float pitch = 0.0f;
float roll = 0.0f;

void updateScreenStatus() {
  M5.Display.fillScreen(deviceConnected ? TFT_GREEN : TFT_RED);
  M5.Display.setTextSize(2);
  M5.Display.setTextColor(TFT_WHITE, deviceConnected ? TFT_GREEN : TFT_RED);
  M5.Display.setCursor(10, 20);
  M5.Display.println(deviceConnected ? "CONNECTED" : "PAIRING..");
  
  int bat = M5.Power.getBatteryLevel();
  M5.Display.setTextSize(1.5);
  M5.Display.setCursor(10, 80);
  if (bat < 0) M5.Display.println("Bat: CHG");
  else M5.Display.printf("Bat: %d%%", bat);
  
  // BATTERY FIX: Dim the screen massively when connected to save power!
  M5.Display.setBrightness(deviceConnected ? 30 : 150);
}

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      updateScreenStatus();
    }
    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      updateScreenStatus();
      pServer->startAdvertising(); 
    }
};

void setup() {
  auto cfg = M5.config();
  M5.begin(cfg);
  
  M5.Display.fillScreen(TFT_BLUE);
  M5.Display.setTextColor(TFT_WHITE, TFT_BLUE);
  M5.Display.setTextSize(1.5);
  M5.Display.setCursor(10, 40);
  M5.Display.println("BOOTING BLE...");

  BLEDevice::init("RAW_IMU");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);
  pCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  pCharacteristic->addDescriptor(new BLE2902());
  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x0C); 
  pAdvertising->setMaxPreferred(0x18); 
  BLEDevice::startAdvertising();

  updateScreenStatus();
}

void loop() {
  M5.update();
  unsigned long now = millis();
  
  if (now - lastUpdate >= updateInterval) {
    lastUpdate = now;
    
    if (deviceConnected) {
      float ax, ay, az;
      float gx, gy, gz;
      
      M5.Imu.getAccel(&ax, &ay, &az);
      M5.Imu.getGyro(&gx, &gy, &gz);
      
      float accelPitch = atan2(ay, az) * 180.0f / M_PI;
      float accelRoll  = atan2(-ax, sqrt(ay * ay + az * az)) * 180.0f / M_PI;
      
      pitch = 0.98f * (pitch + gx * dt) + 0.02f * accelPitch;
      roll  = 0.98f * (roll  + gy * dt) + 0.02f * accelRoll;
      
      float pitchRad = pitch * M_PI / 180.0f;
      float rollRad  = roll  * M_PI / 180.0f;
      
      float gravX = -sin(rollRad);
      float gravY = sin(pitchRad);
      float gravZ = cos(pitchRad) * cos(rollRad);
      
      float finalX = (ax - gravX) * 9.80665f;
      float finalY = (ay - gravY) * 9.80665f;
      float finalZ = (az - gravZ) * 9.80665f;
      
      // THE FIX: HARDWARE NOISE DEADZONE
      // If the movement is less than 0.6 m/s^2, it is just table vibration. Clamp to 0.
      float noiseFloor = 0.6f;
      if (abs(finalX) < noiseFloor) finalX = 0.0f;
      if (abs(finalY) < noiseFloor) finalY = 0.0f;
      if (abs(finalZ) < noiseFloor) finalZ = 0.0f;
      
      AccelData data;
      data.x = finalX;
      data.y = finalY;
      data.z = finalZ;
      
      pCharacteristic->setValue((uint8_t*)&data, sizeof(AccelData));
      pCharacteristic->notify();
    }
  }
  
  if (now - lastBatteryUpdate >= 5000) {
    lastBatteryUpdate = now;
    updateScreenStatus();
  }
}"""