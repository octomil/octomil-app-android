# Octomil App ProGuard Rules
-keep class ai.octomil.client.** { *; }
-keep class ai.octomil.pairing.** { *; }
-keep class ai.octomil.config.** { *; }
-keep class ai.octomil.api.** { *; }
-keepattributes *Annotation*

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Google Tink (crypto) — suppress missing errorprone annotations
-dontwarn com.google.errorprone.annotations.**
