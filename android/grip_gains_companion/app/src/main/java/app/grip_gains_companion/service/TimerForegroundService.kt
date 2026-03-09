package app.grip_gains_companion.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.grip_gains_companion.MainActivity
import app.grip_gains_companion.R
import app.grip_gains_companion.config.AppConstants
import android.content.pm.ServiceInfo
import android.os.Build

class TimerForegroundService : Service() {

    companion object {
        const val ACTION_START = "app.grip_gains_companion.START_TIMER"
        const val ACTION_STOP = "app.grip_gains_companion.STOP_TIMER"
        const val ACTION_FAIL_REP = "app.grip_gains_companion.FAIL_REP"
        const val EXTRA_ELAPSED = "elapsed"
        const val EXTRA_REMAINING = "remaining"

        fun updateNotification(context: Context, elapsed: Int, remaining: Int) {
            try {
                val intent = Intent(context, TimerForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_ELAPSED, elapsed)
                    putExtra(EXTRA_REMAINING, remaining)
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val elapsed = intent.getIntExtra(EXTRA_ELAPSED, 0)
                val remaining = intent.getIntExtra(EXTRA_REMAINING, 0)

                val notification = buildNotification(elapsed, remaining)

                // RESTORED: This is required for Android 14+ to show the notification
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(AppConstants.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                } else {
                    startForeground(AppConstants.NOTIFICATION_ID, notification)
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(elapsed: Int, remaining: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val failIntent = Intent(ACTION_FAIL_REP)
        val failPendingIntent = PendingIntent.getBroadcast(
            this, 1, failIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val failAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Fail Rep",
            failPendingIntent
        ).build()

        val remainingText = when {
            remaining == -9999 -> "Basic Timer"
            remaining < 0 -> "+${-remaining}s bonus"
            else -> "${remaining}s remaining"
        }

        return NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Grip Gains Active")
            .setContentText("${elapsed}s elapsed • $remainingText")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(failAction)
            .build()
    }
}