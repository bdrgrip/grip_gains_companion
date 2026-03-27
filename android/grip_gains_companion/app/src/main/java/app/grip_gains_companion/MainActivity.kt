package app.grip_gains_companion

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.grip_gains_companion.data.PreferencesRepository
import app.grip_gains_companion.database.*
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.model.KinematicsSource
import app.grip_gains_companion.service.IsoSessionManager
import app.grip_gains_companion.service.ProgressorHandler
import app.grip_gains_companion.service.RawSessionManager
import app.grip_gains_companion.service.SessionResult
import app.grip_gains_companion.service.TimerForegroundService
import app.grip_gains_companion.service.ble.BluetoothManager
import app.grip_gains_companion.service.ble.M5StickManager
import app.grip_gains_companion.service.web.WebViewBridge
import app.grip_gains_companion.ui.screens.*
import app.grip_gains_companion.ui.theme.GripGainsTheme
import app.grip_gains_companion.util.HapticManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var m5StickManager: M5StickManager
    private lateinit var progressorHandler: ProgressorHandler
    private lateinit var webViewBridge: WebViewBridge
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var hapticManager: HapticManager

    private lateinit var sensorManager: SensorManager
    private var linearAccelerometer: Sensor? = null

    private var rawSessionManager = RawSessionManager()
    private var isoSessionManager = IsoSessionManager()

    private var showIsoSummary by mutableStateOf(false)
    private var activeKinematicsSource by mutableStateOf(KinematicsSource.PHONE)
    private var currentManualWeight by mutableDoubleStateOf(20.0)

    private var latestX = 0f
    private var latestY = 0f
    private var latestZ = 0f

    private lateinit var database: AppDatabase
    private lateinit var rawRepository: RawSessionRepository
    private lateinit var sessionRepository: SessionRepository
    private var finishedSessionData by mutableStateOf<SessionResult?>(null)

    private val failRepReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TimerForegroundService.ACTION_FAIL_REP) {
                hapticManager.warning()
                webViewBridge.clickFailButton()

                val stopIntent = Intent(this@MainActivity, TimerForegroundService::class.java)
                stopIntent.action = TimerForegroundService.ACTION_STOP
                startService(stopIntent)
            }
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (activeKinematicsSource != KinematicsSource.PHONE) return
            latestX = event.values[0]
            latestY = event.values[1]
            latestZ = event.values[2]

            if (rawSessionManager.isHoldActive) {
                val isConnected = bluetoothManager.connectionState.value == ConnectionState.Connected
                val webWeight = webViewBridge.targetWeight.value

                val activeTension = if (isConnected) {
                    progressorHandler.currentForce.value
                } else if (webWeight != null && webWeight > 0.0) {
                    webWeight
                } else {
                    currentManualWeight
                }

                // SPIKE FIX: Ignore movement if tension is basically zero
                val activeX = if (activeTension > 2.0) latestX else 0f
                val activeY = if (activeTension > 2.0) latestY else 0f
                val activeZ = if (activeTension > 2.0) latestZ else 0f

                rawSessionManager.addSample(
                    activeX, activeY, activeZ, activeTension, event.timestamp
                )
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                bluetoothManager.startScanning()
                m5StickManager.startScanning()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ContextCompat.registerReceiver(
            this,
            failRepReceiver,
            IntentFilter(TimerForegroundService.ACTION_FAIL_REP),
            ContextCompat.RECEIVER_EXPORTED
        )

        database = AppDatabase.getDatabase(this)
        rawRepository = RawSessionRepository(database.rawSessionDao())
        sessionRepository = SessionRepository(database.rawSessionDao(), database.isoSessionDao())

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bluetoothManager = BluetoothManager(this)
        m5StickManager = M5StickManager(this)
        progressorHandler = ProgressorHandler()
        webViewBridge = WebViewBridge()
        preferencesRepository = PreferencesRepository(this)
        hapticManager = HapticManager(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        sensorManager.registerListener(
            sensorListener,
            linearAccelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )

        lifecycleScope.launch { preferencesRepository.initializeUnitsIfNeeded() }

        bluetoothManager.onForceSample = { force, timestamp ->
            lifecycleScope.launch {
                progressorHandler.processSample(force, timestamp)
                if (isoSessionManager.isRepActive) {
                    isoSessionManager.addSample(force)
                }
            }
        }

        setupEventHandlers()

        if (hasAllPermissions()) {
            bluetoothManager.startScanning()
            m5StickManager.startScanning()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            val navController = rememberNavController()
            val haptic = LocalHapticFeedback.current
            val snackbarHostState = remember { SnackbarHostState() }

            // 1. ADD THESE TWO LINES FOR BLUETOOTH CHECKS
            val enableBluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val btAdapter = remember { (getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter }

            var showWeightPrompt by remember { mutableStateOf(false) }
            var showTensionSheet by remember { mutableStateOf(false) }
            var showKinematicsSheet by remember { mutableStateOf(false) }

            val enableAnalytics by preferencesRepository.enableAnalytics.collectAsState(initial = true)
            val showIsoSummaryPref by preferencesRepository.showIsoSummary.collectAsState(initial = true)
            val showRawSummaryPref by preferencesRepository.showRawSummary.collectAsState(initial = true)

            val currentUrl by webViewBridge.currentUrl.collectAsState()
            val webWeight by webViewBridge.targetWeight.collectAsState()
            val isBasicTimerPage = currentUrl.contains("basic-timer")
            val rawSessions by sessionRepository.getAllRawSessions().collectAsState(initial = emptyList())
            val recentMuscles = remember(rawSessions) { rawSessions.map { it.targetMuscle }.distinct().sorted() }
            val allIsoSessions by sessionRepository.getAllIsoSessions().collectAsState(initial = emptyList())

            val recentGrippers = remember(allIsoSessions) { allIsoSessions.map { it.gripperType }.distinct().sorted() }
            var isoOverrideWeight by remember { mutableStateOf<Double?>(null) }

            val unifiedEquipment = remember(recentMuscles, recentGrippers) {
                (recentMuscles + recentGrippers).distinct().sorted()
            }

            val connectionState by bluetoothManager.connectionState.collectAsState()
            val isConnected = connectionState == ConnectionState.Connected
            val discoveredDevices by bluetoothManager.discoveredDevices.collectAsState()

            val isoGripper by webViewBridge.sessionGripper.collectAsState()
            val isoSide by webViewBridge.sessionSide.collectAsState()

            val useLbs by preferencesRepository.useLbs.collectAsState(initial = false)

            // --- SYNC HARDWARE PARSER TO UI ---
            LaunchedEffect(useLbs) {
                bluetoothManager.setHardwareUnitIsLbs(useLbs)
            }

            val showForceGraph by preferencesRepository.showForceGraph.collectAsState(initial = true)
            val forceGraphWindow by preferencesRepository.forceGraphWindow.collectAsState(initial = 5)
            val enableTargetWeight by preferencesRepository.enableTargetWeight.collectAsState(initial = true)
            val useManualTarget by preferencesRepository.useManualTarget.collectAsState(initial = false)
            val weightTolerance by preferencesRepository.weightTolerance.collectAsState(initial = 0.5)
            val enableHaptics by preferencesRepository.enableHaptics.collectAsState(initial = true)
            val enableCalibration by preferencesRepository.enableCalibration.collectAsState(initial = true)
            val enableIsotonicMode by preferencesRepository.enableIsotonicMode.collectAsState(initial = true)
            val enableTargetSound by preferencesRepository.enableTargetSound.collectAsState(initial = true)

            val effectiveTargetWeight = if (isBasicTimerPage) {
                currentManualWeight
            } else {
                isoOverrideWeight ?: webWeight ?: (if (useLbs) 20.0 / 2.20462 else 20.0)
            }

            // --- STRICT EDGE-DETECTION BIOFEEDBACK LOOP ---
            val currentForce by progressorHandler.currentForce.collectAsState()
            var previousForce by remember { mutableDoubleStateOf(0.0) }
            var hasHitTargetThisRep by remember { mutableStateOf(false) }

            LaunchedEffect(currentForce) {
                if (effectiveTargetWeight > 0.0 && enableTargetWeight) {
                    val targetLowerBound = effectiveTargetWeight - weightTolerance

                    // EDGE CROSSED: Moving UP into the target zone
                    if (currentForce >= targetLowerBound && previousForce < targetLowerBound) {
                        hasHitTargetThisRep = true
                        if (enableHaptics) hapticManager.success()
                        if (enableTargetSound) app.grip_gains_companion.util.ToneGenerator.playHighTone()
                    }
                    // EDGE CROSSED: Falling DOWN out of the target zone (but still pulling somewhat hard)
                    else if (currentForce < targetLowerBound && previousForce >= targetLowerBound && hasHitTargetThisRep && currentForce > (effectiveTargetWeight * 0.5)) {
                        if (enableHaptics) hapticManager.warning()
                        if (enableTargetSound) app.grip_gains_companion.util.ToneGenerator.playLowTone() // FIX: NOW USES LOW TONE
                    }

                    // Reset the rep state if tension drops to basically zero
                    if (currentForce < 2.0) {
                        hasHitTargetThisRep = false
                    }
                }
                previousForce = currentForce
            }

            LaunchedEffect(enableCalibration) {
                progressorHandler.enableCalibration = enableCalibration
            }
            LaunchedEffect(connectionState) {
                if (connectionState == ConnectionState.Connected && enableHaptics) hapticManager.success()
                if (connectionState == ConnectionState.Disconnected) progressorHandler.reset()
            }

            LaunchedEffect(effectiveTargetWeight) {
                progressorHandler.targetWeight = effectiveTargetWeight
            }

            LaunchedEffect(Unit) {
                kotlinx.coroutines.flow.merge(
                    webViewBridge.manualSessionEndTrigger,
                    webViewBridge.saveButtonAppeared
                ).collect { triggered ->
                    if (triggered) {
                        val isotonicEnabled = preferencesRepository.enableIsotonicMode.first()
                        processSessionEnd(
                            isotonicEnabled = isotonicEnabled,
                            enableAnalytics = enableAnalytics,
                            showIsoSummaryPref = showIsoSummaryPref,
                            showRawSummaryPref = showRawSummaryPref,
                            onAutoSaveIso = {
                                lifecycleScope.launch { snackbarHostState.showSnackbar("Isometric session auto-saved") }
                            }
                        )
                        webViewBridge.resetManualSessionEndTrigger()
                        webViewBridge.resetSaveFlag()
                    }
                }
            }

            LaunchedEffect(Unit) {
                bluetoothManager.startScanning()
            }

            var weightInputText by remember { mutableStateOf(effectiveTargetWeight.toString()) }

            val baseContext = LocalContext.current
            val darkContext = remember(baseContext) {
                android.view.ContextThemeWrapper(baseContext, android.R.style.Theme_Material_NoActionBar)
            }

            val cachedWebView = remember {
                android.webkit.WebView(darkContext).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#1A2231"))

                    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING)) {
                        androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                    }

                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onJsAlert(view: android.webkit.WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                            android.app.AlertDialog.Builder(darkContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setMessage(message)
                                .setPositiveButton(android.R.string.ok) { dialog, _ -> result?.confirm(); dialog.dismiss() }
                                .setOnDismissListener { result?.cancel() }
                                .show()
                            return true
                        }

                        override fun onJsConfirm(view: android.webkit.WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                            android.app.AlertDialog.Builder(darkContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setMessage(message)
                                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                    result?.confirm()
                                    dialog.dismiss()
                                }
                                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                                    result?.cancel()
                                    dialog.dismiss()
                                }
                                .setOnDismissListener { result?.cancel() }
                                .show()
                            return true
                        }
                    }

                    addJavascriptInterface(webViewBridge, "Android")
                    addJavascriptInterface(webViewBridge, "AndroidBridge")
                }
            }

            GripGainsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = "main",
                            enterTransition = {
                                if (targetState.destination.route == "main") fadeIn(tween(400))
                                else if (targetState.destination.route?.contains("session_details") == true) {
                                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                                }
                                else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(300))
                            },
                            exitTransition = {
                                if (targetState.destination.route == "main") fadeOut(tween(400))
                                else fadeOut(tween(300))
                            },
                            popEnterTransition = { fadeIn(tween(300)) },
                            popExitTransition = {
                                if (initialState.destination.route?.contains("session_details") == true) {
                                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                                }
                                else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(300))
                            }
                        ) {
                            composable("main") {
                                DisposableEffect(Unit) {
                                    enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
                                    onDispose {
                                        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
                                    }
                                }

                                val m5State by m5StickManager.connectionState.collectAsState()
                                val m5Data by m5StickManager.accelerationData.collectAsState()

                                MainScreen(
                                    bluetoothManager = bluetoothManager,
                                    progressorHandler = progressorHandler,
                                    webViewBridge = webViewBridge,
                                    cachedWebView = cachedWebView,
                                    showStatusBar = true,
                                    showForceGraph = showForceGraph,
                                    forceGraphWindow = forceGraphWindow,
                                    useLbs = useLbs,
                                    enableTargetWeight = enableTargetWeight,
                                    useManualTarget = useManualTarget,
                                    manualTargetWeight = effectiveTargetWeight,
                                    weightTolerance = weightTolerance,
                                    activeKinematicsSource = activeKinematicsSource,
                                    enableAnalytics = enableAnalytics,
                                    m5ConnectionState = m5State,
                                    m5Data = m5Data,
                                    preferencesRepository = preferencesRepository,
                                    onSettingsTap = { navController.navigate("settings") },
                                    onHistoryTap = { navController.navigate("history") },
                                    onUnitToggle = { lifecycleScope.launch { preferencesRepository.setUseLbs(!useLbs) } },
                                    onSetManualWeightTap = {
                                        val isDefault = Math.abs(effectiveTargetWeight - 20.0) < 0.1 || Math.abs(effectiveTargetWeight - (20.0 / 2.20462)) < 0.1
                                        weightInputText = if (isDefault) "20" else {
                                            val displayWeight = if (useLbs) effectiveTargetWeight * 2.20462 else effectiveTargetWeight
                                            String.format(java.util.Locale.US, "%.1f", displayWeight)
                                        }
                                        showWeightPrompt = true
                                    },
                                    enableIsotonicMode = enableIsotonicMode,
                                    onShowKinematicsSheet = {
                                        if (btAdapter?.isEnabled == true) {
                                            if (m5State != ConnectionState.Connected) {
                                                m5StickManager.startScanning()
                                            }
                                            showKinematicsSheet = true
                                        } else {
                                            enableBluetoothLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                        }
                                    }
                                )
                            }

                            composable("settings") {
                                val m5State by m5StickManager.connectionState.collectAsState()
                                val m5Data by m5StickManager.accelerationData.collectAsState()

                                SettingsScreen(
                                    preferencesRepository = preferencesRepository,
                                    bluetoothManager = bluetoothManager,
                                    webViewBridge = webViewBridge,
                                    activeKinematicsSource = activeKinematicsSource,
                                    currentManualWeight = currentManualWeight,
                                    m5ConnectionState = m5State,
                                    m5Data = m5Data,
                                    onKinematicsChange = { newSource ->
                                        activeKinematicsSource = newSource
                                        if (newSource == KinematicsSource.M5STICK && m5State != ConnectionState.Connected) {
                                            m5StickManager.startScanning()
                                        }
                                    },
                                    onWeightChange = { newWeight -> currentManualWeight = newWeight },
                                    onDismiss = { navController.popBackStack() },
                                    onDisconnect = { bluetoothManager.disconnect() },
                                    onConnectDevice = { bluetoothManager.startScanning(); navController.popBackStack() },
                                    onRecalibrate = {
                                        progressorHandler.recalibrate()
                                        lifecycleScope.launch { snackbarHostState.showSnackbar("Scale Zeroed") }
                                    },
                                    onViewLogs = { navController.navigate("logs") },
                                    onViewHistory = { navController.navigate("history") }
                                )
                            }

                            composable("history") {
                                HistoryScreen(
                                    sessionRepository = sessionRepository,
                                    enableIsotonicMode = enableIsotonicMode,
                                    onBack = { navController.popBackStack() },
                                    onViewSession = { id, type ->
                                        if (type == SessionType.ISOTONIC) {
                                            val rawId = id.replace("RAW_", "").toLongOrNull() ?: -1L
                                            navController.navigate("session_details/$rawId")
                                        } else {
                                            val isoId = id.replace("ISO_", "")
                                            navController.navigate("iso_session_details/$isoId")
                                        }
                                    }
                                )
                            }

                            composable("session_details/{sessionId}") { backStackEntry ->
                                val sessionIdStr = backStackEntry.arguments?.getString("sessionId") ?: "-1"
                                val sessionId = sessionIdStr.toLongOrNull() ?: -1L
                                SessionDetailScreen(
                                    sessionId = sessionId,
                                    rawRepository = rawRepository,
                                    recentMuscles = recentMuscles,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("iso_session_details/{sessionId}") { backStackEntry ->
                                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                                IsoSessionDetailScreen(
                                    sessionId = sessionId,
                                    sessionRepository = sessionRepository,
                                    useLbs = useLbs,
                                    recentEquipment = unifiedEquipment,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("logs") {
                                LogViewerScreen(onDismiss = { navController.popBackStack() })
                            }
                        }

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 100.dp)
                        )
                    }

                    if (showWeightPrompt) {
                        AlertDialog(
                            onDismissRequest = { showWeightPrompt = false },
                            title = { Text(if (isBasicTimerPage) "Set Basic Timer Weight" else "Override Target Weight") },
                            text = {
                                OutlinedTextField(
                                    value = weightInputText,
                                    onValueChange = { weightInputText = it },
                                    label = { Text(if (useLbs) "Weight (lbs)" else "Weight (kg)") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                )
                            },
                            confirmButton = {
                                Button(onClick = {
                                    weightInputText.toDoubleOrNull()?.let { typedWeight ->
                                        val internalKg = if (useLbs) typedWeight / 2.20462 else typedWeight
                                        if (isBasicTimerPage) {
                                            currentManualWeight = internalKg
                                            lifecycleScope.launch { preferencesRepository.setManualTargetWeight(internalKg) }
                                        } else {
                                            isoOverrideWeight = internalKg
                                        }
                                    }
                                    showWeightPrompt = false
                                }) { Text("Save") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showWeightPrompt = false }) { Text("Cancel") }
                            }
                        )
                    }

                    if (showTensionSheet) {
                        AlertDialog(
                            onDismissRequest = { showTensionSheet = false },
                            shape = RoundedCornerShape(28.dp),
                            title = { Text("Select Tension Source", fontWeight = FontWeight.Bold) },
                            text = {
                                Column {
                                    if (isConnected) {
                                        TextButton(
                                            onClick = { bluetoothManager.disconnect(); showTensionSheet = false },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Disconnect Current Scale", color = MaterialTheme.colorScheme.error)
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }

                                    if (discoveredDevices.isEmpty() && !isConnected) {
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
                                    val m5State by m5StickManager.connectionState.collectAsState()
                                    val m5Data by m5StickManager.accelerationData.collectAsState()

                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            activeKinematicsSource = KinematicsSource.PHONE
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

                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            activeKinematicsSource = KinematicsSource.M5STICK
                                            if (m5State != ConnectionState.Connected) {
                                                m5StickManager.startScanning()
                                            }
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
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(8.dp)
                                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                                    .background(if (m5State == ConnectionState.Connected) Color.Green else MaterialTheme.colorScheme.error)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            if (m5State == ConnectionState.Connected) {
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
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showKinematicsSheet = false }) { Text("Close") }
                            }
                        )
                    }

                    finishedSessionData?.let { result ->
                        RawSummaryScreen(
                            result = result,
                            recentMuscles = recentMuscles,
                            onDismiss = { finishedSessionData = null },
                            onSave = { muscle, side ->
                                lifecycleScope.launch {
                                    val entity = RawSessionEntity(
                                        timestamp = System.currentTimeMillis(),
                                        targetMuscle = muscle,
                                        bodySide = side,
                                        mechanicalWork = result.mechanicalWork,
                                        workoutScore = result.workoutScore,
                                        targetWeight = currentManualWeight,
                                        durationSeconds = result.timeUnderTension,
                                        timeSeries = result.timeSeries,
                                        tensionSeries = result.tensionSeries,
                                        magnitudeSeries = result.magnitudeSeries,
                                        powerSeries = result.powerSeries,
                                        densitySeries = result.densitySeries,
                                        workSeries = result.workSeries,
                                        repTimestamps = result.repTimestamps,
                                        averageRepInterval = result.averageRepInterval,
                                        restDurations = result.restDurations
                                    )
                                    sessionRepository.insertRaw(entity)
                                    finishedSessionData = null
                                    snackbarHostState.showSnackbar("Raw session saved successfully")
                                }
                            }
                        )
                    }

                    if (showIsoSummary) {
                        val mappedSide = when {
                            isoSide?.contains("Left", ignoreCase = true) == true -> "Left"
                            isoSide?.contains("Right", ignoreCase = true) == true -> "Right"
                            else -> "Bilateral"
                        }
                        IsoSummaryScreen(
                            reps = isoSessionManager.completedReps,
                            useLbs = useLbs,
                            initialGripper = isoGripper ?: "",
                            initialSide = mappedSide,
                            recentEquipment = unifiedEquipment,
                            onDismiss = {
                                showIsoSummary = false
                                isoSessionManager.clearSession()
                                webViewBridge.resetSaveFlag()
                            },
                            onSave = { equipment, side ->
                                lifecycleScope.launch {
                                    val sessionEntity = IsoSessionEntity(
                                        id = UUID.randomUUID().toString(),
                                        timestamp = System.currentTimeMillis(),
                                        gripperType = equipment,
                                        side = side,
                                        scrapedGripper = isoGripper,
                                        scrapedSide = mappedSide
                                    )
                                    sessionRepository.insertIsoSession(sessionEntity)
                                    sessionRepository.insertIsoReps(isoSessionManager.completedReps.map {
                                        it.copy(sessionId = sessionEntity.id)
                                    })
                                    showIsoSummary = false
                                    isoSessionManager.clearSession()
                                    webViewBridge.resetSaveFlag()
                                    snackbarHostState.showSnackbar("Isometric session saved successfully")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun setupEventHandlers() {
        lifecycleScope.launch {
            preferencesRepository.manualTargetWeight.collect { currentManualWeight = it }
        }

        var isotonicModeEnabled = true
        lifecycleScope.launch {
            preferencesRepository.enableIsotonicMode.collect { isotonicModeEnabled = it }
        }

        lifecycleScope.launch {
            m5StickManager.accelerationData.collect { (m5x, m5y, m5z) ->
                if (activeKinematicsSource == KinematicsSource.M5STICK) {

                    // Gravity is now stripped natively on the M5Stick hardware!
                    // The data arriving is already pure Linear Acceleration in m/s^2.

                    if (rawSessionManager.isHoldActive) {
                        val isConnected = bluetoothManager.connectionState.value == ConnectionState.Connected
                        val webWeight = webViewBridge.targetWeight.value

                        val activeTension = if (isConnected) {
                            progressorHandler.currentForce.value
                        } else if (webWeight != null && webWeight > 0.0) {
                            webWeight
                        } else {
                            currentManualWeight
                        }

                        // SPIKE FIX: Ignore movements if tension is below 2.0kg (setup/put-down)
                        val activeX = if (activeTension > 2.0) m5x else 0f
                        val activeY = if (activeTension > 2.0) m5y else 0f
                        val activeZ = if (activeTension > 2.0) m5z else 0f

                        rawSessionManager.addSample(
                            activeX, activeY, activeZ, activeTension, System.currentTimeMillis() * 1_000_000
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            webViewBridge.buttonEnabled.collect { isLive ->
                val currentUrl = webViewBridge.currentUrl.value
                val hasAutoWeight = webViewBridge.targetWeight.value != null && webViewBridge.targetWeight.value!! > 0.0
                val isRawEligible = currentUrl.contains("basic-timer") && !hasAutoWeight && isotonicModeEnabled

                if (isRawEligible) {
                    if (isLive) { if (!rawSessionManager.isHoldActive) rawSessionManager.startHold() }
                    else { if (rawSessionManager.isHoldActive) rawSessionManager.stopHold() }
                } else {
                    if (isLive) { if (!isoSessionManager.isRepActive) isoSessionManager.startRep() }
                    else {
                        if (isoSessionManager.isRepActive) {
                            val fallbackWeight = webViewBridge.targetWeight.value ?: currentManualWeight
                            val targetDur = webViewBridge.targetDuration.value
                            isoSessionManager.endRep(fallbackWeight, targetDur)

                            if (isoSessionManager.completedReps.size == 1) {
                                val firstRep = isoSessionManager.completedReps.first()
                                lifecycleScope.launch {
                                    val enableEarlyFail = preferencesRepository.enableEndSessionOnEarlyFail.first()
                                    val thresholdPercent = preferencesRepository.earlyFailThresholdPercent.first()
                                    if (enableEarlyFail && targetDur != null) {
                                        if (firstRep.duration < (targetDur * thresholdPercent)) {
                                            hapticManager.error()
                                            webViewBridge.clickEndSessionButton()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        var isForegroundServiceRunning = false
        var sessionStartTime = 0L

        lifecycleScope.launch {
            webViewBridge.remainingTime.collect { remaining ->
                if (remaining != null && remaining != -9999) {
                    if (!isForegroundServiceRunning) {
                        sessionStartTime = System.currentTimeMillis()
                        isForegroundServiceRunning = true
                    }
                    val elapsed = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
                    TimerForegroundService.updateNotification(this@MainActivity, elapsed, remaining)
                } else {
                    if (isForegroundServiceRunning) {
                        val stopIntent = Intent(this@MainActivity, TimerForegroundService::class.java).apply {
                            action = TimerForegroundService.ACTION_STOP
                        }
                        startService(stopIntent)
                        isForegroundServiceRunning = false
                    }
                }
            }
        }
    }

    private fun processSessionEnd(
        isotonicEnabled: Boolean,
        enableAnalytics: Boolean,
        showIsoSummaryPref: Boolean,
        showRawSummaryPref: Boolean,
        onAutoSaveIso: () -> Unit
    ) {
        val currentUrl = webViewBridge.currentUrl.value
        val hasAutoWeight = (webViewBridge.targetWeight.value ?: 0.0) > 0.0
        val isRawEligible = currentUrl.contains("basic-timer") && !hasAutoWeight && isotonicEnabled

        if (!enableAnalytics) {
            if (isRawEligible) {
                if (rawSessionManager.isHoldActive) rawSessionManager.stopHold()
                rawSessionManager.finalizeSession()
                rawSessionManager = RawSessionManager()
            } else {
                val fallbackWeight = webViewBridge.targetWeight.value ?: currentManualWeight
                if (isoSessionManager.isRepActive) isoSessionManager.endRep(fallbackWeight)
                isoSessionManager.clearSession()
            }
            return
        }

        if (isRawEligible) {
            if (rawSessionManager.isHoldActive) rawSessionManager.stopHold()
            val result = rawSessionManager.finalizeSession()
            if (result.timeUnderTension > 0.5 && showRawSummaryPref) {
                finishedSessionData = result
            }
            rawSessionManager = RawSessionManager()
        } else {
            val fallbackWeight = webViewBridge.targetWeight.value ?: currentManualWeight
            if (isoSessionManager.isRepActive) isoSessionManager.endRep(fallbackWeight)

            if (isoSessionManager.completedReps.isNotEmpty()) {
                if (showIsoSummaryPref) {
                    showIsoSummary = true
                } else {
                    lifecycleScope.launch {
                        val sSide = webViewBridge.sessionSide.value
                        val sGrip = webViewBridge.sessionGripper.value ?: ""
                        val mappedSide = when {
                            sSide?.contains("Left", ignoreCase = true) == true -> "Left"
                            sSide?.contains("Right", ignoreCase = true) == true -> "Right"
                            else -> "Bilateral"
                        }
                        val sessionEntity = IsoSessionEntity(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            gripperType = sGrip,
                            side = mappedSide,
                            scrapedGripper = sGrip.takeIf { it.isNotBlank() },
                            scrapedSide = mappedSide
                        )
                        sessionRepository.insertIsoSession(sessionEntity)
                        sessionRepository.insertIsoReps(isoSessionManager.completedReps.map {
                            it.copy(sessionId = sessionEntity.id)
                        })
                        isoSessionManager.clearSession()
                        onAutoSaveIso()
                    }
                }
            }
        }
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(failRepReceiver)
        sensorManager.unregisterListener(sensorListener)
        bluetoothManager.disconnect()
        m5StickManager.disconnect()
    }
}