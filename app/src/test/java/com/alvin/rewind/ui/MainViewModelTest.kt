package com.alvin.rewind.ui

import com.alvin.rewind.domain.access.AccessStatus
import com.alvin.rewind.domain.report.ReportInput
import com.alvin.rewind.domain.repository.AccessRepository
import com.alvin.rewind.domain.repository.ActivityRepository
import com.alvin.rewind.domain.sample.SampleData
import com.alvin.rewind.domain.time.WeekRange
import com.alvin.rewind.domain.usecase.CompleteOnboardingUseCase
import com.alvin.rewind.domain.usecase.GetAccessStatusUseCase
import com.alvin.rewind.domain.usecase.GetCurrentWeekUseCase
import com.alvin.rewind.domain.usecase.GetWeeklyReportUseCase
import com.alvin.rewind.domain.usecase.IsSetupCompleteUseCase
import com.alvin.rewind.domain.usecase.RefreshActivityDataUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ViewModel runs against the *real* use cases; only the repositories (the domain's outer
 * boundary) are faked. No Android, no Room, no emulator.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val zone = ZoneId.of("Europe/Brussels")
    private val today = LocalDate.of(2026, 11, 11) // Wednesday
    private val clock = Clock.fixed(today.atTime(15, 0).atZone(zone).toInstant(), zone)
    private val thisWeek = WeekRange.containing(today)

    private inner class FakeActivity : ActivityRepository {
        var refreshes = 0
        override suspend fun refresh() {
            refreshes++
        }
        override suspend fun weekActivity(week: WeekRange): ReportInput = SampleData.reportInput(week, today, zone)
    }

    private class FakeAccess(var status: AccessStatus, var completed: Boolean = false) : AccessRepository {
        override fun status() = status
        override fun isOnboardingCompleted() = completed
        override fun setOnboardingCompleted() {
            completed = true
        }
    }

    private val device = FakeActivity()
    private val sample = FakeActivity()

    private fun viewModel(access: FakeAccess) = MainViewModel(
        getWeeklyReport = GetWeeklyReportUseCase(device, sample),
        refreshActivityData = RefreshActivityDataUseCase(device),
        getCurrentWeek = GetCurrentWeekUseCase(clock),
        getAccessStatus = GetAccessStatusUseCase(access),
        isSetupComplete = IsSetupCompleteUseCase(access),
        completeOnboarding = CompleteOnboardingUseCase(access),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `first launch starts in onboarding and loads nothing`() = runTest {
        val vm = viewModel(FakeAccess(AccessStatus()))
        vm.onResume()

        assertEquals(Mode.ONBOARDING, vm.state.value.mode)
        assertNull(vm.state.value.report)
        assertEquals(0, device.refreshes)
    }

    @Test
    fun `continue is ignored until usage access is granted`() = runTest {
        val access = FakeAccess(AccessStatus())
        val vm = viewModel(access)

        vm.onContinueClicked()
        assertEquals(Mode.ONBOARDING, vm.state.value.mode)

        access.status = AccessStatus(usageAccess = true)
        vm.onResume()
        vm.onContinueClicked()

        assertEquals(Mode.LIVE, vm.state.value.mode)
        assertNotNull(vm.state.value.report)
        assertEquals(1, device.refreshes)
        assertTrue(access.completed)
    }

    @Test
    fun `returning user with access goes straight to the report and refreshes`() = runTest {
        val vm = viewModel(FakeAccess(AccessStatus(usageAccess = true), completed = true))
        vm.onResume()

        assertEquals(Mode.LIVE, vm.state.value.mode)
        assertEquals(thisWeek, vm.state.value.report?.week)
        assertFalse(vm.state.value.isLoading)
        assertEquals(1, device.refreshes)
    }

    @Test
    fun `revoking usage access sends the user back to setup`() = runTest {
        val access = FakeAccess(AccessStatus(usageAccess = true), completed = true)
        val vm = viewModel(access)
        vm.onResume()

        access.status = AccessStatus(usageAccess = false)
        vm.onResume()

        assertEquals(Mode.ONBOARDING, vm.state.value.mode)
    }

    @Test
    fun `demo mode uses sample data and never refreshes the device`() = runTest {
        val vm = viewModel(FakeAccess(AccessStatus()))
        vm.onTryDemoClicked()
        vm.onRefreshClicked()

        assertEquals(Mode.DEMO, vm.state.value.mode)
        assertNotNull(vm.state.value.report)
        assertEquals(0, device.refreshes)
    }

    @Test
    fun `week navigation cannot go past the current week`() = runTest {
        val vm = viewModel(FakeAccess(AccessStatus()))
        vm.onTryDemoClicked()

        vm.onNextWeekClicked()
        assertEquals(thisWeek, vm.state.value.week)

        vm.onPreviousWeekClicked()
        assertEquals(thisWeek.previous(), vm.state.value.week)
        assertEquals(thisWeek.previous(), vm.state.value.report?.week)
        assertTrue(vm.state.value.canGoForward)

        vm.onNextWeekClicked()
        assertEquals(thisWeek, vm.state.value.week)
    }
}
