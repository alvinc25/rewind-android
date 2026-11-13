package com.alvin.rewind.domain.usage

import com.alvin.rewind.domain.time.endOfDayMillis
import com.alvin.rewind.domain.time.startOfDayMillis
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyUsageCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Europe/Brussels")
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val start = day.startOfDayMillis(zone)
    private val end = day.endOfDayMillis(zone)
    private val calculator = DailyUsageCalculator()

    private fun at(hour: Int, minute: Int) = start + (hour * 60L + minute) * 60_000
    private fun ev(pkg: String, type: UsageEventType, t: Long) = UsageEvent(pkg, type, t)

    @Test
    fun `aggregates per app and splits time across hour boundaries`() {
        val events = listOf(
            ev("chat", UsageEventType.APP_FOREGROUND, at(22, 50)),
            ev("chat", UsageEventType.APP_BACKGROUND, at(23, 20)),
        )
        val usage = calculator.calculate(day, events, start, end, zone)

        assertEquals(listOf(AppDayUsage("chat", 30 * 60_000L, 1)), usage.apps)
        assertEquals(10 * 60_000L, usage.hourlyForegroundMs[22])
        assertEquals(20 * 60_000L, usage.hourlyForegroundMs[23])
    }

    @Test
    fun `launcher is excluded from usage but still closes the previous app`() {
        val events = listOf(
            ev("chat", UsageEventType.APP_FOREGROUND, at(9, 0)),
            ev("launcher", UsageEventType.APP_FOREGROUND, at(9, 10)), // no pause event for chat
            ev("launcher", UsageEventType.APP_BACKGROUND, at(9, 30)),
        )
        val usage = calculator.calculate(day, events, start, end, zone, excludedPackages = setOf("launcher"))

        assertEquals(listOf(AppDayUsage("chat", 10 * 60_000L, 1)), usage.apps)
        assertEquals(10 * 60_000L, usage.screenTimeMs)
    }

    @Test
    fun `counts unlocks in the window only`() {
        val events = listOf(
            ev("android", UsageEventType.DEVICE_UNLOCK, at(8, 0)),
            ev("android", UsageEventType.DEVICE_UNLOCK, at(12, 0)),
            ev("android", UsageEventType.DEVICE_UNLOCK, end + 1),
        )
        assertEquals(2, calculator.calculate(day, events, start, end, zone).unlocks)
    }
}
