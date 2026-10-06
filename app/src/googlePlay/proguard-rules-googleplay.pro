# ============================================================================
# Speye - Google Play Flavor Specific R8 / ProGuard Rules
# ============================================================================

# ----------------------------------------------------------------------------
# WorkManager (Google Play background sync)
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
