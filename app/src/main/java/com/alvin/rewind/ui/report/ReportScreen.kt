package com.alvin.rewind.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alvin.rewind.domain.report.ReportBuilder
import com.alvin.rewind.domain.report.WeeklyReport
import com.alvin.rewind.domain.sample.SampleData
import com.alvin.rewind.domain.time.WeekRange
import com.alvin.rewind.ui.MainUiState
import com.alvin.rewind.ui.Mode
import com.alvin.rewind.ui.format.Format
import com.alvin.rewind.ui.theme.RewindTheme
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    state: MainUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.mode == Mode.DEMO) "Rewind · sample" else "Rewind") },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                    IconButton(onClick = onOpenSetup) { Icon(Icons.Filled.Settings, contentDescription = "Permissions and setup") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WeekSelector(state, onPreviousWeek, onNextWeek)
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

            val report = state.report
            when {
                state.error != null && report == null -> Message("Couldn't build your report: ${state.error}")
                report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> ReportContent(report, isDemo = state.mode == Mode.DEMO, onOpenSetup = onOpenSetup)
            }
        }
    }
}

@Composable
private fun WeekSelector(state: MainUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Format.weekTitle(state.week), style = MaterialTheme.typography.titleMedium)
            if (state.week == state.currentWeek) {
                Text("This week, so far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        IconButton(onClick = onNext, enabled = state.canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
        }
    }
}

@Composable
private fun ReportContent(report: WeeklyReport, isDemo: Boolean, onOpenSetup: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isDemo) {
            item {
                Banner(
                    "This is sample data, not your phone. Grant usage access to see your own week.",
                    action = "Set up" to onOpenSetup,
                )
            }
        } else {
            coverageMessages(report).forEach { message -> item { Banner(message) } }
        }

        item { HeroCard(report) }
        item { Section("Day by day") { DailyBarChart(report.days) } }
        item { StatGrid(report) }

        if (report.insights.isNotEmpty()) {
            item {
                Section("Highlights") {
                    report.insights.forEach { insight ->
                        val (emoji, text) = Format.insight(insight)
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text(emoji, modifier = Modifier.padding(end = 12.dp))
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        if (report.topApps.isNotEmpty()) {
            item {
                Section("Where your time went") {
                    val max = report.topApps.first().foregroundMs
                    report.topApps.take(TOP_APPS).forEach { app ->
                        AppUsageRow(app, max, Modifier.padding(vertical = 6.dp))
                    }
                    val others = report.topApps.drop(TOP_APPS)
                    if (others.isNotEmpty()) {
                        Text(
                            "+ ${others.size} other apps · ${Format.duration(others.sumOf { it.foregroundMs })}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        if (report.topNotifiers.isNotEmpty()) {
            item {
                Section("Who interrupted you") {
                    report.topNotifiers.forEach { app ->
                        SimpleAppRow(app.packageName, app.label, "${app.notifications}", Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        if (report.totalScreenTimeMs > 0) {
            item {
                Section("Your daily rhythm") {
                    HourlyRhythm(report.hourlyMs)
                    Text(
                        "Late-night hours (23:00–05:00) are highlighted.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        if (report.installed.isNotEmpty() || report.uninstalled.isNotEmpty()) {
            item {
                Section("Apps that came and went") {
                    report.installed.forEach {
                        SimpleAppRow(it.packageName, it.label, "Installed ${Format.dayShort(it.date())}", Modifier.padding(vertical = 4.dp))
                    }
                    report.uninstalled.forEach {
                        val whenText = if (it.isExactTime) Format.dayShort(it.date()) else "by ${Format.dayShort(it.date())}"
                        SimpleAppRow(it.packageName, it.label, "Removed $whenText", Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        if (report.isEmpty) {
            item { Message("Nothing recorded for this week yet.") }
        }

        item { NotTrackedFooter() }
    }
}

@Composable
private fun HeroCard(report: WeeklyReport) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Screen time", style = MaterialTheme.typography.labelLarge)
            Text(
                Format.duration(report.totalScreenTimeMs),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Daily average ${Format.duration(report.dailyAverageMs)}" +
                    (report.trendPercent?.let { " · ${Format.trend(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StatGrid(report: WeeklyReport) {
    val notifications = if (report.coverage.notificationAccessGranted || report.notifications > 0) "${report.notifications}" else "—"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Unlocks", "${report.unlocks}", Modifier.weight(1f))
            StatTile("App opens", "${report.appOpens}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Notifications", notifications, Modifier.weight(1f))
            StatTile("Video watched ≈", Format.duration(report.videoEstimateMs), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun Banner(text: String, action: Pair<String, () -> Unit>? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.outline,
    )
}

/** Scope is a product decision too: say what we deliberately don't show, and why. */
@Composable
private fun NotTrackedFooter() {
    Column(Modifier.padding(vertical = 8.dp)) {
        HorizontalDivider()
        Text(
            "Not shown on purpose: game high scores (each game keeps them privately) and the ads you saw " +
                "(that would require reading your screen). Video time is estimated from time spent in video apps.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun coverageMessages(report: WeeklyReport): List<String> = buildList {
    val coverage = report.coverage
    if (coverage.usagePartial && coverage.daysElapsed > 0) {
        add("Usage data covers ${coverage.daysWithUsageData} of ${coverage.daysElapsed} days. Android only keeps a few days of history before Rewind was installed.")
    }
    val trackedSince = coverage.notificationsTrackedSince
    if (!coverage.notificationAccessGranted && report.notifications == 0) {
        add("Notification counts are off. Enable notification access in setup to count them.")
    } else if (coverage.notificationsPartial && trackedSince != null) {
        add("Notifications counted since ${Format.dateShort(trackedSince)}.")
    }
}

private fun com.alvin.rewind.domain.apps.AppChange.date(): LocalDate =
    java.time.Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()).toLocalDate()

private const val TOP_APPS = 8

@Preview(showBackground = true, heightDp = 1800)
@Composable
private fun ReportPreview() {
    val zone = ZoneId.of("Europe/Brussels")
    val today = LocalDate.of(2026, 11, 15)
    val week = WeekRange.containing(today)
    val report = ReportBuilder().build(SampleData.reportInput(week, today, zone))
    RewindTheme {
        ReportScreen(
            state = MainUiState(mode = Mode.DEMO, week = week, currentWeek = week, report = report),
            onPreviousWeek = {}, onNextWeek = {}, onRefresh = {}, onOpenSetup = {},
        )
    }
}
