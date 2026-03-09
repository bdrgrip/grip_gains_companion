package app.grip_gains_companion.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.model.KinematicsSource
import app.grip_gains_companion.service.ProgressorHandler
import app.grip_gains_companion.service.ble.BluetoothManager
import app.grip_gains_companion.service.web.WebViewBridge
import app.grip_gains_companion.ui.components.ForceGraph
import app.grip_gains_companion.ui.components.StatusBar
import app.grip_gains_companion.ui.components.TimerWebView
import app.grip_gains_companion.ui.theme.GripGainsTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    bluetoothManager: BluetoothManager,
    progressorHandler: ProgressorHandler,
    webViewBridge: WebViewBridge,
    cachedWebView: android.webkit.WebView,
    showStatusBar: Boolean,
    expandedForceBar: Boolean,
    showForceGraph: Boolean,
    forceGraphWindow: Int,
    useLbs: Boolean,
    enableTargetWeight: Boolean,
    useManualTarget: Boolean,
    manualTargetWeight: Double,
    weightTolerance: Double,
    activeKinematicsSource: KinematicsSource,
    enableAnalytics: Boolean,
    onSettingsTap: () -> Unit,
    onHistoryTap: () -> Unit,
    onUnitToggle: () -> Unit,
    onSetManualWeightTap: () -> Unit,
    onShowTensionSheet: () -> Unit,
    enableIsotonicMode: Boolean,
    onShowKinematicsSheet: () -> Unit
) {
    val isToolbarVisible by webViewBridge.isToolbarVisible.collectAsState()
    val currentUrl by webViewBridge.currentUrl.collectAsState()
    val isBasicTimerPage = currentUrl.contains("basic-timer")
    val isRegularTimerPage = currentUrl.contains("/timer") && !isBasicTimerPage

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var canGoBack by remember { mutableStateOf(false) }

    val connectionState by bluetoothManager.connectionState.collectAsState()
    val isConnected = connectionState == ConnectionState.Connected
    val isReconnecting = connectionState == ConnectionState.Reconnecting

    // FIX: Tiny delay gives WebView time to push new URL to history stack
    LaunchedEffect(currentUrl) {
        kotlinx.coroutines.delay(100)
        canGoBack = cachedWebView.canGoBack()
    }

    BackHandler(enabled = canGoBack) {
        cachedWebView.goBack()
        coroutineScope.launch {
            kotlinx.coroutines.delay(100)
            canGoBack = cachedWebView.canGoBack()
        }
    }

    // --- RESTORED TACTILE HAPTICS ---
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.targetValue }.drop(1).collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue }.drop(1).collect {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val emphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Dock right above the nav bar area
    val basePeekHeight = if (showForceGraph) 80.dp + navBarPadding else 72.dp + navBarPadding

    val peekHeight by animateDpAsState(
        targetValue = if (isToolbarVisible) basePeekHeight else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = emphasizedEasing),
        label = "peekHeight"
    )

    GripGainsTheme(darkTheme = true) {
        // LAYER 2: THE UI DRAWER (Now the root container)
        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize(),
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            sheetSwipeEnabled = showForceGraph,
            containerColor = Color(0xFF1A2231), // Grip Gains Gray background
            sheetContainerColor = Color(0xFF1E2737),
            sheetShadowElevation = 0.dp,
            sheetShape = RoundedCornerShape(
                topStart = if (showForceGraph) 28.dp else 0.dp,
                topEnd = if (showForceGraph) 28.dp else 0.dp,
                bottomStart = 0.dp, bottomEnd = 0.dp
            ),
            sheetDragHandle = if (showForceGraph) {
                {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                coroutineScope.launch {
                                    if (sheetState.currentValue == SheetValue.PartiallyExpanded) sheetState.expand()
                                    else sheetState.partialExpand()
                                }
                            }
                            .padding(top = 10.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp).height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                    }
                }
            } else null,
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(top = 8.dp)
                        .padding(bottom = navBarPadding)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onShowTensionSheet()
                                },
                                label = {
                                    Text(
                                        if (isConnected) "Scale" else "No Scale",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Bluetooth,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    leadingIconContentColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = null
                            )

                            if (enableIsotonicMode) {
                                AssistChip(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onShowKinematicsSheet()
                                    },
                                    label = {
                                        Text(
                                            if (activeKinematicsSource == KinematicsSource.PHONE) "Phone" else "M5Stick",
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (activeKinematicsSource == KinematicsSource.PHONE) Icons.Default.Smartphone else Icons.Default.Watch,
                                            null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        leadingIconContentColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    border = null
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedVisibility(visible = isBasicTimerPage || isRegularTimerPage) {
                                TextButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSetManualWeightTap()
                                }) {
                                    val displayWeight =
                                        if (useLbs) manualTargetWeight * 2.20462 else manualTargetWeight
                                    Text(
                                        text = "${
                                            String.format(
                                                "%.1f",
                                                displayWeight
                                            )
                                        } ${if (useLbs) "lbs" else "kg"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            AnimatedVisibility(visible = enableAnalytics) {
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onHistoryTap()
                                }) {
                                    Icon(Icons.Default.Analytics, "History", tint = Color.White)
                                }
                            }

                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSettingsTap()
                            }) {
                                Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                            }
                        }
                    }

                    if (showForceGraph) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isConnected || isReconnecting) {
                                val forceHistory by progressorHandler.forceHistory.collectAsState()
                                ForceGraph(
                                    forceHistory = forceHistory,
                                    useLbs = useLbs,
                                    windowSeconds = forceGraphWindow,
                                    targetWeight = manualTargetWeight,
                                    tolerance = if (enableTargetWeight) weightTolerance else null,
                                    isReconnecting = isReconnecting
                                )
                            } else {
                                Text(
                                    "Connect Scale to Enable Force Graph",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        ) { _ -> // <-- WE IGNORE THE PADDING HERE

            // LAYER 1: THE WEBSITE (Now clickable again!)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars) // Push down from top
                // Notice: NO padding at the bottom! Edge-to-edge behind the drawer.
            ) {
                if (isConnected && showStatusBar) {
                    val state by progressorHandler.state.collectAsState()
                    val currentForce by progressorHandler.currentForce.collectAsState()
                    val scrapedTargetWeight by webViewBridge.targetWeight.collectAsState()
                    val effectiveTargetWeight = if (!enableTargetWeight) null
                    else if (useManualTarget) manualTargetWeight
                    else scrapedTargetWeight

                    StatusBar(
                        force = currentForce,
                        engaged = state.isEngaged,
                        calibrating = state.isCalibrating,
                        waitingForSamples = state.isWaitingForSamples,
                        calibrationTimeRemaining = progressorHandler.calibrationTimeRemaining.collectAsState().value,
                        weightMedian = progressorHandler.weightMedian.collectAsState().value ?: 0.0,
                        targetWeight = effectiveTargetWeight,
                        isOffTarget = progressorHandler.isOffTarget.collectAsState().value,
                        offTargetDirection = progressorHandler.offTargetDirection.collectAsState().value,
                        sessionMean = progressorHandler.sessionMean.collectAsState().value ?: 0.0,
                        sessionStdDev = progressorHandler.sessionStdDev.collectAsState().value
                            ?: 0.0,
                        useLbs = useLbs,
                        expanded = expandedForceBar,
                        deviceShortName = bluetoothManager.selectedDeviceType.collectAsState().value.shortName,
                        onUnitToggle = onUnitToggle,
                        onSettingsTap = onSettingsTap
                    )
                }

                TimerWebView(
                    bridge = webViewBridge,
                    cachedWebView = cachedWebView,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}