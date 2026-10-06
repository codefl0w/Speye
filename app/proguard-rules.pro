# ============================================================================
# Speye - Proguard / R8 Optimization & Obfuscation Rules (Shared / Core)
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
}
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    <fields>;
}

# ----------------------------------------------------------------------------
# Room Database & Architecture Components
# ----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { <fields>; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class com.fl0w.speye.data.model.** { <fields>; }
-dontwarn androidx.room.paging.**

# ----------------------------------------------------------------------------
# Coil Image Loader
# ----------------------------------------------------------------------------
-dontwarn coil3.**
-dontwarn coil.**

# ----------------------------------------------------------------------------
# Androidx Credentials
# ----------------------------------------------------------------------------
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
