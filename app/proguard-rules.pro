# Kotlin reflection & serialization
-keepattributes *Annotation*,EnclosingMethod,Signature,InnerClasses

# Keep data classes used across module boundaries
-keep class com.laconical.player.core.model.** { *; }

# Amplituda native library — classes accessed via JNI reflection
-keep class linc.com.amplituda.** { *; }
-dontwarn linc.com.amplituda.**

# Coil custom fetcher/keyer registered reflectively in LaconicalApp
-keep class com.laconical.player.** extends coil3.fetch.Fetcher { *; }
-keep class com.laconical.player.** extends coil3.key.Keyer { *; }

# Android Visualizer accessed via audioSessionId at runtime
-keep class android.media.audiofx.Visualizer { *; }
