package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AttendanceProApp : Application() {

    override fun onCreate() {
        super.onCreate()
        configureRealtimeDatabase()
        createNotificationChannels()
    }

    private fun configureRealtimeDatabase() {
        runCatching {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_ATTENDANCE_ALERTS,
                    "Attendance & Punch Alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Check-in/out confirmations, late warnings, and shift updates"
                },
                NotificationChannel(
                    CHANNEL_APPROVALS,
                    "Leave & Correction Approvals",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Status notifications for leave applications and punch corrections"
                },
                NotificationChannel(
                    CHANNEL_ANNOUNCEMENTS,
                    "Announcements & Payroll",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Organization announcements and salary slip notifications"
                },
            )
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }

    companion object {
        const val CHANNEL_ATTENDANCE_ALERTS = "attendance_alerts"
        const val CHANNEL_APPROVALS = "approvals"
        const val CHANNEL_ANNOUNCEMENTS = "announcements"
    }
}
