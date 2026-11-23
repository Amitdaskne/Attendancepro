package com.example.ui.screens.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.OfflinePunchEntity
import com.example.data.remote.LocationProvider
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.OperationsRepository
import com.example.domain.engine.AttendanceCalculator
import com.example.domain.model.*
import com.example.ui.util.DateFormatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val attendanceRepository: AttendanceRepository,
    private val operationsRepository: OperationsRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(DateFormatters.todayIso())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _monitorSummaries = MutableStateFlow<UiState<List<AttendanceSummary>>>(UiState.Loading)
    val monitorSummaries: StateFlow<UiState<List<AttendanceSummary>>> = _monitorSummaries.asStateFlow()

    private val _ownTodaySummary = MutableStateFlow<AttendanceSummary?>(null)
    val ownTodaySummary: StateFlow<AttendanceSummary?> = _ownTodaySummary.asStateFlow()

    private val _ownMonthSummaries = MutableStateFlow<UiState<List<AttendanceSummary>>>(UiState.Loading)
    val ownMonthSummaries: StateFlow<UiState<List<AttendanceSummary>>> = _ownMonthSummaries.asStateFlow()

    private val _devices = MutableStateFlow<UiState<List<DeviceRecord>>>(UiState.Loading)
    val devices: StateFlow<UiState<List<DeviceRecord>>> = _devices.asStateFlow()

    private val _activeChallenge = MutableStateFlow<ScannerChallenge?>(null)
    val activeChallenge: StateFlow<ScannerChallenge?> = _activeChallenge.asStateFlow()

    private val _lastGps = MutableStateFlow<GpsSnapshot?>(null)
    val lastGps: StateFlow<GpsSnapshot?> = _lastGps.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    val offlineQueue: StateFlow<List<OfflinePunchEntity>> = attendanceRepository.observeOfflineQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var jobs = mutableListOf<Job>()

    fun bindMonitor(session: UserSession, stores: List<StoreRecord>, selectedStoreId: String?, date: String = _selectedDate.value) {
        jobs.forEach { it.cancel() }
        jobs.clear()
        _selectedDate.value = date

        val storeIds = when {
            !selectedStoreId.isNullOrBlank() -> listOf(selectedStoreId)
            session.role == UserRole.SUPER_ADMIN -> stores.map { it.storeId }
            else -> session.assignedStoreIds
        }

        _monitorSummaries.value = UiState.Loading
        jobs += viewModelScope.launch {
            attendanceRepository.observeStoreDateSummaries(storeIds, date).collectLatest { res ->
                res.fold(
                    onSuccess = { list ->
                        _monitorSummaries.value = if (list.isEmpty()) {
                            UiState.Empty("No attendance records yet for $date.")
                        } else {
                            UiState.Success(list)
                        }
                    },
                    onFailure = { err ->
                        _monitorSummaries.value = UiState.Error(err.message ?: "Failed to load attendance")
                    },
                )
            }
        }

        jobs += viewModelScope.launch {
            operationsRepository.observeDevices(session, selectedStoreId).collectLatest { res ->
                res.fold(
                    onSuccess = { list ->
                        _devices.value = if (list.isEmpty()) {
                            UiState.Empty("No scanner devices registered for this store.")
                        } else {
                            UiState.Success(list)
                        }
                    },
                    onFailure = { err ->
                        _devices.value = UiState.Error(err.message ?: "Unable to load devices")
                    },
                )
            }
        }
    }

    fun bindEmployee(session: UserSession, yearMonth: String = DateFormatters.currentYearMonth()) {
        jobs.forEach { it.cancel() }
        jobs.clear()
        val storeId = session.primaryStoreId
        val empId = session.employeeId
        val today = DateFormatters.todayIso()

        jobs += viewModelScope.launch {
            attendanceRepository.observeTodaySummary(storeId, empId, today).collectLatest {
                _ownTodaySummary.value = it
            }
        }
        _ownMonthSummaries.value = UiState.Loading
        jobs += viewModelScope.launch {
            attendanceRepository.observeEmployeeMonthSummaries(storeId, empId, yearMonth).collectLatest { res ->
                res.fold(
                    onSuccess = { list ->
                        _ownMonthSummaries.value = if (list.isEmpty()) {
                            UiState.Empty("No attendance history for $yearMonth.")
                        } else {
                            UiState.Success(list)
                        }
                    },
                    onFailure = { err ->
                        _ownMonthSummaries.value = UiState.Error(err.message ?: "Unable to load history")
                    },
                )
            }
        }
    }

    fun requestChallenge(storeId: String, deviceId: String, action: String, format: String) {
        if (storeId.isBlank() || deviceId.isBlank()) {
            _feedback.value = "Select an active store and registered scanner device first."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            attendanceRepository.issueScannerChallenge(storeId, deviceId, action, format).fold(
                onSuccess = { challenge -> _activeChallenge.value = challenge },
                onFailure = { err -> _feedback.value = err.message ?: "Unable to issue scanner challenge." },
            )
            _busy.value = false
        }
    }

    fun captureOneShotGps() {
        viewModelScope.launch {
            _busy.value = true
            locationProvider.captureSingleLocation().fold(
                onSuccess = { snap ->
                    _lastGps.value = snap
                    if (snap.isMocked) {
                        _feedback.value = "Warning: Mock GPS detected. Punch will be flagged for review."
                    } else {
                        _feedback.value = "GPS captured (±${snap.accuracyMeters.toInt()}m accuracy)."
                    }
                },
                onFailure = { err ->
                    _feedback.value = err.message ?: "GPS capture failed."
                },
            )
            _busy.value = false
        }
    }

    fun submitEmployeePunch(
        session: UserSession,
        store: StoreRecord?,
        eventType: PunchEventType,
        rawBarcode: String?,
        requireGpsCapture: Boolean,
    ) {
        val currentState = _ownTodaySummary.value?.parsedWorkState ?: AttendanceWorkState.NOT_CHECKED_IN
        val transition = AttendanceCalculator.validateTransition(currentState, eventType)
        if (!transition.allowed) {
            _feedback.value = transition.rejectionReason ?: "Invalid punch transition."
            return
        }

        val storeId = store?.storeId ?: session.primaryStoreId
        val gpsMode = store?.settings?.parsedGpsMode ?: GpsPolicyMode.GPS_AND_SCANNER

        viewModelScope.launch {
            _busy.value = true

            // Validate scanner payload when required
            val scannedPayload = if (!rawBarcode.isNullOrBlank()) {
                val parsed = attendanceRepository.parseScannerPayload(rawBarcode)
                if (parsed.isFailure) {
                    _feedback.value = parsed.exceptionOrNull()?.message ?: "Invalid QR/Aztec code."
                    _busy.value = false
                    return@launch
                }
                parsed.getOrNull()
            } else {
                if (gpsMode == GpsPolicyMode.GPS_AND_SCANNER || gpsMode == GpsPolicyMode.SCANNER_ONLY) {
                    _feedback.value = "Live store QR/Aztec scan is required by store policy."
                    _busy.value = false
                    return@launch
                }
                null
            }

            // Capture one-shot GPS if store policy uses GPS
            val gpsSnapshot = if (requireGpsCapture && gpsMode != GpsPolicyMode.DISABLED && gpsMode != GpsPolicyMode.SCANNER_ONLY) {
                val locResult = locationProvider.captureSingleLocation()
                if (locResult.isFailure && (gpsMode == GpsPolicyMode.REQUIRED || gpsMode == GpsPolicyMode.GPS_AND_SCANNER)) {
                    _feedback.value = locResult.exceptionOrNull()?.message ?: "GPS fix required for this store."
                    _busy.value = false
                    return@launch
                }
                locResult.getOrNull()?.also { _lastGps.value = it }
            } else {
                _lastGps.value
            }

            // Local pre-check on geofence radius (backend re-validates authoritatively)
            if (gpsSnapshot != null && store != null && store.location.radiusMeters > 0 &&
                (gpsMode == GpsPolicyMode.REQUIRED || gpsMode == GpsPolicyMode.GPS_AND_SCANNER)
            ) {
                val geo = AttendanceCalculator.evaluateGeofence(
                    gpsSnapshot,
                    store.location,
                    store.settings.maxGpsAccuracyMeters,
                )
                if (!geo.withinRadius) {
                    _feedback.value = "Outside store geofence (${geo.distanceMeters.toInt()}m away; limit ${store.location.radiusMeters.toInt()}m)."
                    _busy.value = false
                    return@launch
                }
            }

            val allowOffline = gpsMode != GpsPolicyMode.GPS_AND_SCANNER && gpsMode != GpsPolicyMode.SCANNER_ONLY
            attendanceRepository.submitPunch(
                employeeId = session.employeeId,
                storeId = storeId,
                eventType = eventType,
                scannedPayload = scannedPayload,
                gps = gpsSnapshot,
                allowOfflineQueue = allowOffline,
            ).fold(
                onSuccess = { msg -> _feedback.value = msg },
                onFailure = { err -> _feedback.value = err.message ?: "Punch rejected by server." },
            )
            _busy.value = false
        }
    }

    fun registerScannerDevice(name: String, storeId: String, permittedActions: List<String>) {
        if (name.isBlank() || storeId.isBlank()) {
            _feedback.value = "Device name and store are required."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.registerDevice(name, storeId, permittedActions).fold(
                onSuccess = { id -> _feedback.value = "Scanner device registered ($id)." },
                onFailure = { err -> _feedback.value = err.message ?: "Device registration failed." },
            )
            _busy.value = false
        }
    }

    fun setDeviceStatus(deviceId: String, status: String, reason: String) {
        viewModelScope.launch {
            _busy.value = true
            operationsRepository.setDeviceStatus(deviceId, status, reason).fold(
                onSuccess = { _feedback.value = "Device status changed to $status." },
                onFailure = { err -> _feedback.value = err.message ?: "Failed to update device." },
            )
            _busy.value = false
        }
    }

    fun triggerDailyRecalculation(storeId: String?, date: String) {
        viewModelScope.launch {
            _busy.value = true
            attendanceRepository.recalculateDailyAttendance(storeId, date).fold(
                onSuccess = { msg -> _feedback.value = msg },
                onFailure = { err -> _feedback.value = err.message ?: "Recalculation failed." },
            )
            _busy.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }
}
