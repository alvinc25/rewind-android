package com.alvin.rewind.domain.report

import com.alvin.rewind.domain.apps.AppChange
import com.alvin.rewind.domain.apps.AppInfo
import com.alvin.rewind.domain.time.WeekRange
import com.alvin.rewind.domain.usage.DailyUsage
import java.time.LocalDate
import java.time.ZoneId

/** Everything the report needs, already loaded. Building a report is a pure function of this. */
data class ReportInput(
    val week: WeekRange,
    val today: LocalDate,
    val zone: ZoneId,
    val dailyUsage: List<DailyUsage>,
    val previousWeekUsage: List<DailyUsage>,
    /** date -> (package -> count) */
    val notificationsPerDay: Map<LocalDate, Map<String, Int>>,
    val appChanges: List<AppChange>,
    val appInfo: (String) -> AppInfo,
    val usageAccessGranted: Boolean,
    val notificationAccessGranted: Boolean,
    val notificationsTrackedSince: LocalDate?,
)

data class DaySummary(
    val date: LocalDate,
    val screenTimeMs: Long,
    val unlocks: Int,
    val appOpens: Int,
    val notifications: Int,
    /** false = we have no usage data for this day (future, before install, or OS no longer has it). */
    val hasUsageData: Boolean,
)

data class AppWeekUsage(
    val packageName: String,
    val label: String,
    val foregroundMs: Long,
    val launches: Int,
    val notifications: Int,
    val isVideo: Boolean,
)

/**
 * Being honest about gaps is part of the product: a report that silently shows "0 notifications"
 * because access was granted on Thursday is worse than one that says so.
 */
data class DataCoverage(
    val usageAccessGranted: Boolean,
    val notificationAccessGranted: Boolean,
    val daysElapsed: Int,
    val daysWithUsageData: Int,
    val notificationsTrackedSince: LocalDate?,
    val notificationsPartial: Boolean,
) {
    val usagePartial: Boolean get() = daysWithUsageData < daysElapsed
}

data class WeeklyReport(
    val week: WeekRange,
    val days: List<DaySummary>,
    val totalScreenTimeMs: Long,
    /** Average over *completed* days with data, so an in-progress week compares fairly. */
    val dailyAverageMs: Long,
    val previousWeekDailyAverageMs: Long?,
    val unlocks: Int,
    val appOpens: Int,
    val notifications: Int,
    val videoEstimateMs: Long,
    val topApps: List<AppWeekUsage>,
    val topNotifiers: List<AppWeekUsage>,
    val hourlyMs: List<Long>,
    val installed: List<AppChange>,
    val uninstalled: List<AppChange>,
    val coverage: DataCoverage,
    val insights: List<Insight> = emptyList(),
) {
    /** Percent change of daily average vs previous week, or null if not comparable. */
    val trendPercent: Int?
        get() = previousWeekDailyAverageMs
            ?.takeIf { it > 0 && dailyAverageMs > 0 }
            ?.let { prev -> ((dailyAverageMs - prev) * 100 / prev).toInt() }

    val busiestDay: DaySummary?
        get() = days.filter { it.hasUsageData && it.screenTimeMs > 0 }.maxByOrNull { it.screenTimeMs }

    val isEmpty: Boolean get() = totalScreenTimeMs == 0L && notifications == 0 && installed.isEmpty() && uninstalled.isEmpty()
}

/** Typed insights; the UI layer turns them into (localisable) sentences. */
sealed interface Insight {
    data class TrendVsLastWeek(val percent: Int) : Insight
    data class TopApp(val label: String, val sharePercent: Int, val foregroundMs: Long) : Insight
    data class BusiestDay(val date: LocalDate, val screenTimeMs: Long) : Insight
    data class LateNight(val totalMs: Long) : Insight
    data class NotificationMagnet(val label: String, val count: Int, val sharePercent: Int) : Insight
    data class NewApps(val count: Int, val labels: List<String>) : Insight
}
