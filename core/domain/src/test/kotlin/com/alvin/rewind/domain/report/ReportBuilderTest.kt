package com.alvin.rewind.domain.report

import com.alvin.rewind.domain.apps.AppChange
import com.alvin.rewind.domain.apps.AppChangeType
import com.alvin.rewind.domain.apps.AppInfo
import com.alvin.rewind.domain.sample.SampleData
import com.alvin.rewind.domain.time.WeekRange
import com.alvin.rewind.domain.usage.AppDayUsage
import com.alvin.rewind.domain.usage.DailyUsage
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportBuilderTest {

    private val zone = ZoneId.of("Europe/Brussels")
    private val week = WeekRange(LocalDate.of(2026, 11, 9))
    private val builder = ReportBuilder()
    private val hour = 3_600_000L

    private fun day(date: LocalDate, vararg apps: Pair<String, Long>, unlocks: Int = 10) = DailyUsage(
        date = date,
        apps = apps.map { (pkg, ms) -> AppDayUsage(pkg, ms, 3) },
        unlocks = unlocks,
        hourlyForegroundMs = List(24) { if (it == 20) apps.sumOf { a -> a.second } else 0L },
    )

    private val info = { pkg: String -> AppInfo(pkg, pkg.uppercase(), isVideo = pkg == "tube") }

    private fun input(
        today: LocalDate,
        usage: List<DailyUsage>,
        previous: List<DailyUsage> = emptyList(),
        notifications: Map<LocalDate, Map<String, Int>> = emptyMap(),
        changes: List<AppChange> = emptyList(),
        trackedSince: LocalDate? = week.start.minusDays(30),
    ) = ReportInput(week, today, zone, usage, previous, notifications, changes, info, true, true, trackedSince)

    @Test
    fun `totals, top apps and video estimate`() {
        val report = builder.build(
            input(
                today = week.start.plusDays(6),
                usage = listOf(
                    day(week.start, "chat" to 2 * hour, "tube" to 1 * hour),
                    day(week.start.plusDays(1), "tube" to 3 * hour),
                ),
            ),
        )
        assertEquals(6 * hour, report.totalScreenTimeMs)
        assertEquals(listOf("tube", "chat"), report.topApps.map { it.packageName })
        assertEquals(4 * hour, report.videoEstimateMs)
        assertEquals(20, report.unlocks)
        assertEquals(7, report.days.size)
        assertEquals(2, report.days.count { it.hasUsageData })
    }

    @Test
    fun `daily average ignores today and trend compares averages not totals`() {
        val today = week.start.plusDays(2) // Wednesday, in progress
        val report = builder.build(
            input(
                today = today,
                usage = listOf(
                    day(week.start, "chat" to 4 * hour),
                    day(week.start.plusDays(1), "chat" to 4 * hour),
                    day(today, "chat" to 1 * hour), // partial day, must not drag the average down
                ),
                previous = week.previous().days.map { day(it, "chat" to 2 * hour) },
            ),
        )
        assertEquals(4 * hour, report.dailyAverageMs)
        assertEquals(2 * hour, report.previousWeekDailyAverageMs)
        assertEquals(100, report.trendPercent)
        assertTrue(report.insights.contains(Insight.TrendVsLastWeek(100)))
    }

    @Test
    fun `no comparison when previous week has too little data`() {
        val report = builder.build(
            input(
                today = week.start.plusDays(6),
                usage = listOf(day(week.start, "chat" to hour)),
                previous = listOf(day(week.start.minusDays(1), "chat" to hour)),
            ),
        )
        assertNull(report.trendPercent)
    }

    @Test
    fun `notifications are summed per day and per app and changes are bucketed in the week`() {
        val inWeek = week.start.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val beforeWeek = week.start.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val report = builder.build(
            input(
                today = week.start.plusDays(6),
                usage = emptyList(),
                notifications = mapOf(
                    week.start to mapOf("chat" to 30, "mail" to 5),
                    week.start.plusDays(1) to mapOf("chat" to 10),
                    week.start.minusDays(1) to mapOf("chat" to 999), // previous week: excluded
                ),
                changes = listOf(
                    AppChange("new", "New", AppChangeType.INSTALLED, inWeek, true),
                    AppChange("old", "Old", AppChangeType.INSTALLED, beforeWeek, true),
                ),
            ),
        )
        assertEquals(45, report.notifications)
        assertEquals("chat", report.topNotifiers.first().packageName)
        assertEquals(40, report.topNotifiers.first().notifications)
        assertEquals(listOf("new"), report.installed.map { it.packageName })
        assertTrue(report.insights.any { it is Insight.NotificationMagnet })
    }

    @Test
    fun `coverage flags partial notification tracking`() {
        val report = builder.build(
            input(
                today = week.start.plusDays(3),
                usage = listOf(day(week.start, "chat" to hour)),
                trackedSince = week.start.plusDays(2),
            ),
        )
        assertTrue(report.coverage.notificationsPartial)
        assertEquals(4, report.coverage.daysElapsed)
        assertTrue(report.coverage.usagePartial)
    }

    @Test
    fun `sample data produces a full, plausible report through the same builder`() {
        val today = week.start.plusDays(6)
        val report = builder.build(SampleData.reportInput(week, today, zone))
        assertFalse(report.isEmpty)
        assertEquals(7, report.days.count { it.hasUsageData })
        assertTrue(report.videoEstimateMs > 0)
        assertEquals(1, report.installed.size)
        assertEquals(1, report.uninstalled.size)
        assertTrue(report.insights.isNotEmpty())
        // deterministic
        assertEquals(report, builder.build(SampleData.reportInput(week, today, zone)))
    }
}
