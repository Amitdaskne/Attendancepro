package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [OfflinePunchEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AttendanceLocalDatabase : RoomDatabase() {
    abstract fun offlinePunchDao(): OfflinePunchDao
}
