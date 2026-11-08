# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# Rust FFI through JNA. JNA finds Structure fields, Callback methods and the
# Library interface by reflection, so none of it may be renamed or removed.
# The JNA aar ships no consumer rules
-dontwarn java.awt.**
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }

# UniFFI generated bindings (db_service.kt). Kept whole: the Structure
# subclasses (RustBuffer, UniffiRustCallStatus, UniffiForeignFuture*) depend on
# their @Structure.FieldOrder field names, and the callback interfaces are
# called from native code
-keep class onekeepass.mobile.ffi.** { *; }

# react-native-reanimated and react-native-worklets ship these rules in their
# android/proguard-rules.pro but do not apply them as consumer rules, so the
# app has to
-keep class com.swmansion.reanimated.** { *; }
-keep class com.swmansion.worklets.** { *; }
-keep class com.facebook.react.turbomodule.** { *; }
-keep class com.facebook.react.fabric.** { *; }

# Tink (pulled in by androidx.security:security-crypto) references Error Prone
# annotations that are compile-time only and not needed at runtime. The Play
# build gets them through another dependency (most likely vision-camera's); the
# FOSS apk build excludes vision-camera, so R8 reports them as missing classes
-dontwarn com.google.errorprone.annotations.**
