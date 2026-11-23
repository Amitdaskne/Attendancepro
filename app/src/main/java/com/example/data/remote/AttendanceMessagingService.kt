package com.example.data.remote

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.AttendanceProApp
import com.example.MainActivity
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM Push Notification handler (§16) for punches, late warnings, leave/correction
 * status updates, shift changes, announcements, and payroll notifications.
 */
class AttendanceMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val tokenKey = token.hashCode().toUInt().toString(16)
        FirebaseDatabase.getInstance()
            .getReference("${RtdbPaths.user(uid)}/fcmTokens/$tokenKey")
            .setValue(
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to ServerValue.TIMESTAMP,
                ),
            )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val category = message.data["category"] ?: "attendance"
        val channelId = when (category) {
            "leave", "correction" -> AttendanceProApp.CHANNEL_APPROVALS
            "announcement", "payroll" -> AttendanceProApp.CHANNEL_ANNOUNCEMENTS
            else -> AttendanceProApp.CHANNEL_ATTENDANCE_ALERTS
        }

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_category", category)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}
