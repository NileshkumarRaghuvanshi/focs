package com.focs.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Everything Focs remembers, in one SharedPreferences file.
 *
 * Limits follow one anti-skip rule: making a limit stricter takes effect now, but
 * loosening it (raising the minutes or removing it) only takes effect tomorrow.
 * That way there is no "just 15 more minutes" button to negotiate with in the moment.
 */
object Store {
    private const val FILE = "focs"
    private const val KEY_LIMITS = "limits"            // {"pkg": minutes}
    private const val KEY_PENDING = "pending_limits"   // {"pkg": {"m": minutes or 0 to remove, "d": "yyyy-mm-dd"}}
    private const val KEY_GOALS = "goals"              // ["line", ...]
    private const val KEY_FOCUS_START = "focus_start"
    private const val KEY_FOCUS_END = "focus_end"
    private const val KEY_DND_SAVED = "dnd_saved"
    private const val KEY_PREV_FILTER = "prev_filter"
    private const val KEY_PREV_POLICY = "prev_policy"  // [categories, callSenders, messageSenders, visualEffects]

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- App limits ----

    /** Daily limit in minutes for each limited package, with any due pending changes applied. */
    @Synchronized
    fun limits(ctx: Context): Map<String, Int> {
        val p = prefs(ctx)
        val limits = JSONObject(p.getString(KEY_LIMITS, "{}")!!)
        val pending = JSONObject(p.getString(KEY_PENDING, "{}")!!)
        val today = LocalDate.now()
        var changed = false
        for (pkg in pending.keys().asSequence().toList()) {
            val change = pending.getJSONObject(pkg)
            if (!LocalDate.parse(change.getString("d")).isAfter(today)) {
                val minutes = change.getInt("m")
                if (minutes <= 0) limits.remove(pkg) else limits.put(pkg, minutes)
                pending.remove(pkg)
                changed = true
            }
        }
        if (changed) {
            p.edit().putString(KEY_LIMITS, limits.toString()).putString(KEY_PENDING, pending.toString()).apply()
        }
        return limits.keys().asSequence().associateWith { limits.getInt(it) }
    }

    /** Pending (tomorrow) limit for a package, 0 meaning "remove", or null if nothing is pending. */
    fun pendingLimit(ctx: Context, pkg: String): Int? {
        val pending = JSONObject(prefs(ctx).getString(KEY_PENDING, "{}")!!)
        return if (pending.has(pkg)) pending.getJSONObject(pkg).getInt("m") else null
    }

    /**
     * Sets a daily limit; [minutes] of 0 removes it.
     * Returns true when it applied now, false when it was scheduled for tomorrow.
     */
    @Synchronized
    fun setLimit(ctx: Context, pkg: String, minutes: Int): Boolean {
        val current = limits(ctx)[pkg]
        val p = prefs(ctx)
        val limits = JSONObject(p.getString(KEY_LIMITS, "{}")!!)
        val pending = JSONObject(p.getString(KEY_PENDING, "{}")!!)
        val stricter = minutes > 0 && (current == null || minutes <= current)
        return if (stricter) {
            limits.put(pkg, minutes)
            pending.remove(pkg)
            p.edit().putString(KEY_LIMITS, limits.toString()).putString(KEY_PENDING, pending.toString()).apply()
            true
        } else if (current == null) {
            // Removing a limit that doesn't exist: nothing to do.
            pending.remove(pkg)
            p.edit().putString(KEY_PENDING, pending.toString()).apply()
            true
        } else {
            pending.put(pkg, JSONObject().put("m", minutes).put("d", LocalDate.now().plusDays(1).toString()))
            p.edit().putString(KEY_PENDING, pending.toString()).apply()
            false
        }
    }

    // ---- Goals ----

    fun goals(ctx: Context): List<String> {
        val arr = JSONArray(prefs(ctx).getString(KEY_GOALS, "[]")!!)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun setGoals(ctx: Context, goals: List<String>) {
        prefs(ctx).edit().putString(KEY_GOALS, JSONArray(goals).toString()).apply()
    }

    // ---- Focus session ----

    fun focusStart(ctx: Context): Long = prefs(ctx).getLong(KEY_FOCUS_START, 0)
    fun focusEnd(ctx: Context): Long = prefs(ctx).getLong(KEY_FOCUS_END, 0)

    fun setFocus(ctx: Context, start: Long, end: Long) {
        prefs(ctx).edit().putLong(KEY_FOCUS_START, start).putLong(KEY_FOCUS_END, end).apply()
    }

    fun clearFocus(ctx: Context) {
        prefs(ctx).edit().remove(KEY_FOCUS_START).remove(KEY_FOCUS_END).apply()
    }

    // ---- Do Not Disturb state to restore after focus ----

    fun saveDnd(ctx: Context, filter: Int, policy: IntArray) {
        prefs(ctx).edit()
            .putBoolean(KEY_DND_SAVED, true)
            .putInt(KEY_PREV_FILTER, filter)
            .putString(KEY_PREV_POLICY, policy.joinToString(","))
            .apply()
    }

    /** Returns the saved (filter, policy) and forgets it, or null if nothing was saved. */
    fun takeSavedDnd(ctx: Context): Pair<Int, IntArray>? {
        val p = prefs(ctx)
        if (!p.getBoolean(KEY_DND_SAVED, false)) return null
        val filter = p.getInt(KEY_PREV_FILTER, 1)
        val policy = p.getString(KEY_PREV_POLICY, "")!!.split(",").mapNotNull { it.toIntOrNull() }.toIntArray()
        p.edit().remove(KEY_DND_SAVED).remove(KEY_PREV_FILTER).remove(KEY_PREV_POLICY).apply()
        return filter to policy
    }
}
