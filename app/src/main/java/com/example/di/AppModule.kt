package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.local.AttendanceLocalDatabase
import com.example.data.local.OfflinePunchDao
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase = FirebaseDatabase.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideLocalDatabase(@ApplicationContext context: Context): AttendanceLocalDatabase =
        Room.databaseBuilder(
            context,
            AttendanceLocalDatabase::class.java,
            "attendance_pro_local.db",
        ).fallbackToDestructiveMigration(dropAllTables = true).build()

    @Provides
    fun provideOfflinePunchDao(db: AttendanceLocalDatabase): OfflinePunchDao =
        db.offlinePunchDao()

    @Provides
    @Singleton
    fun provideFusedLocationClient(
        @ApplicationContext context: Context,
    ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
}
