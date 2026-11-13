package com.alvin.rewind.domain.sample

import com.alvin.rewind.domain.apps.AppChange
import com.alvin.rewind.domain.apps.AppChangeType
import com.alvin.rewind.domain.apps.AppInfo
import com.alvin.rewind.domain.report.ReportInput
import com.alvin.rewind.domain.time.WeekRange
import com.alvin.rewind.domain.usage.AppDayUsage
import com.alvin.rewind.domain.usage.DailyUsage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Deterministic, plausible sample data for demo mode and Compose previews.
 *
 * Emulators have almost no usage history, and a reviewer shouldn't have to grant special
 * permissions to see what the app does. The sample goes through the same ReportBuilder as real data.
 * Same week -> same numbers (seeded by the week), so screenshots and demos are reproducible.
 */
object SampleData {

    private const val MINUTE = 60_000L

    // Declared before `apps`: object properties initialise top to bottom.
    private val DEFAULT_PROFILE = DoubleArray(24) { h ->
        when (h) {
            in 0..5 -> 0.1
            in 7..8 -> 1.5
            in 12..13 -> 1.5
            in 18..22 -> 2.0
            23 -> 0.8
            else -> 1.0
        }
    }

    private data class SampleApp(
        val info: AppInfo,
        val minutesPerDay: Int,
        val opensPerDay: Int,
        val notificationsPerDay: Int,
        val weekendFactor: Double = 1.0,
        /** relative weight per hour of day, 24 values */
        val profile: DoubleArray = DEFAULT_PROFILE,
    )

    private fun app(pkg: String, label: String, video: Boolean = false) = AppInfo(pkg, label, video)

    private val EVENING = DoubleArray(24) { h -> if (h in 19..23) 3.0 else if (h in 7..18) 0.6 else 0.15 }
    private val WORK = DoubleArray(24) { h -> if (h in 9..17) 3.0 else if (h in 7..8 || h in 18..20) 0.8 else 0.05 }

    private val apps = listOf(
        SampleApp(app("com.whatsapp", "WhatsApp"), 48, 42, 70),
        SampleApp(app("com.instagram.android", "Instagram"), 52, 24, 14, weekendFactor = 1.4),
        SampleApp(app("com.google.android.youtube", "YouTube", video = true), 46, 7, 4, weekendFactor = 1.8, profile = EVENING),
        SampleApp(app("com.netflix.mediaclient", "Netflix", video = true), 28, 1, 1, weekendFactor = 2.5, profile = EVENING),
        SampleApp(app("com.Slack", "Slack"), 30, 18, 34, weekendFactor = 0.1, profile = WORK),
        SampleApp(app("com.google.android.gm", "Gmail"), 12, 11, 16, weekendFactor = 0.4, profile = WORK),
        SampleApp(app("com.spotify.music", "Spotify"), 14, 5, 1),
        SampleApp(app("com.google.android.apps.maps", "Maps"), 9, 3, 2),
        SampleApp(app("com.supercell.clashroyale", "Clash Royale"), 22, 6, 5, weekendFactor = 1.5, profile = EVENING),
        SampleApp(app("com.duolingo", "Duolingo"), 7, 2, 3),
    )

    private val installedThisWeek = app("com.app.tgtg", "Too Good To Go")
    private val removedThisWeek = app("com.king.candycrushsaga", "Candy Crush Saga")

    private val catalog: Map<String, AppInfo> =
        (apps.map { it.info } + installedThisWeek + removedThisWeek).associateBy { it.packageName }

    fun appInfo(packageName: String): AppInfo = catalog[packageName] ?: AppInfo(packageName, packageName, false)

    fun reportInput(week: WeekRange, today: LocalDate, zone: ZoneId): ReportInput {
        val daily = dailyUsage(week, today)
        val notifications = week.days.filter { !it.isAfter(today) }.associateWith { notificationsFor(it) }
        val changes = listOf(
            change(installedThisWeek, AppChangeType.INSTALLED, week.start.with(DayOfWeek.TUESDAY), LocalTime.of(12, 41), zone),
            change(removedThisWeek, AppChangeType.UNINSTALLED, week.start.with(DayOfWeek.THURSDAY), LocalTime.of(22, 5), zone),
        ).filter { it.atMs <= today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }

        return ReportInput(
            week = week,
            today = today,
            zone = zone,
            dailyUsage = daily,
            previousWeekUsage = dailyUsage(week.previous(), today),
            notificationsPerDay = notifications,
            appChanges = changes,
            appInfo = ::appInfo,
            usageAccessGranted = true,
            notificationAccessGranted = true,
            notificationsTrackedSince = week.start.minusWeeks(4),
        )
    }

    fun dailyUsage(week: WeekRange, today: LocalDate): List<DailyUsage> =
        week.days.filter { !it.isAfter(today) }.map { usageFor(it) }

    private fun usageFor(date: LocalDate): DailyUsage {
        val random = Random(date.toEpochDay())
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        val hourly = LongArray(DailyUsage.HOURS)
        val usage = apps.map { sample ->
            val factor = (if (weekend) sample.weekendFactor else 1.0) * (0.6 + random.nextDouble() * 0.8)
            val ms = (sample.minutesPerDay * factor * MINUTE).toLong()
            val opens = maxOf(if (ms > 0) 1 else 0, (sample.opensPerDay * factor).toInt())
            val weightSum = sample.profile.sum()
            sample.profile.forEachIndexed { h, w -> hourly[h] += (ms * w / weightSum).toLong() }
            AppDayUsage(sample.info.packageName, ms, opens)
        }.filter { it.foregroundMs > 0 }
        return DailyUsage(date, usage, unlocks = 55 + random.nextInt(40), hourlyForegroundMs = hourly.toList())
    }

    private fun notificationsFor(date: LocalDate): Map<String, Int> {
        val random = Random(date.toEpochDay() * 31)
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        return apps.associate { sample ->
            val factor = (if (weekend) sample.weekendFactor else 1.0) * (0.5 + random.nextDouble())
            sample.info.packageName to (sample.notificationsPerDay * factor).toInt()
        }.filterValues { it > 0 }
    }

    private fun change(info: AppInfo, type: AppChangeType, date: LocalDate, time: LocalTime, zone: ZoneId) =
        AppChange(info.packageName, info.label, type, date.atTime(time).atZone(zone).toInstant().toEpochMilli(), isExactTime = true)
}
