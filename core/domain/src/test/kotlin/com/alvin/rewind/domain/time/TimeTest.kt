package com.alvin.rewind.domain.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeTest {

    private val zone = ZoneId.of("Europe/Brussels")

    @Test
    fun `week containing a Wednesday starts on the Monday before`() {
        val week = WeekRange.containing(LocalDate.of(2026, 11, 11))
        assertEquals(LocalDate.of(2026, 11, 9), week.start)
        assertEquals(LocalDate.of(2026, 11, 16), week.endExclusive)
        assertEquals(7, week.days.size)
    }

    @Test
    fun `week containing a Monday starts that day and Sunday belongs to the same week`() {
        assertEquals(LocalDate.of(2026, 11, 9), WeekRange.containing(LocalDate.of(2026, 11, 9)).start)
        assertEquals(LocalDate.of(2026, 11, 9), WeekRange.containing(LocalDate.of(2026, 11, 15)).start)
    }

    @Test
    fun `contains is half open`() {
        val week = WeekRange(LocalDate.of(2026, 11, 9))
        assertTrue(LocalDate.of(2026, 11, 15) in week)
        assertFalse(LocalDate.of(2026, 11, 16) in week)
    }

    @Test
    fun `week must start on Monday`() {
        assertFailsWith<IllegalArgumentException> { WeekRange(LocalDate.of(2026, 11, 10)) }
    }

    @Test
    fun `DST week is 169 hours long in Brussels`() {
        // Clocks go back on 25 Oct 2026 -> that week has one extra hour.
        val week = WeekRange.containing(LocalDate.of(2026, 10, 25))
        val hours = (week.endMillis(zone) - week.startMillis(zone)) / 3_600_000
        assertEquals(169, hours)
    }

    @Test
    fun `next digest is the coming Sunday 19h, or next week if already past`() {
        val wednesday = ZonedDateTime.of(2026, 11, 11, 15, 0, 0, 0, zone)
        val next = DigestSchedule.nextRun(wednesday)
        assertEquals(DayOfWeek.SUNDAY, next.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 15), next.toLocalDate())
        assertEquals(19, next.hour)

        val sundayLate = ZonedDateTime.of(2026, 11, 15, 20, 0, 0, 0, zone)
        assertEquals(LocalDate.of(2026, 11, 22), DigestSchedule.nextRun(sundayLate).toLocalDate())
    }
}
