package com.alvin.rewind.data.repo

import com.alvin.rewind.data.collect.DataRetention
import com.alvin.rewind.data.collect.PackageTracker
import com.alvin.rewind.data.collect.UsageIngestor
import com.alvin.rewind.data.local.AppsDao
import com.alvin.rewind.data.local.CollectionPrefs
import com.alvin.rewind.data.local.DailyAppUsageEntity
import com.alvin.rewind.data.local.NotificationDao
import com.alvin.rewind.data.local.UsageDao
import com.alvin.rewind.data.system.AppCatalog
import com.alvin.rewind.data.system.PermissionChecker
import com.alvin.rewind.domain.apps.AppChange
import com.alvin.rewind.domain.apps.AppChangeType
import com.alvin.rewind.domain.report.ReportInput
import com.alvin.rewind.domain.repository.ActivityRepository
import com.alvin.rewind.domain.time.WeekRange
import com.alvin.rewind.domain.usage.AppDayUsage
import com.alvin.rewind.domain.usage.DailyUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Data from this phone. Implements the domain's [ActivityRepository]:
 *  - [refresh] copies OS history into Room (aggregated), diffs installed apps, purges old data;
 *  - [weekActivity] reads *only* our own store (never the OS) and maps entities to domain models.
 * No business rules here: building the report is the use case's job.
 */
class DeviceActivityRepository(
    private val usageDao: UsageDao,
    private val notificationDao: NotificationDao,
    private val appsDao: AppsDao,
    private val ingestor: UsageIngestor,
    private val packageTracker: PackageTracker,
    private val retention: DataRetention,
    private val catalog: AppCatalog,
    private val permissions: PermissionChecker,
    private val prefs: CollectionPrefs,
    private val clock: Clock,
) : ActivityRepository {

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        ingestor.ingestRecentDays()
        packageTracker.sync()
        retention.purge()
    }

    override suspend fun weekActivity(week: WeekRange): ReportInput = withContext(Dispatchers.IO) {
        val zone = clock.zone
        val access = permissions.current()

        val notificationsPerDay = notificationDao.between(week.startMillis(zone), week.endMillis(zone))
            .groupBy { Instant.ofEpochMilli(it.postedAtMs).atZone(zone).toLocalDate() }
            .mapValues { (_, posts) -> posts.groupingBy { it.packageName }.eachCount() }

        // Labels of uninstalled apps are no longer available from PackageManager: use our snapshot.
        val knownLabels = appsDao.snapshot().associate { it.packageName to it.label }

        ReportInput(
            week = week,
            today = LocalDate.now(clock),
            zone = zone,
            dailyUsage = loadDailyUsage(week),
            previousWeekUsage = loadDailyUsage(week.previous()),
            notificationsPerDay = notificationsPerDay,
            appChanges = appsDao.changesBetween(week.startMillis(zone), week.endMillis(zone)).map {
                AppChange(it.packageName, it.label, AppChangeType.valueOf(it.type), it.atMs, it.exactTime)
            },
            appInfo = { pkg ->
                val info = catalog.info(pkg)
                if (info.label == pkg) info.copy(label = knownLabels[pkg] ?: pkg) else info
            },
            usageAccessGranted = access.usageAccess,
            notificationAccessGranted = access.notificationAccess,
            notificationsTrackedSince = prefs.notificationsTrackedSinceMs
                ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() },
        )
    }

    private suspend fun loadDailyUsage(week: WeekRange): List<DailyUsage> {
        val from = week.start.toString()
        val to = week.endExclusive.toString()
        val appsByDay: Map<String, List<DailyAppUsageEntity>> = usageDao.appUsage(from, to).groupBy { it.day }
        return usageDao.devices(from, to).map { device ->
            DailyUsage(
                date = LocalDate.parse(device.day),
                apps = appsByDay[device.day].orEmpty().map { AppDayUsage(it.packageName, it.foregroundMs, it.launches) },
                unlocks = device.unlocks,
                hourlyForegroundMs = device.hourlyMsCsv.split(',').map { it.toLong() },
            )
        }
    }
}
