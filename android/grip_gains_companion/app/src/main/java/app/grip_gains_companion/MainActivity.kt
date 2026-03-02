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
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import app.grip_gains_companion.ui.screens.LogViewerScreen
import app.grip_gains_companion.ui.screens.MainScreen
import app.grip_gains_companion.ui.screens.SettingsScreen
import app.grip_gains_companion.ui.screens.SetupDashboardScreen
import app.grip_gains_companion.ui.screens.KinematicsSource
import app.grip_gains_companion.ui.screens.TensionSource
import app.grip_gains_companion.ui.theme.GripGainsTheme
import app.grip_gains_companion.util.HapticManager
import app.grip_gains_companion.util.ToneGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var progressorHandler: ProgressorHandler
    private lateinit var webViewBridge: WebViewBridge
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var hapticManager: HapticManager

    // --- RAW KINEMATICS STATE ---
    private lateinit var sensorManager: SensorManager
    private var linearAccelerometer: Sensor? = null
    private var rawSessionManager = RawSessionManager()

    private var activeKinematicsSource = KinematicsSource.PHONE
    private var currentManualWeight = 20.0
    private var latestX = 0f
    private var latestY = 0f
    private var latestZ = 0f

    // --- DATABASE & UI STATE ---
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
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            bluetoothManager.startScanning()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bluetoothManager = BluetoothManager(this)
        progressorHandler = ProgressorHandler()
        webViewBridge = WebViewBridge()
        preferencesRepository = PreferencesRepository(this)
        hapticManager = HapticManager(this)

        database = AppDatabase.getDatabase(this)
        rawRepository = RawSessionRepository(database.rawSessionDao())

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        sensorManager.registerListener(
            sensorListener,
            linearAccelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )

        lifecycleScope.launch {
            preferencesRepository.initializeUnitsIfNeeded()
        }

        bluetoothManager.onForceSample = { force, timestamp ->
            lifecycleScope.launch {
                progressorHandler.processSample(force, timestamp)
            }
        }

        setupEventHandlers()

        if (hasAllPermissions()) {
            bluetoothManager.startScanning()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            val connectionState by bluetoothManager.connectionState.collectAsState()
            val isConnected = connectionState == ConnectionState.Connected
            val isReconnecting = connectionState == ConnectionState.Reconnecting

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

            val currentWebWeight by webViewBridge.targetWeight.collectAsState()
            val isBasicTimer = currentWebWeight == null || currentWebWeight == 0.0

            LaunchedEffect(enableCalibration) {
                progressorHandler.enableCalibration = enableCalibration
            }

            var isTrainingActive by remember { mutableStateOf(false) }

            var showSettings by remember { mutableStateOf(false) }
            var showLogViewer by remember { mutableStateOf(false) }
            var showHistory by remember { mutableStateOf(false) }
            var showWeightPrompt by remember { mutableStateOf(false) }
            var weightInputText by remember { mutableStateOf(currentManualWeight.toString()) }

            LaunchedEffect(connectionState) {
                if (connectionState == ConnectionState.Connected && enableHaptics) hapticManager.success()
                if (connectionState == ConnectionState.Disconnected) progressorHandler.reset()
            }

            GripGainsTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        showHistory -> {
                            app.grip_gains_companion.ui.screens.HistoryScreen(
                                rawRepository = rawRepository,
                                onBack = { showHistory = false }
                            )
                        }
                        showLogViewer -> {
                            LogViewerScreen(onDismiss = { showLogViewer = false })
                        }
                        showSettings -> {
                            SettingsScreen(
                                preferencesRepository = preferencesRepository,
                                bluetoothManager = bluetoothManager,
                                webViewBridge = webViewBridge,
                                onDismiss = { showSettings = false },
                                onDisconnect = {
                                    showSettings = false
                                    isTrainingActive = false
                                    bluetoothManager.disconnect()
                                },
                                onConnectDevice = {
                                    showSettings = false
                                    isTrainingActive = false
                                    bluetoothManager.startScanning()
                                },
                                onRecalibrate = {
                                    showSettings = false
                                    progressorHandler.recalibrate()
                                    webViewBridge.refreshButtonState()
                                },
                                onViewLogs = {
                                    showSettings = false
                                    showLogViewer = true
                                },
                                onViewHistory = {
                                    showSettings = false
                                    showHistory = true
                                }
                            )
                        }
                        isTrainingActive -> {
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
                                    manualTargetWeight = manualTargetWeight,
                                    weightTolerance = weightTolerance,
                                    onSettingsTap = { showSettings = true },
                                    onHistoryTap = { showHistory = true }, // NEW TRIGGER HERE
                                    onUnitToggle = {
                                        lifecycleScope.launch { preferencesRepository.setUseLbs(!useLbs) }
                                    }
                                )

                                if (!isConnected && isBasicTimer) {
                                    ExtendedFloatingActionButton(
                                        onClick = {
                                            weightInputText = currentManualWeight.toString()
                                            showWeightPrompt = true
                                        },
                                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
                                    ) {
                                        Text("Basic Timer Weight: $currentManualWeight")
                                    }
                                }
                            }
                        }
                        else -> {
                            SetupDashboardScreen(
                                bluetoothManager = bluetoothManager,
                                onStartTraining = { tensionSrc, weight, kinematicsSrc ->
                                    currentManualWeight = weight
                                    activeKinematicsSource = kinematicsSrc
                                    isTrainingActive = true
                                }
                            )
                        }
                    }

                    if (showWeightPrompt) {
                        AlertDialog(
                            onDismissRequest = { showWeightPrompt = false },
                            title = { Text("RAW Target Weight") },
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
                                    weightInputText.toDoubleOrNull()?.let { currentManualWeight = it }
                                    showWeightPrompt = false
                                }) { Text("Set") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showWeightPrompt = false }) { Text("Cancel") }
                            }
                        )
                    }

                    finishedSessionData?.let { result ->
                        app.grip_gains_companion.ui.screens.RawSummaryScreen(
                            result = result,
                            onDismiss = { finishedSessionData = null },
                            onSave = { muscle, side ->
                                lifecycleScope.launch {
                                    val entity = RawSessionEntity(
                                        timestamp = System.currentTimeMillis(),
                                        targetMuscle = muscle,
                                        bodySide = side,
                                        mechanicalWork = result.mechanicalWork,
                                        targetWeight = currentManualWeight,
                                        durationSeconds = if (result.timeSeries.isNotEmpty()) result.timeSeries.last() else 0.0,
                                        timeSeries = result.timeSeries,
                                        tensionSeries = result.tensionSeries,
                                        magnitudeSeries = result.magnitudeSeries,
                                        powerSeries = result.powerSeries,
                                        fluxSeries = result.fluxSeries,
                                        workSeries = result.workSeries
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
                if (!isConnected) {
                    if (isLive && !rawSessionManager.isHoldActive) {
                        rawSessionManager.startHold()
                        Log.d("RAW_SCORE", "▶️ Hold Started")
                    } else if (!isLive && rawSessionManager.isHoldActive) {
                        rawSessionManager.stopHold()
                        Log.d("RAW_SCORE", "⏸️ Hold Stopped")
                    }
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
                            kotlinx.coroutines.delay(1400)

                            if (wasTicking) {
                                if (rawSessionManager.isHoldActive) rawSessionManager.stopHold()

                                val result = rawSessionManager.finalizeSession()
                                Log.d("RAW_SCORE", "🔥 SILENCE DETECTED. FINALIZING. Score: ${result.workoutScore}")

                                if (result.workoutScore >= 0) {
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

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(sensorListener)
        bluetoothManager.disconnect()
    }
}