# Nemuri Release Readiness & Cutover Guide

This document outlines the steps required when graduating Nemuri from beta/debug builds (`org.nemuri.app.debug`, `minifyEnabled = false`) to signed production releases (`org.nemuri.app` / `org.nemuri.app.fdroid`).

---

## 1. Current Beta / Debug Build Configuration
- **Workflow**: `.github/workflows/build-apk.yml` builds `:app:assembleGithubDebug` on push to `main`.
- **Package ID**: `org.nemuri.app.debug` (separate from `org.nemuri.app` release builds so beta and production installs never collide or overwrite databases unexpectedly).
- **ABIs**: Universal APK (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) with no `abiFilters` restriction.
- **Signing**: Signed with `app/debug.keystore` unless `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` secrets are configured.

---

## 2. R8 / ProGuard Minification Readiness (`release` & `preview`)
`app/build.gradle` enables `minifyEnabled = true` and `shrinkResources = true` for `release` (and `preview`). `:engine` already provides consumer ProGuard rules, and `app/proguard-rules.pro` now includes `-keep` / `-dontwarn` rules for the Phase 5B/5C runtimes:

1. **Gemini Nano (ML Kit GenAI Prompt)**: `com.google.mlkit.genai.**`, `com.google.android.aicore.**`
2. **LiteRT-LM On-Device LLM**: `com.google.ai.edge.litertlm.**`
3. **ONNX Runtime Android (Neural 2x Upscaler)**: `ai.onnxruntime.**`

Before shipping a production release, run both `./gradlew :app:assembleGithubRelease` and `./gradlew :app:assembleFdroidRelease` and smoke-test:
- Gemini Nano availability probing & prompt generation (`github` release)
- LiteRT-LM `.litertlm` download + recap generation (`github` release)
- ONNX Runtime 2x neural upscaler download + page inference (`github` release)
- Ollama / Gemini REST SSE streaming + Android Keystore API key encryption (`github` + `fdroid` releases)
- Mihon APK / LNReader JS extension loading & cloud backups

---

## 3. Release Signing Keystore Setup
Before publishing a `release` build, configure these GitHub Repository Secrets so the signing certificate stays permanent across every release update:

1. Generate a release keystore locally (keep an offline backup in a password manager!):
   ```bash
   keytool -genkeypair -v -keystore nemuri-release.jks -alias nemuri \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Base64-encode it cleanly:
   - Linux/macOS: `base64 -w 0 nemuri-release.jks`
   - PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("nemuri-release.jks"))`
3. Add GitHub Secrets:
   - `KEYSTORE_BASE64`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`
4. Verify the signed APK output with `apksigner verify --print-certs <apk>`.

---

## 4. Transitioning Beta Users (`org.nemuri.app.debug` -> `org.nemuri.app`)
Because `debug` (`org.nemuri.app.debug`) and `release` (`org.nemuri.app`) intentionally use different Android application IDs:
1. Inside the beta app, open **Settings -> Backup & Restore** and export a `.bk.zip` backup.
2. Install the release APK (`org.nemuri.app`). Both apps can coexist temporarily while verifying data.
3. Restore the `.bk.zip` backup inside the release app.
4. Re-enter Gemini / OpenAI / Bearer API keys in **Enhance -> AI Settings** (Android Keystore secrets are intentionally excluded from backups for security), and re-download any optional LiteRT-LM / ONNX model files or re-enable sandbox extensions.

---

## 5. Optional ABI Splits for Leaner Release Downloads
Once ready for public GitHub Releases, you can optionally enable ABI splits in `app/build.gradle` (`splits { abi { enable true; reset(); include 'arm64-v8a', 'armeabi-v7a', 'x86_64'; universalApk true } }`) with distinct per-ABI `versionCode` offsets so phone users download a smaller `arm64-v8a` APK while retaining a universal APK fallback. Remember to keep `applicationVariants.all` / `androidComponents` `versionCode` mapping consistent across updates.
