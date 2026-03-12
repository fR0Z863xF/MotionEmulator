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
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn reactor.blockhound.integration.BlockHoundIntegration

# Suppress optional Netty tcnative and logging/Jetty classes not packaged in Android
-dontwarn io.netty.internal.tcnative.**
-dontwarn java.lang.management.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

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

# ------------------------------
# AMap SDK
# ------------------------------
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**

# ------------------------------
# Ktor Application module reflection (embeddedServer)
# ------------------------------
-keepclassmembers class com.zhufucdev.motion_emulator.provider.SchedulerKt {
    public static void eventServer(io.ktor.server.application.Application);
}

# ------------------------------
# Ktor keep all (avoid reflection issues under R8)
# ------------------------------
-keep class io.ktor.** { *; }
-keep class io.ktor.server.netty.EngineMain { *; }
-keep class io.ktor.server.config.HoconConfigLoader { *; }
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { *; }


-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep class kotlin.text.RegexOption { *; }
-dontwarn io.ktor.**

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-keep class io.netty.** { *; }

-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.google.protobuf.**
-dontwarn com.google.protobuf.nano.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn lzma.sdk.**
-dontwarn net.jpountz.**
-dontwarn org.jboss.marshalling.**
-dontwarn reactor.blockhound.**
-dontwarn com.oracle.svm.**
-dontwarn sun.security.x509.**
-dontwarn io.netty.pkitesting.**
-dontwarn jdk.jfr.**
-dontwarn org.osgi.annotation.bundle.**