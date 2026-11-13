package com.alvin.rewind.ui.format

import com.alvin.rewind.domain.report.Insight
import com.alvin.rewind.domain.time.WeekRange
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * All user-facing wording in one place. English only for now; moving these to string resources
 * (EN/NL/FR for Belgium) is mechanical because the domain only emits typed values.
 */
object Format {

    fun duration(ms: Long): String {
        val totalMinutes = ms / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    /** Compact label for chart bars: "3.4h", "45m". */
    fun durationCompact(ms: Long): String =
        if (ms >= 3_600_000) String.format(Locale.getDefault(), "%.1fh", ms / 3_600_000.0) else "${ms / 60_000}m"

    fun weekTitle(week: WeekRange): String {
        val end = week.endExclusive.minusDays(1)
        val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        return if (week.start.month == end.month) {
            "${week.start.dayOfMonth} – ${end.format(dayMonth)}"
        } else {
            "${week.start.format(dayMonth)} – ${end.format(dayMonth)}"
        }
    }

    fun dayShort(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    fun dayLong(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

    fun dateShort(date: LocalDate): String =
        "${dayShort(date)} ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}"

    fun trend(percent: Int): String = when {
        percent > 0 -> "▲ $percent% vs last week"
        percent < 0 -> "▼ ${-percent}% vs last week"
        else -> "Same as last week"
    }

    fun insight(insight: Insight): Pair<String, String> = when (insight) {
        is Insight.TrendVsLastWeek ->
            (if (insight.percent > 0) "📈" else "📉") to
                "Your daily screen time is ${if (insight.percent > 0) "up" else "down"} ${kotlin.math.abs(insight.percent)}% compared with last week."
        is Insight.TopApp ->
            "🏆" to "${insight.label} took ${insight.sharePercent}% of your screen time (${duration(insight.foregroundMs)})."
        is Insight.BusiestDay ->
            "📅" to "${dayLong(insight.date)} was your busiest day with ${duration(insight.screenTimeMs)}."
        is Insight.LateNight ->
            "🌙" to "You spent ${duration(insight.totalMs)} on your phone between 23:00 and 05:00."
        is Insight.NotificationMagnet ->
            "🔔" to "${insight.label} sent ${insight.count} notifications, ${insight.sharePercent}% of all you received."
        is Insight.NewApps ->
            "✨" to if (insight.count == 1) {
                "You tried a new app: ${insight.labels.first()}."
            } else {
                "You installed ${insight.count} new apps: ${insight.labels.joinToString()}."
            }
    }
}
