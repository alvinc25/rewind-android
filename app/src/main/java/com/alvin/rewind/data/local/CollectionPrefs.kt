package com.alvin.rewind.data.local

import android.content.Context
import androidx.core.content.edit

/** Small flags that don't deserve a table. */
class CollectionPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("rewind_prefs", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_DONE, value) }

    /** First time the notification listener connected: counts before this date are unknown, not zero. */
    val notificationsTrackedSinceMs: Long?
        get() = prefs.getLong(KEY_NOTIF_SINCE, -1L).takeIf { it >= 0 }

    fun markNotificationTrackingStarted(nowMs: Long) {
        if (!prefs.contains(KEY_NOTIF_SINCE)) prefs.edit { putLong(KEY_NOTIF_SINCE, nowMs) }
    }

    private companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_NOTIF_SINCE = "notifications_tracked_since"
    }
}
