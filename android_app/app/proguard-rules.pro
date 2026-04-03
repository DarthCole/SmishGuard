# ProGuard / R8 Rules for SmishGuard
# ------------------------------------
# ProGuard (now R8) shrinks, optimizes, and obfuscates your code in release builds.
# These rules tell R8 what NOT to touch.

# ── Keep TensorFlow Lite classes (ML model needs these at runtime) ──
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# ── Keep Room database entities and DAOs ──
# Room uses reflection to map classes to database tables
-keep class com.smishguard.app.data.local.entity.** { *; }
-keep class com.smishguard.app.data.local.dao.** { *; }

# ── Prevent stripping of Kotlin metadata (needed for reflection) ──
-keep class kotlin.Metadata { *; }

# ── Security: Remove all Log calls in release builds ──
# This prevents sensitive SMS content from appearing in logcat
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
