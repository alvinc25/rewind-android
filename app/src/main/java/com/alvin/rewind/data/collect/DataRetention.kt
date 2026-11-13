package com.alvin.rewind.data.collect

import com.alvin.rewind.data.local.NotificationDao
import com.alvin.rewind.data.local.UsageDao
import com.alvin.rewind.domain.time.startOfDayMillis
import java.time.Clock
import java.time.LocalDate

/** Data minimisation: we keep ~3 months, enough for week-over-week and a bit of history. */
class DataRetention(
    private val usageDao: UsageDao,
    private val notificationDao: NotificationDao,
    private val clock: Clock,
) {
    suspend fun purge() {
        val cutoff = LocalDate.now(clock).minusWeeks(KEEP_WEEKS)
        usageDao.deleteAppUsageBefore(cutoff.toString())
        usageDao.deleteDevicesBefore(cutoff.toString())
        notificationDao.deleteBefore(cutoff.startOfDayMillis(clock.zone))
    }

    private companion object {
        const val KEEP_WEEKS = 13L
    }
}
