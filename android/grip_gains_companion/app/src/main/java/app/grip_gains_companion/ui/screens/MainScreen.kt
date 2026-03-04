package app.grip_gains_companion.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.service.ProgressorHandler
import app.grip_gains_companion.service.ble.BluetoothManager
import app.grip_gains_companion.service.web.WebViewBridge
import app.grip_gains_companion.ui.components.ForceGraph
import app.grip_gains_companion.ui.components.StatusBar
import app.grip_gains_companion.ui.components.TimerWebView
import app.grip_gains_companion.ui.theme.GripGainsTheme

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
    onSetManualWeightTap: () -> Unit,
) {
    val canGoBack by webViewBridge.canGoBack.collectAsState()
    val canGoForward by webViewBridge.canGoForward.collectAsState()
    val isToolbarVisible by webViewBridge.isToolbarVisible.collectAsState()

    val currentUrl by webViewBridge.currentUrl.collectAsState()
    val isBasicTimerPage = currentUrl.endsWith("/basic-timer")

    BackHandler(enabled = canGoBack) {
        webViewBridge.goBack()
    }

    GripGainsTheme(darkTheme = true) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF1A2231),
            contentWindowInsets = WindowInsets.statusBars
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
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

                    if (isConnected && showStatusBar) {
                        StatusBar(
                            force = currentForce,
                            engaged = state.isEngaged,
                            calibrating = state.isCalibrating,
                            waitingForSamples = state.isWaitingForSamples,
                            calibrationTimeRemaining = calibrationTimeRemaining,
                            weightMedian = weightMedian ?: 0.0,
                            targetWeight = effectiveTargetWeight,
                            isOffTarget = isOffTarget,
                            offTargetDirection = offTargetDirection,
                            sessionMean = sessionMean ?: 0.0,
                            sessionStdDev = sessionStdDev ?: 0.0,
                            useLbs = useLbs,
                            expanded = expandedForceBar,
                            deviceShortName = selectedDeviceType.shortName,
                            onUnitToggle = onUnitToggle,
                            onSettingsTap = onSettingsTap
                        )
                    }

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

                    TimerWebView(
                        webViewBridge = webViewBridge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                AnimatedVisibility(
                    visible = isToolbarVisible,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A2231))
                    ) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color.White.copy(alpha = 0.1f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .windowInsetsPadding(WindowInsets.navigationBars),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { webViewBridge.goBack() },
                                enabled = canGoBack,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = Color.White,
                                    disabledContentColor = Color.White.copy(alpha = 0.38f)
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Web Back")
                            }

                            IconButton(
                                onClick = { webViewBridge.goForward() },
                                enabled = canGoForward,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = Color.White,
                                    disabledContentColor = Color.White.copy(alpha = 0.38f)
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Web Forward")
                            }

                            AnimatedVisibility(visible = isBasicTimerPage) {
                                TextButton(
                                    onClick = onSetManualWeightTap,
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                                ) {
                                        Text(
                                            text = "${String.format("%.1f", manualTargetWeight)} ${if (useLbs) "lbs" else "kg"}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                }
                            }

                            IconButton(onClick = onHistoryTap) {
                                Icon(Icons.Default.Analytics, contentDescription = "RAW Analytics", tint = Color.White)
                            }

                            IconButton(onClick = onSettingsTap) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}