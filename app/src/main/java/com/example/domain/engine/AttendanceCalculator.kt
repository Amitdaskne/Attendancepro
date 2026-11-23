package com.example.domain.engine

import com.example.domain.model.AttendanceEvent
import com.example.domain.model.AttendanceSummary
import com.example.domain.model.AttendanceWorkState
import com.example.domain.model.GpsSnapshot
import com.example.domain.model.PunchEventType
import com.example.domain.model.ShiftRecord
import com.example.domain.model.StoreLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Kotlin attendance engine matching server-side Cloud Function formulas (§6 & §8).
 * Used for deterministic local previews, state validation before submission, and JVM unit tests.
 * Backend Cloud Functions remain the sole authoritative writer to RTDB.
 */
object AttendanceCalculator {

    data class TransitionCheck(
        val allowed: Boolean,
        val nextState: AttendanceWorkState,
        val rejectionReason: String? = null,
    )

    data class GeofenceResult(
        val distanceMeters: Double,
        val withinRadius: Boolean,
        val accuracyAcceptable: Boolean,
        val mockedFlag: Boolean,
    )

    fun validateTransition(
        currentState: AttendanceWorkState,
        requestedEvent: PunchEventType,
    ): TransitionCheck {
        return when (requestedEvent) {
            PunchEventType.CHECK_IN -> when (currentState) {
                AttendanceWorkState.NOT_CHECKED_IN ->
                    TransitionCheck(true, AttendanceWorkState.WORKING)
                AttendanceWorkState.WORKING, AttendanceWorkState.ON_BREAK ->
                    TransitionCheck(false, currentState, "Duplicate check-in rejected: already checked in.")
                AttendanceWorkState.COMPLETED ->
                    TransitionCheck(false, currentState, "Duplicate check-in rejected: shift already completed.")
                AttendanceWorkState.EXCEPTION ->
                    TransitionCheck(false, currentState, "Resolve missing check-out exception before checking in.")
            }

            PunchEventType.BREAK_START -> when (currentState) {
                AttendanceWorkState.WORKING ->
                    TransitionCheck(true, AttendanceWorkState.ON_BREAK)
                AttendanceWorkState.ON_BREAK ->
                    TransitionCheck(false, currentState, "Already on break.")
                else ->
                    TransitionCheck(false, currentState, "Cannot start break unless currently working.")
            }

            PunchEventType.BREAK_END -> when (currentState) {
                AttendanceWorkState.ON_BREAK ->
                    TransitionCheck(true, AttendanceWorkState.WORKING)
                else ->
                    TransitionCheck(false, currentState, "Cannot end break: no active break.")
            }

            PunchEventType.CHECK_OUT -> when (currentState) {
                AttendanceWorkState.WORKING ->
                    TransitionCheck(true, AttendanceWorkState.COMPLETED)
                AttendanceWorkState.ON_BREAK ->
                    TransitionCheck(false, currentState, "End active break before checking out.")
                AttendanceWorkState.NOT_CHECKED_IN ->
                    TransitionCheck(false, currentState, "Cannot check out before checking in.")
                AttendanceWorkState.COMPLETED ->
                    TransitionCheck(false, currentState, "Shift already checked out.")
                AttendanceWorkState.EXCEPTION ->
                    TransitionCheck(false, currentState, "Use attendance correction to resolve past missing check-out.")
            }

            PunchEventType.ADMIN_CORRECTION, PunchEventType.ADMIN_OVERRIDE ->
                TransitionCheck(true, AttendanceWorkState.COMPLETED)
        }
    }

