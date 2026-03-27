package app.grip_gains_companion.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grip_gains_companion.data.PreferencesRepository
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.model.ForceDevice
import app.grip_gains_companion.model.KinematicsSource
import app.grip_gains_companion.service.ProgressorHandler
import app.grip_gains_companion.service.ble.BluetoothManager
import app.grip_gains_companion.service.web.WebViewBridge
import app.grip_gains_companion.ui.components.ForceGraph
import app.grip_gains_companion.ui.components.TimerWebView
import app.grip_gains_companion.ui.theme.GripGainsTheme
import app.grip_gains_companion.util.StatisticsUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    preferencesRepository: PreferencesRepository,
    bluetoothManager: BluetoothManager,
    progressorHandler: ProgressorHandler,
    webViewBridge: WebViewBridge,
    cachedWebView: android.webkit.WebView,
    showStatusBar: Boolean,
    showForceGraph: Boolean,
    forceGraphWindow: Int,
    useLbs: Boolean,
    enableTargetWeight: Boolean,
    useManualTarget: Boolean,
    manualTargetWeight: Double,
    weightTolerance: Double,
    activeKinematicsSource: KinematicsSource,
    enableAnalytics: Boolean,
    m5ConnectionState: ConnectionState,
    m5Data: Triple<Float, Float, Float>,
    deviceAliases: Map<String, String> = emptyMap(),
    onSettingsTap: () -> Unit,
    onHistoryTap: () -> Unit,
    onUnitToggle: () -> Unit,
    onSetManualWeightTap: () -> Unit,
    enableIsotonicMode: Boolean,
    onShowKinematicsSheet: () -> Unit
) {
    val context = LocalContext.current
    val isToolbarVisible by webViewBridge.isToolbarVisible.collectAsState()
    val currentUrl by webViewBridge.currentUrl.collectAsState()
    val isBasicTimerPage = currentUrl.contains("basic-timer")
    val isRegularTimerPage = currentUrl.contains("/timer") && !isBasicTimerPage

    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var canGoBack by remember { mutableStateOf(false) }

    // --- BLUETOOTH NATIVE LAUNCHER (THE FIX) ---
    val btManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager }
    val btAdapter = btManager?.adapter
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    // --- BLUETOOTH & ALIAS STATES ---
    val connectionState by bluetoothManager.connectionState.collectAsState()
    val isConnected = connectionState == ConnectionState.Connected
    val isReconnecting = connectionState == ConnectionState.Reconnecting

    val connectedAddress by bluetoothManager.connectedDeviceAddress.collectAsState()
    val connectedName by bluetoothManager.connectedDeviceName.collectAsState()
    val discoveredDevices by bluetoothManager.discoveredDevices.collectAsStateWithLifecycle()

    val deviceAliases by preferencesRepository.deviceAliases.collectAsStateWithLifecycle(initialValue = emptyMap())

    // Dialog States
    var showTensionSheet by remember { mutableStateOf(false) }
    var deviceToAlias by remember { mutableStateOf<ForceDevice?>(null) }
    var aliasInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        bluetoothManager.startScanning()
    }

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

    val density = LocalDensity.current
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtLeast(16.dp)

    val level1Px = with(density) { ((if (showForceGraph) 88.dp else 64.dp) + navBarPadding).toPx() }
    val level2Px = with(density) { (290.dp + navBarPadding).toPx() }
    val level3Px = with(density) { (360.dp + navBarPadding).toPx() }

    val sheetHeightPx = remember { Animatable(if (isToolbarVisible) level1Px else 0f) }
    var dragMomentum by remember { mutableFloatStateOf(0f) }
    val currentLevel1Px by rememberUpdatedState(level1Px)

    LaunchedEffect(cachedWebView) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            cachedWebView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val dy = scrollY - oldScrollY
                if (scrollY <= 40) {
                    webViewBridge.setToolbarVisible(true)
                } else if (Math.abs(dy) > 40) {
                    val isScrollingUp = dy < 0
                    if (isScrollingUp || sheetHeightPx.value <= currentLevel1Px + 20f) {
                        webViewBridge.setToolbarVisible(isScrollingUp)
                    }
                }
            }
        }
    }

    LaunchedEffect(isToolbarVisible, showForceGraph) {
        if (!isToolbarVisible) {
            if (sheetHeightPx.value <= level1Px + 20f) {
                sheetHeightPx.animateTo(0f, tween(300))
            }
        } else if (sheetHeightPx.value < level1Px || !showForceGraph) {
            sheetHeightPx.animateTo(level1Px, spring(dampingRatio = 0.7f, stiffness = 400f))
        }
    }

    val dragModifier = if (showForceGraph) {
        Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = {
                    dragMomentum = 0f
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!isToolbarVisible) webViewBridge.setToolbarVisible(true)
                },
                onDragEnd = {
                    coroutineScope.launch {
                        val current = sheetHeightPx.value
                        val target = if (dragMomentum < -4f) {
                            if (current < level2Px - 20f) level2Px else level3Px
                        } else if (dragMomentum > 4f) {
                            if (current > level2Px + 20f) level2Px else level1Px
                        } else {
                            when {
                                current > (level2Px + level3Px) / 2 -> level3Px
                                current > (level1Px + level2Px) / 2 -> level2Px
                                else -> level1Px
                            }
                        }

                        val isOpening = target > current

                        launch {
                            snapshotFlow { sheetHeightPx.value }.first {
                                if (isOpening) it >= target - 2f else it <= target + 2f
                            }
                            if (target == level1Px) haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            else haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        val animSpec = if (isOpening) spring<Float>(dampingRatio = 0.6f, stiffness = 800f)
                        else tween<Float>(durationMillis = 200, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))

                        sheetHeightPx.animateTo(target, animSpec)
                    }
                }
            ) { change, dragAmount ->
                change.consume()
                dragMomentum = dragAmount
                coroutineScope.launch {
                    val newHeight = (sheetHeightPx.value - dragAmount).coerceIn(level1Px, level3Px)
                    sheetHeightPx.snapTo(newHeight)
                }
            }
        }
    } else Modifier

    GripGainsTheme(darkTheme = true) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A2231))) {

            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                TimerWebView(
                    bridge = webViewBridge,
                    cachedWebView = cachedWebView,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(density) { sheetHeightPx.value.toDp() })
                    .then(dragModifier),
                shape = RoundedCornerShape(
                    topStart = if (showForceGraph) 28.dp else 0.dp,
                    topEnd = if (showForceGraph) 28.dp else 0.dp
                ),
                color = Color(0xFF1E2737),
                shadowElevation = 16.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(with(density) { level3Px.toDp() })) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(top = 8.dp)
                    ) {
                        if (showForceGraph) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        coroutineScope.launch {
                                            val current = sheetHeightPx.value
                                            val target = if (current > level1Px + 10f) level1Px else level2Px
                                            val isOpening = target > current

                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                            launch {
                                                snapshotFlow { sheetHeightPx.value }.first {
                                                    if (isOpening) it >= target - 2f else it <= target + 2f
                                                }
                                                if (target == level1Px) haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                else haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }

                                            val animSpec = if (isOpening) spring<Float>(dampingRatio = 0.6f, stiffness = 800f)
                                            else tween<Float>(200, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))

                                            sheetHeightPx.animateTo(target, animSpec)
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                                val displayName = deviceAliases[connectedAddress] ?: connectedName ?: "No Scale"

                                AssistChip(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        // THE BLUETOOTH FIX: Prompt for BT natively!
                                        if (btAdapter?.isEnabled == true) {
                                            bluetoothManager.startScanning()
                                            showTensionSheet = true
                                        } else {
                                            enableBluetoothLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                        }
                                    },
                                    label = { Text(if (isConnected) displayName else "No Scale", fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(16.dp)) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        leadingIconContentColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = null
                                )

                                if (enableIsotonicMode) {
                                    val m5Label = if (activeKinematicsSource == KinematicsSource.PHONE) "Phone"
                                    else if (m5ConnectionState == ConnectionState.Connected) "M5Stick"
                                    else "Scanning..."

                                    val m5Icon = if (activeKinematicsSource == KinematicsSource.PHONE) Icons.Default.Smartphone
                                    else if (m5ConnectionState == ConnectionState.Connected) Icons.Default.Watch
                                    else Icons.AutoMirrored.Filled.BluetoothSearching

                                    AssistChip(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            // THE BLUETOOTH FIX: Prompt for BT natively!
                                            if (btAdapter?.isEnabled == true) {
                                                onShowKinematicsSheet() // Still triggers scan in MainActivity
                                                showTensionSheet = false // Hide other sheet if open
                                            } else {
                                                enableBluetoothLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                            }
                                        },
                                        label = { Text(m5Label, fontWeight = FontWeight.Bold) },
                                        leadingIcon = { Icon(m5Icon, null, modifier = Modifier.size(16.dp)) },
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
                                        val displayWeight = if (useLbs) manualTargetWeight * 2.20462 else manualTargetWeight
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.1f", displayWeight)} ${if (useLbs) "lbs" else "kg"}",
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
                                    }) { Icon(Icons.Default.Analytics, "History", tint = Color.White) }
                                }

                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSettingsTap()
                                }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
                            }
                        }

                        val contentAlpha = ((sheetHeightPx.value - level1Px) / (level2Px - level1Px)).coerceIn(0f, 1f)

                        if (showForceGraph) {
                            Column(modifier = Modifier.alpha(contentAlpha)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isConnected || isReconnecting) {
                                        val forceHistory by progressorHandler.forceHistory.collectAsState()
                                        val currentForce by progressorHandler.currentForce.collectAsState()
                                        val state by progressorHandler.state.collectAsState()

                                        val displayWeight = if (useLbs) currentForce * 2.20462 else currentForce
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.1f", displayWeight)} ${if (useLbs) "Lbs" else "Kg"}",
                                            style = MaterialTheme.typography.displayLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 64.sp,
                                                letterSpacing = (-2).sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.align(Alignment.Center)
                                        )

                                        ForceGraph(
                                            forceHistory = forceHistory,
                                            useLbs = useLbs,
                                            windowSeconds = forceGraphWindow,
                                            targetWeight = manualTargetWeight,
                                            tolerance = if (enableTargetWeight) weightTolerance else null,
                                            isReconnecting = isReconnecting
                                        )

                                        if (state.isCalibrating) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                                            ) {
                                                Text(
                                                    "Zeroing Scale...",
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            "Connect Scale to Enable Force Graph",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                val forceHistory by progressorHandler.forceHistory.collectAsState()
                                val activeForces = remember(forceHistory) {
                                    val now = System.currentTimeMillis()
                                    val cutoff = now - (forceGraphWindow * 1000L)
                                    forceHistory.filter { it.timestamp.time >= cutoff && it.force > 2.0 }.map { it.force }
                                }

                                val mean = if (activeForces.isNotEmpty()) StatisticsUtils.mean(activeForces) else 0.0
                                val median = if (activeForces.isNotEmpty()) StatisticsUtils.median(activeForces) else 0.0
                                val stdDev = if (activeForces.isNotEmpty()) StatisticsUtils.standardDeviation(activeForces) else 0.0

                                val fmt = { value: Double ->
                                    if (value > 0.0) String.format(java.util.Locale.US, "%.1f", if (useLbs) value * 2.20462 else value)
                                    else "-"
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text("Mean: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                                            Text(fmt(mean), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text("Median: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                                            Text(fmt(median), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text("Std Dev: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                                            Text(fmt(stdDev), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- SIBLING DIALOGS ---
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
                                            val defaultName = device.name ?: "Unknown Device"
                                            val displayName = deviceAliases[device.address] ?: defaultName

                                            ListItem(
                                                headlineContent = { Text(displayName, fontWeight = FontWeight.Bold) },
                                                supportingContent = { Text(device.address) },
                                                leadingContent = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                                trailingContent = {
                                                    IconButton(
                                                        onClick = {
                                                            aliasInput = if (deviceAliases.containsKey(device.address)) displayName else ""
                                                            deviceToAlias = device
                                                            showTensionSheet = false
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = "Edit Name", modifier = Modifier.size(20.dp))
                                                    }
                                                },
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

            if (deviceToAlias != null) {
                AlertDialog(
                    onDismissRequest = { deviceToAlias = null },
                    shape = RoundedCornerShape(24.dp),
                    title = { Text("Rename Device", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                text = "Hardware MAC: ${deviceToAlias?.address}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = aliasInput,
                                onValueChange = { aliasInput = it },
                                label = { Text("Custom Name") },
                                placeholder = { Text(deviceToAlias?.name ?: "e.g. My Progressor") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (deviceAliases.containsKey(deviceToAlias?.address)) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch { preferencesRepository.setDeviceAlias(deviceToAlias!!.address, "") }
                                        deviceToAlias = null
                                    },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text("Remove Custom Name", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            coroutineScope.launch {
                                deviceToAlias?.let {
                                    preferencesRepository.setDeviceAlias(it.address, aliasInput.trim())
                                }
                                deviceToAlias = null
                            }
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { deviceToAlias = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}