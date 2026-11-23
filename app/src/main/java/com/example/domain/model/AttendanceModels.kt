package com.example.domain.model

import kotlinx.serialization.Serializable

enum class PunchEventType(val value: String, val label: String) {
    CHECK_IN("CHECK_IN", "Check In"),
    CHECK_OUT("CHECK_OUT", "Check Out"),
    BREAK_START("BREAK_START", "Start Break"),
    BREAK_END("BREAK_END", "End Break"),
    ADMIN_CORRECTION("ADMIN_CORRECTION", "Admin Correction"),
    ADMIN_OVERRIDE("ADMIN_OVERRIDE", "Admin Override");

    companion object {
        fun fromValue(raw: String?): PunchEventType =
            entries.firstOrNull { it.value.equals(raw, ignoreCase = true) } ?: CHECK_IN
    }
}

enum class AttendanceWorkState(val value: String, val label: String) {
    NOT_CHECKED_IN("NOT_CHECKED_IN", "Not Checked In"),
    WORKING("WORKING", "Working"),
    ON_BREAK("ON_BREAK", "On Break"),
    COMPLETED("COMPLETED", "Shift Completed"),
    EXCEPTION("EXCEPTION", "Missing Check-Out");

    companion object {
        fun fromValue(raw: String?): AttendanceWorkState =
            entries.firstOrNull { it.value.equals(raw, ignoreCase = true) } ?: NOT_CHECKED_IN
    }
}

data class GpsSnapshot(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val isMocked: Boolean = false,
    val capturedAtMs: Long = 0L,
)

data class AttendanceSummary(
    val employeeId: String = "",
    val employeeName: String = "",
    val storeId: String = "",
    val date: String = "",
    val shiftId: String = "",
    val checkIn: Long? = null,
    val checkOut: Long? = null,
    val breakMinutes: Int = 0,
    val workingMinutes: Int = 0,
    val lateMinutes: Int = 0,
    val earlyDepartureMinutes: Int = 0,
    val overtimeMinutes: Int = 0,
    val status: String = "ABSENT",
    val workState: String = AttendanceWorkState.NOT_CHECKED_IN.value,
    val source: String = "SCANNER",
    val calcVersion: Int = 1,
    val finalized: Boolean = false,
    val missingCheckoutException: Boolean = false,
    val updatedAt: Long = 0L,
) {
    val parsedWorkState: AttendanceWorkState
        get() = AttendanceWorkState.fromValue(workState)
}

data class AttendanceEvent(
    val eventId: String = "",
    val idempotencyKey: String = "",
    val employeeId: String = "",
    val storeId: String = "",
    val date: String = "",
    val eventType: String = PunchEventType.CHECK_IN.value,
    val serverTimestamp: Long = 0L,
    val source: String = "SCANNER",
    val deviceId: String = "",
    val challengeId: String = "",
    val gps: GpsSnapshot? = null,
    val gpsFlaggedMock: Boolean = false,
    val distanceMeters: Double? = null,
    val verified: Boolean = true,
    val note: String = "",
    val actorUid: String = "",
)

data class ScannerChallenge(
    val challengeId: String = "",
    val storeId: String = "",
    val deviceId: String = "",
    val action: String = "ANY",
    val nonce: String = "",
    val signature: String = "",
    val format: String = "QR",
    val issuedAtMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    val payloadJson: String = "",
)

@Serializable
data class ScannerPayload(
    val v: Int = 1,
    val challengeId: String,
    val storeId: String,
    val deviceId: String,
    val nonce: String,
    val sig: String,
    val exp: Long,
    val action: String = "ANY",
)

enum class ShiftType(val value: String, val label: String) {
    FIXED("FIXED", "Fixed"),
    ROTATIONAL("ROTATIONAL", "Rotational"),
    FLEXIBLE("FLEXIBLE", "Flexible"),
    SPLIT("SPLIT", "Split"),
    TEMPORARY("TEMPORARY", "Temporary");

    companion object {
        fun fromValue(raw: String?): ShiftType =
            entries.firstOrNull { it.value.equals(raw, ignoreCase = true) } ?: FIXED
    }
}

data class ShiftRecord(
    val shiftId: String = "",
    val storeId: String = "",
    val name: String = "",
    val shiftType: String = ShiftType.FIXED.value,
    val startTime: String = "09:00",
    val endTime: String = "18:00",
    val graceMinutes: Int = 10,
    val maxBreakMinutes: Int = 60,
    val paidBreakMinutes: Int = 0,
    val minWorkingMinutes: Int = 480,
    val halfDayMinutes: Int = 240,
    val overtimeThresholdMinutes: Int = 510,
    val weeklyOff: List<String> = listOf("SUNDAY"),
    val overnight: Boolean = false,
    val status: String = "ACTIVE",
)

data class HolidayRecord(
    val holidayId: String = "",
    val storeId: String = "",
    val date: String = "",
    val name: String = "",
    val isOptional: Boolean = false,
    val createdAt: Long = 0L,
)
