# Octomil App ProGuard Rules
-keep class ai.octomil.client.** { *; }
-keep class ai.octomil.pairing.** { *; }
-keep class ai.octomil.config.** { *; }
-keep class ai.octomil.api.** { *; }
-keepattributes *Annotation*

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
