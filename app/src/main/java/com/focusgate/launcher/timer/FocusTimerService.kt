package com.focusgate.launcher.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.focusgate.launcher.MainActivity
import com.focusgate.launcher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service holding the countdown for an approved app session.
 * Shows a persistent notification with live time remaining; on expiry fires a
 * full-screen notification that lands the user back on the FocusGate chat.
 */
class FocusTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var countdown: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            countdown?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "App"
        val minutes = intent?.getIntExtra(EXTRA_MINUTES, 0) ?: 0
        if (minutes <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        val durationMs = minutes * 60_000L

        val notification = countdownNotification(label, System.currentTimeMillis() + durationMs)
        // targetSdk 34+: startForeground must pass the (manifest-declared) type.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ONGOING_ID, notification)
        }

        countdown?.cancel() // a fresh approval replaces any running session
        countdown = scope.launch {
            delay(durationMs)
            onExpired(label)
        }
        return START_NOT_STICKY
    }

    private fun countdownNotification(label: String, endAtMillis: Long): Notification {
        val openChat = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, FocusTimerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_stat_hourglass)
            .setContentTitle(label)
            .setContentText("Focus session running")
            // The system renders "time remaining" itself: a count-DOWN chronometer
            // pinned to the session end time. Zero per-second notification updates.
            .setWhen(endAtMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openChat)
            .addAction(0, "End session", stop)
            .build()
    }

    private fun onExpired(label: String) {
        // The granted session is over: drop it so the eject service treats this app
        // by the schedule again (a blocked category becomes ejectable once more).
        com.focusgate.launcher.schedule.ScheduleRepository.getInstance(this).clearSession()

        val backToChat = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_EXPIRED_LABEL, label),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_EXPIRY)
            .setSmallIcon(R.drawable.ic_stat_hourglass)
            .setContentTitle("Time's up")
            .setContentText("$label session ended — back to FoKused.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            // Full-screen intent pulls the user back to the chat. On Android 14+
            // non-alarm/call apps may get downgraded to a heads-up notification;
            // tapping it still lands on FocusGate, so the loop closes either way.
            .setFullScreenIntent(backToChat, true)
            .build()
        getSystemService(NotificationManager::class.java).notify(EXPIRY_ID, notification)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, "Focus timer", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EXPIRY, "Session ended", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Fires when an approved app session runs out" }
        )
    }

    companion object {
        private const val CHANNEL_TIMER = "focus_timer"
        private const val CHANNEL_EXPIRY = "focus_expiry"
        private const val ONGOING_ID = 1
        private const val EXPIRY_ID = 2
        private const val ACTION_STOP = "com.focusgate.launcher.action.STOP_TIMER"

        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_MINUTES = "extra_minutes"
        const val EXTRA_EXPIRED_LABEL = "extra_expired_label"

        fun start(context: Context, packageName: String, label: String, minutes: Int) {
            val intent = Intent(context, FocusTimerService::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_MINUTES, minutes)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
