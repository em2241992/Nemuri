-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn coil3.PlatformContext

-keep class org.koitharu.kotatsu.core.exceptions.* { *; }
-keep class org.koitharu.kotatsu.core.prefs.ScreenshotsPolicy { *; }
-keep class org.jsoup.parser.Tag
-keep class org.jsoup.internal.StringUtil

# ============================================================
# Native JNI (libjxldec.so, AVIF, QuickJS, Zstd) & Image Decoders
# ============================================================
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class org.koitharu.kotatsu.core.image.JxlDecoder { *; }
-keep class org.aomedia.avif.android.** { *; }
-dontwarn org.aomedia.avif.android.**
-keep class com.davemorrissey.labs.subscaleview.** { *; }
-keep class * implements com.davemorrissey.labs.subscaleview.decoder.ImageDecoder {
    public <init>(...);
    *;
}
-keep class * implements com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder {
    public <init>(...);
    *;
}

# ============================================================
# Shizuku user service, AIDL & silent installer receivers
# ============================================================
-keep class org.koitharu.kotatsu.extensions.install.** { *; }
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep interface moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# ============================================================
# LNReader / Novel Plugin Host & WebView @JavascriptInterface
# ============================================================
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class org.koitharu.kotatsu.lnreader.** { *; }
-keep interface org.koitharu.kotatsu.lnreader.** { *; }
-keep class ireader.** { *; }
-keep interface ireader.** { *; }
-dontwarn ireader.**

# ============================================================
# Mihon Extension Support
# Extensions are separate APKs loaded at runtime via
# ChildFirstPathClassLoader. They depend on these host classes.
# ============================================================

-keepattributes Signature
-keepattributes Annotation
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# Tachiyomi / Mihon API classes
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keepclassmembers class eu.kanade.tachiyomi.** {
    public <init>(...);
    public protected *;
}
-keep class tachiyomi.core.common.** { *; }
-keep interface tachiyomi.core.common.** { *; }
-keepclassmembers class tachiyomi.core.common.** {
    public <init>(...);
    public protected *;
}

# Injekt dependency injection (used by extensions via injectLazy)
-keep class uy.kohesive.injekt.** { *; }
-keep interface uy.kohesive.injekt.** { *; }
-keepclassmembers class uy.kohesive.injekt.** {
    public <init>(...);
    public protected *;
}

# RxJava (used by legacy extension API)
-keep class rx.** { *; }
-keep interface rx.** { *; }
-dontwarn rx.**

# QuickJS (used by Mihon's JavaScriptEngine and directly by some extensions)
-keep class app.cash.quickjs.** { *; }
-keepclassmembers class app.cash.quickjs.** {
    public <init>(...);
    public protected *;
}

# OkHttp and Okio (used by extensions)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepclassmembers class okhttp3.** {
    public <init>(...);
}
-keep class okio.** { *; }
-keep interface okio.** { *; }
-dontwarn okio.**
-dontwarn okhttp3.**

# zstd JNI (okhttp3.zstd CompressionInterceptor)
-keep class com.squareup.zstd.** { *; }

# UniFile (used by Mihon DiskUtil signatures)
-keep class com.hippo.unifile.** { *; }
-keep interface com.hippo.unifile.** { *; }
-dontwarn com.hippo.unifile.**

# Mihon bridge and model classes
-keep class org.koitharu.kotatsu.mihon.** { *; }
-keepclassmembers class org.koitharu.kotatsu.mihon.** {
    public <init>(...);
    public protected *;
}
-keep class org.koitharu.kotatsu.mihon.compat.** { *; }

# Jsoup (used by ParsedHttpSource)
-keep class org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** {
    public <init>(...);
}
-dontwarn com.google.re2j.**

# kotlinx.serialization (used by some extensions)
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
}
-keepclassmembers class **$$serializer {
    *** INSTANCE;
}

# xmlutil (nl.adaptivity.xmlutil) — used by extensions that parse XML manga sites
-keep class nl.adaptivity.xmlutil.** { *; }
-keep interface nl.adaptivity.xmlutil.** { *; }
-keepclassmembers class nl.adaptivity.xmlutil.** { *; }
-keep public enum nl.adaptivity.xmlutil.EventType { *; }
-dontwarn nl.adaptivity.xmlutil.**

# Dalvik ClassLoader (used by ChildFirstPathClassLoader)
-keep class dalvik.system.** { *; }
-dontwarn dalvik.system.**

# Application class (Injekt injects Application instances)
-keep class android.app.Application { *; }
-keepclassmembers class * extends android.app.Application {
    public <init>(...);
}

# SharedPreferences (ConfigurableSource uses it)
-keep class android.content.SharedPreferences { *; }
-keep interface android.content.SharedPreferences$** { *; }

# Kotlin stdlib
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-dontwarn kotlin.**

# Kotlin LazyKt
-keep class kotlin.LazyKt { *; }
-keep class kotlin.LazyKt__LazyJVMKt { *; }
-keep class kotlin.LazyKt__LazyKt { *; }
-keep class kotlin.SynchronizedLazyImpl { *; }
-keep class kotlin.UnsafeLazyImpl { *; }

# Kotlin reflection
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Kotlin coroutines (extensions-lib 1.5+)
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# AndroidX Preference (ConfigurableSource settings screen)
-keep class androidx.preference.** { *; }
-keep interface androidx.preference.** { *; }
-keepclassmembers class androidx.preference.** {
    public <init>(...);
    public protected *;
}

# ============================================================
# Room Entities/DAOs, Backup JSON/Proto Models, Enums & Parsers
# ============================================================
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class org.koitharu.kotatsu.**.*_Impl { *; }

-keep @kotlinx.serialization.Serializable class org.koitharu.kotatsu.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.protobuf.ProtoNumber <fields>;
}
-keep class org.koitharu.kotatsu.backup.** { *; }
-keep class org.koitharu.kotatsu.sync.** { *; }
-keep class org.koitharu.kotatsu.parsers.** { *; }
-keep interface org.koitharu.kotatsu.parsers.** { *; }

-keep enum org.koitharu.kotatsu.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static final <fields>;
}

# Discord RPC (KizzyRPC), Junrar (.cbr RAR unpacker), Biometric & Local Archive Parsers
-keep class com.my.kizzyrpc.** { *; }
-dontwarn com.my.kizzyrpc.**
-keep class com.github.junrar.** { *; }
-dontwarn com.github.junrar.**
-dontwarn org.apache.commons.logging.**
-keep class org.koitharu.kotatsu.local.** { *; }
