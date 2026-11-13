package com.alvin.rewind.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alvin.rewind.domain.report.DaySummary
import com.alvin.rewind.ui.format.Format

/*
 * Charts are plain layout boxes rather than Canvas drawing: simpler, theme-aware, and each bar can
 * carry an accessibility description (TalkBack reads "Tuesday, 3h 12m").
 */

@Composable
fun DailyBarChart(days: List<DaySummary>, modifier: Modifier = Modifier) {
    val max = (days.maxOfOrNull { it.screenTimeMs } ?: 0L).coerceAtLeast(1L)
    val busiest = days.filter { it.hasUsageData }.maxByOrNull { it.screenTimeMs }?.date
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val description = if (day.hasUsageData) {
                "${Format.dayLong(day.date)}, ${Format.duration(day.screenTimeMs)}"
            } else {
                "${Format.dayLong(day.date)}, no data"
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (day.hasUsageData) Format.durationCompact(day.screenTimeMs) else "–",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .height(120.dp),
                ) {
                    val fraction = (day.screenTimeMs.toFloat() / max).coerceIn(0.02f, 1f)
                    val color = when {
                        !day.hasUsageData -> MaterialTheme.colorScheme.surfaceVariant
                        day.date == busiest -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(color),
                    )
                }
                Text(Format.dayShort(day.date), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 24 thin bars: when in the day the phone is used. */
@Composable
fun HourlyRhythm(hourlyMs: List<Long>, modifier: Modifier = Modifier) {
    val max = (hourlyMs.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val peakHour = hourlyMs.indices.maxByOrNull { hourlyMs[it] }
    Column(modifier.semantics { contentDescription = "Usage by hour of day, busiest around ${peakHour ?: 0}:00" }) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hourlyMs.forEachIndexed { hour, ms ->
                val lateNight = hour >= 23 || hour < 5
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((ms.toFloat() / max).coerceIn(0.03f, 1f))
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(if (lateNight) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf("00", "06", "12", "18", "24").forEachIndexed { index, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        4 -> TextAlign.End
                        else -> TextAlign.Center
                    },
                )
            }
        }
    }
}
