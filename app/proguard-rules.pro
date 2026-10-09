# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.nearbyspot.data.remote.dto.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }

# Debug diagnostics must not be emitted by minified production builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
