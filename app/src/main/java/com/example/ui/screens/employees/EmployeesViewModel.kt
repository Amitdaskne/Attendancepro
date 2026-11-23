package com.example.ui.screens.employees

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.CloudinaryService
import com.example.data.repository.EmployeeRepository
import com.example.data.repository.OperationsRepository
import com.example.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmployeesViewModel @Inject constructor(
    private val employeeRepository: EmployeeRepository,
    private val operationsRepository: OperationsRepository,
    private val cloudinaryService: CloudinaryService,
) : ViewModel() {

    private val _employeesState = MutableStateFlow<UiState<List<EmployeeRecord>>>(UiState.Loading)
    val employeesState: StateFlow<UiState<List<EmployeeRecord>>> = _employeesState.asStateFlow()

    private val _usersState = MutableStateFlow<UiState<List<UserRecord>>>(UiState.Loading)
    val usersState: StateFlow<UiState<List<UserRecord>>> = _usersState.asStateFlow()

    private val _shifts = MutableStateFlow<List<ShiftRecord>>(emptyList())
    val shifts: StateFlow<List<ShiftRecord>> = _shifts.asStateFlow()

    private val _departments = MutableStateFlow<List<Department>>(emptyList())
    val departments: StateFlow<List<Department>> = _departments.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _uploadedPhotoUrl = MutableStateFlow("")
    val uploadedPhotoUrl: StateFlow<String> = _uploadedPhotoUrl.asStateFlow()

    private var jobs = mutableListOf<Job>()

    fun bind(session: UserSession, selectedStoreId: String?) {
        jobs.forEach { it.cancel() }
        jobs.clear()

        _employeesState.value = UiState.Loading
        jobs += viewModelScope.launch {
            employeeRepository.observeEmployees(session, selectedStoreId).collectLatest { res ->
                res.fold(
                    onSuccess = { list ->
                        _employeesState.value = if (list.isEmpty()) {
                            UiState.Empty("No employees found for the selected store scope.")
                        } else {
                            UiState.Success(list)
                        }
                    },
                    onFailure = { err ->
                        _employeesState.value = UiState.Error(err.message ?: "Failed to load employees")
                    },
                )
            }
        }

        if (session.role == UserRole.SUPER_ADMIN || session.role == UserRole.STORE_ADMIN) {
            jobs += viewModelScope.launch {
                employeeRepository.observeUsers(session).collectLatest { res ->
                    res.fold(
                        onSuccess = { list ->
                            _usersState.value = if (list.isEmpty()) {
                                UiState.Empty("No user accounts found.")
                            } else {
                                UiState.Success(list)
                            }
                        },
                        onFailure = { err ->
                            _usersState.value = UiState.Error(err.message ?: "Failed to load users")
                        },
                    )
                }
            }
        }

        jobs += viewModelScope.launch {
            operationsRepository.observeShifts(selectedStoreId).collectLatest { res ->
                res.onSuccess { _shifts.value = it }
            }
        }

        jobs += viewModelScope.launch {
            employeeRepository.observeDepartments().collectLatest { _departments.value = it }
        }
    }

    fun uploadProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            cloudinaryService.uploadPublicImage(uri, folder = "profiles").fold(
                onSuccess = { url ->
                    _uploadedPhotoUrl.value = url
                    _feedback.value = "Profile photo uploaded."
                },
                onFailure = { err ->
                    _feedback.value = err.message ?: "Photo upload failed."
                },
            )
            _busy.value = false
        }
    }

    fun provisionUser(
        fullName: String,
        email: String,
        tempPassword: String,
        phone: String,
        role: UserRole,
        storeIds: List<String>,
        departmentName: String,
        designation: String,
        shiftId: String,
        managerId: String,
        salaryType: SalaryType,
        baseSalary: Double,
        allowances: Double,
        overtimeHourlyRate: Double,
        onDone: () -> Unit,
    ) {
        if (fullName.isBlank() || email.isBlank() || tempPassword.length < 8) {
            _feedback.value = "Full name, valid email, and password (min 8 chars) are required."
            return
        }
        if (role != UserRole.SUPER_ADMIN && storeIds.isEmpty()) {
            _feedback.value = "Assign at least one store."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            employeeRepository.provisionUser(
                fullName = fullName,
                email = email,
                tempPassword = tempPassword,
                phone = phone,
                role = role,
                storeIds = storeIds,
                departmentName = departmentName,
                designation = designation,
                shiftId = shiftId,
                managerId = managerId,
                salaryType = salaryType,
                baseSalary = baseSalary,
                allowances = allowances,
                overtimeHourlyRate = overtimeHourlyRate,
                photoUrl = _uploadedPhotoUrl.value,
            ).fold(
                onSuccess = {
                    _uploadedPhotoUrl.value = ""
                    _feedback.value = "${role.displayLabel} provisioned with verified custom claims."
                    onDone()
                },
                onFailure = { err ->
                    _feedback.value = err.message ?: "User provisioning failed."
                },
            )
            _busy.value = false
        }
    }

    fun transferEmployee(
        employee: EmployeeRecord,
        toStoreId: String,
        newShiftId: String,
        newManagerId: String,
        transferDate: String,
        reason: String,
        onDone: () -> Unit,
    ) {
        if (toStoreId.isBlank() || toStoreId == employee.currentAssignment.storeId) {
            _feedback.value = "Select a different destination store."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            employeeRepository.transferEmployee(
                employeeId = employee.employeeId,
                fromStoreId = employee.currentAssignment.storeId,
                toStoreId = toStoreId,
                newShiftId = newShiftId,
                newManagerId = newManagerId,
                transferDate = transferDate,
                reason = reason,
            ).fold(
                onSuccess = {
                    _feedback.value = "${employee.profile.fullName} transferred. Prior store history preserved."
                    onDone()
                },
                onFailure = { err ->
                    _feedback.value = err.message ?: "Transfer failed."
                },
            )
            _busy.value = false
        }
    }

    fun toggleUserDisabled(user: UserRecord, reason: String) {
        viewModelScope.launch {
            _busy.value = true
            val willDisable = user.isActive
            employeeRepository.updateUserClaimsAndStatus(
                targetUid = user.uid,
                role = user.parsedRole,
                storeIds = user.storeIds,
                disabled = willDisable,
                reason = reason.ifBlank { if (willDisable) "Disabled by admin" else "Re-enabled by admin" },
            ).fold(
                onSuccess = {
                    _feedback.value = if (willDisable) {
                        "${user.profile.fullName} disabled."
                    } else {
                        "${user.profile.fullName} reactivated."
                    }
                },
                onFailure = { err ->
                    _feedback.value = err.message ?: "Failed to update account status."
                },
            )
            _busy.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }
}
