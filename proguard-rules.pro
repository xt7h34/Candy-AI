# The libraries (Compose) bring their own rules. The app uses no reflection.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Vosk (offline wake word) talks to its native library through JNA
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
