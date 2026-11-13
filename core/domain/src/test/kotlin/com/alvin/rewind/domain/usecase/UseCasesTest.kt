package com.alvin.rewind.domain.usecase

import com.alvin.rewind.domain.access.AccessStatus
import com.alvin.rewind.domain.report.ReportInput
import com.alvin.rewind.domain.repository.AccessRepository
import com.alvin.rewind.domain.repository.ActivityRepository
import com.alvin.rewind.domain.sample.SampleData
import com.alvin.rewind.domain.time.WeekRange
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ZONE: ZoneId = ZoneId.of("Europe/Brussels")
private val TODAY: LocalDate = LocalDate.of(2026, 11, 15) // Sunday
private val CLOCK: Clock = Clock.fixed(TODAY.atTime(19, 0).atZone(ZONE).toInstant(), ZONE)

/** Fake: sample data, or an empty week. Records calls. */
private class FakeActivityRepository(private val empty: Boolean = false) : ActivityRepository {
    var refreshCalls = 0
    val requestedWeeks = mutableListOf<WeekRange>()

    override suspend fun refresh() {
        refreshCalls++
    }

    override suspend fun weekActivity(week: WeekRange): ReportInput {
        requestedWeeks += week
        val input = SampleData.reportInput(week, TODAY, ZONE)
        return if (empty) {
            input.copy(dailyUsage = emptyList(), previousWeekUsage = emptyList(), notificationsPerDay = emptyMap(), appChanges = emptyList())
        } else {
            input
        }
    }
}

private class FakeAccessRepository(var status: AccessStatus, var completed: Boolean = false) : AccessRepository {
    override fun status() = status
    override fun isOnboardingCompleted() = completed
    override fun setOnboardingCompleted() {
        completed = true
    }
}

class UseCasesTest {

    private val week = WeekRange.containing(TODAY)

    @Test
    fun `weekly report reads from the source that was asked for`() = runBlocking {
        val device = FakeActivityRepository()
        val sample = FakeActivityRepository()
        val useCase = GetWeeklyReportUseCase(device, sample)

        val report = useCase(week, ReportSource.SAMPLE)

        assertEquals(week, report.week)
        assertEquals(listOf(week), sample.requestedWeeks)
        assertTrue(device.requestedWeeks.isEmpty())
        assertTrue(report.insights.isNotEmpty()) // business rules applied by the use case
    }

    @Test
    fun `refresh only touches the device repository`() = runBlocking {
        val device = FakeActivityRepository()
        RefreshActivityDataUseCase(device)()
        assertEquals(1, device.refreshCalls)
    }

    @Test
    fun `current week follows the injected clock`() {
        assertEquals(LocalDate.of(2026, 11, 9), GetCurrentWeekUseCase(CLOCK)().start)
    }

    @Test
    fun `onboarding cannot complete without usage access`() {
        val access = FakeAccessRepository(AccessStatus(usageAccess = false))
        assertFalse(CompleteOnboardingUseCase(access)())
        assertFalse(access.completed)

        access.status = AccessStatus(usageAccess = true)
        assertTrue(CompleteOnboardingUseCase(access)())
        assertTrue(access.completed)
    }

    @Test
    fun `setup is incomplete again when usage access is revoked`() {
        val access = FakeAccessRepository(AccessStatus(usageAccess = true), completed = true)
        assertTrue(IsSetupCompleteUseCase(access)())
        access.status = AccessStatus(usageAccess = false)
        assertFalse(IsSetupCompleteUseCase(access)())
    }

    private fun digest(status: AccessStatus, device: FakeActivityRepository): GetWeeklyDigestUseCase {
        val access = FakeAccessRepository(status)
        return GetWeeklyDigestUseCase(
            getAccessStatus = GetAccessStatusUseCase(access),
            refreshActivityData = RefreshActivityDataUseCase(device),
            getWeeklyReport = GetWeeklyReportUseCase(device, FakeActivityRepository()),
            getCurrentWeek = GetCurrentWeekUseCase(CLOCK),
        )
    }

    @Test
    fun `digest is sent when allowed and the week has data`() = runBlocking {
        val device = FakeActivityRepository()
        val report = digest(AccessStatus(usageAccess = true, postNotifications = true), device)()
        assertNotNull(report)
        assertEquals(1, device.refreshCalls)
    }

    @Test
    fun `no digest without notification permission, and no refresh either`() = runBlocking {
        val device = FakeActivityRepository()
        assertNull(digest(AccessStatus(usageAccess = true, postNotifications = false), device)())
        assertEquals(0, device.refreshCalls)
    }

    @Test
    fun `no digest for an empty week`() = runBlocking {
        val device = FakeActivityRepository(empty = true)
        assertNull(digest(AccessStatus(usageAccess = true, postNotifications = true), device)())
    }
}
