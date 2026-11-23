package com.example.data.repository

import com.example.data.remote.CloudFunctionsDataSource
import com.example.data.remote.RtdbPaths
import com.example.domain.model.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrganizationRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val cloudFunctions: CloudFunctionsDataSource,
) {
    fun observeOrganization(): Flow<OrganizationNode> = callbackFlow {
        val ref = database.getReference(RtdbPaths.ORGANIZATION)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(if (snapshot.exists()) RtdbMappers.toOrganization(snapshot) else OrganizationNode())
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(OrganizationNode())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeStores(session: UserSession): Flow<Result<List<StoreRecord>>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.STORES)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val all = snapshot.children.map { RtdbMappers.toStore(it) }
                val scoped = if (session.role == UserRole.SUPER_ADMIN) {
                    all
                } else {
                    all.filter { session.canAccessStore(it.storeId) }
                }.sortedBy { it.profile.name.lowercase() }
                trySend(Result.success(scoped))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun saveStore(store: StoreRecord, isNew: Boolean): Result<String> = runCatching {
        val ref = database.getReference(RtdbPaths.STORES)
        val storeId = if (isNew || store.storeId.isBlank()) {
            ref.push().key ?: error("Failed to allocate store ID")
        } else {
            store.storeId
        }
        val payload = mutableMapOf<String, Any?>(
            "storeId" to storeId,
            "profile" to mapOf(
                "name" to store.profile.name.trim(),
                "code" to store.profile.code.trim().uppercase(),
                "logoUrl" to store.profile.logoUrl,
                "contact" to mapOf(
                    "phone" to store.profile.contact.phone.trim(),
                    "email" to store.profile.contact.email.trim(),
                ),
            ),
            "address" to mapOf(
                "building" to store.address.building.trim(),
                "locality" to store.address.locality.trim(),
                "city" to store.address.city.trim(),
                "district" to store.address.district.trim(),
                "state" to store.address.state.trim(),
                "postalCode" to store.address.postalCode.trim(),
                "country" to store.address.country.trim(),
            ),
            "location" to mapOf(
                "latitude" to store.location.latitude,
                "longitude" to store.location.longitude,
                "radiusMeters" to store.location.radiusMeters,
            ),
            "settings" to mapOf(
                "openingHours" to store.settings.openingHours.trim(),
                "timezone" to store.settings.timezone.trim(),
                "weeklyOff" to store.settings.weeklyOff,
                "gpsMode" to store.settings.gpsMode,
                "maxGpsAccuracyMeters" to store.settings.maxGpsAccuracyMeters,
                "scannerTtlSeconds" to store.settings.scannerTtlSeconds.coerceIn(15, 60),
            ),
            "status" to store.status,
            "updatedAt" to ServerValue.TIMESTAMP,
        )
        if (isNew) {
            payload["createdAt"] = ServerValue.TIMESTAMP
        }
        ref.child(storeId).updateChildren(payload).await()

        runCatching {
            cloudFunctions.writeAuditLog(
                mapOf(
                    "action" to if (isNew) "STORE_CREATED" else "STORE_UPDATED",
                    "target" to "stores/$storeId",
                    "storeId" to storeId,
                    "afterSummary" to "${store.profile.name} (${store.profile.code})",
                    "reason" to if (isNew) "New store created" else "Store details updated",
                ),
            )
        }
        storeId
    }

    /**
     * Deactivate or reactivate a store WITHOUT deleting historical attendance (§4).
     */
    suspend fun setStoreStatus(storeId: String, active: Boolean, reason: String): Result<Unit> = runCatching {
        val newStatus = if (active) "ACTIVE" else "INACTIVE"
        database.getReference(RtdbPaths.store(storeId))
            .updateChildren(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to ServerValue.TIMESTAMP,
                ),
            )
            .await()

        runCatching {
            cloudFunctions.writeAuditLog(
                mapOf(
                    "action" to if (active) "STORE_ACTIVATED" else "STORE_DEACTIVATED",
                    "target" to "stores/$storeId",
                    "storeId" to storeId,
                    "afterSummary" to "status=$newStatus",
                    "reason" to reason.ifBlank { "Status updated" },
                ),
            )
        }
    }

    suspend fun updateBranding(branding: OrganizationBranding): Result<Unit> = runCatching {
        database.getReference(RtdbPaths.ORG_BRANDING)
            .updateChildren(
                mapOf(
                    "appTitle" to branding.appTitle.trim(),
                    "logoUrl" to branding.logoUrl,
                    "primaryColorHex" to branding.primaryColorHex.trim(),
                    "accentColorHex" to branding.accentColorHex.trim(),
                    "tagline" to branding.tagline.trim(),
                    "updatedAt" to ServerValue.TIMESTAMP,
                ),
            )
            .await()
        runCatching {
            cloudFunctions.writeAuditLog(
                mapOf(
                    "action" to "BRANDING_UPDATED",
                    "target" to "organization/branding",
                    "afterSummary" to "title=${branding.appTitle}, accent=${branding.accentColorHex}",
                ),
            )
        }
    }

    suspend fun updateOrganizationSettings(
        profile: OrganizationProfile,
        settings: OrganizationSettings,
    ): Result<Unit> = runCatching {
        database.getReference(RtdbPaths.ORGANIZATION)
            .updateChildren(
                mapOf(
                    "profile/name" to profile.name.trim(),
                    "profile/legalName" to profile.legalName.trim(),
                    "profile/supportEmail" to profile.supportEmail.trim(),
                    "profile/supportPhone" to profile.supportPhone.trim(),
                    "profile/timezone" to profile.timezone.trim(),
                    "profile/currency" to profile.currency.trim(),
                    "settings/defaultGraceMinutes" to settings.defaultGraceMinutes,
                    "settings/halfDayThresholdMinutes" to settings.halfDayThresholdMinutes,
                    "settings/absentThresholdMinutes" to settings.absentThresholdMinutes,
                    "settings/overtimeRequiresApproval" to settings.overtimeRequiresApproval,
                    "settings/maxOfflineQueueHours" to settings.maxOfflineQueueHours,
                    "settings/gpsRetentionDays" to settings.gpsRetentionDays,
                    "updatedAt" to ServerValue.TIMESTAMP,
                ),
            )
            .await()
        runCatching {
            cloudFunctions.writeAuditLog(
                mapOf(
                    "action" to "ORG_SETTINGS_UPDATED",
                    "target" to "organization/settings",
                    "afterSummary" to "grace=${settings.defaultGraceMinutes}m, halfDay=${settings.halfDayThresholdMinutes}m",
                ),
            )
        }
    }
}
