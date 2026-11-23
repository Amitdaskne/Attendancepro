package com.example.domain.model

data class OrganizationProfile(
    val name: String = "Attendance Pro",
    val legalName: String = "",
    val supportEmail: String = "",
    val supportPhone: String = "",
    val timezone: String = "Asia/Kolkata",
    val currency: String = "INR",
)

data class OrganizationBranding(
    val appTitle: String = "Attendance Pro",
    val logoUrl: String = "",
    val primaryColorHex: String = "#171717",
    val accentColorHex: String = "#FFC400",
    val tagline: String = "Multi-Store Workforce Attendance",
)

data class OrganizationSettings(
    val defaultGraceMinutes: Int = 10,
    val halfDayThresholdMinutes: Int = 240,
    val absentThresholdMinutes: Int = 120,
    val overtimeRequiresApproval: Boolean = true,
    val maxOfflineQueueHours: Int = 12,
    val gpsRetentionDays: Int = 90,
)

data class OrganizationPolicies(
    val leaveTypes: List<String> = listOf("CASUAL", "SICK", "EARNED", "UNPAID"),
    val defaultAnnualLeaveDays: Map<String, Double> = mapOf(
        "CASUAL" to 12.0,
        "SICK" to 10.0,
        "EARNED" to 15.0,
    ),
    val payrollCycleDay: Int = 1,
    val allowMultipleBreaks: Boolean = true,
)

data class OrganizationNode(
    val profile: OrganizationProfile = OrganizationProfile(),
    val branding: OrganizationBranding = OrganizationBranding(),
    val settings: OrganizationSettings = OrganizationSettings(),
    val policies: OrganizationPolicies = OrganizationPolicies(),
)

enum class GpsPolicyMode(val value: String, val label: String) {
    DISABLED("disabled", "GPS Disabled"),
    OPTIONAL("optional", "GPS Optional"),
    REQUIRED("required", "GPS Required"),
    GPS_AND_SCANNER("gps_and_scanner", "GPS + Scanner Required"),
    SCANNER_ONLY("scanner_only", "Scanner Only");

    companion object {
        fun fromValue(raw: String?): GpsPolicyMode =
            entries.firstOrNull { it.value.equals(raw, ignoreCase = true) } ?: GPS_AND_SCANNER
    }
}

data class StoreAddress(
    val building: String = "",
    val locality: String = "",
    val city: String = "",
    val district: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "India",
) {
    fun formattedLine(): String =
        listOf(building, locality, city, state, postalCode)
            .filter { it.isNotBlank() }
            .joinToString(", ")
}

data class StoreContact(
    val phone: String = "",
    val email: String = "",
)

data class StoreLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 100.0,
)

data class StoreProfile(
    val name: String = "",
    val code: String = "",
    val logoUrl: String = "",
    val contact: StoreContact = StoreContact(),
)

data class StoreSettings(
    val openingHours: String = "09:00 - 21:00",
    val timezone: String = "Asia/Kolkata",
    val weeklyOff: List<String> = listOf("SUNDAY"),
    val gpsMode: String = GpsPolicyMode.GPS_AND_SCANNER.value,
    val maxGpsAccuracyMeters: Double = 80.0,
    val scannerTtlSeconds: Int = 45,
) {
    val parsedGpsMode: GpsPolicyMode
        get() = GpsPolicyMode.fromValue(gpsMode)
}

data class StoreRecord(
    val storeId: String = "",
    val profile: StoreProfile = StoreProfile(),
    val address: StoreAddress = StoreAddress(),
    val location: StoreLocation = StoreLocation(),
    val settings: StoreSettings = StoreSettings(),
    val status: String = "ACTIVE",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val isActive: Boolean
        get() = status.equals("ACTIVE", ignoreCase = true)
}

data class DeviceRecord(
    val deviceId: String = "",
    val name: String = "",
    val storeId: String = "",
    val permittedActions: List<String> = listOf("CHECK_IN", "CHECK_OUT", "BREAK_START", "BREAK_END"),
    val status: String = "ACTIVE",
    val registeredBy: String = "",
    val lastSeenAt: Long = 0L,
    val createdAt: Long = 0L,
) {
    val isActive: Boolean
        get() = status.equals("ACTIVE", ignoreCase = true)
}
