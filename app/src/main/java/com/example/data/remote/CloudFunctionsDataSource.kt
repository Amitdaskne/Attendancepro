package com.example.data.remote

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudFunctionsDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
) {
    @Suppress("UNCHECKED_CAST")
    suspend fun call(
        functionName: String,
        payload: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val result = functions
            .getHttpsCallable(functionName)
            .call(payload)
            .await()
        return (result.data as? Map<String, Any?>) ?: emptyMap()
    }

    suspend fun provisionUser(payload: Map<String, Any?>): Map<String, Any?> =
        call("provisionUser", payload)

    suspend fun setCustomClaims(payload: Map<String, Any?>): Map<String, Any?> =
        call("setCustomClaims", payload)

    suspend fun transferEmployee(payload: Map<String, Any?>): Map<String, Any?> =
        call("transferEmployee", payload)

    suspend fun issueScannerChallenge(
        storeId: String,
        deviceId: String,
        action: String = "ANY",
        format: String = "QR",
    ): Map<String, Any?> = call(
        "issueScannerChallenge",
        mapOf(
            "storeId" to storeId,
            "deviceId" to deviceId,
            "action" to action,
            "format" to format,
        ),
    )

    suspend fun validatePunch(payload: Map<String, Any?>): Map<String, Any?> =
        call("validatePunch", payload)

    suspend fun approveLeave(requestId: String, note: String): Map<String, Any?> =
        call("approveLeave", mapOf("requestId" to requestId, "note" to note))

    suspend fun rejectLeave(requestId: String, note: String): Map<String, Any?> =
        call("rejectLeave", mapOf("requestId" to requestId, "note" to note))

    suspend fun approveCorrection(requestId: String, note: String): Map<String, Any?> =
        call("approveCorrection", mapOf("requestId" to requestId, "note" to note))

    suspend fun rejectCorrection(requestId: String, note: String): Map<String, Any?> =
        call("rejectCorrection", mapOf("requestId" to requestId, "note" to note))

    suspend fun calculateDailyAttendance(storeId: String?, date: String): Map<String, Any?> =
        call("calculateDailyAttendance", mapOf("storeId" to storeId, "date" to date))

    suspend fun calculatePayroll(storeId: String, month: String): Map<String, Any?> =
        call("calculatePayroll", mapOf("storeId" to storeId, "month" to month))

    suspend fun approvePayroll(periodId: String, note: String): Map<String, Any?> =
        call("approvePayroll", mapOf("periodId" to periodId, "note" to note))

    suspend fun lockPayroll(periodId: String): Map<String, Any?> =
        call("lockPayroll", mapOf("periodId" to periodId))

    suspend fun unlockPayroll(periodId: String, reason: String): Map<String, Any?> =
        call("unlockPayroll", mapOf("periodId" to periodId, "reason" to reason))

    suspend fun exportReport(payload: Map<String, Any?>): Map<String, Any?> =
        call("exportReport", payload)

    suspend fun sendAnnouncement(payload: Map<String, Any?>): Map<String, Any?> =
        call("sendAnnouncement", payload)

    suspend fun registerDevice(payload: Map<String, Any?>): Map<String, Any?> =
        call("registerDevice", payload)

    suspend fun revokeDevice(deviceId: String, status: String, reason: String): Map<String, Any?> =
        call("revokeDevice", mapOf("deviceId" to deviceId, "status" to status, "reason" to reason))

    suspend fun signCloudinaryUpload(folder: String, resourceType: String = "auto"): Map<String, Any?> =
        call("signCloudinaryUpload", mapOf("folder" to folder, "resourceType" to resourceType))

    suspend fun writeAuditLog(payload: Map<String, Any?>): Map<String, Any?> =
        call("writeAuditLog", payload)
}
