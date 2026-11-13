package com.alvin.rewind.domain.time

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * When the "your week in review" notification should fire.
 * Sunday evening: the week is (almost) complete and people tend to have a moment to look back.
 */
object DigestSchedule {
    val DAY: DayOfWeek = DayOfWeek.SUNDAY
    val TIME: LocalTime = LocalTime.of(19, 0)

    /** The next digest moment strictly after [now]. */
    fun nextRun(now: ZonedDateTime, day: DayOfWeek = DAY, time: LocalTime = TIME): ZonedDateTime {
        val candidate = now.with(TemporalAdjusters.nextOrSame(day)).with(time)
        return if (candidate.isAfter(now)) candidate else candidate.plusWeeks(1)
    }
}
