package com.example.ui.screens.operations

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.CloudinaryService
import com.example.data.repository.OperationsRepository
import com.example.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OperationsViewModel @Inject constructor(
    private val operationsRepository: OperationsRepository,
    private val cloudinaryService: CloudinaryService,
) : ViewModel() {

    private val _shiftsState = MutableStateFlow<UiState<List<ShiftRecord>>>(UiState.Loading)
    val shiftsState: StateFlow<UiState<List<ShiftRecord>>> = _shiftsState.asStateFlow()

    private val _holidaysState = MutableStateFlow<UiState<List<HolidayRecord>>>(UiState.Loading)
    val holidaysState: StateFlow<UiState<List<HolidayRecord>>> = _holidaysState.asStateFlow()

    private val _leavesState = MutableStateFlow<UiState<List<LeaveRequest>>>(UiState.Loading)
    val leavesState: StateFlow<UiState<List<LeaveRequest>>> = _leavesState.asStateFlow()

    private val _leaveBalance = MutableStateFlow(LeaveBalance())
    val leaveBalance: StateFlow<LeaveBalance> = _leaveBalance.asStateFlow()

    private val _correctionsState = MutableStateFlow<UiState<List<AttendanceCorrection>>>(UiState.Loading)
    val correctionsState: StateFlow<UiState<List<AttendanceCorrection>>> = _correctionsState.asStateFlow()

    private val _announcementsState = MutableStateFlow<UiState<List<AnnouncementRecord>>>(UiState.Loading)
    val announcementsState: StateFlow<UiState<List<AnnouncementRecord>>> = _announcementsState.asStateFlow()

    private val _auditLogsState = MutableStateFlow<UiState<List<AuditLogEntry>>>(UiState.Loading)
    val auditLogsState: StateFlow<UiState<List<AuditLogEntry>>> = _auditLogsState.asStateFlow()

    private val _uploadedAttachmentUrl = MutableStateFlow("")
    val uploadedAttachmentUrl: StateFlow<String> = _uploadedAttachmentUrl.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private var jobs = mutableListOf<Job>()

    fun bind(session: UserSession, selectedStoreId: String?) {
        jobs.forEach { it.cancel() }
        jobs.clear()

        val effectiveStoreId = selectedStoreId ?: session.primaryStoreId

        jobs += viewModelScope.launch {
            operationsRepository.observeShifts(selectedStoreId).collectLatest { res ->
                res.fold(
                    onSuccess = { list ->
                        _shiftsState.value = if (list.isEmpty()) UiState.Empty("No shifts configured yet.")
                        else UiState.Success(list)
                    },
                    onFailure = { err -> _shiftsState.value = UiState.Error(err.message ?: "Failed to load shifts") },
                )
            }
        }

        jobs += viewModelScope.launch {
            operationsRepository.observeHolidays(effectiveStoreId).collectLatest { res ->
                res.fold(
                    onSuccess = { list ->
                        _holidaysState.value = if (list.isEmpty()) UiState.Empty("No store holidays configured.")
                        else UiState.Success(list)
                    },
                    onFailure = { err -> _holidaysState.value = UiState.Error(err.message ?: "Failed to load holidays") },
                )
            }
        }

        jobs += viewModelScope.launch {
            operationsRepository.observeLeaveRequests(session, selectedStoreId).collectLatest { res ->
                res.fold(
                    onSuccess = { list ->
                        _leavesState.value = if (list.isEmpty()) UiState.Empty("No leave requests found.")
                        else UiState.Success(list)
                    },
                    onFailure = { err -> _leavesState.value = UiState.Error(err.message ?: "Failed to load leave requests") },
                )
            }
        }

        if (session.employeeId.isNotBlank()) {
            jobs += viewModelScope.launch {
                operationsRepository.observeLeaveBalance(session.employeeId).collectLatest {
                    _leaveBalance.value = it
                }
            }
        }

        jobs += viewModelScope.launch {
            operationsRepository.observeCorrections(session, selectedStoreId).collectLatest { res ->
                res.fold(
                    onSuccess = { list ->
                        _correctionsState.value = if (list.isEmpty()) UiState.Empty("No attendance correction requests.")
                        else UiState.Success(list)
                    },
                    onFailure = { err -> _correctionsState.value = UiState.Error(err.message ?: "Failed to load corrections") },
                )
            }
        }

        jobs += viewModelScope.launch {
            operationsRepository.observeAnnouncements().collectLatest { res ->
                res.fold(
                    onSuccess = { list ->
                        _announcementsState.value = if (list.isEmpty()) UiState.Empty("No announcements published yet.")
                        else UiState.Success(list)
                    },
                    onFailure = { err -> _announcementsState.value = UiState.Error(err.message ?: "Failed to load announcements") },
                )
            }
        }

        if (session.role == UserRole.SUPER_ADMIN || session.role == UserRole.STORE_ADMIN) {
            jobs += viewModelScope.launch {
                operationsRepository.observeAuditLogs(120).collectLatest { res ->
                    res.fold(
                        onSuccess = { list ->
                            val scoped = if (session.role == UserRole.SUPER_ADMIN) list
                            else list.filter { it.storeId.isBlank() || session.canAccessStore(it.storeId) }
                            _auditLogsState.value = if (scoped.isEmpty()) UiState.Empty("No audit entries recorded yet.")
                            else UiState.Success(scoped)
                        },
                        onFailure = { err -> _auditLogsState.value = UiState.Error(err.message ?: "Failed to load audit logs") },
                    )
                }
            }
        }
    }

    fun uploadPrivateLeaveAttachment(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            cloudinaryService.uploadPrivateDocument(uri, folder = "leave_attachments").fold(
                onSuccess = { url ->
                    _uploadedAttachmentUrl.value = url
                    _feedback.value = "Document securely uploaded via signed Cloudinary channel."
                },
                onFailure = { err -> _feedback.value = err.message ?: "Signed upload failed." },
            )
            _busy.value = false
        }
    }

    fun saveShift(shift: ShiftRecord, isNew: Boolean, onDone: () -> Unit) {
        if (shift.name.isBlank()) {
            _feedback.value = "Shift name is required."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.saveShift(shift, isNew).fold(
                onSuccess = {
                    _feedback.value = "Shift saved."
                    onDone()
                },
                onFailure = { err -> _feedback.value = err.message ?: "Failed to save shift." },
            )
            _busy.value = false
        }
    }

    fun addHoliday(storeId: String, date: String, name: String, isOptional: Boolean, onDone: () -> Unit) {
        if (storeId.isBlank() || date.isBlank() || name.isBlank()) {
            _feedback.value = "Store, date (YYYY-MM-DD), and holiday name are required."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.saveHoliday(storeId, date, name, isOptional).fold(
                onSuccess = {
                    _feedback.value = "Holiday added."
                    onDone()
                },
                onFailure = { err -> _feedback.value = err.message ?: "Failed to add holiday." },
            )
            _busy.value = false
        }
    }

    fun applyLeave(
        session: UserSession,
        storeId: String,
        leaveType: String,
        startDate: String,
        endDate: String,
        totalDays: Double,
        reason: String,
        onDone: () -> Unit,
    ) {
        if (startDate.isBlank() || endDate.isBlank() || reason.isBlank() || totalDays <= 0.0) {
            _feedback.value = "Fill dates, total days, and reason."
            return
        }
        val available = _leaveBalance.value.balances[leaveType] ?: 0.0
        if (leaveType != "UNPAID" && totalDays > available) {
            _feedback.value = "Insufficient $leaveType balance ($available days available)."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.submitLeaveRequest(
                session = session,
                storeId = storeId,
                leaveType = leaveType,
                startDate = startDate,
                endDate = endDate,
                totalDays = totalDays,
                reason = reason,
                attachmentUrl = _uploadedAttachmentUrl.value,
            ).fold(
                onSuccess = {
                    _uploadedAttachmentUrl.value = ""
                    _feedback.value = "Leave request submitted for approval."
                    onDone()
                },
                onFailure = { err -> _feedback.value = err.message ?: "Failed to apply leave." },
            )
            _busy.value = false
        }
    }

    fun decideLeave(requestId: String, approve: Boolean, note: String) {
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.decideLeave(requestId, approve, note).fold(
                onSuccess = { _feedback.value = if (approve) "Leave approved & balance updated." else "Leave rejected." },
                onFailure = { err -> _feedback.value = err.message ?: "Decision failed." },
            )
            _busy.value = false
        }
    }

    fun submitCorrection(
        session: UserSession,
        storeId: String,
        date: String,
        correctionType: String,
        requestedTimeLabel: String,
        requestedTimestampMs: Long,
        reason: String,
        onDone: () -> Unit,
    ) {
        if (date.isBlank() || requestedTimeLabel.isBlank() || reason.isBlank()) {
            _feedback.value = "Date, requested time, and reason are required."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.submitCorrectionRequest(
                session = session,
                storeId = storeId,
                date = date,
                correctionType = correctionType,
                requestedTimeLabel = requestedTimeLabel,
                requestedTimestampMs = requestedTimestampMs,
                reason = reason,
            ).fold(
                onSuccess = {
                    _feedback.value = "Correction request submitted. Original punches remain immutable."
                    onDone()
                },
                onFailure = { err -> _feedback.value = err.message ?: "Failed to submit correction." },
            )
            _busy.value = false
        }
    }

    fun decideCorrection(requestId: String, approve: Boolean, note: String) {
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.decideCorrection(requestId, approve, note).fold(
                onSuccess = {
                    _feedback.value = if (approve) {
                        "Correction approved — ADMIN_CORRECTION event recorded."
                    } else {
                        "Correction rejected."
                    }
                },
                onFailure = { err -> _feedback.value = err.message ?: "Correction decision failed." },
            )
            _busy.value = false
        }
    }

    fun sendAnnouncement(
        title: String,
        body: String,
        targetScope: String,
        targetIds: List<String>,
        onDone: () -> Unit,
    ) {
        if (title.isBlank() || body.isBlank()) {
            _feedback.value = "Announcement title and message are required."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.sendAnnouncement(title, body, targetScope, targetIds).fold(
                onSuccess = {
                    _feedback.value = "Announcement broadcast queued via FCM."
                    onDone()
                },
                onFailure = { err -> _feedback.value = err.message ?: "Failed to publish announcement." },
            )
            _busy.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }
}
