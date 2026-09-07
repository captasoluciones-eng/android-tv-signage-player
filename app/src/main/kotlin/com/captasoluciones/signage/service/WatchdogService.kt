package com.captasoluciones.signage.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.captasoluciones.signage.R
import com.captasoluciones.signage.ui.MainActivity
import java.util.concurrent.atomic.AtomicLong

/**
 * Foreground service that (a) satisfies the Android requirement that autostart /
 * long-running background work show a persistent, low-priority notification, and
 * (b) supervises MainActivity: if it hasn't reported itself alive recently (see
 * [reportAlive], called from MainActivity.onResume) the service assumes the process
 * died or got stuck and relaunches it.
 *
 * A heartbeat-timestamp watchdog (rather than inspecting ActivityManager task state,
 * which is unreliable/restricted for a foreground app's own process on modern Android)
 * keeps this robust across API levels.
 */
class WatchdogService : Service() {

    companion object {
        private const val CHANNEL_ID = "signage_watchdog_channel"
        private const val NOTIFICATION_ID = 1001
        private const val CHECK_INTERVAL_MS = 15_000L
        private const val ALIVE_TIMEOUT_MS = 45_000L

        private val lastAliveAt = AtomicLong(System.currentTimeMillis())

        /** Called by MainActivity while it is resumed to reset the watchdog timer. */
        fun reportAlive() {
            lastAliveAt.set(System.currentTimeMillis())
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        launchMainActivity(force = false)
        startWatchdogLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun startWatchdogLoop() {
        val runnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - lastAliveAt.get()
                if (elapsed > ALIVE_TIMEOUT_MS) {
                    launchMainActivity(force = true)
                }
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
        watchdogRunnable = runnable
        handler.postDelayed(runnable, CHECK_INTERVAL_MS)
    }

    private fun launchMainActivity(force: Boolean) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (force) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
            reportAlive()
        } catch (e: Exception) {
            // Best-effort; will retry on the next watchdog tick.
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.watchdog_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.watchdog_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.watchdog_notification_title))
            .setContentText(getString(R.string.watchdog_notification_text))
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }
}
