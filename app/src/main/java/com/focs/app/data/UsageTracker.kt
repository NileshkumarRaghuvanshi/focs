package com.focs.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

data class AppInfo(val packageName: String, val label: String, val icon: Drawable)

object UsageTracker {
    // UsageEvents.Event constants, inlined so older API levels compile cleanly.
    private const val RESUMED = 1               // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    private const val PAUSED = 2                // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val DEVICE_SHUTDOWN = 26

    fun hasAccess(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Foreground time today per package, in milliseconds, including the session that is
     * still running right now. Built from raw events rather than daily buckets because
     * the buckets don't line up with midnight and lag behind the current session.
     */
    fun usageTodayMs(ctx: Context): Map<String, Long> {
        if (!hasAccess(ctx)) return emptyMap()
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val start = startOfToday()
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(start, now)
        val event = UsageEvents.Event()
        val total = HashMap<String, Long>()
        val seen = HashSet<String>()
        var openPkg: String? = null
        var openSince = 0L

        fun close(at: Long) {
            val pkg = openPkg ?: return
            total[pkg] = (total[pkg] ?: 0L) + (at - openSince).coerceAtLeast(0)
            openPkg = null
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            when (event.eventType) {
                RESUMED -> {
                    if (openPkg != pkg) {
                        close(event.timeStamp)
                        openPkg = pkg
                        openSince = event.timeStamp
                    }
                    seen += pkg
                }
                PAUSED -> {
                    if (openPkg == pkg) {
                        close(event.timeStamp)
                    } else if (pkg !in seen && openPkg == null) {
                        // App was already open at midnight.
                        total[pkg] = (total[pkg] ?: 0L) + (event.timeStamp - start)
                    }
                    seen += pkg
                }
                SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN -> close(event.timeStamp)
            }
        }
        close(now)
        return total
    }

    /** Apps that show up in the launcher, minus Focs itself, sorted by name. */
    fun launchableApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ctx.packageName }
            .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun appLabel(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
