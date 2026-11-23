package com.example.domain.model

enum class UserRole(val claimValue: String, val displayLabel: String) {
    SUPER_ADMIN("super_admin", "Super Admin"),
    STORE_ADMIN("store_admin", "Store Admin"),
    MANAGER("manager", "Manager"),
    EMPLOYEE("employee", "Employee");

    val isAdminTier: Boolean
        get() = this == SUPER_ADMIN || this == STORE_ADMIN || this == MANAGER

    companion object {
        fun fromClaim(raw: String?): UserRole? =
            entries.firstOrNull { it.claimValue.equals(raw, ignoreCase = true) }
    }
}

data class UserSession(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val role: UserRole = UserRole.EMPLOYEE,
    val employeeId: String = "",
    val assignedStoreIds: List<String> = emptyList(),
    val primaryStoreId: String = "",
    val disabled: Boolean = false,
) {
    fun canAccessStore(storeId: String): Boolean =
        role == UserRole.SUPER_ADMIN || assignedStoreIds.contains(storeId) || primaryStoreId == storeId
}

data class UserProfile(
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String = "",
)

data class UserRecord(
    val uid: String = "",
    val employeeId: String = "",
    val profile: UserProfile = UserProfile(),
    val role: String = UserRole.EMPLOYEE.claimValue,
    val status: String = "ACTIVE",
    val storeAssignments: Map<String, Boolean> = emptyMap(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val parsedRole: UserRole
        get() = UserRole.fromClaim(role) ?: UserRole.EMPLOYEE
    val isActive: Boolean
        get() = status.equals("ACTIVE", ignoreCase = true)
    val storeIds: List<String>
        get() = storeAssignments.filterValues { it }.keys.toList()
}

data class EmergencyContact(
    val name: String = "",
    val relation: String = "",
    val phone: String = "",
)

data class EmployeeProfile(
    val fullName: String = "",
    val photoUrl: String = "",
    val email: String = "",
    val phone: String = "",
    val dob: String = "",
    val gender: String = "",
    val address: String = "",
    val emergencyContact: EmergencyContact = EmergencyContact(),
)

data class EmploymentInfo(
    val employeeCode: String = "",
    val joiningDate: String = "",
    val departmentId: String = "",
    val departmentName: String = "",
    val designation: String = "",
    val employmentStatus: String = "ACTIVE",
)

data class CurrentAssignment(
    val storeId: String = "",
    val managerId: String = "",
    val shiftId: String = "",
    val assignedAt: Long = 0L,
)

data class TransferRecord(
    val transferId: String = "",
    val fromStoreId: String = "",
    val toStoreId: String = "",
    val transferDate: String = "",
    val reason: String = "",
    val actorUid: String = "",
    val timestamp: Long = 0L,
)

enum class SalaryType(val value: String, val label: String) {
    MONTHLY("MONTHLY", "Monthly"),
    PER_DAY("PER_DAY", "Per Day"),
    HOURLY("HOURLY", "Hourly");

    companion object {
        fun fromValue(raw: String?): SalaryType =
            entries.firstOrNull { it.value.equals(raw, ignoreCase = true) } ?: MONTHLY
    }
}

data class SalaryPrivate(
    val salaryType: String = SalaryType.MONTHLY.value,
    val baseSalary: Double = 0.0,
    val allowances: Double = 0.0,
    val overtimeHourlyRate: Double = 0.0,
    val defaultDeductions: Double = 0.0,
    val currency: String = "INR",
    val bankAccountMasked: String = "",
    val updatedAt: Long = 0L,
)

data class EmployeeRecord(
    val employeeId: String = "",
    val uid: String = "",
    val profile: EmployeeProfile = EmployeeProfile(),
    val employment: EmploymentInfo = EmploymentInfo(),
    val currentAssignment: CurrentAssignment = CurrentAssignment(),
    val salaryPrivate: SalaryPrivate? = null,
    val transferHistory: Map<String, TransferRecord> = emptyMap(),
)

data class Department(
    val departmentId: String = "",
    val name: String = "",
    val code: String = "",
    val storeId: String = "",
    val status: String = "ACTIVE",
)
