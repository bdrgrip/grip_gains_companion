package app.grip_gains_companion.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.service.ble.BluetoothManager

enum class KinematicsSource { PHONE, M5STICK }
enum class TensionSource { BLE_SCALE, MANUAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupDashboardScreen(
    bluetoothManager: BluetoothManager,
    onStartTraining: (TensionSource, Double, KinematicsSource) -> Unit
) {
    val connectionState by bluetoothManager.connectionState.collectAsStateWithLifecycle()
    val connectedDeviceName by bluetoothManager.connectedDeviceName.collectAsStateWithLifecycle()
    val discoveredDevices by bluetoothManager.discoveredDevices.collectAsStateWithLifecycle()

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
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Open Grip Gains", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
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
                activeSource = if (tensionSource == TensionSource.BLE_SCALE && connectionState == ConnectionState.Connected) {
                    connectedDeviceName ?: "Bluetooth Scale"
                } else {
                    "Basic Timer ($manualWeight lbs)"
                },
                statusColor = if (tensionSource == TensionSource.BLE_SCALE && connectionState == ConnectionState.Connected) Color.Green else Color.LightGray,
                onClick = {
                    bluetoothManager.startScanning()
                    showTensionSheet = true
                }
            )

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

                // Manual Input Option
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable {
                        tensionSource = TensionSource.MANUAL
                        showTensionSheet = false
                    },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (tensionSource == TensionSource.MANUAL) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Basic Web Timer (Manual Weight)", style = MaterialTheme.typography.titleMedium)
                        }
                        if (tensionSource == TensionSource.MANUAL) {
                            OutlinedTextField(
                                value = manualWeight,
                                onValueChange = { manualWeight = it },
                                label = { Text("Target Weight") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Bluetooth Crane Scales", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                if (discoveredDevices.isEmpty()) {
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
                    supportingContent = { Text("Uses internal gravity sensors. Requires phone strapped to body.") },
                    leadingContent = { Icon(Icons.Default.Smartphone, contentDescription = null) },
                    modifier = Modifier.clickable {
                        kinematicsSource = KinematicsSource.PHONE
                        showKinematicsSheet = false
                    }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("M5StickC Plus 2") },
                    supportingContent = { Text("External Bluetooth IMU. Straps directly to the body.") },
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

@Composable
fun DataSourceCard(
    title: String,
    icon: ImageVector,
    activeSource: String,
    statusColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(activeSource, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(statusColor))
        }
    }
}