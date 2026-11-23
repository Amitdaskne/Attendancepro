package com.example.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.*
import com.example.domain.model.*
import com.example.ui.util.DateFormatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiModel(
    val date: String = DateFormatters.todayIso(),
    val month: String = DateFormatters.currentYearMonth(),
    val employees: List<EmployeeRecord> = emptyList(),
    val todaySummaries: List<AttendanceSummary> = emptyList(),
    val pendingLeaves: List<LeaveRequest> = emptyList(),
    val pendingCorrections: List<AttendanceCorrection> = emptyList(),
    val announcements: List<AnnouncementRecord> = emptyList(),
    val shifts: List<ShiftRecord> = emptyList(),
    // Employee-specific fields
    val ownEmployee: EmployeeRecord? = null,
    val ownTodaySummary: AttendanceSummary? = null,
    val ownMonthSummaries: List<AttendanceSummary> = emptyList(),
    val ownLeaveBalance: LeaveBalance = LeaveBalance(),
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val presentCount: Int
        get() = todaySummaries.count { it.checkIn != null && !it.missingCheckoutException }
    val lateCount: Int
        get() = todaySummaries.count { it.lateMinutes > 0 }
    val onBreakCount: Int
        get() = todaySummaries.count { it.parsedWorkState == AttendanceWorkState.ON_BREAK }
    val exceptionCount: Int
        get() = todaySummaries.count { it.missingCheckoutException || it.status == "EXCEPTION" }
    val absentCount: Int
        get() = (employees.size - presentCount).coerceAtLeast(0)
    val totalOvertimeMinutes: Int
        get() = todaySummaries.sumOf { it.overtimeMinutes }

    val ownMonthlyPresentDays: Int
        get() = ownMonthSummaries.count { it.status in listOf("PRESENT", "LATE", "WORKING", "LATE_WORKING") }
    val ownMonthlyWorkingMinutes: Int
        get() = ownMonthSummaries.sumOf { it.workingMinutes }
    val ownMonthlyOvertimeMinutes: Int
        get() = ownMonthSummaries.sumOf { it.overtimeMinutes }
    val ownMonthlyLateCount: Int
        get() = ownMonthSummaries.count { it.lateMinutes > 0 }
    val ownAttendancePercentage: Int
        get() {
            if (ownMonthSummaries.isEmpty()) return 0
            return ((ownMonthlyPresentDays * 100.0) / ownMonthSummaries.size).toInt().coerceIn(0, 100)
        }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val employeeRepository: EmployeeRepository,
    private val attendanceRepository: AttendanceRepository,
    private val operationsRepository: OperationsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiModel())
    val uiState: StateFlow<DashboardUiModel> = _uiState.asStateFlow()

    private var activeJobs: MutableList<Job> = mutableListOf()

    fun bind(session: UserSession, stores: List<StoreRecord>, selectedStoreId: String?) {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()

        val today = DateFormatters.todayIso()
        val yearMonth = DateFormatters.currentYearMonth()
        val scopedStoreIds = when {
            !selectedStoreId.isNullOrBlank() -> listOf(selectedStoreId)
            session.role == UserRole.SUPER_ADMIN -> stores.map { it.storeId }
            else -> session.assignedStoreIds
        }

        _uiState.update { it.copy(date = today, month = yearMonth, isLoading = true, error = null) }

        activeJobs += viewModelScope.launch {
            operationsRepository.observeAnnouncements().collectLatest { res ->
                res.onSuccess { list -> _uiState.update { it.copy(announcements = list.take(5)) } }
            }
        }

        activeJobs += viewModelScope.launch {
            operationsRepository.observeShifts(selectedStoreId).collectLatest { res ->
                res.onSuccess { list -> _uiState.update { it.copy(shifts = list) } }
            }
        }

        if (session.role == UserRole.EMPLOYEE) {
            val empId = session.employeeId
            val storeId = selectedStoreId ?: session.primaryStoreId

            activeJobs += viewModelScope.launch {
                employeeRepository.observeEmployee(empId).collectLatest { res ->
                    res.fold(
                        onSuccess = { emp ->
                            _uiState.update { it.copy(ownEmployee = emp, isLoading = false) }
                        },
                        onFailure = { err ->
                            _uiState.update { it.copy(error = err.message, isLoading = false) }
                        },
                    )
                }
            }
            activeJobs += viewModelScope.launch {
                attendanceRepository.observeTodaySummary(storeId, empId, today).collectLatest { sum ->
                    _uiState.update { it.copy(ownTodaySummary = sum, isLoading = false) }
                }
            }
            activeJobs += viewModelScope.launch {
                attendanceRepository.observeEmployeeMonthSummaries(storeId, empId, yearMonth)
                    .collectLatest { res ->
                        res.onSuccess { list ->
                            _uiState.update { it.copy(ownMonthSummaries = list, isLoading = false) }
                        }
                    }
            }
            activeJobs += viewModelScope.launch {
                operationsRepository.observeLeaveBalance(empId).collectLatest { bal ->
                    _uiState.update { it.copy(ownLeaveBalance = bal) }
                }
            }
        } else {
            activeJobs += viewModelScope.launch {
                employeeRepository.observeEmployees(session, selectedStoreId).collectLatest { res ->
                    res.fold(
                        onSuccess = { list ->
                            _uiState.update { it.copy(employees = list, isLoading = false) }
                        },
                        onFailure = { err ->
                            _uiState.update { it.copy(error = err.message, isLoading = false) }
                        },
                    )
                }
            }
            activeJobs += viewModelScope.launch {
                attendanceRepository.observeStoreDateSummaries(scopedStoreIds, today)
                    .collectLatest { res ->
                        res.fold(
                            onSuccess = { list ->
                                _uiState.update { it.copy(todaySummaries = list, isLoading = false) }
                            },
                            onFailure = { err ->
                                _uiState.update { it.copy(error = err.message, isLoading = false) }
                            },
                        )
                    }
            }
            activeJobs += viewModelScope.launch {
                operationsRepository.observeLeaveRequests(session, selectedStoreId)
                    .collectLatest { res ->
                        res.onSuccess { list ->
                            _uiState.update {
                                it.copy(
                                    pendingLeaves = list.filter { l -> l.parsedStatus == ApprovalStatus.PENDING },
                                )
                            }
                        }
                    }
            }
            activeJobs += viewModelScope.launch {
                operationsRepository.observeCorrections(session, selectedStoreId)
                    .collectLatest { res ->
                        res.onSuccess { list ->
                            _uiState.update {
                                it.copy(
                                    pendingCorrections = list.filter { c -> c.parsedStatus == ApprovalStatus.PENDING },
                                )
                            }
                        }
                    }
            }
        }
    }
}
