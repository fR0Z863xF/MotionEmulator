# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class com.github.aachartmodel.aainfographics.** { *; }
# Suppress optional runtime bindings referenced by transitive Netty/SLF4J code paths.
# These implementations are not packaged/used in the Android app.
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn reactor.blockhound.integration.BlockHoundIntegration

# ------------------------------
# Kotlinx Serialization
# ------------------------------
# Keep serializer() methods and Companion objects for serializable models that are
# looked up reflectively (serializer<T>(), ProtoBuf/JSON, Ktor WebSocket converter).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.zhufucdev.motion_emulator.** {
    *** Companion;
    public static ** serializer(...);
}
-keepclassmembers class com.zhufucdev.motion_emulator.**$Companion {
    public ** serializer(...);
}
-keepclassmembers class com.zhufucdev.me.stub.** {
    *** Companion;
    public static ** serializer(...);
}
-keepclassmembers class com.zhufucdev.me.stub.**$Companion {
    public ** serializer(...);
}

# ------------------------------
# WorkManager
# ------------------------------
# Workers are instantiated by class name at runtime.
-keep class * extends androidx.work.ListenableWorker {
    <init>(...);
}

# ------------------------------
# SpongyCastle (BouncyCastle for Android)
# ------------------------------
# Security providers load implementations by class name.
-keep class org.spongycastle.** { *; }
-dontwarn org.spongycastle.**
