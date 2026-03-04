package app.grip_gains_companion.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.service.ProgressorHandler
import app.grip_gains_companion.service.ble.BluetoothManager
import app.grip_gains_companion.service.web.WebViewBridge
import app.grip_gains_companion.ui.components.ForceGraph
import app.grip_gains_companion.ui.components.StatusBar
import app.grip_gains_companion.ui.components.TimerWebView
import androidx.compose.foundation.background
import app.grip_gains_companion.ui.theme.GripGainsTheme
import androidx.compose.ui.graphics.Color

/**
 * Main dashboard: WebView + Status Bar + Force Graph
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    bluetoothManager: BluetoothManager,
    progressorHandler: ProgressorHandler,
    webViewBridge: WebViewBridge,
    showStatusBar: Boolean,
    expandedForceBar: Boolean,
    showForceGraph: Boolean,
    forceGraphWindow: Int,
    useLbs: Boolean,
    enableTargetWeight: Boolean,
    useManualTarget: Boolean,
    manualTargetWeight: Double,
    weightTolerance: Double,
    onSettingsTap: () -> Unit,
    onHistoryTap: () -> Unit,
    onUnitToggle: () -> Unit,
) {
    // This forces the dashboard HUD to stay dark regardless of system settings
    GripGainsTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A2231)) // Your target Grip Gains gray
                .statusBarsPadding()
                .navigationBarsPadding()
        )

            val connectionState by bluetoothManager.connectionState.collectAsState()
            val isConnected = connectionState == ConnectionState.Connected
            val isReconnecting = connectionState == ConnectionState.Reconnecting
            val selectedDeviceType by bluetoothManager.selectedDeviceType.collectAsState()

            val state by progressorHandler.state.collectAsState()
            val currentForce by progressorHandler.currentForce.collectAsState()
            val calibrationTimeRemaining by progressorHandler.calibrationTimeRemaining.collectAsState()
            val weightMedian by progressorHandler.weightMedian.collectAsState()
            val sessionMean by progressorHandler.sessionMean.collectAsState()
            val sessionStdDev by progressorHandler.sessionStdDev.collectAsState()
            val forceHistory by progressorHandler.forceHistory.collectAsState()
            val isOffTarget by progressorHandler.isOffTarget.collectAsState()
            val offTargetDirection by progressorHandler.offTargetDirection.collectAsState()

            val scrapedTargetWeight by webViewBridge.targetWeight.collectAsState()

            val effectiveTargetWeight = remember(
                enableTargetWeight,
                useManualTarget,
                manualTargetWeight,
                scrapedTargetWeight
            ) {
                if (!enableTargetWeight) null
                else if (useManualTarget) manualTargetWeight
                else scrapedTargetWeight
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0xFF1A2231)) // Paint behind the status bar
                    .statusBarsPadding() // Push content below the system icons
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isConnected && showStatusBar) {
                        StatusBar(
                            force = currentForce,
                            engaged = state.isEngaged,
                            calibrating = state.isCalibrating,
                            waitingForSamples = state.isWaitingForSamples,
                            calibrationTimeRemaining = calibrationTimeRemaining,
                            weightMedian = weightMedian ?: 0.0, // <-- Default to 0.0 if null
                            targetWeight = effectiveTargetWeight,
                            isOffTarget = isOffTarget,
                            offTargetDirection = offTargetDirection,
                            sessionMean = sessionMean ?: 0.0,   // <-- Default to 0.0 if null
                            sessionStdDev = sessionStdDev ?: 0.0, // <-- Default to 0.0 if null
                            useLbs = useLbs,
                            expanded = expandedForceBar,
                            deviceShortName = selectedDeviceType.shortName,
                            onUnitToggle = onUnitToggle,
                            onSettingsTap = onSettingsTap
                        )
                    }

                    // Force graph (Test mode active)
                    val showGraphForTesting = false

                    if ((isConnected || isReconnecting) && showForceGraph) {
                        ForceGraph(
                            forceHistory = forceHistory,
                            useLbs = useLbs,
                            windowSeconds = forceGraphWindow,
                            targetWeight = effectiveTargetWeight,
                            tolerance = if (enableTargetWeight) weightTolerance else null,
                            isReconnecting = isReconnecting
                        )
                    }

                    // Central WebView Timer
                    TimerWebView(
                        webViewBridge = webViewBridge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                // Floating Action Buttons
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (!isConnected || !showStatusBar) {
                        SmallFloatingActionButton(
                            onClick = onSettingsTap,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }

                    SmallFloatingActionButton(
                        onClick = onHistoryTap,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "RAW Analytics"
                        )
                    }
                }
            }
        }
    }
