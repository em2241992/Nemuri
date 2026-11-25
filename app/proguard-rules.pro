# Engine consumer ProGuard rules are bundled automatically via :engine's consumerProguardFiles.
# App-level keep & release-safety rules below ensure Phase 5B/5C AI and ML runtimes survive R8 when
# building minified release or preview APKs (debug builds leave minification off).

# --- Gemini Nano / ML Kit GenAI Prompt API (github flavor only) ---
-keep class com.google.mlkit.genai.** { *; }
-keep class com.google.android.aicore.** { *; }
-dontwarn com.google.mlkit.genai.**
-dontwarn com.google.android.aicore.**

# --- LiteRT-LM Local LLM Runtime (github flavor only) ---
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# --- ONNX Runtime Android JNI & Native Bridge (github flavor only) ---
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
