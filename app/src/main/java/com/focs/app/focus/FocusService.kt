package com.focs.app.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.focs.app.MainActivity
import com.focs.app.R
import com.focs.app.data.Store

/** Shows the live countdown in the notification shade and ends the session on time. */
class FocusService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val finish = Runnable { FocusManager.stop(this) }

    /** Whenever the screen goes off mid-session, put the timer over the lock screen. */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (FocusManager.isActive(context) && Settings.canDrawOverlays(context)) {
                startActivity(
                    Intent(context, FocusLockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
            }
        }
    }
    private var receiverRegistered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val end = Store.focusEnd(this)
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(end),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (!receiverRegistered) {
            registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
            receiverRegistered = true
        }
        handler.removeCallbacks(finish)
        val remaining = end - System.currentTimeMillis()
        if (remaining <= 0) {
            FocusManager.stop(this)
        } else {
            handler.postDelayed(finish, remaining)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(finish)
        if (receiverRegistered) {
            unregisterReceiver(screenOff)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Focus session", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the time left in your focus session"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(end: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_FOCUS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Focusing")
            .setContentText("Notifications are silenced. Starred contacts and repeat callers can still reach you.")
            .setWhen(end)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "focus"
        private const val NOTIFICATION_ID = 1
    }
}
