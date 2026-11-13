package com.alvin.rewind.data.repo

import com.alvin.rewind.domain.report.ReportInput
import com.alvin.rewind.domain.repository.ActivityRepository
import com.alvin.rewind.domain.sample.SampleData
import com.alvin.rewind.domain.time.WeekRange
import java.time.Clock
import java.time.LocalDate

/** Deterministic sample data behind the same contract as the device data ("Explore with sample data"). */
class SampleActivityRepository(private val clock: Clock) : ActivityRepository {

    override suspend fun refresh() = Unit

    override suspend fun weekActivity(week: WeekRange): ReportInput =
        SampleData.reportInput(week, LocalDate.now(clock), clock.zone)
}
