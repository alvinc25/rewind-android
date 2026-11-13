package com.alvin.rewind.domain.usecase

import com.alvin.rewind.domain.report.WeeklyReport

/**
 * Decides whether a Sunday digest should be sent, and with which report.
 * Returns null when there is nothing to send: no usage access, notifications not allowed,
 * or an empty week. Keeping this rule here (not in the worker) makes it unit-testable.
 */
class GetWeeklyDigestUseCase(
    private val getAccessStatus: GetAccessStatusUseCase,
    private val refreshActivityData: RefreshActivityDataUseCase,
    private val getWeeklyReport: GetWeeklyReportUseCase,
    private val getCurrentWeek: GetCurrentWeekUseCase,
) {
    suspend operator fun invoke(): WeeklyReport? {
        val access = getAccessStatus()
        if (!access.usageAccess || !access.postNotifications) return null
        refreshActivityData()
        return getWeeklyReport(getCurrentWeek(), ReportSource.DEVICE).takeUnless { it.isEmpty }
    }
}
