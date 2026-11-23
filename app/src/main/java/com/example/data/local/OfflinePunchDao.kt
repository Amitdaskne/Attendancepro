package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflinePunchDao {

    @Query("SELECT * FROM offline_punches ORDER BY capturedAtMs DESC")
    fun observeAll(): Flow<List<OfflinePunchEntity>>

    @Query("SELECT * FROM offline_punches WHERE syncStatus IN ('QUEUED', 'STALE') ORDER BY capturedAtMs ASC")
    suspend fun getPendingPunches(): List<OfflinePunchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(punch: OfflinePunchEntity)

    @Query("UPDATE offline_punches SET syncStatus = :status, serverMessage = :message WHERE eventId = :eventId")
    suspend fun updateStatus(eventId: String, status: String, message: String)

    @Query("DELETE FROM offline_punches WHERE syncStatus = 'VERIFIED'")
    suspend fun pruneVerified()
}
