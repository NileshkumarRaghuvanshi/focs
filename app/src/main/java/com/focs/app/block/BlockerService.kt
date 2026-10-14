package com.focs.app.block

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.focs.app.data.Store
import com.focs.app.data.UsageTracker
import com.focs.app.focus.FocusManager
import java.time.LocalDate

/**
 * The part that makes limits hard. Android's own app timers offer "ignore for today";
 * this doesn't. Whenever a limited app comes to the front, or every few seconds while
 * it stays there, it checks today's usage. Once the limit is used up (or focus mode is
 * on), it sends you to the home screen and shows the block wall. There is no snooze.
 */
class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null
    private var ignoredPkgs: Set<String> = emptySet()
    private var lastBlockAt = 0L
    private val warned = HashSet<String>()
    private var warnedDay: LocalDate = LocalDate.now()

    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        ignoredPkgs = buildIgnoredPackages()
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // The notification shade and keyboards also fire window events; they don't change which app you're in.
        if (pkg in ignoredPkgs) return
        currentPkg = pkg
        check()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun check() {
        FocusManager.endIfExpired(this)
        val pkg = currentPkg ?: return
        if (pkg == packageName) return

        val limits = Store.limits(this)
        val limitMin = limits[pkg] ?: return

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
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                .putExtra(BlockActivity.EXTRA_REASON, reason)
        )
        currentPkg = packageName
    }

    private fun buildIgnoredPackages(): Set<String> {
        val ime = getSystemService(InputMethodManager::class.java)
            .enabledInputMethodList.map { it.packageName }
        return (ime + listOf("com.android.systemui", "android")).toSet()
    }

    companion object {
        private const val TICK_MS = 5_000L

        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(ctx, BlockerService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
