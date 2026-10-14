package com.focs.app.block

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.focs.app.MainActivity
import com.focs.app.R
import com.focs.app.data.Store
import com.focs.app.data.UsageTracker
import com.focs.app.focus.FocusManager
import java.time.LocalDate
import java.time.ZoneId

/**
 * The part that makes limits hard. Android's own app timers offer "ignore for today";
 * this doesn't. Every couple of seconds it works out which app is in front (from usage
 * events) and checks today's usage. Once the limit is used up (or focus mode is on) it
 * puts the block wall over the app. There is no snooze.
 *
 * Showing the wall from the background relies on the "display over other apps"
 * permission. This deliberately avoids an accessibility service, which makes Play
 * Protect block sideloaded installs.
 */
class BlockerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var foregroundPkg: String? = null
    private var queriedUntil = 0L
    private var lastBlockAt = 0L
    private val warned = HashSet<String>()
    private var warnedDay: LocalDate = LocalDate.now()

    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun check() {
        FocusManager.endIfExpired(this)
        val pkg = updateForegroundApp() ?: return
        if (pkg == packageName) return

        val limitMin = Store.limits(this)[pkg] ?: return

        if (FocusManager.isActive(this)) {
            block(pkg, BlockActivity.REASON_FOCUS)
            return
        }

        val usedMs = UsageTracker.usageTodayMs(this)[pkg] ?: 0L
        val limitMs = limitMin * 60_000L
        if (usedMs >= limitMs) {
            block(pkg, BlockActivity.REASON_LIMIT)
        } else if (limitMs - usedMs <= 60_000L) {
            warnOnce(pkg)
        }
    }

    /** Reads only the usage events since the last tick and returns the app currently in front. */
    private fun updateForegroundApp(): String? {
        if (!UsageTracker.hasAccess(this)) return null
        val now = System.currentTimeMillis()
        if (queriedUntil == 0L) {
            queriedUntil = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val events = getSystemService(UsageStatsManager::class.java).queryEvents(queriedUntil, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                RESUMED -> foregroundPkg = event.packageName
                PAUSED -> if (event.packageName == foregroundPkg) foregroundPkg = null
                SCREEN_NON_INTERACTIVE -> foregroundPkg = null
            }
        }
        queriedUntil = now
        return foregroundPkg
    }

    private fun warnOnce(pkg: String) {
        val today = LocalDate.now()
        if (today != warnedDay) {
            warned.clear()
            warnedDay = today
        }
        if (warned.add(pkg)) {
            Toast.makeText(this, "1 minute left on ${UsageTracker.appLabel(this, pkg)} today", Toast.LENGTH_LONG).show()
        }
    }

    private fun block(pkg: String, reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockAt < 1500) return
        lastBlockAt = now
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                .putExtra(BlockActivity.EXTRA_REASON, reason)
        )
        foregroundPkg = packageName
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App blocker", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps Focs watching your app limits"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Guarding your app limits")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 1, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .build()

    companion object {
        private const val TICK_MS = 2_000L
        private const val CHANNEL_ID = "blocker"
        private const val NOTIFICATION_ID = 2
        private const val RESUMED = 1
        private const val PAUSED = 2
        private const val SCREEN_NON_INTERACTIVE = 16

        fun canShowWall(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

        @Suppress("DEPRECATION")
        fun isRunning(ctx: Context): Boolean =
            ctx.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == BlockerService::class.java.name }

        /** Starts the watcher when it has what it needs; safe to call repeatedly. */
        fun startIfReady(ctx: Context) {
            if (UsageTracker.hasAccess(ctx) && canShowWall(ctx)) {
                ContextCompat.startForegroundService(ctx, Intent(ctx, BlockerService::class.java))
            }
        }
    }
}
