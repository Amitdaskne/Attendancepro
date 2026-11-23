package com.example.data.repository

import com.example.domain.model.*
import com.google.firebase.database.DataSnapshot

/**
 * Safe typed DataSnapshot mappers for /attendance/ RTDB schema (§5).
 * Avoids reflection pitfalls and safely coerces Firebase Long/Double/String values.
 */
object RtdbMappers {

    private fun DataSnapshot.str(key: String, default: String = ""): String =
        child(key).value?.toString() ?: default

    private fun DataSnapshot.lng(key: String, default: Long = 0L): Long =
        when (val v = child(key).value) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: default
            else -> default
        }

    private fun DataSnapshot.lngOrNull(key: String): Long? =
        when (val v = child(key).value) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull()
            else -> null
        }

    private fun DataSnapshot.dbl(key: String, default: Double = 0.0): Double =
        when (val v = child(key).value) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: default
            else -> default
        }

    private fun DataSnapshot.intVal(key: String, default: Int = 0): Int =
        when (val v = child(key).value) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }

    private fun DataSnapshot.bool(key: String, default: Boolean = false): Boolean =
        when (val v = child(key).value) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            else -> default
        }

    private fun DataSnapshot.strList(key: String): List<String> =
        child(key).children.mapNotNull { it.value?.toString() }

    fun toOrganization(snap: DataSnapshot): OrganizationNode {
        val profile = snap.child("profile")
        val branding = snap.child("branding")
        val settings = snap.child("settings")
        val policies = snap.child("policies")

        val leaveDays = mutableMapOf<String, Double>()
        policies.child("defaultAnnualLeaveDays").children.forEach { child ->
            val k = child.key ?: return@forEach
            val v = (child.value as? Number)?.toDouble() ?: return@forEach
            leaveDays[k] = v
        }

        return OrganizationNode(
            profile = OrganizationProfile(
                name = profile.str("name", "Attendance Pro"),
                legalName = profile.str("legalName"),
                supportEmail = profile.str("supportEmail"),
                supportPhone = profile.str("supportPhone"),
                timezone = profile.str("timezone", "Asia/Kolkata"),
                currency = profile.str("currency", "INR"),
            ),
            branding = OrganizationBranding(
                appTitle = branding.str("appTitle", "Attendance Pro"),
                logoUrl = branding.str("logoUrl"),
                primaryColorHex = branding.str("primaryColorHex", "#171717"),
                accentColorHex = branding.str("accentColorHex", "#FFC400"),
                tagline = branding.str("tagline", "Multi-Store Workforce Attendance"),
            ),
            settings = OrganizationSettings(
                defaultGraceMinutes = settings.intVal("defaultGraceMinutes", 10),
                halfDayThresholdMinutes = settings.intVal("halfDayThresholdMinutes", 240),
                absentThresholdMinutes = settings.intVal("absentThresholdMinutes", 120),
                overtimeRequiresApproval = settings.bool("overtimeRequiresApproval", true),
                maxOfflineQueueHours = settings.intVal("maxOfflineQueueHours", 12),
                gpsRetentionDays = settings.intVal("gpsRetentionDays", 90),
            ),
            policies = OrganizationPolicies(
                leaveTypes = policies.strList("leaveTypes").ifEmpty {
                    listOf("CASUAL", "SICK", "EARNED", "UNPAID")
                },
                defaultAnnualLeaveDays = leaveDays.ifEmpty {
                    mapOf("CASUAL" to 12.0, "SICK" to 10.0, "EARNED" to 15.0)
                },
                payrollCycleDay = policies.intVal("payrollCycleDay", 1),
                allowMultipleBreaks = policies.bool("allowMultipleBreaks", true),
            ),
        )
    }

    fun toStore(snap: DataSnapshot): StoreRecord {
        val id = snap.key ?: snap.str("storeId")
        val profile = snap.child("profile")
        val contact = profile.child("contact")
        val address = snap.child("address")
        val location = snap.child("location")
        val settings = snap.child("settings")
        return StoreRecord(
            storeId = id,
            profile = StoreProfile(
                name = profile.str("name"),
                code = profile.str("code"),
                logoUrl = profile.str("logoUrl"),
                contact = StoreContact(
                    phone = contact.str("phone"),
                    email = contact.str("email"),
                ),
            ),
            address = StoreAddress(
                building = address.str("building"),
                locality = address.str("locality"),
                city = address.str("city"),
                district = address.str("district"),
                state = address.str("state"),
                postalCode = address.str("postalCode"),
                country = address.str("country", "India"),
            ),
            location = StoreLocation(
                latitude = location.dbl("latitude"),
                longitude = location.dbl("longitude"),
                radiusMeters = location.dbl("radiusMeters", 100.0),
            ),
            settings = StoreSettings(
                openingHours = settings.str("openingHours", "09:00 - 21:00"),
                timezone = settings.str("timezone", "Asia/Kolkata"),
                weeklyOff = settings.strList("weeklyOff").ifEmpty { listOf("SUNDAY") },
                gpsMode = settings.str("gpsMode", GpsPolicyMode.GPS_AND_SCANNER.value),
                maxGpsAccuracyMeters = settings.dbl("maxGpsAccuracyMeters", 80.0),
                scannerTtlSeconds = settings.intVal("scannerTtlSeconds", 45),
            ),
            status = snap.str("status", "ACTIVE"),
            createdAt = snap.lng("createdAt"),
            updatedAt = snap.lng("updatedAt"),
        )
    }

    fun toUser(snap: DataSnapshot): UserRecord {
        val uid = snap.key ?: snap.str("uid")
        val profile = snap.child("profile")
        val stores = mutableMapOf<String, Boolean>()
        snap.child("storeAssignments").children.forEach { child ->
            val key = child.key ?: return@forEach
            val enabled = (child.value as? Boolean) ?: true
            stores[key] = enabled
        }
        return UserRecord(
            uid = uid,
            employeeId = snap.str("employeeId"),
            profile = UserProfile(
                fullName = profile.str("fullName"),
                email = profile.str("email"),
                phone = profile.str("phone"),
                photoUrl = profile.str("photoUrl"),
            ),
            role = snap.str("role", UserRole.EMPLOYEE.claimValue),
            status = snap.str("status", "ACTIVE"),
            storeAssignments = stores,
            createdAt = snap.lng("createdAt"),
            updatedAt = snap.lng("updatedAt"),
        )
    }

    fun toEmployee(snap: DataSnapshot): EmployeeRecord {
        val id = snap.key ?: snap.str("employeeId")
        val profile = snap.child("profile")
        val emergency = profile.child("emergencyContact")
        val employment = snap.child("employment")
        val assignment = snap.child("currentAssignment")
        val salarySnap = snap.child("salaryPrivate")
        val salaryPrivate = if (salarySnap.exists()) {
            SalaryPrivate(
                salaryType = salarySnap.str("salaryType", SalaryType.MONTHLY.value),
                baseSalary = salarySnap.dbl("baseSalary"),
                allowances = salarySnap.dbl("allowances"),
                overtimeHourlyRate = salarySnap.dbl("overtimeHourlyRate"),
                defaultDeductions = salarySnap.dbl("defaultDeductions"),
                currency = salarySnap.str("currency", "INR"),
                bankAccountMasked = salarySnap.str("bankAccountMasked"),
                updatedAt = salarySnap.lng("updatedAt"),
            )
        } else {
            null
        }
        val transfers = mutableMapOf<String, TransferRecord>()
        snap.child("transferHistory").children.forEach { t ->
            val key = t.key ?: return@forEach
            transfers[key] = TransferRecord(
                transferId = key,
                fromStoreId = t.str("fromStoreId"),
                toStoreId = t.str("toStoreId"),
                transferDate = t.str("transferDate"),
                reason = t.str("reason"),
                actorUid = t.str("actorUid"),
                timestamp = t.lng("timestamp"),
            )
        }
        return EmployeeRecord(
            employeeId = id,
            uid = snap.str("uid"),
            profile = EmployeeProfile(
                fullName = profile.str("fullName"),
                photoUrl = profile.str("photoUrl"),
                email = profile.str("email"),
                phone = profile.str("phone"),
                dob = profile.str("dob"),
                gender = profile.str("gender"),
                address = profile.str("address"),
                emergencyContact = EmergencyContact(
                    name = emergency.str("name"),
                    relation = emergency.str("relation"),
                    phone = emergency.str("phone"),
                ),
            ),
            employment = EmploymentInfo(
                employeeCode = employment.str("employeeCode", id),
                joiningDate = employment.str("joiningDate"),
                departmentId = employment.str("departmentId"),
                departmentName = employment.str("departmentName"),
                designation = employment.str("designation"),
                employmentStatus = employment.str("employmentStatus", "ACTIVE"),
            ),
            currentAssignment = CurrentAssignment(
                storeId = assignment.str("storeId"),
                managerId = assignment.str("managerId"),
                shiftId = assignment.str("shiftId"),
                assignedAt = assignment.lng("assignedAt"),
            ),
            salaryPrivate = salaryPrivate,
            transferHistory = transfers,
        )
    }

    fun toDepartment(snap: DataSnapshot): Department = Department(
        departmentId = snap.key ?: snap.str("departmentId"),
        name = snap.str("name"),
        code = snap.str("code"),
        storeId = snap.str("storeId"),
        status = snap.str("status", "ACTIVE"),
    )

    fun toShift(snap: DataSnapshot): ShiftRecord = ShiftRecord(
        shiftId = snap.key ?: snap.str("shiftId"),
        storeId = snap.str("storeId"),
        name = snap.str("name"),
        shiftType = snap.str("shiftType", ShiftType.FIXED.value),
        startTime = snap.str("startTime", "09:00"),
        endTime = snap.str("endTime", "18:00"),
        graceMinutes = snap.intVal("graceMinutes", 10),
        maxBreakMinutes = snap.intVal("maxBreakMinutes", 60),
        paidBreakMinutes = snap.intVal("paidBreakMinutes", 0),
        minWorkingMinutes = snap.intVal("minWorkingMinutes", 480),
        halfDayMinutes = snap.intVal("halfDayMinutes", 240),
        overtimeThresholdMinutes = snap.intVal("overtimeThresholdMinutes", 510),
        weeklyOff = snap.strList("weeklyOff").ifEmpty { listOf("SUNDAY") },
        overnight = snap.bool("overnight", false),
        status = snap.str("status", "ACTIVE"),
    )

    fun toHoliday(snap: DataSnapshot, storeId: String): HolidayRecord = HolidayRecord(
        holidayId = snap.key ?: snap.str("holidayId"),
        storeId = storeId,
        date = snap.str("date"),
        name = snap.str("name"),
        isOptional = snap.bool("isOptional", false),
        createdAt = snap.lng("createdAt"),
    )

    fun toSummary(snap: DataSnapshot): AttendanceSummary = AttendanceSummary(
        employeeId = snap.str("employeeId"),
        employeeName = snap.str("employeeName"),
        storeId = snap.str("storeId"),
        date = snap.str("date"),
        shiftId = snap.str("shiftId"),
        checkIn = snap.lngOrNull("checkIn"),
        checkOut = snap.lngOrNull("checkOut"),
        breakMinutes = snap.intVal("breakMinutes"),
        workingMinutes = snap.intVal("workingMinutes"),
        lateMinutes = snap.intVal("lateMinutes"),
        earlyDepartureMinutes = snap.intVal("earlyDepartureMinutes"),
        overtimeMinutes = snap.intVal("overtimeMinutes"),
        status = snap.str("status", "ABSENT"),
        workState = snap.str("workState", AttendanceWorkState.NOT_CHECKED_IN.value),
        source = snap.str("source", "SCANNER"),
        calcVersion = snap.intVal("calcVersion", 1),
        finalized = snap.bool("finalized"),
        missingCheckoutException = snap.bool("missingCheckoutException"),
        updatedAt = snap.lng("updatedAt"),
    )

    fun toLeaveRequest(snap: DataSnapshot): LeaveRequest = LeaveRequest(
        requestId = snap.key ?: snap.str("requestId"),
        employeeId = snap.str("employeeId"),
        employeeName = snap.str("employeeName"),
        storeId = snap.str("storeId"),
        leaveType = snap.str("leaveType", "CASUAL"),
        startDate = snap.str("startDate"),
        endDate = snap.str("endDate"),
        totalDays = snap.dbl("totalDays", 1.0),
        reason = snap.str("reason"),
        attachmentUrl = snap.str("attachmentUrl"),
        status = snap.str("status", ApprovalStatus.PENDING.value),
        reviewerUid = snap.str("reviewerUid"),
        reviewerNote = snap.str("reviewerNote"),
        createdAt = snap.lng("createdAt"),
        reviewedAt = snap.lng("reviewedAt"),
    )

    fun toLeaveBalance(snap: DataSnapshot, employeeId: String): LeaveBalance {
        val balances = mutableMapOf<String, Double>()
        snap.child("balances").children.forEach { c ->
            val k = c.key ?: return@forEach
            val v = (c.value as? Number)?.toDouble() ?: return@forEach
            balances[k] = v
        }
        val used = mutableMapOf<String, Double>()
        snap.child("used").children.forEach { c ->
            val k = c.key ?: return@forEach
            val v = (c.value as? Number)?.toDouble() ?: return@forEach
            used[k] = v
        }
        return LeaveBalance(
            employeeId = employeeId,
            balances = balances.ifEmpty { mapOf("CASUAL" to 12.0, "SICK" to 10.0, "EARNED" to 15.0) },
            used = used,
            updatedAt = snap.lng("updatedAt"),
        )
    }

    fun toCorrection(snap: DataSnapshot): AttendanceCorrection = AttendanceCorrection(
        requestId = snap.key ?: snap.str("requestId"),
        employeeId = snap.str("employeeId"),
        employeeName = snap.str("employeeName"),
        storeId = snap.str("storeId"),
        date = snap.str("date"),
        correctionType = snap.str("correctionType", "MISSING_CHECK_OUT"),
        requestedTimestampMs = snap.lng("requestedTimestampMs"),
        requestedTimeLabel = snap.str("requestedTimeLabel"),
        reason = snap.str("reason"),
        status = snap.str("status", ApprovalStatus.PENDING.value),
        reviewerUid = snap.str("reviewerUid"),
        reviewerNote = snap.str("reviewerNote"),
        resultingEventId = snap.str("resultingEventId"),
        createdAt = snap.lng("createdAt"),
        reviewedAt = snap.lng("reviewedAt"),
    )

    fun toDevice(snap: DataSnapshot): DeviceRecord = DeviceRecord(
        deviceId = snap.key ?: snap.str("deviceId"),
        name = snap.str("name"),
        storeId = snap.str("storeId"),
        permittedActions = snap.strList("permittedActions").ifEmpty {
            listOf("CHECK_IN", "CHECK_OUT", "BREAK_START", "BREAK_END")
        },
        status = snap.str("status", "ACTIVE"),
        registeredBy = snap.str("registeredBy"),
        lastSeenAt = snap.lng("lastSeenAt"),
        createdAt = snap.lng("createdAt"),
    )

    fun toAuditLog(snap: DataSnapshot): AuditLogEntry = AuditLogEntry(
        logId = snap.key ?: snap.str("logId"),
        actorUid = snap.str("actorUid"),
        actorRole = snap.str("actorRole"),
        action = snap.str("action"),
        target = snap.str("target"),
        storeId = snap.str("storeId"),
        beforeSummary = snap.str("beforeSummary"),
        afterSummary = snap.str("afterSummary"),
        reason = snap.str("reason"),
        serverTimestamp = snap.lng("serverTimestamp"),
    )

    fun toAnnouncement(snap: DataSnapshot): AnnouncementRecord = AnnouncementRecord(
        announcementId = snap.key ?: snap.str("announcementId"),
        title = snap.str("title"),
        body = snap.str("body"),
        targetScope = snap.str("targetScope", "ALL"),
        targetIds = snap.strList("targetIds"),
        createdBy = snap.str("createdBy"),
        createdAt = snap.lng("createdAt"),
    )

    fun toPayrollPeriod(snap: DataSnapshot): PayrollPeriod {
        val lines = mutableMapOf<String, PayrollEmployeeLine>()
        snap.child("lines").children.forEach { lineSnap ->
            val empId = lineSnap.key ?: return@forEach
            lines[empId] = PayrollEmployeeLine(
                employeeId = empId,
                employeeName = lineSnap.str("employeeName"),
                storeId = lineSnap.str("storeId"),
                salaryType = lineSnap.str("salaryType", SalaryType.MONTHLY.value),
                presentDays = lineSnap.dbl("presentDays"),
                paidLeaveDays = lineSnap.dbl("paidLeaveDays"),
                unpaidLeaveDays = lineSnap.dbl("unpaidLeaveDays"),
                workingHours = lineSnap.dbl("workingHours"),
                overtimeHours = lineSnap.dbl("overtimeHours"),
                exceptionCount = lineSnap.intVal("exceptionCount"),
                baseAmount = lineSnap.dbl("baseAmount"),
                allowances = lineSnap.dbl("allowances"),
                overtimePay = lineSnap.dbl("overtimePay"),
                bonus = lineSnap.dbl("bonus"),
                deductions = lineSnap.dbl("deductions"),
                unpaidLeaveDeduction = lineSnap.dbl("unpaidLeaveDeduction"),
                netPay = lineSnap.dbl("netPay"),
                slipUrl = lineSnap.str("slipUrl"),
            )
        }
        return PayrollPeriod(
            periodId = snap.key ?: snap.str("periodId"),
            storeId = snap.str("storeId"),
            month = snap.str("month"),
            status = snap.str("status", PayrollStatus.DRAFT.value),
            totalNetPay = snap.dbl("totalNetPay"),
            employeeCount = snap.intVal("employeeCount", lines.size),
            exceptionCount = snap.intVal("exceptionCount"),
            lines = lines,
            calculatedBy = snap.str("calculatedBy"),
            approvedBy = snap.str("approvedBy"),
            lockedBy = snap.str("lockedBy"),
            updatedAt = snap.lng("updatedAt"),
        )
    }

    fun toSalarySlip(snap: DataSnapshot, employeeId: String): SalarySlip = SalarySlip(
        periodId = snap.key ?: snap.str("periodId"),
        employeeId = employeeId,
        storeId = snap.str("storeId"),
        month = snap.str("month"),
        currency = snap.str("currency", "INR"),
        baseAmount = snap.dbl("baseAmount"),
        allowances = snap.dbl("allowances"),
        overtimePay = snap.dbl("overtimePay"),
        bonus = snap.dbl("bonus"),
        deductions = snap.dbl("deductions"),
        unpaidLeaveDeduction = snap.dbl("unpaidLeaveDeduction"),
        netPay = snap.dbl("netPay"),
        pdfUrl = snap.str("pdfUrl"),
        generatedAt = snap.lng("generatedAt"),
    )

    fun toStoreStatistics(snap: DataSnapshot, storeId: String, date: String): StoreStatistics =
        StoreStatistics(
            storeId = storeId,
            date = date,
            totalEmployees = snap.intVal("totalEmployees"),
            presentCount = snap.intVal("presentCount"),
            lateCount = snap.intVal("lateCount"),
            onBreakCount = snap.intVal("onBreakCount"),
            onLeaveCount = snap.intVal("onLeaveCount"),
            absentCount = snap.intVal("absentCount"),
            exceptionCount = snap.intVal("exceptionCount"),
            totalOvertimeMinutes = snap.intVal("totalOvertimeMinutes"),
            updatedAt = snap.lng("updatedAt"),
        )
}
