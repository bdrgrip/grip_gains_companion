package app.grip_gains_companion

import android.Manifest
import android.content.Context
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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.grip_gains_companion.data.PreferencesRepository
import app.grip_gains_companion.database.AppDatabase
import app.grip_gains_companion.database.RawSessionEntity
import app.grip_gains_companion.database.RawSessionRepository
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.service.ProgressorHandler
import app.grip_gains_companion.service.RawSessionManager
import app.grip_gains_companion.service.SessionResult
import app.grip_gains_companion.service.ble.BluetoothManager
import app.grip_gains_companion.service.web.WebViewBridge
import app.grip_gains_companion.ui.screens.*
import app.grip_gains_companion.ui.theme.GripGainsTheme
import app.grip_gains_companion.util.HapticManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.grip_gains_companion.model.KinematicsSource
import app.grip_gains_companion.model.TensionSource

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var progressorHandler: ProgressorHandler
    private lateinit var webViewBridge: WebViewBridge
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var hapticManager: HapticManager

    private lateinit var sensorManager: SensorManager
    private var linearAccelerometer: Sensor? = null
    private var rawSessionManager = RawSessionManager()

    private var activeKinematicsSource by mutableStateOf(KinematicsSource.PHONE)
    private var currentManualWeight by mutableDoubleStateOf(20.0)

    private var latestX = 0f
    private var latestY = 0f
    private var latestZ = 0f

    private lateinit var database: AppDatabase
    private lateinit var rawRepository: RawSessionRepository
    private var finishedSessionData by mutableStateOf<SessionResult?>(null)

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
                rawSessionManager.addSample(latestX, latestY, latestZ, activeTension, event.timestamp)
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.all { it.value }) bluetoothManager.startScanning()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Init Services
        bluetoothManager = BluetoothManager(this)
        progressorHandler = ProgressorHandler()
        webViewBridge = WebViewBridge()
        preferencesRepository = PreferencesRepository(this)
        hapticManager = HapticManager(this)
        database = AppDatabase.getDatabase(this)
        rawRepository = RawSessionRepository(database.rawSessionDao())

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        sensorManager.registerListener(sensorListener, linearAccelerometer, SensorManager.SENSOR_DELAY_GAME)

        lifecycleScope.launch { preferencesRepository.initializeUnitsIfNeeded() }
        bluetoothManager.onForceSample = { force, timestamp ->
            lifecycleScope.launch { progressorHandler.processSample(force, timestamp) }
        }

        setupEventHandlers()

        if (hasAllPermissions()) bluetoothManager.startScanning()
        else permissionLauncher.launch(requiredPermissions)

        setContent {
            val navController = rememberNavController()
            var showWeightPrompt by remember { mutableStateOf(false) }
            var weightInputText by remember { mutableStateOf(currentManualWeight.toString()) }

            val connectionState by bluetoothManager.connectionState.collectAsState()
            val isConnected = connectionState == ConnectionState.Connected

            val useLbs by preferencesRepository.useLbs.collectAsState(initial = false)
            val showStatusBar by preferencesRepository.showStatusBar.collectAsState(initial = true)
            val expandedForceBar by preferencesRepository.expandedForceBar.collectAsState(initial = true)
            val showForceGraph by preferencesRepository.showForceGraph.collectAsState(initial = true)
            val forceGraphWindow by preferencesRepository.forceGraphWindow.collectAsState(initial = 5)
            val enableTargetWeight by preferencesRepository.enableTargetWeight.collectAsState(initial = true)
            val useManualTarget by preferencesRepository.useManualTarget.collectAsState(initial = false)
            val manualTargetWeight by preferencesRepository.manualTargetWeight.collectAsState(initial = 20.0)
            val weightTolerance by preferencesRepository.weightTolerance.collectAsState(initial = 0.5)
            val enableHaptics by preferencesRepository.enableHaptics.collectAsState(initial = true)
            val enableCalibration by preferencesRepository.enableCalibration.collectAsState(initial = true)

            LaunchedEffect(enableCalibration) { progressorHandler.enableCalibration = enableCalibration }
            LaunchedEffect(connectionState) {
                if (connectionState == ConnectionState.Connected && enableHaptics) hapticManager.success()
                if (connectionState == ConnectionState.Disconnected) progressorHandler.reset()
            }

            GripGainsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NavHost(
                        navController = navController,
                        startDestination = "setup",
                        enterTransition = {
                            if (targetState.destination.route == "main") fadeIn(tween(400))
                            else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(300))
                        },
                        exitTransition = {
                            if (targetState.destination.route == "main") fadeOut(tween(400))
                            else fadeOut(tween(300))
                        },
                        popEnterTransition = { fadeIn(tween(300)) },
                        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(300)) }
                    ) {
                        composable("setup") {
                            SetupDashboardScreen(
                                preferencesRepository = preferencesRepository, // Added this parameter
                                bluetoothManager = bluetoothManager,
                                onStartTraining = { source, weight, kinematicsSrc -> // Named the first parameter 'source'
                                    currentManualWeight = weight
                                    activeKinematicsSource = kinematicsSrc
                                    // You can use 'source' here if you need to trigger specific BLE logic for the scale
                                    navController.navigate("main") { popUpTo(0) }
                                }
                            )
                        }

                        composable("main") {
                            DisposableEffect(Unit) {
                                enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
                                onDispose {
                                    enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
                                }
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                                MainScreen(
                                    bluetoothManager = bluetoothManager,
                                    progressorHandler = progressorHandler,
                                    webViewBridge = webViewBridge,
                                    showStatusBar = showStatusBar,
                                    expandedForceBar = expandedForceBar,
                                    showForceGraph = showForceGraph,
                                    forceGraphWindow = forceGraphWindow,
                                    useLbs = useLbs,
                                    enableTargetWeight = enableTargetWeight,
                                    useManualTarget = useManualTarget,
                                    manualTargetWeight = currentManualWeight, // Use the fast local state here
                                    weightTolerance = weightTolerance,
                                    onSettingsTap = { navController.navigate("settings") },
                                    onHistoryTap = { navController.navigate("history") },
                                    onUnitToggle = { lifecycleScope.launch { preferencesRepository.setUseLbs(!useLbs) } },
                                    onSetManualWeightTap = {
                                        weightInputText = currentManualWeight.toString()
                                        showWeightPrompt = true
                                    }
                                )

                                if (showWeightPrompt) {
                                    AlertDialog(
                                        onDismissRequest = { showWeightPrompt = false },
                                        title = { Text("Set Basic Timer Weight") },
                                        text = {
                                            OutlinedTextField(
                                                value = weightInputText,
                                                onValueChange = { weightInputText = it },
                                                label = { Text(if (useLbs) "Weight (lbs)" else "Weight (kg)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                        },
                                        confirmButton = {
                                            Button(onClick = {
                                                weightInputText.toDoubleOrNull()?.let { newWeight ->
                                                    currentManualWeight = newWeight
                                                    // Force the DataStore to update in the background
                                                    lifecycleScope.launch { preferencesRepository.setManualTargetWeight(newWeight) }
                                                }
                                                showWeightPrompt = false
                                            }) { Text("Save") }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showWeightPrompt = false }) { Text("Cancel") }
                                        }
                                    )
                                }
                            }
                        }

                        composable("settings") {
                            SettingsScreen(
                                preferencesRepository = preferencesRepository,
                                bluetoothManager = bluetoothManager,
                                webViewBridge = webViewBridge,
                                activeKinematicsSource = activeKinematicsSource,
                                currentManualWeight = currentManualWeight,
                                onKinematicsChange = { activeKinematicsSource = it },
                                onWeightChange = { currentManualWeight = it },
                                onDismiss = { navController.popBackStack() },
                                onDisconnect = {
                                    bluetoothManager.disconnect()
                                    navController.navigate("setup") { popUpTo(0) }
                                },
                                onConnectDevice = {
                                    bluetoothManager.startScanning()
                                    navController.popBackStack()
                                },
                                onRecalibrate = {
                                    progressorHandler.recalibrate()
                                    android.widget.Toast.makeText(this@MainActivity, "Scale Zeroed", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onViewLogs = { navController.navigate("logs") },
                                onViewHistory = { navController.navigate("history") }
                            )
                        }

                        composable("history") {
                            HistoryScreen(
                                rawRepository = rawRepository,
                                onBack = { navController.popBackStack() },
                                onViewSession = { id -> navController.navigate("session_details/$id") }
                            )
                        }

                        composable("session_details/{sessionId}") { backStackEntry ->
                            val sessionIdStr = backStackEntry.arguments?.getString("sessionId") ?: "-1"
                            val sessionId = sessionIdStr.toLongOrNull() ?: -1L

                            SessionDetailScreen(
                                sessionId = sessionId,
                                rawRepository = rawRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("logs") {
                            LogViewerScreen(onDismiss = { navController.popBackStack() })
                        }
                    }

                    finishedSessionData?.let { result ->
                        RawSummaryScreen(
                            result = result,
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
                                    rawRepository.insert(entity)
                                    finishedSessionData = null
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
            preferencesRepository.manualTargetWeight.collect { weight -> currentManualWeight = weight }
        }

        lifecycleScope.launch {
            webViewBridge.buttonEnabled.collect { isLive ->
                val isConnected = bluetoothManager.connectionState.value == ConnectionState.Connected
                val currentUrl = webViewBridge.currentUrl.value
                val hasAutoWeight = webViewBridge.targetWeight.value != null && webViewBridge.targetWeight.value!! > 0.0

                // GATEKEEPER: Only allow RAW session if on basic-timer AND no auto-weight is set
                val isRawEligible = currentUrl.contains("basic-timer") && !hasAutoWeight

                if (!isConnected && isRawEligible) {
                    if (isLive && !rawSessionManager.isHoldActive) {
                        rawSessionManager.startHold()
                    } else if (!isLive && rawSessionManager.isHoldActive) {
                        rawSessionManager.stopHold()
                    }
                } else if (!isRawEligible && rawSessionManager.isHoldActive) {
                    // Failsafe: Stop the engine if it somehow started but is no longer eligible
                    rawSessionManager.stopHold()
                }
            }
        }

        lifecycleScope.launch {
            var wasTicking = false
            var watchdogJob: kotlinx.coroutines.Job? = null

            webViewBridge.remainingTime.collect { remaining ->
                val isConnected = bluetoothManager.connectionState.value == ConnectionState.Connected
                if (!isConnected) {
                    if (remaining != null) {
                        if (remaining > 0) wasTicking = true
                        watchdogJob?.cancel()
                        watchdogJob = launch {
                            kotlinx.coroutines.delay(3500)
                            if (wasTicking) {
                                if (rawSessionManager.isHoldActive) rawSessionManager.stopHold()
                                val result = rawSessionManager.finalizeSession()
                                if (result.workoutScore >= 0 && result.timeSeries.isNotEmpty()) {
                                    finishedSessionData = result
                                }
                                wasTicking = false
                                rawSessionManager = RawSessionManager()
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            webViewBridge.buttonEnabled.collect { enabled -> progressorHandler.canEngage = enabled }
        }

        lifecycleScope.launch {
            webViewBridge.targetWeight.collect { weight ->
                val enableTargetWeight = preferencesRepository.enableTargetWeight.first()
                val useManualTarget = preferencesRepository.useManualTarget.first()
                if (enableTargetWeight && !useManualTarget) {
                    progressorHandler.targetWeight = weight
                }
            }
        }
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(sensorListener)
        bluetoothManager.disconnect()
    }
}