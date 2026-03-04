package app.grip_gains_companion.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grip_gains_companion.data.PreferencesRepository
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.model.KinematicsSource
import app.grip_gains_companion.model.TensionSource
import app.grip_gains_companion.service.ble.BluetoothManager
import app.grip_gains_companion.ui.components.DataSourceCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupDashboardScreen(
    preferencesRepository: PreferencesRepository,
    bluetoothManager: BluetoothManager,
    onStartTraining: (TensionSource, Double, KinematicsSource) -> Unit
) {
    val connectionState by bluetoothManager.connectionState.collectAsStateWithLifecycle()
    val connectedDeviceName by bluetoothManager.connectedDeviceName.collectAsStateWithLifecycle()
    val discoveredDevices by bluetoothManager.discoveredDevices.collectAsStateWithLifecycle()
    val useLbs by preferencesRepository.useLbs.collectAsStateWithLifecycle(initialValue = false)

    // Dashboard State
    var tensionSource by remember { mutableStateOf(TensionSource.MANUAL) }
    var kinematicsSource by remember { mutableStateOf(KinematicsSource.PHONE) }
    var manualWeight by remember { mutableStateOf("20.0") }

    // Bottom Sheet States
    var showTensionSheet by remember { mutableStateOf(false) }
    var showKinematicsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.Connected) {
            tensionSource = TensionSource.BLE_SCALE
            showTensionSheet = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    val weight = manualWeight.toDoubleOrNull() ?: 20.0
                    onStartTraining(tensionSource, weight, kinematicsSource)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Text("Open Grip Gains", style = MaterialTheme.typography.titleMedium)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // TENSION DATA SOURCE CARD
            DataSourceCard(
                title = "Tension Data",
                icon = Icons.Default.FitnessCenter,
                activeSource = if (connectionState == ConnectionState.Connected) {
                    connectedDeviceName ?: "Bluetooth Scale"
                } else {
                    "[No Device]"
                },
                statusColor = if (connectionState == ConnectionState.Connected) Color.Green else Color.LightGray,
                onClick = {
                    bluetoothManager.startScanning()
                    showTensionSheet = true
                }
            )

            // MANUAL WEIGHT INPUT: Shows only if No Device is connected
            if (connectionState != ConnectionState.Connected) {
                OutlinedTextField(
                    value = manualWeight,
                    onValueChange = { manualWeight = it },
                    label = { Text("Manual Weight Target (${if (useLbs) "lbs" else "kg"})") },
                    placeholder = { Text("20.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // KINEMATICS DATA SOURCE CARD
            DataSourceCard(
                title = "Kinematics Data",
                icon = Icons.Default.Speed,
                activeSource = if (kinematicsSource == KinematicsSource.PHONE) "Phone Accelerometer" else "M5StickC Plus 2",
                statusColor = if (kinematicsSource == KinematicsSource.PHONE) Color.Cyan else Color(0xFFE65100),
                onClick = { showKinematicsSheet = true }
            )
        }
    }

    // --- TENSION BOTTOM SHEET ---
    if (showTensionSheet) {
        ModalBottomSheet(onDismissRequest = { showTensionSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Tension Source", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Bluetooth Crane Scales", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (connectionState == ConnectionState.Connected) {
                    TextButton(onClick = { bluetoothManager.disconnect(); tensionSource = TensionSource.MANUAL }) {
                        Text("Disconnect Current Device", color = MaterialTheme.colorScheme.error)
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
                                modifier = Modifier.clickable { bluetoothManager.connect(device) }
                            )
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
                    supportingContent = { Text("Uses internal gravity sensors.") },
                    leadingContent = { Icon(Icons.Default.Smartphone, contentDescription = null) },
                    modifier = Modifier.clickable {
                        kinematicsSource = KinematicsSource.PHONE
                        showKinematicsSheet = false
                    }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("M5StickC Plus 2") },
                    supportingContent = { Text("External Bluetooth IMU.") },
                    leadingContent = { Icon(Icons.Default.Watch, contentDescription = null) },
                    modifier = Modifier.clickable {
                        kinematicsSource = KinematicsSource.M5STICK
                        showKinematicsSheet = false
                    }
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}