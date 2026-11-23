package com.example.data.repository

import com.example.data.local.OfflinePunchDao
import com.example.data.local.OfflinePunchEntity
import com.example.data.remote.CloudFunctionsDataSource
import com.example.data.remote.NetworkMonitor
import com.example.data.remote.RtdbPaths
import com.example.domain.model.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val offlinePunchDao: OfflinePunchDao,
    private val networkMonitor: NetworkMonitor,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeOfflineQueue(): Flow<List<OfflinePunchEntity>> = offlinePunchDao.observeAll()

    fun observeTodaySummary(
        storeId: String,
        employeeId: String,
        date: String,
    ): Flow<AttendanceSummary?> = callbackFlow {
        if (storeId.isBlank() || employeeId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val ref = database.getReference(RtdbPaths.employeeSummary(storeId, employeeId, date))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(if (snapshot.exists()) RtdbMappers.toSummary(snapshot) else null)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeEmployeeMonthSummaries(
        storeId: String,
        employeeId: String,
        yearMonthPrefix: String,
    ): Flow<Result<List<AttendanceSummary>>> = callbackFlow {
        if (storeId.isBlank() || employeeId.isBlank()) {
            trySend(Result.success(emptyList()))
            awaitClose { }
            return@callbackFlow
        }
        val ref = database.getReference(RtdbPaths.employeeAttendance(storeId, employeeId))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { dateNode ->
                    val dateKey = dateNode.key ?: return@mapNotNull null
                    if (yearMonthPrefix.isNotBlank() && !dateKey.startsWith(yearMonthPrefix)) {
                        return@mapNotNull null
                    }
                    val sumSnap = dateNode.child("summary")
                    if (sumSnap.exists()) RtdbMappers.toSummary(sumSnap) else null
                }.sortedByDescending { it.date }
                trySend(Result.success(list))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeStoreDateSummaries(
        storeIds: List<String>,
        date: String,
    ): Flow<Result<List<AttendanceSummary>>> = callbackFlow {
        if (storeIds.isEmpty()) {
            trySend(Result.success(emptyList()))
            awaitClose { }
            return@callbackFlow
        }
        val ref = database.getReference(RtdbPaths.ATTENDANCE)
        val allowedSet = storeIds.toSet()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val summaries = mutableListOf<AttendanceSummary>()
                snapshot.children.forEach { storeNode ->
                    val sId = storeNode.key ?: return@forEach
                    if (!allowedSet.contains(sId)) return@forEach
                    storeNode.children.forEach { empNode ->
                        val summarySnap = empNode.child(date).child("summary")
                        if (summarySnap.exists()) {
                            summaries.add(RtdbMappers.toSummary(summarySnap))
                        }
                    }
                }
                trySend(Result.success(summaries.sortedByDescending { it.updatedAt }))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeStoreStatistics(storeId: String, date: String): Flow<StoreStatistics?> = callbackFlow {
        if (storeId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val ref = database.getReference(RtdbPaths.storeStatistics(storeId, date))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(
                    if (snapshot.exists()) RtdbMappers.toStoreStatistics(snapshot, storeId, date)
                    else null,
                )
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Request a short-lived (<= 60s) signed challenge from Cloud Function (§7).
     */
    suspend fun issueScannerChallenge(
        storeId: String,
        deviceId: String,
        action: String = "ANY",
        format: String = "QR",
    ): Result<ScannerChallenge> = runCatching {
        val data = cloudFunctions.issueScannerChallenge(storeId, deviceId, action, format)
        val challengeId = data["challengeId"]?.toString() ?: error("Missing challengeId")
        val nonce = data["nonce"]?.toString() ?: error("Missing nonce")
        val signature = data["signature"]?.toString() ?: error("Missing signature")
        val issuedAt = (data["issuedAtMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val expiresAt = (data["expiresAtMs"] as? Number)?.toLong() ?: (issuedAt + 45_000L)

        val payload = ScannerPayload(
            v = 1,
            challengeId = challengeId,
            storeId = storeId,
            deviceId = deviceId,
            nonce = nonce,
            sig = signature,
            exp = expiresAt,
            action = action,
        )
        ScannerChallenge(
            challengeId = challengeId,
            storeId = storeId,
            deviceId = deviceId,
            action = action,
            nonce = nonce,
            signature = signature,
            format = format,
            issuedAtMs = issuedAt,
            expiresAtMs = expiresAt,
            payloadJson = json.encodeToString(payload),
        )
    }

    fun parseScannerPayload(rawBarcode: String): Result<ScannerPayload> = runCatching {
        val parsed = json.decodeFromString<ScannerPayload>(rawBarcode)
        require(parsed.challengeId.isNotBlank() && parsed.nonce.isNotBlank() && parsed.sig.isNotBlank()) {
            "Static or malformed QR code rejected. Scan a live rotating Attendance Pro kiosk code."
        }
        parsed
    }

    /**
     * Submit attendance punch exclusively to Cloud Function `validatePunch` (§7).
     * Never writes attendanceEvents/summaries directly from the client.
     * When offline and eligible (non-scanner / GPS-only mode), queues in Room with idempotency key (§17).
     */
    suspend fun submitPunch(
        employeeId: String,
        storeId: String,
        eventType: PunchEventType,
        scannedPayload: ScannerPayload?,
        gps: GpsSnapshot?,
        allowOfflineQueue: Boolean,
    ): Result<String> {
        val eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val idempotencyKey = "${employeeId}_${eventType.value}_${System.currentTimeMillis() / 15_000L}_${UUID.randomUUID().toString().take(8)}"

        if (!networkMonitor.isOnline.value) {
            return if (allowOfflineQueue && scannedPayload == null) {
                offlinePunchDao.upsert(
                    OfflinePunchEntity(
                        eventId = eventId,
                        idempotencyKey = idempotencyKey,
                        employeeId = employeeId,
                        storeId = storeId,
                        eventType = eventType.value,
                        challengeId = "",
                        nonce = "",
                        deviceId = "",
                        gpsLat = gps?.latitude,
                        gpsLng = gps?.longitude,
                        gpsAccuracy = gps?.accuracyMeters,
                        gpsMocked = gps?.isMocked ?: false,
                        capturedAtMs = System.currentTimeMillis(),
                        syncStatus = "QUEUED",
                        serverMessage = "Queued offline (unverified until server validation)",
                    ),
                )
                Result.success("Offline: punch queued locally (unverified). It will submit for server validation on reconnect.")
            } else {
                Result.failure(
                    IllegalStateException(
                        "Network required to validate live scanner challenge. Static or offline scanner codes cannot be verified.",
                    ),
                )
            }
        }

        return runCatching {
            val payload = mutableMapOf<String, Any?>(
                "eventId" to eventId,
                "idempotencyKey" to idempotencyKey,
                "storeId" to storeId,
                "eventType" to eventType.value,
                "clientCapturedAtMs" to System.currentTimeMillis(),
            )
            if (scannedPayload != null) {
                payload["challengeId"] = scannedPayload.challengeId
                payload["nonce"] = scannedPayload.nonce
                payload["deviceId"] = scannedPayload.deviceId
                payload["signature"] = scannedPayload.sig
            }
            if (gps != null) {
                payload["gps"] = mapOf(
                    "latitude" to gps.latitude,
                    "longitude" to gps.longitude,
                    "accuracyMeters" to gps.accuracyMeters.toDouble(),
                    "isMocked" to gps.isMocked,
                )
            }
            val resp = cloudFunctions.validatePunch(payload)
            resp["message"]?.toString() ?: "${eventType.label} verified by server"
        }
    }

    /**
     * Syncs Room-queued offline punches when network returns (§17).
     * Flags stale punches (> maxOfflineHours) instead of silently marking verified.
     */
    suspend fun syncOfflineQueue(maxOfflineHours: Int = 12): Int {
        if (!networkMonitor.isOnline.value) return 0
        val pending = offlinePunchDao.getPendingPunches()
        var syncedCount = 0
        val now = System.currentTimeMillis()
        val staleThresholdMs = maxOfflineHours * 3_600_000L

        for (item in pending) {
            val isStale = (now - item.capturedAtMs) > staleThresholdMs
            offlinePunchDao.updateStatus(item.eventId, "SYNCING", "Submitting to backend...")
            val payload = mutableMapOf<String, Any?>(
                "eventId" to item.eventId,
                "idempotencyKey" to item.idempotencyKey,
                "storeId" to item.storeId,
                "eventType" to item.eventType,
                "offlineQueued" to true,
                "staleFlag" to isStale,
                "clientCapturedAtMs" to item.capturedAtMs,
            )
            if (item.gpsLat != null && item.gpsLng != null) {
                payload["gps"] = mapOf(
                    "latitude" to item.gpsLat,
                    "longitude" to item.gpsLng,
                    "accuracyMeters" to (item.gpsAccuracy?.toDouble() ?: 0.0),
                    "isMocked" to item.gpsMocked,
                )
            }
            val result = runCatching { cloudFunctions.validatePunch(payload) }
            result.fold(
                onSuccess = { resp ->
                    val status = if (isStale) "STALE" else "VERIFIED"
                    val msg = resp["message"]?.toString()
                        ?: if (isStale) "Flagged stale — requires admin review" else "Verified by server"
                    offlinePunchDao.updateStatus(item.eventId, status, msg)
                    syncedCount++
                },
                onFailure = { err ->
                    offlinePunchDao.updateStatus(
                        item.eventId,
                        "REJECTED",
                        err.message ?: "Rejected by backend validation",
                    )
                },
            )
        }
        return syncedCount
    }

    suspend fun recalculateDailyAttendance(storeId: String?, date: String): Result<String> = runCatching {
        val resp = cloudFunctions.calculateDailyAttendance(storeId, date)
        resp["message"]?.toString() ?: "Attendance recalculated for $date"
    }
}
