package com.alvin.rewind.domain.repository

import com.alvin.rewind.domain.access.AccessStatus
import com.alvin.rewind.domain.report.ReportInput
import com.alvin.rewind.domain.time.WeekRange

/*
 * Contracts the domain needs from the outside world (dependency inversion).
 * The domain owns these interfaces; the :app data layer implements them.
 * Nothing here knows about Android, Room or WorkManager.
 */

/** Where a week's raw activity comes from: the phone (live) or generated sample data. */
interface ActivityRepository {

    /** Pull fresh data from the system into local storage (and apply retention). */
    suspend fun refresh()

    /** Everything the report needs for [week]: usage, notifications, app changes, coverage flags. */
    suspend fun weekActivity(week: WeekRange): ReportInput
}

/** Permission status and setup progress. */
interface AccessRepository {

    fun status(): AccessStatus

    fun isOnboardingCompleted(): Boolean

    fun setOnboardingCompleted()
}
