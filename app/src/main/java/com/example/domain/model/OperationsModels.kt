package com.example.domain.model

enum class ApprovalStatus(val value: String, val label: String) {
    PENDING("PENDING", "Pending"),
    APPROVED("APPROVED", "Approved"),
    REJECTED("REJECTED", "Rejected");

    companion object {
        fun fromValue(raw: String?): ApprovalStatus =
            entries.firstOrNull { it.value.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

data class LeaveRequest(
    val requestId: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val storeId: String = "",
    val leaveType: String = "CASUAL",
    val startDate: String = "",
    val endDate: String = "",
    val totalDays: Double = 1.0,
    val reason: String = "",
    val attachmentUrl: String = "",
    val status: String = ApprovalStatus.PENDING.value,
    val reviewerUid: String = "",
    val reviewerNote: String = "",
    val createdAt: Long = 0L,
    val reviewedAt: Long = 0L,
) {
    val parsedStatus: ApprovalStatus
        get() = ApprovalStatus.fromValue(status)
}

data class LeaveBalance(
    val employeeId: String = "",
    val balances: Map<String, Double> = mapOf("CASUAL" to 12.0, "SICK" to 10.0, "EARNED" to 15.0),
    val used: Map<String, Double> = emptyMap(),
    val updatedAt: Long = 0L,
)

data class AttendanceCorrection(
    val requestId: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val storeId: String = "",
    val date: String = "",
    val correctionType: String = "MISSING_CHECK_OUT",
    val requestedTimestampMs: Long = 0L,
    val requestedTimeLabel: String = "",
    val reason: String = "",
    val status: String = ApprovalStatus.PENDING.value,
    val reviewerUid: String = "",
    val reviewerNote: String = "",
    val resultingEventId: String = "",
    val createdAt: Long = 0L,
    val reviewedAt: Long = 0L,
) {
    val parsedStatus: ApprovalStatus
        get() = ApprovalStatus.fromValue(status)
}

enum class PayrollStatus(val value: String, val label: String) {
    DRAFT("DRAFT", "Draft"),
    CALCULATED("CALCULATED", "Calculated"),
    APPROVED("APPROVED", "Approved"),
    LOCKED("LOCKED", "Locked");

    companion object {
        fun fromValue(raw: String?): PayrollStatus =
            entries.firstOrNull { it.value.equals(raw, ignoreCase = true) } ?: DRAFT
    }
}

data class PayrollEmployeeLine(
    val employeeId: String = "",
    val employeeName: String = "",
    val storeId: String = "",
    val salaryType: String = SalaryType.MONTHLY.value,
    val presentDays: Double = 0.0,
    val paidLeaveDays: Double = 0.0,
    val unpaidLeaveDays: Double = 0.0,
    val workingHours: Double = 0.0,
    val overtimeHours: Double = 0.0,
    val exceptionCount: Int = 0,
    val baseAmount: Double = 0.0,
    val allowances: Double = 0.0,
    val overtimePay: Double = 0.0,
    val bonus: Double = 0.0,
    val deductions: Double = 0.0,
    val unpaidLeaveDeduction: Double = 0.0,
    val netPay: Double = 0.0,
    val slipUrl: String = "",
)

data class PayrollPeriod(
    val periodId: String = "",
    val storeId: String = "",
    val month: String = "",
    val status: String = PayrollStatus.DRAFT.value,
    val totalNetPay: Double = 0.0,
    val employeeCount: Int = 0,
    val exceptionCount: Int = 0,
    val lines: Map<String, PayrollEmployeeLine> = emptyMap(),
    val calculatedBy: String = "",
    val approvedBy: String = "",
    val lockedBy: String = "",
    val updatedAt: Long = 0L,
) {
    val parsedStatus: PayrollStatus
        get() = PayrollStatus.fromValue(status)
}

data class SalarySlip(
    val periodId: String = "",
    val employeeId: String = "",
    val storeId: String = "",
    val month: String = "",
    val currency: String = "INR",
    val baseAmount: Double = 0.0,
    val allowances: Double = 0.0,
    val overtimePay: Double = 0.0,
    val bonus: Double = 0.0,
    val deductions: Double = 0.0,
    val unpaidLeaveDeduction: Double = 0.0,
    val netPay: Double = 0.0,
    val pdfUrl: String = "",
    val generatedAt: Long = 0L,
)

data class AuditLogEntry(
    val logId: String = "",
    val actorUid: String = "",
    val actorRole: String = "",
    val action: String = "",
    val target: String = "",
    val storeId: String = "",
    val beforeSummary: String = "",
    val afterSummary: String = "",
    val reason: String = "",
    val serverTimestamp: Long = 0L,
)

data class AnnouncementRecord(
    val announcementId: String = "",
    val title: String = "",
    val body: String = "",
    val targetScope: String = "ALL",
    val targetIds: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Long = 0L,
)

data class StoreStatistics(
    val storeId: String = "",
    val date: String = "",
    val totalEmployees: Int = 0,
    val presentCount: Int = 0,
    val lateCount: Int = 0,
    val onBreakCount: Int = 0,
    val onLeaveCount: Int = 0,
    val absentCount: Int = 0,
    val exceptionCount: Int = 0,
    val totalOvertimeMinutes: Int = 0,
    val updatedAt: Long = 0L,
)

enum class ReportType(val value: String, val label: String) {
    DAILY("DAILY", "Daily Attendance"),
    MONTHLY("MONTHLY", "Monthly Summary"),
    EMPLOYEE_WISE("EMPLOYEE_WISE", "Employee-wise"),
    STORE_WISE("STORE_WISE", "Store-wise"),
    MULTI_STORE("MULTI_STORE", "Multi-Store Comparison"),
    LATE("LATE", "Late Arrivals"),
    EARLY_DEPARTURE("EARLY_DEPARTURE", "Early Departures"),
    OVERTIME("OVERTIME", "Overtime"),
    ABSENCE("ABSENCE", "Absences"),
    LEAVE("LEAVE", "Leave Report"),
    WORKING_HOURS("WORKING_HOURS", "Working Hours"),
    PAYROLL("PAYROLL", "Payroll Summary"),
    CORRECTION("CORRECTION", "Corrections Audit"),
    SCANNER("SCANNER", "Scanner Activity");
}

enum class ExportFormat(val value: String, val label: String) {
    PDF("PDF", "PDF"),
    CSV("CSV", "CSV"),
    XLSX("XLSX", "Excel (XLSX)");
}

data class ReportExportResult(
    val reportId: String = "",
    val downloadUrl: String = "",
    val format: String = "CSV",
    val rowCount: Int = 0,
    val generatedAt: Long = 0L,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Empty(val message: String) : UiState<Nothing>
    data class Error(val message: String, val retryable: Boolean = true) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class PermissionDenied(val message: String) : UiState<Nothing>
    data object Offline : UiState<Nothing>
}
