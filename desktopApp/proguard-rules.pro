# Nucleus supplies Compose, Kotlin, coroutines, serialization and JNA base rules.
# Retain useful crash locations; archive mapping.txt with every release.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-printmapping build/reports/proguard/mapping.txt
-printseeds build/reports/proguard/seeds.txt
-printusage build/reports/proguard/usage.txt

# 7.9.1 narrows Okio.buffer(Source) from BufferedSource to RealBufferedSource incorrectly,
# producing VerifyError for the branch that returns an existing BufferedSource.
# Keep the other optimizations, shrinking and obfuscation enabled.
-optimizations !method/specialization/returntype

# Persisted settings and polymorphic JSON must retain their existing serialization contracts.
-keep @kotlinx.serialization.Serializable class me.rerere.** { *; }

# JNI symbols, native callbacks and reflection-based JNA structures.
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class * extends com.sun.jna.Union { *; }
-keep class dev.nucleusframework.webview.** { *; }
-keep class com.dokar.quickjs.** { *; }

# notification-common discovers platform centers by name; JNI calls bridge callbacks by name as well.
-keepnames class dev.nucleusframework.notification.NotificationCenter
-keepnames class dev.nucleusframework.notification.windows.WindowsNotificationCenter
-keep class dev.nucleusframework.notification.macos.NativeMacNotificationBridge { *; }
-keep class dev.nucleusframework.notification.windows.NativeWindowsNotificationBridge { *; }

# PlatformBootstrap finds this optional Windows integration and its method through reflection.
-keep class dev.nucleusframework.launcher.windows.WindowsJumpListManager {
    public static ** INSTANCE;
    public *** setProcessAppId(java.lang.String);
}
# The launcher DLL also looks up its shared taskbar callback interface by JNI name.
-keep interface dev.nucleusframework.launcher.windows.ThumbBarClickListener { *; }

# Tao is the UI dispatcher, including when dependencies still bring in coroutines-swing.
# Nucleus's ProGuard task does not automatically read dependency META-INF/proguard files.
-keep class dev.nucleusframework.window.tao.dispatch.TaoMainDispatcherFactory { *; }
-keepnames class dev.nucleusframework.window.tao.dispatch.**
-keep class androidx.lifecycle.MainDispatcherChecker { *; }

# JavaFX discovers platform, graphics and media implementations by name/from native code.
-keep class javafx.** { *; }
-keep class com.sun.javafx.** { *; }
-keep class com.sun.glass.** { *; }
-keep class com.sun.prism.** { *; }
-keep class com.sun.scenario.** { *; }
-keep class com.sun.media.** { *; }

# Room's generated implementation and bundled SQLite JNI.
-keep class * extends androidx.room3.RoomDatabase { *; }
-keep class androidx.sqlite.** { *; }

# Preserve relative resource paths and service discovery across the separate output JARs.
-keepdirectories
-adaptresourcefilecontents META-INF/services/**
# Service types and their registered provider classes are kept by generated/proguard/services.pro.

# Optional TLS providers: desktop uses the JDK TLS implementation.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Optional logging backends/servlet integrations are not present in the desktop application.
-dontwarn io.github.oshai.kotlinlogging.logback.**
-dontwarn org.apache.commons.logging.impl.**
-dontwarn org.apache.commons.logging.jakarta.**

# KScan's unreferenced camera UI is removed by shrinking; only ZXing image decoding is used on desktop.
-dontwarn org.bytedeco.**
-dontwarn androidx.compose.material.icons.**

# These calls use signature-polymorphic MethodHandle/VarHandle methods available in JDK 21.
-dontwarn com.google.common.hash.ChecksumHashFunction$ChecksumMethodHandles
-dontwarn com.google.common.hash.LittleEndianByteArray$VarHandleLittleEndianBytes$*
-dontwarn com.google.common.util.concurrent.AbstractFutureState$VarHandleAtomicHelper
-dontwarn org.apache.pdfbox.io.IOUtils
