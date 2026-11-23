package com.example.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import com.example.domain.model.GpsSnapshot
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot GPS capture for attendance punch verification (§8).
 * Strictly no continuous background tracking. Flags mocked locations.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedClient: FusedLocationProviderClient,
) {
    @SuppressLint("MissingPermission")
    suspend fun captureSingleLocation(): Result<GpsSnapshot> = runCatching {
        val tokenSource = CancellationTokenSource()
        val freshLocation: Location? = fusedClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
            .await()

        val location = freshLocation ?: fusedClient.lastLocation.await()
            ?: error("Unable to obtain GPS fix. Ensure Location Services are enabled.")

        val mocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }

        GpsSnapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            isMocked = mocked,
            capturedAtMs = System.currentTimeMillis(),
        )
    }
}
