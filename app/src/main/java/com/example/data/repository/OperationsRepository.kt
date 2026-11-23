package com.example.data.repository

import com.example.data.remote.CloudFunctionsDataSource
import com.example.data.remote.RtdbPaths
import com.example.domain.model.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationsRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val cloudFunctions: CloudFunctionsDataSource,
) {
    // -------------------------------------------------------------------------
    // Shifts & Holidays (§13)
    // -------------------------------------------------------------------------
    fun observeShifts(storeIdFilter: String?): Flow<Result<List<ShiftRecord>>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.SHIFTS)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.map { RtdbMappers.toShift(it) }
                    .filter { storeIdFilter.isNullOrBlank() || it.storeId.isBlank() || it.storeId == storeIdFilter }
                    .sortedBy { it.name.lowercase() }
                trySend(Result.success(list))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun saveShift(shift: ShiftRecord, isNew: Boolean): Result<String> = runCatching {
        val ref = database.getReference(RtdbPaths.SHIFTS)
        val id = if (isNew || shift.shiftId.isBlank()) {
            ref.push().key ?: error("Unable to allocate shift ID")
        } else {
            shift.shiftId
        }
        ref.child(id).updateChildren(
            mapOf(
                "shiftId" to id,
                "storeId" to shift.storeId,
                "name" to shift.name.trim(),
                "shiftType" to shift.shiftType,
                "startTime" to shift.startTime.trim(),
                "endTime" to shift.endTime.trim(),
                "graceMinutes" to shift.graceMinutes,
                "maxBreakMinutes" to shift.maxBreakMinutes,
                "paidBreakMinutes" to shift.paidBreakMinutes,
                "minWorkingMinutes" to shift.minWorkingMinutes,
                "halfDayMinutes" to shift.halfDayMinutes,
                "overtimeThresholdMinutes" to shift.overtimeThresholdMinutes,
                "weeklyOff" to shift.weeklyOff,
                "overnight" to shift.overnight,
                "status" to shift.status,
                "updatedAt" to ServerValue.TIMESTAMP,
            ),
        ).await()
        id
    }

    fun observeHolidays(storeId: String): Flow<Result<List<HolidayRecord>>> = callbackFlow {
        if (storeId.isBlank()) {
            trySend(Result.success(emptyList()))
            awaitClose { }
            return@callbackFlow
        }
        val ref = database.getReference(RtdbPaths.storeHolidays(storeId))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.map { RtdbMappers.toHoliday(it, storeId) }
                    .sortedBy { it.date }
                trySend(Result.success(list))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun saveHoliday(storeId: String, date: String, name: String, isOptional: Boolean): Result<Unit> =
        runCatching {
            val ref = database.getReference(RtdbPaths.storeHolidays(storeId))
            val id = ref.push().key ?: error("Unable to allocate holiday ID")
            ref.child(id).setValue(
                mapOf(
                    "holidayId" to id,
                    "storeId" to storeId,
                    "date" to date.trim(),
                    "name" to name.trim(),
                    "isOptional" to isOptional,
                    "createdAt" to ServerValue.TIMESTAMP,
                ),
            ).await()
        }

    // -------------------------------------------------------------------------
    // Leave Requests & Balances (§13)
    // -------------------------------------------------------------------------
    fun observeLeaveRequests(
        session: UserSession,
        selectedStoreId: String?,
    ): Flow<Result<List<LeaveRequest>>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.LEAVE_REQUESTS)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.map { RtdbMappers.toLeaveRequest(it) }
                    .filter { req ->
                        val allowed = when (session.role) {
                            UserRole.SUPER_ADMIN -> true
                            UserRole.STORE_ADMIN, UserRole.MANAGER -> session.canAccessStore(req.storeId)
                            UserRole.EMPLOYEE -> req.employeeId == session.employeeId
                        }
                        val matchesStore = selectedStoreId.isNullOrBlank() || req.storeId == selectedStoreId
                        allowed && matchesStore
                    }
                    .sortedByDescending { it.createdAt }
                trySend(Result.success(list))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeLeaveBalance(employeeId: String): Flow<LeaveBalance> = callbackFlow {
        if (employeeId.isBlank()) {
            trySend(LeaveBalance())
            awaitClose { }
            return@callbackFlow
        }
        val ref = database.getReference(RtdbPaths.leaveBalance(employeeId))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(RtdbMappers.toLeaveBalance(snapshot, employeeId))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(LeaveBalance(employeeId = employeeId))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun submitLeaveRequest(
        session: UserSession,
        storeId: String,
        leaveType: String,
        startDate: String,
        endDate: String,
        totalDays: Double,
        reason: String,
        attachmentUrl: String,
    ): Result<String> = runCatching {
        val ref = database.getReference(RtdbPaths.LEAVE_REQUESTS)
        val id = ref.push().key ?: error("Unable to allocate leave request ID")
        ref.child(id).setValue(
            mapOf(
                "requestId" to id,
                "employeeId" to session.employeeId,
                "employeeName" to session.displayName,
                "storeId" to storeId,
                "leaveType" to leaveType,
                "startDate" to startDate.trim(),
                "endDate" to endDate.trim(),
                "totalDays" to totalDays,
                "reason" to reason.trim(),
                "attachmentUrl" to attachmentUrl,
                "status" to ApprovalStatus.PENDING.value,
                "createdAt" to ServerValue.TIMESTAMP,
            ),
        ).await()
        id
    }

    suspend fun decideLeave(requestId: String, approve: Boolean, note: String): Result<Unit> = runCatching {
        if (approve) {
            cloudFunctions.approveLeave(requestId, note.trim())
        } else {
            cloudFunctions.rejectLeave(requestId, note.trim())
        }
    }

    // -------------------------------------------------------------------------
    // Attendance Corrections (§13)
    // -------------------------------------------------------------------------
    fun observeCorrections(
        session: UserSession,
        selectedStoreId: String?,
    ): Flow<Result<List<AttendanceCorrection>>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.ATTENDANCE_CORRECTIONS)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.map { RtdbMappers.toCorrection(it) }
                    .filter { req ->
                        val allowed = when (session.role) {
                            UserRole.SUPER_ADMIN -> true
                            UserRole.STORE_ADMIN, UserRole.MANAGER -> session.canAccessStore(req.storeId)
                            UserRole.EMPLOYEE -> req.employeeId == session.employeeId
                        }
                        val matchesStore = selectedStoreId.isNullOrBlank() || req.storeId == selectedStoreId
                        allowed && matchesStore
                    }
                    .sortedByDescending { it.createdAt }
                trySend(Result.success(list))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun submitCorrectionRequest(
        session: UserSession,
        storeId: String,
        date: String,
        correctionType: String,
        requestedTimeLabel: String,
        requestedTimestampMs: Long,
        reason: String,
    ): Result<String> = runCatching {
        val ref = database.getReference(RtdbPaths.ATTENDANCE_CORRECTIONS)
        val id = ref.push().key ?: error("Unable to allocate correction ID")
        ref.child(id).setValue(
            mapOf(
                "requestId" to id,
                "employeeId" to session.employeeId,
                "employeeName" to session.displayName,
                "storeId" to storeId,
                "date" to date.trim(),
                "correctionType" to correctionType,
                "requestedTimeLabel" to requestedTimeLabel.trim(),
                "requestedTimestampMs" to requestedTimestampMs,
                "reason" to reason.trim(),
                "status" to ApprovalStatus.PENDING.value,
                "createdAt" to ServerValue.TIMESTAMP,
            ),
        ).await()
        id
    }

    suspend fun decideCorrection(requestId: String, approve: Boolean, note: String): Result<Unit> =
        runCatching {
            if (approve) {
                cloudFunctions.approveCorrection(requestId, note.trim())
            } else {
                cloudFunctions.rejectCorrection(requestId, note.trim())
            }
        }

    // -------------------------------------------------------------------------
    // Scanner Devices (§16)
    // -------------------------------------------------------------------------
    fun observeDevices(
        session: UserSession,
        selectedStoreId: String?,
    ): Flow<Result<List<DeviceRecord>>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.DEVICES)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.map { RtdbMappers.toDevice(it) }
                    .filter { dev ->
                        val allowed = session.canAccessStore(dev.storeId)
                        val matchesStore = selectedStoreId.isNullOrBlank() || dev.storeId == selectedStoreId
                        allowed && matchesStore
                    }
                    .sortedByDescending { it.createdAt }
                trySend(Result.success(list))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun registerDevice(
        name: String,
        storeId: String,
        permittedActions: List<String>,
    ): Result<String> = runCatching {
        val resp = cloudFunctions.registerDevice(
            mapOf(
                "name" to name.trim(),
                "storeId" to storeId,
                "permittedActions" to permittedActions,
            ),
        )
        resp["deviceId"]?.toString() ?: "Registered"
    }

    suspend fun setDeviceStatus(deviceId: String, status: String, reason: String): Result<Unit> =
        runCatching {
            cloudFunctions.revokeDevice(deviceId, status, reason)
        }

    // -------------------------------------------------------------------------
    // Payroll, Salary Slips & Reports (§14 & §15)
    // -------------------------------------------------------------------------
    fun observePayrollPeriods(
        session: UserSession,
        selectedStoreId: String?,
    ): Flow<Result<List<PayrollPeriod>>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.PAYROLL)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.map { RtdbMappers.toPayrollPeriod(it) }
                    .filter { p ->
                        val allowed = session.canAccessStore(p.storeId)
                        val matchesStore = selectedStoreId.isNullOrBlank() || p.storeId == selectedStoreId
                        allowed && matchesStore
                    }
                    .sortedByDescending { it.month }
                trySend(Result.success(list))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeSalarySlips(employeeId: String): Flow<Result<List<SalarySlip>>> = callbackFlow {
        if (employeeId.isBlank()) {
            trySend(Result.success(emptyList()))
            awaitClose { }
            return@callbackFlow
        }
        val ref = database.getReference(RtdbPaths.employeeSlips(employeeId))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.map { RtdbMappers.toSalarySlip(it, employeeId) }
                    .sortedByDescending { it.month }
                trySend(Result.success(list))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun calculatePayroll(storeId: String, month: String): Result<String> = runCatching {
        val resp = cloudFunctions.calculatePayroll(storeId, month)
        resp["periodId"]?.toString() ?: "Payroll calculated"
    }

    suspend fun approvePayroll(periodId: String, note: String): Result<Unit> = runCatching {
        cloudFunctions.approvePayroll(periodId, note)
    }

    suspend fun lockPayroll(periodId: String): Result<Unit> = runCatching {
        cloudFunctions.lockPayroll(periodId)
    }

    suspend fun unlockPayroll(periodId: String, reason: String): Result<Unit> = runCatching {
        cloudFunctions.unlockPayroll(periodId, reason)
    }

    suspend fun exportReport(
        reportType: ReportType,
        format: ExportFormat,
        storeId: String?,
        startDate: String,
        endDate: String,
        departmentId: String?,
        employeeId: String?,
        shiftId: String?,
        statusFilter: String?,
    ): Result<ReportExportResult> = runCatching {
        val resp = cloudFunctions.exportReport(
            mapOf(
                "reportType" to reportType.value,
                "format" to format.value,
                "storeId" to storeId,
                "startDate" to startDate,
                "endDate" to endDate,
                "departmentId" to departmentId,
                "employeeId" to employeeId,
                "shiftId" to shiftId,
                "status" to statusFilter,
            ),
        )
        ReportExportResult(
            reportId = resp["reportId"]?.toString().orEmpty(),
            downloadUrl = resp["downloadUrl"]?.toString().orEmpty(),
            format = resp["format"]?.toString() ?: format.value,
            rowCount = (resp["rowCount"] as? Number)?.toInt() ?: 0,
            generatedAt = (resp["generatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        )
    }

    // -------------------------------------------------------------------------
    // Announcements & Audit Logs (§16)
    // -------------------------------------------------------------------------
    fun observeAnnouncements(): Flow<Result<List<AnnouncementRecord>>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.ANNOUNCEMENTS)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.map { RtdbMappers.toAnnouncement(it) }
                    .sortedByDescending { it.createdAt }
                trySend(Result.success(list))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendAnnouncement(
        title: String,
        body: String,
        targetScope: String,
        targetIds: List<String>,
    ): Result<Unit> = runCatching {
        cloudFunctions.sendAnnouncement(
            mapOf(
                "title" to title.trim(),
                "body" to body.trim(),
                "targetScope" to targetScope,
                "targetIds" to targetIds,
            ),
        )
    }

    fun observeAuditLogs(limit: Int = 100): Flow<Result<List<AuditLogEntry>>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.AUDIT_LOGS).limitToLast(limit)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.map { RtdbMappers.toAuditLog(it) }
                    .sortedByDescending { it.serverTimestamp }
                trySend(Result.success(list))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
