# ==============================================================================
# Attendance Pro — ProGuard / R8 Keep Rules (§20)
# ==============================================================================

# Preserve source file and line numbers for readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ==============================================================================
# 1. Dagger / Hilt Dependency Injection
# ==============================================================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
}

# ==============================================================================
# 2. Firebase Realtime Database POJOs & Field Reflection
# ==============================================================================
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class com.example.domain.model.** {
    *;
}
-keep class com.example.domain.model.** { *; }
-keep class com.google.firebase.database.** { *; }
-keep class com.google.firebase.functions.** { *; }
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.auth.** { *; }

# ==============================================================================
# 3. Kotlin Parcelable, Serializable & kotlinx.serialization
# ==============================================================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==============================================================================
# 4. CameraX
# ==============================================================================
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }
-keepclassmembers class * extends androidx.camera.core.impl.Config { *; }

# ==============================================================================
# 5. ML Kit Barcode Scanning & ZXing
# ==============================================================================
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.zxing.** { *; }

# ==============================================================================
# 6. Cloudinary / OkHttp / Retrofit / Moshi Reflection Serialization
# ==============================================================================
-keep class com.cloudinary.** { *; }
-keepattributes Exceptions,InnerClasses,EnclosingMethod
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ==============================================================================
# 7. Room Database Entities & DAOs
# ==============================================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
