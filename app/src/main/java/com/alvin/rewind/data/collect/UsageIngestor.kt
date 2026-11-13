package com.alvin.rewind.data.collect

import com.alvin.rewind.data.local.DailyAppUsageEntity
import com.alvin.rewind.data.local.DailyDeviceEntity
import com.alvin.rewind.data.local.UsageDao
import com.alvin.rewind.data.system.AppCatalog
import com.alvin.rewind.data.system.PermissionChecker
import com.alvin.rewind.data.system.UsageEventsSource
import com.alvin.rewind.domain.time.endOfDayMillis
import com.alvin.rewind.domain.time.startOfDayMillis
import com.alvin.rewind.domain.usage.DailyUsageCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate

/**
 * Copies the OS usage history into our own aggregated store, one day at a time.
 *
 * Why persist at all instead of querying the OS when the report opens?
 * The OS keeps detailed events for a limited, version/OEM-dependent period (often around a week).
 * To show "last week" reliably on a Monday, and to compare with the week before, we must have
 * captured those days ourselves. Running twice a day (WorkManager) + on app open is plenty.
 *
 * Properties:
 *  - Idempotent: a day is recomputed from scratch and replaced; running it twice is harmless.
 *  - Cheap: completed days are skipped; normally only today (and maybe yesterday) is computed.
 *  - Backfills: on first run we pick up whatever history the OS still has (LOOKBACK_DAYS).
 */
class UsageIngestor(
    private val source: UsageEventsSource,
    private val dao: UsageDao,
    private val catalog: AppCatalog,
    private val permissions: PermissionChecker,
    private val clock: Clock,
    private val calculator: DailyUsageCalculator = DailyUsageCalculator(),
) {
    private val mutex = Mutex() // worker and UI refresh can race

    suspend fun ingestRecentDays(lookbackDays: Int = LOOKBACK_DAYS) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!permissions.hasUsageAccess()) return@withLock

            val zone = clock.zone
            val nowMs = clock.millis()
            val today = LocalDate.now(clock)
            val excluded = catalog.excludedFromScreenTime()

            for (offset in lookbackDays downTo 0) {
                val day = today.minusDays(offset.toLong())
                val key = day.toString()
                if (dao.device(key)?.isComplete == true) continue

                val dayStart = day.startOfDayMillis(zone)
                val dayEnd = day.endOfDayMillis(zone)
                val windowEnd = minOf(dayEnd, nowMs)
                val events = source.events(dayStart, windowEnd)
                // No events for a past day = the OS no longer has it (or phone was off).
                // Don't write zeros: "unknown" must stay distinguishable from "0 minutes".
                if (events.isEmpty() && day != today) continue

                val usage = calculator.calculate(day, events, dayStart, windowEnd, zone, excluded)
                dao.replaceDay(
                    device = DailyDeviceEntity(
                        day = key,
                        unlocks = usage.unlocks,
                        hourlyMsCsv = usage.hourlyForegroundMs.joinToString(","),
                        isComplete = dayEnd <= nowMs,
                        updatedAtMs = nowMs,
                    ),
                    apps = usage.apps.map { DailyAppUsageEntity(key, it.packageName, it.foregroundMs, it.launches) },
                )
            }
        }
    }

    companion object {
        const val LOOKBACK_DAYS = 9
    }
}
