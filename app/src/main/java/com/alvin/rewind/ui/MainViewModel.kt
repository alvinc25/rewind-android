package com.alvin.rewind.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvin.rewind.domain.access.AccessStatus
import com.alvin.rewind.domain.report.WeeklyReport
import com.alvin.rewind.domain.time.WeekRange
import com.alvin.rewind.domain.usecase.CompleteOnboardingUseCase
import com.alvin.rewind.domain.usecase.GetAccessStatusUseCase
import com.alvin.rewind.domain.usecase.GetCurrentWeekUseCase
import com.alvin.rewind.domain.usecase.GetWeeklyReportUseCase
import com.alvin.rewind.domain.usecase.IsSetupCompleteUseCase
import com.alvin.rewind.domain.usecase.RefreshActivityDataUseCase
import com.alvin.rewind.domain.usecase.ReportSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Mode { ONBOARDING, LIVE, DEMO }

data class MainUiState(
    val mode: Mode,
    val week: WeekRange,
    val currentWeek: WeekRange,
    val access: AccessStatus = AccessStatus(),
    val report: WeeklyReport? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val canGoForward: Boolean get() = week.start.isBefore(currentWeek.start)
}

class MainViewModel(
    private val getWeeklyReport: GetWeeklyReportUseCase,
    private val refreshActivityData: RefreshActivityDataUseCase,
    getCurrentWeek: GetCurrentWeekUseCase,
    private val getAccessStatus: GetAccessStatusUseCase,
    isSetupComplete: IsSetupCompleteUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
) : ViewModel() {

    private val _state: MutableStateFlow<MainUiState> = getCurrentWeek().let { thisWeek ->
        MutableStateFlow(
            MainUiState(
                mode = if (isSetupComplete()) Mode.LIVE else Mode.ONBOARDING,
                week = thisWeek,
                currentWeek = thisWeek,
            ),
        )
    }
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun onResume() {
        val access = getAccessStatus()
        _state.update { current ->
            val mode = if (current.mode == Mode.LIVE && !access.usageAccess) Mode.ONBOARDING else current.mode
            current.copy(access = access, mode = mode)
        }
        if (_state.value.mode != Mode.ONBOARDING) load(refresh = true)
    }

    fun onContinueClicked() {
        if (!completeOnboarding()) return
        _state.update { it.copy(mode = Mode.LIVE, report = null) }
        load(refresh = true)
    }

    fun onTryDemoClicked() {
        _state.update { it.copy(mode = Mode.DEMO, report = null) }
        load(refresh = false)
    }

    fun onOpenSetupClicked() {
        loadJob?.cancel()
        _state.update { it.copy(mode = Mode.ONBOARDING, isLoading = false) }
    }

    fun onPreviousWeekClicked() = changeWeek(_state.value.week.previous())

    fun onNextWeekClicked() {
        if (_state.value.canGoForward) changeWeek(_state.value.week.next())
    }

    fun onRefreshClicked() = load(refresh = true)

    private fun changeWeek(week: WeekRange) {
        _state.update { it.copy(week = week) }
        load(refresh = false)
    }

    private fun load(refresh: Boolean) {
        val source = when (_state.value.mode) {
            Mode.LIVE -> ReportSource.DEVICE
            Mode.DEMO -> ReportSource.SAMPLE
            Mode.ONBOARDING -> return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                if (refresh && source == ReportSource.DEVICE) refreshActivityData()
                val report = getWeeklyReport(_state.value.week, source)
                _state.update { it.copy(report = report, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Something went wrong") }
            }
        }
    }

    companion object {
        fun factory(create: () -> MainViewModel) = viewModelFactory {
            initializer { create() }
        }
    }
}
