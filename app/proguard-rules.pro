# Octomil App ProGuard Rules
-keep class ai.octomil.client.** { *; }
-keep class ai.octomil.ui.** { *; }
-keepattributes *Annotation*

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
