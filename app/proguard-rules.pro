# ============================================================================
# Speye - Proguard / R8 Optimization & Obfuscation Rules
# ============================================================================

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# ----------------------------------------------------------------------------
# Kotlin Serialization
# ----------------------------------------------------------------------------
-keepclassmembers class **$$serializer {
    *** INSTANCE;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable class *;
}
-keepclassmembers class * {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ----------------------------------------------------------------------------
# Room Database & Architecture Components
# ----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.fl0w.speye.data.model.** { *; }
-keep class com.fl0w.speye.data.db.** { *; }
-dontwarn androidx.room.paging.**

# ----------------------------------------------------------------------------
# WorkManager
# ----------------------------------------------------------------------------
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ----------------------------------------------------------------------------
# Google Drive API & Google HTTP Client / Gson
# ----------------------------------------------------------------------------
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
    @com.google.api.client.util.Value <fields>;
}
-dontwarn com.google.api.client.**
-dontwarn com.google.common.**
-dontwarn org.apache.http.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# ----------------------------------------------------------------------------
# Google Play Services & Google Sign-In
# ----------------------------------------------------------------------------
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.common.api.** { *; }
-keep class com.google.android.gms.common.internal.safeparcel.SafeParcelable { *; }

# ----------------------------------------------------------------------------
# Google Play Billing
# ----------------------------------------------------------------------------
-keep class com.android.billingclient.api.** { *; }

# ----------------------------------------------------------------------------
# Google Mobile Ads (AdMob)
# ----------------------------------------------------------------------------
-keep public class com.google.android.gms.ads.** {
   public *;
}
-keep public class com.google.ads.** {
   public *;
}

# ----------------------------------------------------------------------------
# Coil Image Loader
# ----------------------------------------------------------------------------
-dontwarn coil3.**
-dontwarn coil.**
