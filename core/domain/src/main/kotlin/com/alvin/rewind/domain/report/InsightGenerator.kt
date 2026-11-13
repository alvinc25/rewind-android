package com.alvin.rewind.domain.report

import kotlin.math.abs

/**
 * Picks a handful of observations worth surfacing. Thresholds exist so we only say something
 * when it's meaningful ("YouTube was 4% of your time" is not an insight).
 * Order = priority; the list is capped so the report stays scannable.
 */
class InsightGenerator {

    fun generate(report: WeeklyReport): List<Insight> = buildList {
        report.trendPercent?.let { if (abs(it) >= MIN_TREND_PERCENT) add(Insight.TrendVsLastWeek(it)) }

        report.topApps.firstOrNull()?.let { top ->
            if (report.totalScreenTimeMs > 0) {
                val share = (top.foregroundMs * 100 / report.totalScreenTimeMs).toInt()
                if (share >= MIN_TOP_APP_SHARE) add(Insight.TopApp(top.label, share, top.foregroundMs))
            }
        }

        val daysWithUsage = report.days.count { it.hasUsageData && it.screenTimeMs > 0 }
        if (daysWithUsage >= MIN_DAYS_FOR_BUSIEST) {
            report.busiestDay?.let { add(Insight.BusiestDay(it.date, it.screenTimeMs)) }
        }

        val lateNightMs = LATE_NIGHT_HOURS.sumOf { report.hourlyMs.getOrElse(it) { 0L } }
        if (lateNightMs >= MIN_LATE_NIGHT_MS) add(Insight.LateNight(lateNightMs))

        report.topNotifiers.firstOrNull()?.let { top ->
            if (report.notifications >= MIN_NOTIFICATIONS) {
                val share = top.notifications * 100 / report.notifications
                if (share >= MIN_NOTIFIER_SHARE) add(Insight.NotificationMagnet(top.label, top.notifications, share))
            }
        }

        if (report.installed.isNotEmpty()) {
            add(Insight.NewApps(report.installed.size, report.installed.map { it.label }))
        }
    }.take(MAX_INSIGHTS)

    companion object {
        const val MIN_TREND_PERCENT = 5
        const val MIN_TOP_APP_SHARE = 20
        const val MIN_DAYS_FOR_BUSIEST = 3
        const val MIN_LATE_NIGHT_MS = 30 * 60 * 1000L
        const val MIN_NOTIFICATIONS = 20
        const val MIN_NOTIFIER_SHARE = 25
        const val MAX_INSIGHTS = 4
        val LATE_NIGHT_HOURS = listOf(23, 0, 1, 2, 3, 4)
    }
}
