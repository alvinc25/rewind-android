package com.alvin.rewind.domain.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * A calendar week: [start, start + 7 days).
 *
 * Weeks start on Monday (ISO-8601, which is also the norm in Belgium/EU).
 * Dates are local dates; converting to epoch millis always goes through an explicit [ZoneId]
 * so that "Monday 00:00" means Monday midnight where the user actually is.
 */
data class WeekRange(val start: LocalDate) {

    init {
        require(start.dayOfWeek == FIRST_DAY) { "A week must start on $FIRST_DAY, got ${start.dayOfWeek}" }
    }

    val endExclusive: LocalDate get() = start.plusDays(DAYS)

    val days: List<LocalDate> get() = (0 until DAYS).map { start.plusDays(it) }

    fun previous(): WeekRange = WeekRange(start.minusWeeks(1))

    fun next(): WeekRange = WeekRange(start.plusWeeks(1))

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(endExclusive)

    fun startMillis(zone: ZoneId): Long = start.startOfDayMillis(zone)

    fun endMillis(zone: ZoneId): Long = endExclusive.startOfDayMillis(zone)

    companion object {
        const val DAYS = 7L
        val FIRST_DAY: DayOfWeek = DayOfWeek.MONDAY

        fun containing(date: LocalDate): WeekRange =
            WeekRange(date.with(TemporalAdjusters.previousOrSame(FIRST_DAY)))
    }
}

fun LocalDate.startOfDayMillis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

fun LocalDate.endOfDayMillis(zone: ZoneId): Long = plusDays(1).startOfDayMillis(zone)