    /**
     * Evaluates sorted immutable attendance events for a single shift date and computes:
     * GrossMin = checkOut - checkIn
     * BreakMin = sum(valid breaks)
     * NetWorkingMin = GrossMin - BreakMin
     * LateMin = max(0, checkIn - shiftStart - grace)
     * EarlyDepartureMin = max(0, shiftEnd - checkOut)
     * OvertimeMin = max(0, NetWorkingMin - shift.overtimeThresholdMinutes)
     */
    fun calculateSummary(
        employeeId: String,
        employeeName: String,
        storeId: String,
        date: String,
        shift: ShiftRecord,
        shiftStartEpochMs: Long,
        shiftEndEpochMs: Long,
        events: List<AttendanceEvent>,
        isHoliday: Boolean = false,
        isWeeklyOff: Boolean = false,
        hasApprovedLeave: Boolean = false,
        dayClosed: Boolean = false,
    ): AttendanceSummary {
        val sorted = events.sortedBy { it.serverTimestamp }
        var checkInMs: Long? = null
        var checkOutMs: Long? = null
        var activeBreakStart: Long? = null
        var totalBreakMs = 0L
        var state = AttendanceWorkState.NOT_CHECKED_IN

        for (event in sorted) {
            when (PunchEventType.fromValue(event.eventType)) {
                PunchEventType.CHECK_IN -> {
                    if (checkInMs == null) {
                        checkInMs = event.serverTimestamp
                        state = AttendanceWorkState.WORKING
                    }
                }
                PunchEventType.BREAK_START -> {
                    if (state == AttendanceWorkState.WORKING && activeBreakStart == null) {
                        activeBreakStart = event.serverTimestamp
                        state = AttendanceWorkState.ON_BREAK
                    }
                }
                PunchEventType.BREAK_END -> {
                    val start = activeBreakStart
                    if (state == AttendanceWorkState.ON_BREAK && start != null && event.serverTimestamp >= start) {
                        totalBreakMs += (event.serverTimestamp - start)
                        activeBreakStart = null
                        state = AttendanceWorkState.WORKING
                    }
                }
                PunchEventType.CHECK_OUT -> {
                    if (checkInMs != null && event.serverTimestamp >= checkInMs) {
                        // If a break was accidentally left open on an admin correction, close it safely
                        activeBreakStart?.let { bStart ->
                            if (event.serverTimestamp >= bStart) {
                                totalBreakMs += (event.serverTimestamp - bStart)
                            }
                            activeBreakStart = null
                        }
                        checkOutMs = event.serverTimestamp
                        state = AttendanceWorkState.COMPLETED
                    }
                }
                PunchEventType.ADMIN_CORRECTION, PunchEventType.ADMIN_OVERRIDE -> {
                    if (checkInMs == null) {
                        checkInMs = event.serverTimestamp
                        state = AttendanceWorkState.WORKING
                    } else if (checkOutMs == null || event.serverTimestamp > checkInMs) {
                        checkOutMs = event.serverTimestamp
                        state = AttendanceWorkState.COMPLETED
                    }
                }
            }
        }

        // Overnight shift end normalization
        val normalizedShiftEndMs = if (shift.overnight && shiftEndEpochMs <= shiftStartEpochMs) {
            shiftEndEpochMs + 24L * 60L * 60L * 1000L
        } else {
            shiftEndEpochMs
        }

        val breakMinutes = (totalBreakMs / 60_000L).toInt()
        val missingCheckout = checkInMs != null && checkOutMs == null && dayClosed

        if (missingCheckout) {
            state = AttendanceWorkState.EXCEPTION
        }

        val grossMinutes = if (checkInMs != null && checkOutMs != null && checkOutMs >= checkInMs) {
            ((checkOutMs - checkInMs) / 60_000L).toInt()
        } else {
            0
        }

        val netWorkingMinutes = max(0, grossMinutes - breakMinutes)

        val lateMinutes = if (checkInMs != null) {
            val graceDeadlineMs = shiftStartEpochMs + shift.graceMinutes * 60_000L
            max(0, ((checkInMs - graceDeadlineMs) / 60_000L).toInt())
        } else {
            0
        }

        val earlyDepartureMinutes = if (checkOutMs != null) {
            max(0, ((normalizedShiftEndMs - checkOutMs) / 60_000L).toInt())
        } else {
            0
        }

        val overtimeMinutes = if (checkOutMs != null && shift.overtimeThresholdMinutes > 0) {
            max(0, netWorkingMinutes - shift.overtimeThresholdMinutes)
        } else {
            0
        }

        val status = when {
            missingCheckout -> "EXCEPTION"
            checkInMs == null && hasApprovedLeave -> "ON_LEAVE"
            checkInMs == null && isHoliday -> "HOLIDAY"
            checkInMs == null && isWeeklyOff -> "WEEKLY_OFF"
            checkInMs == null -> "ABSENT"
            checkOutMs == null -> if (lateMinutes > 0) "LATE_WORKING" else "WORKING"
            netWorkingMinutes >= shift.minWorkingMinutes -> if (lateMinutes > 0) "LATE" else "PRESENT"
            netWorkingMinutes >= shift.halfDayMinutes -> "HALF_DAY"
            else -> "ABSENT"
        }

        return AttendanceSummary(
            employeeId = employeeId,
            employeeName = employeeName,
            storeId = storeId,
            date = date,
            shiftId = shift.shiftId,
            checkIn = checkInMs,
            checkOut = checkOutMs,
            breakMinutes = breakMinutes,
            workingMinutes = netWorkingMinutes,
            lateMinutes = lateMinutes,
            earlyDepartureMinutes = earlyDepartureMinutes,
            overtimeMinutes = overtimeMinutes,
            status = status,
            workState = state.value,
            source = sorted.lastOrNull()?.source ?: "SCANNER",
            calcVersion = 2,
            finalized = dayClosed && !missingCheckout,
            missingCheckoutException = missingCheckout,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Haversine distance in meters between GPS reading and Store coordinates (§8).
     */
    fun evaluateGeofence(
        gps: GpsSnapshot,
        storeLocation: StoreLocation,
        maxAccuracyMeters: Double = 80.0,
    ): GeofenceResult {
        val distance = haversineMeters(
            gps.latitude,
            gps.longitude,
            storeLocation.latitude,
            storeLocation.longitude,
        )
        return GeofenceResult(
            distanceMeters = distance,
            withinRadius = distance <= storeLocation.radiusMeters,
            accuracyAcceptable = gps.accuracyMeters <= maxAccuracyMeters,
            mockedFlag = gps.isMocked,
        )
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    /**
     * Net = Base + Allowances + Overtime + Bonus - Deductions - UnpaidLeaveDeduction (§14).
     */
    fun calculateNetPay(
        baseAmount: Double,
        allowances: Double,
        overtimePay: Double,
        bonus: Double,
        deductions: Double,
        unpaidLeaveDeduction: Double,
    ): Double {
        return max(
            0.0,
            baseAmount + allowances + overtimePay + bonus - deductions - unpaidLeaveDeduction,
        )
    }
}
