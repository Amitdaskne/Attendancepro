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
class EmployeeRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val cloudFunctions: CloudFunctionsDataSource,
) {
    fun observeEmployees(
        session: UserSession,
        selectedStoreId: String?,
    ): Flow<Result<List<EmployeeRecord>>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.EMPLOYEES)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val all = snapshot.children.map { RtdbMappers.toEmployee(it) }
                val filtered = all.filter { emp ->
                    val storeId = emp.currentAssignment.storeId
                    val permitted = when (session.role) {
                        UserRole.SUPER_ADMIN -> true
                        UserRole.STORE_ADMIN -> session.canAccessStore(storeId)
                        UserRole.MANAGER -> session.canAccessStore(storeId) &&
                            (emp.currentAssignment.managerId == session.uid || emp.currentAssignment.managerId.isBlank())
                        UserRole.EMPLOYEE -> emp.employeeId == session.employeeId
                    }
                    val matchesStore = selectedStoreId.isNullOrBlank() || storeId == selectedStoreId
                    permitted && matchesStore
                }.sortedBy { it.profile.fullName.lowercase() }
                trySend(Result.success(filtered))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeEmployee(employeeId: String): Flow<Result<EmployeeRecord?>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.employee(employeeId))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(Result.success(if (snapshot.exists()) RtdbMappers.toEmployee(snapshot) else null))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeUsers(session: UserSession): Flow<Result<List<UserRecord>>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.USERS)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val all = snapshot.children.map { RtdbMappers.toUser(it) }
                val scoped = if (session.role == UserRole.SUPER_ADMIN) {
                    all
                } else {
                    all.filter { user -> user.storeIds.any { session.canAccessStore(it) } }
                }.sortedBy { it.profile.fullName.lowercase() }
                trySend(Result.success(scoped))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeDepartments(): Flow<List<Department>> = callbackFlow {
        val ref = database.getReference(RtdbPaths.DEPARTMENTS)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.map { RtdbMappers.toDepartment(it) })
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Users are provisioned exclusively through Cloud Function `provisionUser` (§3).
     */
    suspend fun provisionUser(
        fullName: String,
        email: String,
        tempPassword: String,
        phone: String,
        role: UserRole,
        storeIds: List<String>,
        departmentName: String,
        designation: String,
        shiftId: String,
        managerId: String,
        salaryType: SalaryType,
        baseSalary: Double,
        allowances: Double,
        overtimeHourlyRate: Double,
        photoUrl: String,
    ): Result<Map<String, Any?>> = runCatching {
        cloudFunctions.provisionUser(
            mapOf(
                "fullName" to fullName.trim(),
                "email" to email.trim().lowercase(),
                "password" to tempPassword,
                "phone" to phone.trim(),
                "role" to role.claimValue,
                "storeIds" to storeIds,
                "departmentName" to departmentName.trim(),
                "designation" to designation.trim(),
                "shiftId" to shiftId,
                "managerId" to managerId,
                "photoUrl" to photoUrl,
                "salaryPrivate" to mapOf(
                    "salaryType" to salaryType.value,
                    "baseSalary" to baseSalary,
                    "allowances" to allowances,
                    "overtimeHourlyRate" to overtimeHourlyRate,
                ),
            ),
        )
    }

    suspend fun updateUserClaimsAndStatus(
        targetUid: String,
        role: UserRole,
        storeIds: List<String>,
        disabled: Boolean,
        reason: String,
    ): Result<Unit> = runCatching {
        cloudFunctions.setCustomClaims(
            mapOf(
                "targetUid" to targetUid,
                "role" to role.claimValue,
                "storeIds" to storeIds,
                "disabled" to disabled,
                "reason" to reason,
            ),
        )
    }

    /**
     * Transfer preserves historical attendance under original store and updates only currentAssignment (§4).
     */
    suspend fun transferEmployee(
        employeeId: String,
        fromStoreId: String,
        toStoreId: String,
        newShiftId: String,
        newManagerId: String,
        transferDate: String,
        reason: String,
    ): Result<Unit> = runCatching {
        cloudFunctions.transferEmployee(
            mapOf(
                "employeeId" to employeeId,
                "fromStoreId" to fromStoreId,
                "toStoreId" to toStoreId,
                "shiftId" to newShiftId,
                "managerId" to newManagerId,
                "transferDate" to transferDate,
                "reason" to reason.trim(),
            ),
        )
    }

    /**
     * Non-privileged profile updates allowed for employees themselves (photo, phone, address, emergency contact).
     */
    suspend fun updateOwnNonPrivilegedProfile(
        uid: String,
        employeeId: String,
        phone: String,
        address: String,
        photoUrl: String,
        emergencyContact: EmergencyContact,
    ): Result<Unit> = runCatching {
        database.getReference("${RtdbPaths.employee(employeeId)}/profile")
            .updateChildren(
                mapOf(
                    "phone" to phone.trim(),
                    "address" to address.trim(),
                    "photoUrl" to photoUrl,
                    "emergencyContact/name" to emergencyContact.name.trim(),
                    "emergencyContact/relation" to emergencyContact.relation.trim(),
                    "emergencyContact/phone" to emergencyContact.phone.trim(),
                    "updatedAt" to ServerValue.TIMESTAMP,
                ),
            )
            .await()

        database.getReference("${RtdbPaths.user(uid)}/profile")
            .updateChildren(
                mapOf(
                    "phone" to phone.trim(),
                    "photoUrl" to photoUrl,
                    "updatedAt" to ServerValue.TIMESTAMP,
                ),
            )
            .await()
    }

    suspend fun saveDepartment(name: String, code: String, storeId: String): Result<Unit> = runCatching {
        val ref = database.getReference(RtdbPaths.DEPARTMENTS)
        val id = ref.push().key ?: error("Unable to allocate department ID")
        ref.child(id).setValue(
            mapOf(
                "departmentId" to id,
                "name" to name.trim(),
                "code" to code.trim().uppercase(),
                "storeId" to storeId,
                "status" to "ACTIVE",
                "createdAt" to ServerValue.TIMESTAMP,
            ),
        ).await()
    }
}
