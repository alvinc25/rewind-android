package com.alvin.rewind.domain.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Aggregates one day of raw events into the compact [DailyUsage] we persist.
 *
 * [excludedPackages] (home launcher, System UI, this app) are removed *after* sessions are reduced:
 * the launcher coming to the foreground is still a useful signal that the previous app was closed,
 * we just don't want "time spent on the home screen" to show up as app usage.
 */
class DailyUsageCalculator(private val reducer: SessionReducer = SessionReducer()) {

    fun calculate(
        date: LocalDate,
        events: List<UsageEvent>,
        windowStartMs: Long,
        windowEndMs: Long,
        zone: ZoneId,
        excludedPackages: Set<String> = emptySet(),
    ): DailyUsage {
        val sessions = reducer.reduce(events, windowStartMs, windowEndMs)
            .filterNot { it.packageName in excludedPackages }

        val apps = sessions
            .groupBy { it.packageName }
            .map { (pkg, list) -> AppDayUsage(pkg, list.sumOf { it.durationMs }, list.size) }
            .sortedByDescending { it.foregroundMs }

        val hourly = LongArray(DailyUsage.HOURS)
        sessions.forEach { spreadOverHours(it, zone, hourly) }

        val unlocks = events.count {
            it.type == UsageEventType.DEVICE_UNLOCK && it.timestampMs in windowStartMs until windowEndMs
        }

        return DailyUsage(date, apps, unlocks, hourly.toList())
    }

    /** Splits a session across local hour boundaries (so a 22:50–23:20 session counts 10' + 20'). */
    private fun spreadOverHours(session: AppSession, zone: ZoneId, hourly: LongArray) {
        var t = session.startMs
        while (t < session.endMs) {
            val local = Instant.ofEpochMilli(t).atZone(zone)
            val nextHour = local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val segmentEnd = minOf(nextHour, session.endMs)
            hourly[local.hour] += segmentEnd - t
            t = segmentEnd
        }
    }
}
