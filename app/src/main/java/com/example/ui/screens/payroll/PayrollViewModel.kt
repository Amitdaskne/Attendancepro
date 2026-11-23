package com.example.ui.screens.payroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.EmployeeRepository
import com.example.data.repository.OperationsRepository
import com.example.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PayrollViewModel @Inject constructor(
    private val operationsRepository: OperationsRepository,
    private val employeeRepository: EmployeeRepository,
) : ViewModel() {

    private val _periodsState = MutableStateFlow<UiState<List<PayrollPeriod>>>(UiState.Loading)
    val periodsState: StateFlow<UiState<List<PayrollPeriod>>> = _periodsState.asStateFlow()

    private val _ownSalaryPrivate = MutableStateFlow<SalaryPrivate?>(null)
    val ownSalaryPrivate: StateFlow<SalaryPrivate?> = _ownSalaryPrivate.asStateFlow()

    private val _ownSlipsState = MutableStateFlow<UiState<List<SalarySlip>>>(UiState.Loading)
    val ownSlipsState: StateFlow<UiState<List<SalarySlip>>> = _ownSlipsState.asStateFlow()

    private val _lastExport = MutableStateFlow<ReportExportResult?>(null)
    val lastExport: StateFlow<ReportExportResult?> = _lastExport.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private var jobs = mutableListOf<Job>()

    fun bindAdminPayroll(session: UserSession, selectedStoreId: String?) {
        jobs.forEach { it.cancel() }
        jobs.clear()
        _periodsState.value = UiState.Loading
        jobs += viewModelScope.launch {
            operationsRepository.observePayrollPeriods(session, selectedStoreId).collectLatest { res ->
                res.fold(
                    onSuccess = { list ->
                        _periodsState.value = if (list.isEmpty()) {
                            UiState.Empty("No payroll periods calculated yet. Select a store and month to calculate.")
                        } else {
                            UiState.Success(list)
                        }
                    },
                    onFailure = { err ->
                        _periodsState.value = UiState.Error(err.message ?: "Failed to load payroll")
                    },
                )
            }
        }
    }

    fun bindEmployeeSalary(session: UserSession) {
        jobs.forEach { it.cancel() }
        jobs.clear()
        _ownSlipsState.value = UiState.Loading
        jobs += viewModelScope.launch {
            employeeRepository.observeEmployee(session.employeeId).collectLatest { res ->
                res.onSuccess { emp -> _ownSalaryPrivate.value = emp?.salaryPrivate }
            }
        }
        jobs += viewModelScope.launch {
            operationsRepository.observeSalarySlips(session.employeeId).collectLatest { res ->
                res.fold(
                    onSuccess = { list ->
                        _ownSlipsState.value = if (list.isEmpty()) {
                            UiState.Empty("No salary slips generated yet.")
                        } else {
                            UiState.Success(list)
                        }
                    },
                    onFailure = { err ->
                        _ownSlipsState.value = UiState.Error(err.message ?: "Unable to load salary slips")
                    },
                )
            }
        }
    }

    fun calculatePayroll(storeId: String, month: String) {
        if (storeId.isBlank() || month.isBlank()) {
            _feedback.value = "Select a specific store and YYYY-MM payroll period."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.calculatePayroll(storeId, month).fold(
                onSuccess = { id -> _feedback.value = "Payroll calculated for $month ($id). Review exceptions before approval." },
                onFailure = { err -> _feedback.value = err.message ?: "Payroll calculation failed." },
            )
            _busy.value = false
        }
    }

    fun approvePayroll(periodId: String, note: String) {
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.approvePayroll(periodId, note).fold(
                onSuccess = { _feedback.value = "Payroll approved." },
                onFailure = { err -> _feedback.value = err.message ?: "Approval failed." },
            )
            _busy.value = false
        }
    }

    fun lockPayroll(periodId: String) {
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.lockPayroll(periodId).fold(
                onSuccess = { _feedback.value = "Payroll locked and PDF salary slips queued to Cloudinary." },
                onFailure = { err -> _feedback.value = err.message ?: "Lock failed." },
            )
            _busy.value = false
        }
    }

    fun unlockPayroll(periodId: String, reason: String) {
        if (reason.isBlank()) {
            _feedback.value = "Audit reason is required to unlock locked payroll."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.unlockPayroll(periodId, reason).fold(
                onSuccess = { _feedback.value = "Payroll unlocked and logged in Audit Trail." },
                onFailure = { err -> _feedback.value = err.message ?: "Unlock failed." },
            )
            _busy.value = false
        }
    }

    fun exportReport(
        reportType: ReportType,
        format: ExportFormat,
        storeId: String?,
        startDate: String,
        endDate: String,
        departmentId: String?,
        employeeId: String?,
        shiftId: String?,
        statusFilter: String?,
    ) {
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.exportReport(
                reportType = reportType,
                format = format,
                storeId = storeId,
                startDate = startDate,
                endDate = endDate,
                departmentId = departmentId,
                employeeId = employeeId,
                shiftId = shiftId,
                statusFilter = statusFilter,
            ).fold(
                onSuccess = { result ->
                    _lastExport.value = result
                    _feedback.value = "${reportType.label} (${result.format}, ${result.rowCount} rows) generated server-side."
                },
                onFailure = { err ->
                    _feedback.value = err.message ?: "Report export failed."
                },
            )
            _busy.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }
}
