package com.example

import com.example.domain.engine.AttendanceCalculator
import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class AttendanceCalculatorTest {

    private val standardShift = ShiftRecord(
        shiftId = "shift_day",
        name = "Day Shift",
        startTime = "09:00",
        endTime = "18:00",
        graceMinutes = 10,
        maxBreakMinutes = 60,
        minWorkingMinutes = 480,
        halfDayMinutes = 240,
        overtimeThresholdMinutes = 510,
        overnight = false,
    )

    private val overnightShift = ShiftRecord(
        shiftId = "shift_night",
        name = "Night Shift",
        startTime = "22:00",
        endTime = "06:00",
        graceMinutes = 10,
        maxBreakMinutes = 60,
        minWorkingMinutes = 420,
        halfDayMinutes = 210,
        overtimeThresholdMinutes = 450,
        overnight = true,
    )

    @Test
    fun stateMachine_rejectsDuplicateCheckInAndCheckoutBeforeCheckIn() {
        val dup = AttendanceCalculator.validateTransition(AttendanceWorkState.WORKING, PunchEventType.CHECK_IN)
        assertFalse(dup.allowed)

        val earlyOut = AttendanceCalculator.validateTransition(AttendanceWorkState.NOT_CHECKED_IN, PunchEventType.CHECK_OUT)
        assertFalse(earlyOut.allowed)

        val breakCheckout = AttendanceCalculator.validateTransition(AttendanceWorkState.ON_BREAK, PunchEventType.CHECK_OUT)
        assertFalse(breakCheckout.allowed)

        val validStartBreak = AttendanceCalculator.validateTransition(AttendanceWorkState.WORKING, PunchEventType.BREAK_START)
        assertTrue(validStartBreak.allowed)
        assertEquals(AttendanceWorkState.ON_BREAK, validStartBreak.nextState)
    }

    @Test
    fun calculateSummary_computesGrossBreakNetLateOvertimeCorrectly() {
        val shiftStart = 1_700_000_000_000L
        val shiftEnd = shiftStart + 9 * 3_600_000L // 9 hours
        // Check in 25 min after shift start (grace 10m -> late 15m)
        val checkIn = shiftStart + 25 * 60_000L
        // Two breaks: 20 min + 15 min = 35 min
        val b1Start = checkIn + 2 * 3_600_000L
        val b1End = b1Start + 20 * 60_000L
        val b2Start = b1End + 2 * 3_600_000L
        val b2End = b2Start + 15 * 60_000L
        // Check out 10 hours after checkIn -> gross 600m, break 35m, net 565m, OT (threshold 510m) = 55m
        val checkOut = checkIn + 600 * 60_000L

        val events = listOf(
            AttendanceEvent(eventId = "1", eventType = "CHECK_IN", serverTimestamp = checkIn),
            AttendanceEvent(eventId = "2", eventType = "BREAK_START", serverTimestamp = b1Start),
            AttendanceEvent(eventId = "3", eventType = "BREAK_END", serverTimestamp = b1End),
            AttendanceEvent(eventId = "4", eventType = "BREAK_START", serverTimestamp = b2Start),
            AttendanceEvent(eventId = "5", eventType = "BREAK_END", serverTimestamp = b2End),
            AttendanceEvent(eventId = "6", eventType = "CHECK_OUT", serverTimestamp = checkOut),
        )

        val summary = AttendanceCalculator.calculateSummary(
            employeeId = "EMP-1",
            employeeName = "Asha Rao",
            storeId = "ST-1",
            date = "2026-11-23",
            shift = standardShift,
            shiftStartEpochMs = shiftStart,
            shiftEndEpochMs = shiftEnd,
            events = events,
            dayClosed = true,
        )

        assertEquals(35, summary.breakMinutes)
        assertEquals(565, summary.workingMinutes)
        assertEquals(15, summary.lateMinutes)
        assertEquals(0, summary.earlyDepartureMinutes)
        assertEquals(55, summary.overtimeMinutes)
        assertEquals("LATE", summary.status)
        assertFalse(summary.missingCheckoutException)
    }

    @Test
    fun calculateSummary_flagsMissingCheckoutAsExceptionNeverAutoAssumed() {
        val shiftStart = 1_700_000_000_000L
        val shiftEnd = shiftStart + 9 * 3_600_000L
        val summary = AttendanceCalculator.calculateSummary(
            employeeId = "EMP-2",
            employeeName = "Rohan Das",
            storeId = "ST-1",
            date = "2026-11-23",
            shift = standardShift,
            shiftStartEpochMs = shiftStart,
            shiftEndEpochMs = shiftEnd,
            events = listOf(
                AttendanceEvent(eventId = "1", eventType = "CHECK_IN", serverTimestamp = shiftStart),
            ),
            dayClosed = true,
        )

        assertTrue(summary.missingCheckoutException)
        assertEquals("EXCEPTION", summary.status)
        assertEquals(AttendanceWorkState.EXCEPTION.value, summary.workState)
        assertNull(summary.checkOut)
        assertEquals(0, summary.workingMinutes)
    }

    @Test
    fun calculateSummary_handlesOvernightShiftAcrossMidnight() {
        val shiftStart = 1_700_000_000_000L
        // Intentionally pass shiftEnd < shiftStart to simulate 06:00 on same date before normalization
        val rawShiftEnd = shiftStart - 16 * 3_600_000L
        val checkIn = shiftStart + 5 * 60_000L // within 10m grace -> 0 late
        val checkOut = shiftStart + 8 * 3_600_000L // 06:00 next day

        val summary = AttendanceCalculator.calculateSummary(
            employeeId = "EMP-3",
            employeeName = "Meera Nair",
            storeId = "ST-1",
            date = "2026-11-23",
            shift = overnightShift,
            shiftStartEpochMs = shiftStart,
            shiftEndEpochMs = rawShiftEnd,
            events = listOf(
                AttendanceEvent(eventId = "1", eventType = "CHECK_IN", serverTimestamp = checkIn),
                AttendanceEvent(eventId = "2", eventType = "CHECK_OUT", serverTimestamp = checkOut),
            ),
            dayClosed = true,
        )

        assertEquals(0, summary.lateMinutes)
        assertEquals(0, summary.earlyDepartureMinutes)
        assertEquals(475, summary.workingMinutes)
        assertEquals(25, summary.overtimeMinutes) // 475 - 450
        assertEquals("PRESENT", summary.status)
    }

    @Test
    fun geofenceEvaluation_detectsInsideOutsideAndMockedGps() {
        val storeLoc = StoreLocation(latitude = 28.6139, longitude = 77.2090, radiusMeters = 100.0)
        val inside = GpsSnapshot(latitude = 28.6141, longitude = 77.2091, accuracyMeters = 15f, isMocked = false)
        val outside = GpsSnapshot(latitude = 28.6200, longitude = 77.2200, accuracyMeters = 15f, isMocked = true)

        val r1 = AttendanceCalculator.evaluateGeofence(inside, storeLoc)
        assertTrue(r1.withinRadius)
        assertTrue(r1.accuracyAcceptable)
        assertFalse(r1.mockedFlag)

        val r2 = AttendanceCalculator.evaluateGeofence(outside, storeLoc)
        assertFalse(r2.withinRadius)
        assertTrue(r2.mockedFlag)
    }

    @Test
    fun payrollFormula_computesExactNetPay() {
        val net = AttendanceCalculator.calculateNetPay(
            baseAmount = 30000.0,
            allowances = 3500.0,
            overtimePay = 1200.0,
            bonus = 500.0,
            deductions = 1000.0,
            unpaidLeaveDeduction = 2000.0,
        )
        assertEquals(32200.0, net, 0.001)
    }
}
