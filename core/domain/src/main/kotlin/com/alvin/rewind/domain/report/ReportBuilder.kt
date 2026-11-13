package com.alvin.rewind.domain.report

import com.alvin.rewind.domain.apps.AppChangeType
import com.alvin.rewind.domain.usage.DailyUsage

/**
 * Pure function: [ReportInput] -> [WeeklyReport].
 * Used identically by the real repository and by demo mode, so the demo exercises the real logic.
 */
class ReportBuilder(private val insights: InsightGenerator = InsightGenerator()) {

    fun build(input: ReportInput): WeeklyReport {
        val week = input.week
        val usageByDay = input.dailyUsage.filter { it.date in week }.associateBy { it.date }
        val notificationsByDay = input.notificationsPerDay.filterKeys { it in week }

        val days = week.days.map { date ->
            val usage = usageByDay[date]
            DaySummary(
                date = date,
                screenTimeMs = usage?.screenTimeMs ?: 0L,
                unlocks = usage?.unlocks ?: 0,
                appOpens = usage?.launches ?: 0,
                notifications = notificationsByDay[date].orEmpty().values.sum(),
                hasUsageData = usage != null,
            )
        }

        val foregroundByApp = HashMap<String, Long>()
        val launchesByApp = HashMap<String, Int>()
        usageByDay.values.forEach { day ->
            day.apps.forEach { app ->
                foregroundByApp[app.packageName] = (foregroundByApp[app.packageName] ?: 0L) + app.foregroundMs
                launchesByApp[app.packageName] = (launchesByApp[app.packageName] ?: 0) + app.launches
            }
        }
        val notificationsByApp = HashMap<String, Int>()
        notificationsByDay.values.forEach { perApp ->
            perApp.forEach { (pkg, count) -> notificationsByApp[pkg] = (notificationsByApp[pkg] ?: 0) + count }
        }

        val apps = (foregroundByApp.keys + notificationsByApp.keys).map { pkg ->
            val info = input.appInfo(pkg)
            AppWeekUsage(
                packageName = pkg,
                label = info.label,
                foregroundMs = foregroundByApp[pkg] ?: 0L,
                launches = launchesByApp[pkg] ?: 0,
                notifications = notificationsByApp[pkg] ?: 0,
                isVideo = info.isVideo,
            )
        }

        val hourly = LongArray(DailyUsage.HOURS)
        usageByDay.values.forEach { day -> day.hourlyForegroundMs.forEachIndexed { h, ms -> hourly[h] += ms } }

        val weekStartMs = week.startMillis(input.zone)
        val weekEndMs = week.endMillis(input.zone)
        val changes = input.appChanges.filter { it.atMs in weekStartMs until weekEndMs }.sortedBy { it.atMs }

        val daysElapsed = week.days.count { !it.isAfter(input.today) }
        val notificationsPartial = input.notificationsTrackedSince == null ||
            input.notificationsTrackedSince.isAfter(week.start)

        val report = WeeklyReport(
            week = week,
            days = days,
            totalScreenTimeMs = days.sumOf { it.screenTimeMs },
            dailyAverageMs = dailyAverage(usageByDay.values, input),
            previousWeekDailyAverageMs = previousWeekAverage(input),
            unlocks = days.sumOf { it.unlocks },
            appOpens = days.sumOf { it.appOpens },
            notifications = days.sumOf { it.notifications },
            videoEstimateMs = apps.filter { it.isVideo }.sumOf { it.foregroundMs },
            topApps = apps.filter { it.foregroundMs > 0 }.sortedByDescending { it.foregroundMs },
            topNotifiers = apps.filter { it.notifications > 0 }.sortedByDescending { it.notifications }.take(TOP_NOTIFIERS),
            hourlyMs = hourly.toList(),
            installed = changes.filter { it.type == AppChangeType.INSTALLED },
            uninstalled = changes.filter { it.type == AppChangeType.UNINSTALLED },
            coverage = DataCoverage(
                usageAccessGranted = input.usageAccessGranted,
                notificationAccessGranted = input.notificationAccessGranted,
                daysElapsed = daysElapsed,
                daysWithUsageData = usageByDay.keys.count { !it.isAfter(input.today) },
                notificationsTrackedSince = input.notificationsTrackedSince,
                notificationsPartial = notificationsPartial,
            ),
        )
        return report.copy(insights = insights.generate(report))
    }

    /**
     * Today is still in progress, so including it would drag the average down every morning.
     * We average completed days only; if today is the only day we have, we use it anyway.
     */
    private fun dailyAverage(days: Collection<DailyUsage>, input: ReportInput): Long {
        val completed = days.filter { it.date.isBefore(input.today) }
        val basis = completed.ifEmpty { days.toList() }
        return if (basis.isEmpty()) 0L else basis.sumOf { it.screenTimeMs } / basis.size
    }

    private fun previousWeekAverage(input: ReportInput): Long? {
        val previous = input.week.previous()
        val days = input.previousWeekUsage.filter { it.date in previous && it.date.isBefore(input.today) }
        if (days.size < MIN_DAYS_FOR_COMPARISON) return null
        return days.sumOf { it.screenTimeMs } / days.size
    }

    companion object {
        /** Below this many days, a week-over-week comparison is noise rather than signal. */
        const val MIN_DAYS_FOR_COMPARISON = 3
        const val TOP_NOTIFIERS = 5
    }
}
