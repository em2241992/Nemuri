# Engine consumer ProGuard rules are bundled automatically via :engine's consumerProguardFiles.
# App-level keep & release-safety rules below ensure AI/ML/NPU runtimes, JNI bridges, extensions,
# Room entities, and backup/serialization models survive R8 when building minified release/preview APKs.

# --- Native JNI Entry Points (JxlDecoder, LiteRT, QNN, ONNX, QuickJS) ---
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class org.koitharu.kotatsu.core.image.JxlDecoder { *; }

# --- External Mihon Extensions & LNReader / Novel Plugin Interfaces ---
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keep class tachiyomi.** { *; }
-keep interface tachiyomi.** { *; }
-keep class ireader.** { *; }
-keep interface ireader.** { *; }
-dontwarn ireader.**
-keep class org.koitharu.kotatsu.lnreader.** { *; }
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- Shizuku Silent Installer & Binder IPC ---
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep interface moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**
-keep class org.koitharu.kotatsu.extensions.install.** { *; }

# --- Gemini Nano / ML Kit GenAI Prompt & Summarization APIs (github flavor) ---
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.aicore.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.aicore.**

# --- LiteRT-LM Local LLM Runtime (github flavor) ---
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# --- ONNX Runtime Android JNI & Native Bridge (github flavor) ---
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# --- Qualcomm QNN NPU & TFLite / LiteRT Delegates (github flavor) ---
-keep class com.qualcomm.qti.** { *; }
-dontwarn com.qualcomm.qti.**
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.litert.**
-keep class org.tensorflow.lite.** { *; }
-keep interface org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# --- App @Serializable Models, Enums, Biometric, Crash Handler & Room ---
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**
-keep class org.nemuri.app.crash.** { *; }
-keep @kotlinx.serialization.Serializable class org.nemuri.app.** { *; }
-keep enum org.nemuri.app.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Dao class * { *; }
