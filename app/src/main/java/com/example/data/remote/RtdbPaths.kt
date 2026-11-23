package com.example.data.remote

/**
 * Authoritative Firebase Realtime Database path schema (§5).
 * Everything lives strictly under /attendance/.
 */
object RtdbPaths {
    const val ROOT = "attendance"

    const val ORGANIZATION = "$ROOT/organization"
    const val ORG_PROFILE = "$ORGANIZATION/profile"
    const val ORG_SETTINGS = "$ORGANIZATION/settings"
    const val ORG_BRANDING = "$ORGANIZATION/branding"
    const val ORG_POLICIES = "$ORGANIZATION/policies"

    const val STORES = "$ROOT/stores"
    const val USERS = "$ROOT/users"
    const val EMPLOYEES = "$ROOT/employees"
    const val DEPARTMENTS = "$ROOT/departments"
    const val SHIFTS = "$ROOT/shifts"
    const val ATTENDANCE = "$ROOT/attendance"
    const val ATTENDANCE_EVENTS = "$ROOT/attendanceEvents"
    const val ATTENDANCE_CORRECTIONS = "$ROOT/attendanceCorrections"
    const val LEAVE_REQUESTS = "$ROOT/leaveRequests"
    const val LEAVE_BALANCES = "$ROOT/leaveBalances"
    const val HOLIDAYS = "$ROOT/holidays"
    const val PAYROLL = "$ROOT/payroll"
    const val SALARY_SLIPS = "$ROOT/salarySlips"
    const val DEVICES = "$ROOT/devices"
    const val SCANNER_CHALLENGES = "$ROOT/scannerChallenges"
    const val NOTIFICATIONS = "$ROOT/notifications"
    const val ANNOUNCEMENTS = "$ROOT/announcements"
    const val AUDIT_LOGS = "$ROOT/auditLogs"
    const val STATISTICS = "$ROOT/statistics"
    const val INDEXES = "$ROOT/indexes"

    fun store(storeId: String) = "$STORES/$storeId"
    fun user(uid: String) = "$USERS/$uid"
    fun employee(employeeId: String) = "$EMPLOYEES/$employeeId"
    fun employeeSummary(storeId: String, employeeId: String, date: String) =
        "$ATTENDANCE/$storeId/$employeeId/$date/summary"
    fun storeAttendance(storeId: String) = "$ATTENDANCE/$storeId"
    fun employeeAttendance(storeId: String, employeeId: String) = "$ATTENDANCE/$storeId/$employeeId"
    fun leaveBalance(employeeId: String) = "$LEAVE_BALANCES/$employeeId"
    fun storeHolidays(storeId: String) = "$HOLIDAYS/$storeId"
    fun employeeSlips(employeeId: String) = "$SALARY_SLIPS/$employeeId"
    fun storeStatistics(storeId: String, date: String) = "$STATISTICS/$storeId/$date"
}
