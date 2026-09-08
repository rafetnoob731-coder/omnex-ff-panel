-keep class com.omnex.ffpanel.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepclassmembers class * {
    native <methods>;
}