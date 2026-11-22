# R8 / ProGuard rules for MIMORA release builds.
# The Android Gradle plugin's default optimized rules are applied first.

# Preserve diagnostics so crashes remain actionable without exposing source.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose / Kotlin metadata and annotations.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
-dontwarn androidx.compose.**
-dontwarn kotlinx.coroutines.**

# Enums rely on values()/valueOf() at runtime (ThemeMode, MediaType, ...).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Model classes are built from org.json objects by explicit key names.
# Keeping them avoids any reflective access surprises after shrinking.
-keep class com.mimora.app.core.model.** { *; }
-keep class com.mimora.app.feature.backup.GoogleDriveFile { *; }

# Standard Android component / callback contracts.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# WorkManager instantiates its Room database implementation reflectively.
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.**

# Google Play Services ships its own consumer rules; silence noisy warnings only.
-dontwarn com.google.android.gms.**
