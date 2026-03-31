package app.grip_gains_companion.service.ble

import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import android.util.Log

class WHC06Service {

    companion object {
        private const val TAG = "WHC06Service"
        private const val DISCONNECT_TIMEOUT_MS = 15000L // Bumped to 15s to tolerate crowded gym interference
    }

    var onForceSample: ((Double, Long) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null
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
        val rawBytes = scanResult.scanRecord?.bytes ?: return
        val weight = parseRawBytes(rawBytes) ?: return

        resetDisconnectTimer()
        val timestamp = generateTimestamp()
        onForceSample?.invoke(weight, timestamp)
    }

    private fun parseRawBytes(data: ByteArray): Double? {
        var i = 0
        while (i < data.size - 1) {
            val length = data[i].toInt() and 0xFF
            if (length == 0) break

            val type = data[i + 1].toInt() and 0xFF
            if (type == 0xFF && length >= 15) {
                val dataStart = i + 2
                val highByte = data[dataStart + 12].toInt() and 0xFF
                val lowByte = data[dataStart + 13].toInt() and 0xFF

                val rawWeight = (highByte shl 8) or lowByte
                val rawValue = rawWeight.toDouble() / 100.0

                return if (assumeHardwareIsLbs) {
                    rawValue / 2.20462
                } else {
                    rawValue
                }
            }
            i += length + 1
        }
        return null
    }

    private fun generateTimestamp(): Long {
        sampleCounter++
        return baseTimestamp + (sampleCounter * 10_000)
    }

    private fun resetDisconnectTimer() {
        cancelDisconnectTimer()
        disconnectTimer = Runnable {
            Log.i(TAG, "WHC06 disconnect timeout - no advertisements received")
            onDisconnect?.invoke()
        }
        handler.postDelayed(disconnectTimer!!, DISCONNECT_TIMEOUT_MS)
    }

    private fun cancelDisconnectTimer() {
        disconnectTimer?.let { handler.removeCallbacks(it) }
        disconnectTimer = null
    }
}