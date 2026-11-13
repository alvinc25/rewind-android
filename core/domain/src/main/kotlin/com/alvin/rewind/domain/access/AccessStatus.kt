package com.alvin.rewind.domain.access

/** Which data sources the user has allowed. Platform-neutral: the data layer maps OS permissions onto it. */
data class AccessStatus(
    /** Required: screen time, opens, unlocks, hourly rhythm. */
    val usageAccess: Boolean = false,
    /** Optional: notification counts. */
    val notificationAccess: Boolean = false,
    /** Optional: the Sunday digest notification. */
    val postNotifications: Boolean = false,
)
