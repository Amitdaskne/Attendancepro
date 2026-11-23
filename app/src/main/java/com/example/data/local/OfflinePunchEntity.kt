package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_punches")
data class OfflinePunchEntity(
    @PrimaryKey val eventId: String,
    val idempotencyKey: String,
    val employeeId: String,
    val storeId: String,
    val eventType: String,
    val challengeId: String,
    val nonce: String,
    val deviceId: String,
    val gpsLat: Double?,
    val gpsLng: Double?,
    val gpsAccuracy: Float?,
    val gpsMocked: Boolean,
    val capturedAtMs: Long,
    /** QUEUED | SYNCING | STALE | REJECTED | VERIFIED */
    val syncStatus: String = "QUEUED",
    val serverMessage: String = "",
)
