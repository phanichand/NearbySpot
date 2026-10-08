# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.nearbyspot.data.remote.dto.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }
