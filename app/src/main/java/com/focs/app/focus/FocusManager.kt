package com.focs.app.focus

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.focs.app.data.Store

/**
 * Starts and stops focus sessions.
 *
 * During a session Do Not Disturb runs in "priority only" mode with a policy that lets
 * through exactly: calls from starred contacts, anyone calling a second time within
 * 15 minutes (Android's "repeat callers"), and alarms. Every other notification is
 * silenced and hidden. The previous DND setup is restored when the session ends.
 */
object FocusManager {

    fun hasDndAccess(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun isActive(ctx: Context): Boolean = Store.focusEnd(ctx) > System.currentTimeMillis()

    fun start(ctx: Context, minutes: Int) {
        val now = System.currentTimeMillis()
        Store.setFocus(ctx, now, now + minutes * 60_000L)
        enableDnd(ctx)
        ContextCompat.startForegroundService(ctx, Intent(ctx, FocusService::class.java))
    }

    fun stop(ctx: Context) {
        Store.clearFocus(ctx)
        restoreDnd(ctx)
        ctx.stopService(Intent(ctx, FocusService::class.java))
    }

    /** Safety net in case the service was killed: ends a session whose time has passed. */
    fun endIfExpired(ctx: Context) {
        val end = Store.focusEnd(ctx)
        if (end != 0L && end <= System.currentTimeMillis()) stop(ctx)
    }

    private fun enableDnd(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        val old = nm.notificationPolicy
        Store.saveDnd(
            ctx,
            nm.currentInterruptionFilter,
            intArrayOf(old.priorityCategories, old.priorityCallSenders, old.priorityMessageSenders, old.suppressedVisualEffects),
        )
        nm.notificationPolicy = Policy(
            Policy.PRIORITY_CATEGORY_CALLS or
                Policy.PRIORITY_CATEGORY_REPEAT_CALLERS or
                Policy.PRIORITY_CATEGORY_ALARMS,
            Policy.PRIORITY_SENDERS_STARRED,
            Policy.PRIORITY_SENDERS_STARRED,
            Policy.SUPPRESSED_EFFECT_PEEK or
                Policy.SUPPRESSED_EFFECT_STATUS_BAR or
                Policy.SUPPRESSED_EFFECT_BADGE or
                Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST or
                Policy.SUPPRESSED_EFFECT_LIGHTS or
                Policy.SUPPRESSED_EFFECT_AMBIENT or
                Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT,
        )
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    private fun restoreDnd(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val (filter, policy) = Store.takeSavedDnd(ctx) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        if (policy.size == 4) {
            nm.notificationPolicy = Policy(policy[0], policy[1], policy[2], policy[3])
        }
        nm.setInterruptionFilter(filter)
    }
}
