package com.alvin.rewind.domain.usage

import java.time.LocalDate

/**
 * Platform-neutral subset of the OS usage events we care about.
 * The Android data layer maps `UsageEvents.Event` types onto these; nothing else leaks into the domain.
 */
enum class UsageEventType {
    /** An activity of the app came to the foreground (Android: ACTIVITY_RESUMED). */
    APP_FOREGROUND,

    /** An activity of the app left the foreground (Android: ACTIVITY_PAUSED). */
    APP_BACKGROUND,

    /** Screen turned off: whatever was in the foreground is no longer being "used". */
    SCREEN_OFF,

    /** Keyguard dismissed (Android: KEYGUARD_HIDDEN). Used to count unlocks. */
    DEVICE_UNLOCK,

    /** Device shutting down: closes any open session. */
    DEVICE_SHUTDOWN,
}

data class UsageEvent(
    val packageName: String,
    val type: UsageEventType,
    val timestampMs: Long,
)

/** A continuous period during which one app was in the foreground. */
data class AppSession(
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = endMs - startMs
}

/** What we persist per app per day. Small, aggregated, no raw event history kept. */
data class AppDayUsage(
    val packageName: String,
    val foregroundMs: Long,
    /** Number of distinct sessions after merging quick in-app activity transitions. */
    val launches: Int,
)

data class DailyUsage(
    val date: LocalDate,
    val apps: List<AppDayUsage>,
    val unlocks: Int,
    /** 24 buckets, local time, foreground ms per hour of day. */
    val hourlyForegroundMs: List<Long>,
) {
    init {
        require(hourlyForegroundMs.size == HOURS) { "Expected $HOURS hourly buckets, got ${hourlyForegroundMs.size}" }
    }

    val screenTimeMs: Long get() = apps.sumOf { it.foregroundMs }
    val launches: Int get() = apps.sumOf { it.launches }

    companion object {
        const val HOURS = 24
    }
}
