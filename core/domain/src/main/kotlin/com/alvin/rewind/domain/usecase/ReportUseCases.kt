package com.alvin.rewind.domain.usecase

import com.alvin.rewind.domain.report.ReportBuilder
import com.alvin.rewind.domain.report.WeeklyReport
import com.alvin.rewind.domain.repository.ActivityRepository
import com.alvin.rewind.domain.time.WeekRange
import java.time.Clock
import java.time.LocalDate

/** Which data a report is built from. */
enum class ReportSource { DEVICE, SAMPLE }

/**
 * Builds the weekly report for a week, from either the device data or sample data.
 * The repository only *loads* data; the business rules (aggregation, trends, insights) are applied here,
 * so the live report and the demo report are guaranteed to follow the same rules.
 */
class GetWeeklyReportUseCase(
    private val deviceActivity: ActivityRepository,
    private val sampleActivity: ActivityRepository,
    private val builder: ReportBuilder = ReportBuilder(),
) {
    suspend operator fun invoke(week: WeekRange, source: ReportSource): WeeklyReport {
        val repository = when (source) {
            ReportSource.DEVICE -> deviceActivity
            ReportSource.SAMPLE -> sampleActivity
        }
        return builder.build(repository.weekActivity(week))
    }
}

/** Copies the latest system data into local storage. Called on app open, on pull to refresh and by the background worker. */
class RefreshActivityDataUseCase(private val deviceActivity: ActivityRepository) {
    suspend operator fun invoke() = deviceActivity.refresh()
}

/** The week containing "today", in the user's time zone. */
class GetCurrentWeekUseCase(private val clock: Clock) {
    operator fun invoke(): WeekRange = WeekRange.containing(LocalDate.now(clock))
}
