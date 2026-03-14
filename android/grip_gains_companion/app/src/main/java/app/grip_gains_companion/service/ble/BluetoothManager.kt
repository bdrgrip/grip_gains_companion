package app.grip_gains_companion.service.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.grip_gains_companion.config.AppConstants
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.model.DeviceType
import app.grip_gains_companion.model.ForceDevice
import app.grip_gains_companion.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothManager"
        private const val PREFS_NAME = "bluetooth_prefs"
        private const val KEY_SELECTED_DEVICE_TYPE = "selected_device_type"
        private const val KEY_LAST_CONNECTED_DEVICE = "last_connected_device"
        private val CLIENT_CHARACTERISTIC_CONFIG: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    private var bluetoothGatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var pitchSixDeviceModeCharacteristic: BluetoothGattCharacteristic? = null

    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var pendingDevice: ForceDevice? = null
    private var shouldAutoReconnect = true

    private var pitchSixService: PitchSixService? = null
    private var whc06Service: WHC06Service? = null

    // Holds the UI state to sync with the clone hardware
    private var hardwareUnitIsLbs: Boolean = false

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Initializing)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<ForceDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ForceDevice>> = _discoveredDevices.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceType = MutableStateFlow<DeviceType?>(null)
    val connectedDeviceType: StateFlow<DeviceType?> = _connectedDeviceType.asStateFlow()

    private val _selectedDeviceType = MutableStateFlow(DeviceType.TINDEQ_PROGRESSOR)
    val selectedDeviceType: StateFlow<DeviceType> = _selectedDeviceType.asStateFlow()

    var onForceSample: ((Double, Long) -> Unit)? = null
    private var lastConnectedDeviceAddress: String? = null

    init {
        val savedType = prefs.getString(KEY_SELECTED_DEVICE_TYPE, null)
        DeviceType.fromString(savedType)?.let {
            _selectedDeviceType.value = it
        }
        lastConnectedDeviceAddress = prefs.getString(KEY_LAST_CONNECTED_DEVICE, null)

        if (bluetoothAdapter == null) {
            _connectionState.value = ConnectionState.Error("Bluetooth not available")
        } else if (!bluetoothAdapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("Bluetooth is off")
        } else {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun setSelectedDeviceType(type: DeviceType) {
        _selectedDeviceType.value = type
        prefs.edit().putString(KEY_SELECTED_DEVICE_TYPE, type.name).apply()
        _discoveredDevices.value = emptyList()
    }

    // Called from MainActivity to sync the math!
    fun setHardwareUnitIsLbs(isLbs: Boolean) {
        hardwareUnitIsLbs = isLbs
        whc06Service?.assumeHardwareIsLbs = isLbs
    }

    fun startScanning() {
        if (bluetoothAdapter?.isEnabled != true) {
            _connectionState.value = ConnectionState.Error("Bluetooth is off")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (!isLocationEnabled) {
                Log.e(TAG, "Location services disabled")
                _connectionState.value = ConnectionState.Error("Location services required")
                return
            }
        }

        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.Scanning

        bluetoothLeScanner = bluetoothManager.adapter?.bluetoothLeScanner

        if (bluetoothLeScanner == null) {
            _connectionState.value = ConnectionState.Error("Failed to initialize scanner. Try restarting Bluetooth.")
            return
        }

        // AGGRESSIVE SCANNING: Stop Android from throttling your connection
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        bluetoothLeScanner?.startScan(null, settings, scanCallback)
    }

    fun stopScanning() {
        bluetoothLeScanner?.stopScan(scanCallback)
        if (_connectionState.value == ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name
            val deviceAddress = result.device.address

            // THE RECONNECT FIX: Process advertisements if we are Connected OR Reconnecting!
            val isWhc06Active = _connectedDeviceType.value == DeviceType.WEIHENG_WHC06 || pendingDevice?.type == DeviceType.WEIHENG_WHC06
            val isExpectedDevice = pendingDevice != null && deviceAddress == pendingDevice?.address

            if (isWhc06Active && isExpectedDevice) {
                // If we were reconnecting, and we just heard a packet, we are officially connected again!
                if (_connectionState.value == ConnectionState.Reconnecting || _connectionState.value == ConnectionState.Connecting) {
                    _connectionState.value = ConnectionState.Connected
                    _connectedDeviceName.value = pendingDevice?.name ?: "WH-C06"
                    retryCount = 0
                }
                whc06Service?.processAdvertisement(result)
            }

            if (deviceName != null && deviceName.isNotBlank()) {
                val inferredType = when {
                    deviceName.contains("Progressor", ignoreCase = true) -> DeviceType.TINDEQ_PROGRESSOR
                    deviceName.contains("PitchSix", ignoreCase = true) || deviceName.contains("Force Board", ignoreCase = true) -> DeviceType.PITCH_SIX_FORCE_BOARD
                    deviceName.contains("WH-C06", ignoreCase = true) || deviceName.contains("IF_B7", ignoreCase = true) -> DeviceType.WEIHENG_WHC06
                    else -> null
                }

                if (inferredType != null) {
                    val device = ForceDevice.fromScanResult(result, inferredType)
                        ?: ForceDevice(deviceAddress, deviceName, inferredType)

                    val currentList = _discoveredDevices.value.toMutableList()
                    val existingIndex = currentList.indexOfFirst { it.address == device.address }

                    if (existingIndex >= 0) {
                        currentList[existingIndex] = device
                    } else {
                        currentList.add(device)
                        if (device.address == lastConnectedDeviceAddress) {
                            connect(device)
                        }
                    }
                    _discoveredDevices.value = currentList
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = ConnectionState.Error("Scan failed: $errorCode")
        }
    }

    fun connect(device: ForceDevice) {
        stopScanning()
        cancelRetryTimer()
        pendingDevice = device
        shouldAutoReconnect = true
        _connectionState.value = ConnectionState.Connecting

        when (device.type) {
            DeviceType.WEIHENG_WHC06 -> connectWHC06(device)
            else -> {
                val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                bluetoothGatt = bluetoothDevice?.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }
        }
    }

    private fun connectWHC06(device: ForceDevice) {
        whc06Service = WHC06Service().apply {
            assumeHardwareIsLbs = hardwareUnitIsLbs // Inject the UI state immediately on connect!
            onForceSample = { weight, timestamp -> this@BluetoothManager.onForceSample?.invoke(weight, timestamp) }
            onDisconnect = {
                if (shouldAutoReconnect) {
                    _connectionState.value = ConnectionState.Reconnecting
                    scheduleRetry()
                } else {
                    _connectionState.value = ConnectionState.Disconnected
                    _connectedDeviceName.value = null
                    _connectedDeviceType.value = null
                }
            }
        }

        whc06Service?.start()

        _connectionState.value = ConnectionState.Connected
        _connectedDeviceName.value = device.name
        _connectedDeviceType.value = device.type
        lastConnectedDeviceAddress = device.address
        prefs.edit().putString(KEY_LAST_CONNECTED_DEVICE, device.address).apply()

        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        bluetoothLeScanner?.startScan(null, settings, scanCallback)
    }

    fun disconnect(preserveAutoReconnect: Boolean = false) {
        shouldAutoReconnect = false
        cancelRetryTimer()
        pendingDevice = null
        stopScanning()

        pitchSixService?.stop()
        pitchSixService = null
        whc06Service?.stop()
        whc06Service = null

        bluetoothGatt?.close()
        bluetoothGatt = null
        notifyCharacteristic = null
        writeCharacteristic = null
        _connectedDeviceName.value = null
        _connectedDeviceType.value = null

        if (!preserveAutoReconnect) {
            lastConnectedDeviceAddress = null
            prefs.edit().remove(KEY_LAST_CONNECTED_DEVICE).apply()
        }

        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.Disconnected

        if (!preserveAutoReconnect) startScanning()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    retryCount = 0
                    _connectionState.value = ConnectionState.Connected
                    _connectedDeviceName.value = gatt.device.name ?: pendingDevice?.type?.displayName ?: "Unknown"
                    _connectedDeviceType.value = pendingDevice?.type
                    lastConnectedDeviceAddress = gatt.device.address
                    prefs.edit().putString(KEY_LAST_CONNECTED_DEVICE, gatt.device.address).apply()
                    handler.post { gatt.discoverServices() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (shouldAutoReconnect) {
                        _connectionState.value = ConnectionState.Reconnecting
                        scheduleRetry()
                    } else {
                        _connectionState.value = ConnectionState.Disconnected
                        _connectedDeviceName.value = null
                        _connectedDeviceType.value = null
                    }
                    notifyCharacteristic = null
                    writeCharacteristic = null
                    pitchSixService?.stop()
                    pitchSixService = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            when (pendingDevice?.type) {
                DeviceType.TINDEQ_PROGRESSOR -> setupProgressorService(gatt)
                DeviceType.PITCH_SIX_FORCE_BOARD -> setupPitchSixService(gatt)
                else -> {}
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (pendingDevice?.type) {
                    DeviceType.TINDEQ_PROGRESSOR -> startProgressorMeasurement()
                    DeviceType.PITCH_SIX_FORCE_BOARD -> pitchSixService?.let { service ->
                        pitchSixDeviceModeCharacteristic?.let { char -> service.startStreaming(gatt, char) }
                    }
                    else -> {}
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicChanged(characteristic, value)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value
            if (value != null) handleCharacteristicChanged(characteristic, value)
        }

        private fun handleCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            when (pendingDevice?.type) {
                DeviceType.TINDEQ_PROGRESSOR -> {
                    if (characteristic.uuid == AppConstants.PROGRESSOR_NOTIFY_CHARACTERISTIC_UUID) {
                        parseProgressorNotification(value)
                    }
                }
                DeviceType.PITCH_SIX_FORCE_BOARD -> pitchSixService?.parseNotification(value)
                else -> {}
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {}
    }

    private fun setupProgressorService(gatt: BluetoothGatt) {
        val service = gatt.getService(AppConstants.PROGRESSOR_SERVICE_UUID) ?: return
        notifyCharacteristic = service.getCharacteristic(AppConstants.PROGRESSOR_NOTIFY_CHARACTERISTIC_UUID)
        writeCharacteristic = service.getCharacteristic(AppConstants.PROGRESSOR_WRITE_CHARACTERISTIC_UUID)
        if (notifyCharacteristic == null) return
        enableNotifications(gatt, notifyCharacteristic!!)
    }

    private fun startProgressorMeasurement() {
        val characteristic = writeCharacteristic ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(characteristic, AppConstants.PROGRESSOR_START_WEIGHT_COMMAND, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = AppConstants.PROGRESSOR_START_WEIGHT_COMMAND
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            bluetoothGatt?.writeCharacteristic(characteristic)
        }
    }

    private fun parseProgressorNotification(data: ByteArray) {
        if (data.size < AppConstants.PACKET_MIN_SIZE || data[0] != AppConstants.WEIGHT_DATA_PACKET_TYPE) return
        val payload = data.copyOfRange(2, data.size)
        var offset = 0
        while (offset + AppConstants.PROGRESSOR_SAMPLE_SIZE <= payload.size) {
            val weightBytes = payload.copyOfRange(offset, offset + 4)
            val timeBytes = payload.copyOfRange(offset + 4, offset + 8)
            val weightFloat = ByteBuffer.wrap(weightBytes).order(ByteOrder.LITTLE_ENDIAN).float
            val timestamp = ByteBuffer.wrap(timeBytes).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            onForceSample?.invoke(weightFloat.toDouble(), timestamp)
            offset += AppConstants.PROGRESSOR_SAMPLE_SIZE
        }
    }

    private fun setupPitchSixService(gatt: BluetoothGatt) {
        val forceService = gatt.getService(AppConstants.PITCH_SIX_FORCE_SERVICE_UUID) ?: return
        val deviceModeService = gatt.getService(AppConstants.PITCH_SIX_DEVICE_MODE_SERVICE_UUID) ?: return
        notifyCharacteristic = forceService.getCharacteristic(AppConstants.PITCH_SIX_FORCE_CHARACTERISTIC_UUID) ?: return
        pitchSixDeviceModeCharacteristic = deviceModeService.getCharacteristic(AppConstants.PITCH_SIX_DEVICE_MODE_CHARACTERISTIC_UUID) ?: return
        writeCharacteristic = forceService.getCharacteristic(AppConstants.PITCH_SIX_TARE_CHARACTERISTIC_UUID)

        pitchSixService = PitchSixService().apply {
            onForceSample = { weight, timestamp -> this@BluetoothManager.onForceSample?.invoke(weight, timestamp) }
        }
        pitchSixService?.start()
        enableNotifications(gatt, notifyCharacteristic!!)
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    private fun calculateRetryDelay(): Long = minOf(1000L * (1L shl minOf(retryCount, 5)), AppConstants.MAX_RETRY_DELAY_MS)

    private fun scheduleRetry() {
        val device = pendingDevice ?: return
        retryCount++
        handler.postDelayed({
            if (shouldAutoReconnect) {
                _connectionState.value = ConnectionState.Reconnecting
                when (device.type) {
                    DeviceType.WEIHENG_WHC06 -> connectWHC06(device)
                    else -> {
                        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                        bluetoothGatt = bluetoothDevice?.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    }
                }
            }
        }, calculateRetryDelay())
    }

    private fun cancelRetryTimer() {
        handler.removeCallbacksAndMessages(null)
    }
}