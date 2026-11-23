package com.example.data.repository

import com.example.data.remote.CloudFunctionsDataSource
import com.example.data.remote.RtdbPaths
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Loading : SessionState
    data object Unauthenticated : SessionState
    data class Disabled(val email: String) : SessionState
    data class Authenticated(val session: UserSession) : SessionState
    data class Error(val message: String) : SessionState
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val cloudFunctions: CloudFunctionsDataSource,
) {
    companion object {
        const val SUPER_ADMIN_EMAIL = "amitdaskishanganj@gmail.com"
    }

    fun observeAuthUid(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.uid)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): Result<UserSession> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        resolveCurrentSession(forceTokenRefresh = true)
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun resolveCurrentSession(forceTokenRefresh: Boolean = true): UserSession {
        val user = auth.currentUser ?: error("Not signed in")
        var tokenResult = user.getIdToken(forceTokenRefresh).await()
        var claims = tokenResult.claims

        val isDesignatedSuperAdmin = user.email.equals(SUPER_ADMIN_EMAIL, ignoreCase = true)
        var claimRole = UserRole.fromClaim(claims["role"]?.toString())

        // Auto-bootstrap server custom claims for the designated Super Admin on first login
        if (claimRole == null && isDesignatedSuperAdmin) {
            runCatching {
                cloudFunctions.bootstrapSuperAdmin()
                tokenResult = user.getIdToken(true).await()
                claims = tokenResult.claims
                claimRole = UserRole.fromClaim(claims["role"]?.toString())
            }
            if (claimRole == null) {
                claimRole = UserRole.SUPER_ADMIN
            }
        }

        val resolvedRole = claimRole
            ?: error("Account role claim is missing. Contact your Super Admin ($SUPER_ADMIN_EMAIL) to provision access.")

        val claimDisabled = (claims["disabled"] as? Boolean) == true
        val claimEmployeeId = claims["employeeId"]?.toString().orEmpty()
        val claimStoreIds = when (val rawStores = claims["storeIds"]) {
            is List<*> -> rawStores.mapNotNull { it?.toString() }
            is Map<*, *> -> rawStores.filterValues { it == true }.keys.mapNotNull { it?.toString() }
            else -> emptyList()
        }

        val userSnap = runCatching {
            database.getReference(RtdbPaths.user(user.uid)).get().await()
        }.getOrNull()

        val userRecord = userSnap?.takeIf { it.exists() }?.let { RtdbMappers.toUser(it) }
        val isDisabled = claimDisabled || (userRecord != null && !userRecord.isActive)

        val mergedStoreIds = (claimStoreIds + (userRecord?.storeIds ?: emptyList())).distinct()
        val employeeId = claimEmployeeId.ifBlank {
            userRecord?.employeeId?.ifBlank { null }
                ?: if (resolvedRole == UserRole.SUPER_ADMIN) {
                    "ADMIN-${user.uid.take(6).uppercase()}"
                } else {
                    user.uid
                }
        }

        // Best-effort FCM token sync
        syncFcmToken(user.uid)

        return UserSession(
            uid = user.uid,
            email = user.email ?: userRecord?.profile?.email.orEmpty(),
            displayName = userRecord?.profile?.fullName?.ifBlank { user.displayName.orEmpty() }
                ?: user.displayName.orEmpty().ifBlank { user.email.orEmpty().substringBefore("@") },
            photoUrl = userRecord?.profile?.photoUrl.orEmpty(),
            role = resolvedRole,
            employeeId = employeeId,
            assignedStoreIds = mergedStoreIds,
            primaryStoreId = mergedStoreIds.firstOrNull().orEmpty(),
            disabled = isDisabled,
        )
    }

    private suspend fun syncFcmToken(uid: String) {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            val tokenKey = token.hashCode().toUInt().toString(16)
            database.getReference("${RtdbPaths.user(uid)}/fcmTokens/$tokenKey")
                .setValue(
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "updatedAt" to ServerValue.TIMESTAMP,
                    ),
                )
                .await()
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
