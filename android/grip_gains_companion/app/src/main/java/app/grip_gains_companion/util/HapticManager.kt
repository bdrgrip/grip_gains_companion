package app.grip_gains_companion.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HapticManager(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Uses the STREAM_MUSIC channel so the user can easily control the volume!
    private val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    fun success() {
        // Fire immediately in a background thread to prevent UI stuttering
        CoroutineScope(Dispatchers.Default).launch {
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 40), intArrayOf(0, 150, 0, 255), -1)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 30, 60, 40), -1)
                }
            }
            toneGen.startTone(ToneGenerator.TONE_PROP_PROMPT, 150)
        }
    }

    fun warning() {
        CoroutineScope(Dispatchers.Default).launch {
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), intArrayOf(0, 255, 0, 255), -1)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 50, 50, 50), -1)
                }
            }
            toneGen.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 200)
        }
    }

    fun error() {
        CoroutineScope(Dispatchers.Default).launch {
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 150, 50, 150), intArrayOf(0, 255, 0, 255), -1)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 150, 50, 150), -1)
                }
            }
            toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 400)
        }
    }
}