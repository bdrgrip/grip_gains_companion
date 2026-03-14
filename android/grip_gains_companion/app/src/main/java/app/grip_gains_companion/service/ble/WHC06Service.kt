package app.grip_gains_companion.service.ble

import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.grip_gains_companion.config.AppConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Protocol handler for Weiheng WH-C06 hanging scale
 * Uses advertisement-based protocol (no GATT connection)
 */
class WHC06Service {

    companion object {
        private const val TAG = "WHC06Service"
        private const val DISCONNECT_TIMEOUT_M10 = 10000L  // 5 seconds without data = disconnected
    }

    var onForceSample: ((Double, Long) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null

    // THE BYPASS: We let the App's UI dictate the hardware math
    var assumeHardwareIsLbs: Boolean = false

    private var baseTimestamp: Long = 0
    private var sampleCounter: Long = 0
    private var disconnectTimer: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        Log.i(TAG, "Starting WHC06 service...")
        baseTimestamp = System.currentTimeMillis() * 1000
        sampleCounter = 0
        resetDisconnectTimer()
    }

    fun stop() {
        Log.i(TAG, "Stopping WHC06 service...")
        cancelDisconnectTimer()
    }

    fun processAdvertisement(scanResult: ScanResult) {
        val manufacturerData = scanResult.scanRecord?.getManufacturerSpecificData(AppConstants.WHC06_MANUFACTURER_ID)
        if (manufacturerData == null) return

        val weight = parseManufacturerData(manufacturerData)
        if (weight == null) return

        resetDisconnectTimer()
        val timestamp = generateTimestamp()
        onForceSample?.invoke(weight, timestamp)
    }

    private fun parseManufacturerData(data: ByteArray): Double? {
        if (data.size < AppConstants.WHC06_MIN_DATA_SIZE) return null

        val weightOffset = AppConstants.WHC06_WEIGHT_BYTE_OFFSET
        val buffer = ByteBuffer.wrap(data, weightOffset, 2)
        buffer.order(ByteOrder.BIG_ENDIAN)
        val rawWeight = buffer.short.toInt()

        val rawValue = rawWeight.toDouble() / AppConstants.WHC06_WEIGHT_DIVISOR

        // THE FIX: We ignore the scale's broken unit byte entirely.
        // If the app is in LBS, we force the scale's output back into pure KG for the physics engine!
        return if (assumeHardwareIsLbs) {
            rawValue / 2.20462
        } else {
            rawValue
        }
    }

    private fun generateTimestamp(): Long {
        sampleCounter++
        return baseTimestamp + (sampleCounter * 1_000_000)
    }

    private fun resetDisconnectTimer() {
        cancelDisconnectTimer()
        disconnectTimer = Runnable {
            Log.i(TAG, "WHC06 disconnect timeout - no advertisements received")
            onDisconnect?.invoke()
        }
        handler.postDelayed(disconnectTimer!!, DISCONNECT_TIMEOUT_M10)
    }

    private fun cancelDisconnectTimer() {
        disconnectTimer?.let { handler.removeCallbacks(it) }
        disconnectTimer = null
    }
}