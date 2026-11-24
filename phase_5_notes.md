Used tool: invoke_subagent
Used tool: invoke_subagent
Ran inspect engine build.gradle, cpp dir, and libs.versions.toml
Analyzed UpscaleEffect.kt:L1-160
Analyzed MigrateUseCase.kt:L1-160
Analyzed StatsRepository.kt:L1-160
Ran check StatsCollector, UpscaleEffect, and alternatives in engine/app
Ran inspect alternatives use cases and stats recording methods
Analyzed StatsDao.kt:L1-100
Ran check StatsCollector in dropsauce and StatsDao writes in engine
Analyzed StatsCollector.kt:L1-120
Found 12 subagents
Timed 2m

Auditing the DropSauce, Neko, and Nemuri donor codebases for items A–G — I'll follow up shortly with the full feasibility, risk, and design review before taking any action.

Found 12 subagents
Analyzed MlKitGenAiPrompt.kt:L1-120
Analyzed PluggableAiRouter.kt:L1-180
Analyzed NemuriAiCoreEngine.kt:L1-180
Analyzed WhereWasIRecapUseCase.kt:L1-150
Ran check DropSauce FixIt/Dupe and Neko Browse/Feed files

Here is a full feasibility, risk, and limits assessment based on auditing [nemuri-app](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/nemuri-app), [dropsauce](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/dropsauce), [neko](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/neko), and the prior [nemuri](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/nemuri) codebase.

---

## 1. Item-by-Item Assessment (Feasibility, Risks & Limits)

### A. Neko-Style Hub / Feed with User-Picked "Home Source"
- **Verdict**: **Very feasible & a smart design.**
- **What we found in Neko**:
  - Neko's **Feed Hub** (`Continue Reading`, `Recently Updated`, `Newly Added to Library`, grouped `History` & `Updates`) is almost 100% **local Room DB driven** — it works across *all* your sources automatically.
  - Neko's **Browse Homepage** has 7 carousels, of which 4 are MangaDex-only (`Feed Updates`, `Seasonal`, `Staff Picks`, `Dev Picks`) and 3 map generically (`Popular`, `Latest Updates`, `Recently Added`) plus client-side `In Library / Not in Library` visibility filters.
- **How your 1-source idea solves the multi-source problem**:
  - Letting the user pin **1 Primary Home Source** (e.g., MangaDex, Comick, or any installed Mihon/LNReader source) avoids hitting 10+ sources simultaneously and triggering Cloudflare/rate-limit bans.
  - We combine:
    1. **Local Reading Hub** (Continue Reading + Newly Updated + Newly Added to Library — works across every source).
    2. **Pinned Home Source Carousels** dynamically adapted to `repo.sortOrders` (`Popular`, `Latest Updates`, `Newest` / `Top Rated` when supported) + Neko's `In Library / Not in Library` filter chip + "See All" drill-down.
- **Limit to know**: Generic Mihon/LNReader extensions only expose `POPULARITY`, `UPDATED`, and `RELEVANCE` (not MangaDex editorial lists like Seasonal/Staff Picks). So the Home Source carousels will show **Popular** + **Latest Updates** (and **Newest/Top Rated** if that specific parser supports them), rather than hardcoded editorial lists.

---

### B. Deduplication (On-Add + Library-Wide Scanner)
- **Verdict**: **High feasibility — core engine already exists.**
- **What's already in `:engine`**: [DuplicatesUseCase.kt](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/nemuri-app/engine/src/main/kotlin/org/koitharu/kotatsu/favourites/domain/DuplicatesUseCase.kt) from DropSauce is already compiled in `nemuri-app/engine`, along with `AppSettings.isDuplicateCheckEnabled` and `AppSettings.isDuplicateProgressMigrated`.
- **Recommended Deduplication Logic (Hybrid)**:
  1. **Signal 1 — Shared Tracker ID (Highest Confidence)**: `ScrobblingDao.findLinkedMangaIds()` matches titles linked to the same AniList/MAL/Shikimori/Kitsu/MangaBaka ID — catches completely different romanized/English titles (*Sousou no Frieren* vs *Frieren: Beyond Journey's End*).
  2. **Signal 2 — Normalized Title + Alt-Title Match**: Lowercase, strip `(...)`/`[...]`, strip punctuation & leading `"the "`, keep manga vs novel strictly separated (`isNovelSource` / `isEpub`).
  3. **Where it runs**:
     - **On-Add Duplicate Guard** (DropSauce/Mihon behavior): Intercepts adding to Library; if a match exists, shows a side-by-side sheet with chapter counts and **Replace (Migrate)** / **Add Anyway** / **Skip**.
     - **Enhance Hub Library Duplicate Scanner**: Groups existing duplicates across the library into **High Confidence** (tracker match or normalized exact/near-exact match) vs **Review Carefully** (substring containment, so sequels/spin-offs are never auto-merged). User must explicitly pick which entry to **Keep** and confirm merging categories/progress before deleting the duplicate.
- **Risk**: Substring containment alone can false-match spin-offs (e.g., *Solo Leveling* vs *Solo Leveling: Ragnarok*). Keeping substring hits in a separate "Review" bucket + requiring user confirmation eliminates accidental data loss.

---

### C. Source Migration & DropSauce "Fix It" / Smart Replace
- **Verdict**: **High feasibility — backend is already in `:engine` and can be ported cleanly.**
- **What's already in `:engine`**:
  - [AlternativesUseCase.kt](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/nemuri-app/engine/src/main/kotlin/org/koitharu/kotatsu/alternatives/domain/AlternativesUseCase.kt) (strict & loose cross-source title matching, same-kind guard).
  - [AutoFixUseCase.kt](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/nemuri-app/engine/src/main/kotlin/org/koitharu/kotatsu/alternatives/domain/AutoFixUseCase.kt) (verifies real page-URL health, searches either all enabled sources for the healthiest/highest-chapter match OR user-ordered target sources).
  - [MigrateUseCase.kt](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/nemuri-app/engine/src/main/kotlin/org/koitharu/kotatsu/alternatives/domain/MigrateUseCase.kt) (atomic Room transaction migrating favourites, prefs, bookmarks, reading history by `(volume, number)` or `%` fallback, tracker entry, and scrobbler links).
- **How we meet your confirmation & source-selection rules**:
  - **Manual Migrate / Switch Source** (from `MangaDetailsScreen` & Enhance Hub): User selects target source(s), sees candidates with cover + chapter count delta, and clicks an explicit **Confirm Migration** dialog (with toggle: *Carry over reading progress & bookmarks*).
  - **Broken Source Repair & Smart Replace Wizard** (ported from DropSauce's `BrokenSourcesMigrationFragment` + `AutoFixService`):
    1. Detects library titles whose source is uninstalled/unavailable or failing health checks.
    2. User selects & prioritizes target sources.
    3. **Smart Replace** finds the best working match per title and presents a **Review & Confirm** list (1-tap confirm per title, or confirm selected batch — never silent unconfirmed replacement).
- **Risks & Engineering Catch**:
  - **Main-thread OkHttp trap**: [MigrateUseCase.kt](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/nemuri-app/engine/src/main/kotlin/org/koitharu/kotatsu/alternatives/domain/MigrateUseCase.kt#L26) currently injects `Set<@JvmSuppressWildcards Scrobbler>` directly; we must switch it to `Provider<Set<@JvmSuppressWildcards Scrobbler>>` before injecting it into `:app`.
  - **Rate limits & Cloudflare**: `AutoFixUseCase` calls `getDetails` + `getPages` across sources (up to 40s/title, 4 concurrent). Batch Smart Replace must run in a cancellable background service/worker with per-title progress so it won't die if the user leaves the screen or hammer one site into a Cloudflare block.

---

### D. Reading Statistics
- **Verdict**: **High feasibility — analytics layer already exists, with 1 critical gap to close.**
- **What's already in `:engine`**: [StatsDao.kt](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/nemuri-app/engine/src/main/kotlin/org/koitharu/kotatsu/stats/data/StatsDao.kt) and [StatsRepository.kt](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/nemuri-app/engine/src/main/kotlin/org/koitharu/kotatsu/stats/data/StatsRepository.kt) already compute time period snapshots (`Day`, `Week`, `Month`, `3 Months`, `Year`, `All`), category filters, reading time, pages, chapters, `minutesPerChapter`, active days, current/longest streaks, activity bar charts, and per-manga 90-day timelines.
- **CRITICAL MISSED FACTOR**: DropSauce's `StatsCollector` was **not** ported into `:engine` or wired to `ReaderActivity` yet. Right now `stats` is only populated from restored backups — live reading in Nemuri isn't recording sessions!
  - **Fix**: Wire a clean session recorder in `ReaderViewModel` (respecting `AppSettings.isStatsEnabled` + Incognito mode) for **both** manga pages and novel/EPUB reading, plus a per-title Stats sheet on `MangaDetailsScreen`.

---

### E. Neko Multi-Source Merge
- **Verdict**: **Strongly recommend DEFERRING / SKIPPING.**
- **Why your instinct is right**:
  - Requires a new Room table (`merged_manga`), custom chapter deduplication/numbering conflict rules across scanlators, changes to `LocalBackupRepository` + `MihonBackupExporter` + `GoogleDriveSyncRepository`, and multi-repository handoff inside `ReaderActivity` & `DownloadWorker`.
  - With **Smart Replace & Source Migration (C)**, if a source breaks or falls behind, migrating cleanly solves ~90% of the problem with zero database/sync fragility.

---

### F. Reader Upscaler (Anime4K GPU Shader + Optional Neural NPU/GPU Upscaler)
- **Verdict**: **Split into two tiers — Tier 1 is instant/low-risk; Tier 2 (NPU/Neural) has heavy limits & risks.**

| Tier | Approach | Feasibility & Effort | Limits & Risks |
|---|---|---|---|
| **Tier 1: Anime4K GPU Shader** | Wire existing [UpscaleEffect.kt](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/nemuri-app/engine/src/main/kotlin/org/koitharu/kotatsu/reader/domain/UpscaleEffect.kt) (AGSL `RuntimeShader` + `RenderEffect`) into `ReaderActivity` + split-screen before/after preview + strength/preset options. | **Ready now** (already in `:engine`), 0 MB APK bloat, <2 ms/frame, auto-skips on Battery Saver. | **Android 13+ (API 33+) only.** Screen-space line-art sharpener — crispens manga lines well, but doesn't reconstruct heavily compressed JPEG artifacts like a CNN. |
| **Tier 2: Neural AI Upscaler (NPU / GPU / CPU)** | Tiled `LiteRT` (TFLite) or `ONNX Runtime` running lightweight anime super-resolution models (e.g., `FSRCNNX-x2` ~1–3 MB or `Real-ESRGAN-animevideov3-x2` ~2.5 MB downloaded on-demand) with GPU/NPU delegate + CPU fallback, pre-upscaling 1–2 pages ahead to disk cache. | **Medium-Hard**, requires tiled bitmap chunking (256×256 + overlap stitching) & background worker queue. | **Major risks**: (1) **+20–45 MB APK size** for native ML runtime `.so` libs; (2) **Android NPU fragmentation** — Android 15 deprecated NNAPI in favor of vendor LiteRT delegates (Qualcomm QNN / MediaTek / GPU OpenCL), so "NPU" varies wildly per SoC and often silently falls back to GPU/CPU; (3) **Latency/Battery/OOM**: 0.5–1.5s/page on flagship NPU/GPU, **3–15s/page** on mid-range CPU, plus Webtoon strips (800×12000px) must be strictly capped/chunked or they OOM alongside SSIV; (4) Proprietary vendor NPU delegates (QNN) must be `githubImplementation`-only for F-Droid. |

- **Recommendation**: Wire **Tier 1 (Anime4K AGSL + Preview)** immediately in Phase 5A/5B, and treat **Tier 2 (Neural Tiled Upscaler with downloadable models + GPU/NPU/CPU selector)** as an isolated, opt-in **Phase 5C** module with strict RAM/resolution caps and a clear hardware/speed warning.

---

### G. AI "Where Was I?" Recaps & Pluggable AI Model Architecture
- **Crucial Audit Finding on the Prior `nemuri` Donor Code**:
  We inspected every AI file in [nemuri](file:///c:/Users/ericm/OneDrive/Desktop/Nemuri/nemuri) and found **why its model selection was flawed**:
  1. `MlKitGenAiPrompt.kt` in the donor was a **100% fake local stub** (no ML Kit Gradle dependency existed; `generateContent()` literally returned empty).
  2. `LOCAL_LITERT_FILE` had **no inference runtime** and `downloadLiteRtModel()` didn't actually download anything — it just flipped a flag and fell through to the empty stub.
  3. `testActiveEndpointConnection()` returned `✓ Connected` **even when the HTTP request failed**.
  4. `WhereWasIRecapCard` cached the deterministic fallback template in Room and stamped it with `"Gemini Nano"` / `"LiteRT Local"` badges, lying about which engine generated it.
  5. API keys were saved in plaintext `SharedPreferences`, Gemini's API key was sent in the URL `?key=` query string, and HTTP timeouts were hardcoded to 15s (too short for local Ollama cold loads) with no streaming.

- **Real Feasibility, Risks & Limits for Your Desired AI Stack**:

| Backend | Real Implementation | Feasibility | Key Risks & Limits |
|---|---|---|---|
| **Off (Master Switch)** | Completely disables AI UI/calls (optional honest non-AI *"Reading Trail"* recap of last chapters read). | Trivial, 0 MB, 0 RAM. | None. |
| **1. Gemini Nano (Primary when available)** | Official `com.google.mlkit:genai-prompt` (`githubImplementation` ONLY, behind a flavor-safe facade so `fdroid` compiles cleanly with `Unavailable`). | Real & lightweight (~1–2 MB client; OS AICore holds weights). | **Strict hardware gate**: Only works on AICore-provisioned flagships (Pixel 8/9/10, Galaxy S24/S25+, select Snapdragon 8 Gen 3/Elite devices, locked bootloader). Will report `Unavailable` on all other phones & F-Droid. |
| **2. Local CPU/GPU On-Device LLM Fallback** | **LiteRT-LM** (`litertlm-android`) or **llama.cpp (GGUF)** with user-downloaded 1B–1.5B INT4/Q4 model (~500 MB–1.0 GB stored in app files, never in APK). | Feasible on `arm64-v8a` phones with **6–8 GB+ RAM**. | **Highest risk in Phase 5**: (1) **+30–60 MB APK size** for native runtime even for users with AI Off; (2) **1.0–1.8 GB RAM spike** while loaded — must lazy-load & auto-unload after ~60s idle or Android OOM-kills the reader; (3) Thermal throttling & slow token speed on low-end CPUs; (4) `llama.cpp` adds heavy CMake build time on GitHub Actions. |
| **3. Ollama / Open LLMs / API Keys** | Native OkHttp SSE streaming (already in `:engine`, **0 MB APK bloat**, F-Droid safe): Gemini REST API (`x-goog-api-key` header), OpenAI-compatible (`/v1/chat/completions` — Ollama Termux/LAN, LM Studio, OpenRouter, Groq, OpenAI, etc.). | **Rock-solid & highest quality** across every device. | Needs 60–120s read timeout for Ollama cold start, **Android Keystore AES-GCM encryption** for API keys (excluded from backups/Drive sync), real error reporting on Test Connection, and `network_security_config.xml` allowing cleartext HTTP **only for localhost/LAN** (for local Ollama). |

- **Design Fix for Your Auto-Fallback Chain**:
  - Replace the donor's broken selector with an explicit **AI Mode**:
    - `Off`
    - `Auto` *(tries **Gemini Nano (AICore)** → if unavailable, **User's Configured Remote/Ollama** if set, OR **Local CPU/GPU Model** if downloaded → honest **Reading Trail** fallback if nothing is ready)*
    - `Pin Specific Backend`: `Gemini Nano` | `Local CPU/GPU Model` | `Ollama / OpenAI-Compatible` | `Gemini API Key`
  - Store `engineUsed` (e.g., `"Gemini Nano"`, `"Ollama (llama3.2)"`, `"Reading Trail"`) in the Room `manga_ai_recaps` cache row (`Migration40To41`) so the badge **always tells the truth**, and never overwrite an LLM recap with a fallback template (or cache a failed call as an LLM recap).

---

## 2. Missed Factors Highlighted for Your Awareness

1. **APK Size Budget (Big One!)**:
   - Currently the APK is lean (~40 MB).
   - Adding **both** a Local LLM runtime (+30–60 MB) **and** a Neural Image Upscaler runtime (+20–45 MB) into a universal 4-ABI APK (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) could easily push the APK to **120–160+ MB**.
   - **Mitigation**: Restrict heavy ML native libs strictly to `arm64-v8a` (almost all modern phones) and/or split GitHub CI APKs by ABI (`arm64-v8a` release APK), and keep all model weights downloadable on-demand (never bundled in APK).
2. **Plot Quality Limit on "Where Was I?" Recaps**:
   - Spoiler quarantine only feeds **series premise + genres + last 12 read chapter titles** (never unread chapters!).
   - If a manga only has numbered chapter titles (`Chapter 81`, `Chapter 82`, ...), any LLM can only synthesize premise + chapter position — unless for **novels/EPUBs** (or downloaded text) we optionally include brief snippets of the last 1–2 already-read chapters. Setting clear expectations (and supporting novel text context where available) makes recaps dramatically better.
3. **Room Migration `40 -> 41` & Backup/Drive Sync Safety**:
   - Adding `manga_ai_recaps` needs a clean `Migration40To41` (non-destructive so existing libraries stay intact) and intentionally stays local-only (cache regenerates per device, so Drive Sync & `.bk.zip` don't bloat).
4. **Live Stats Recording (`StatsCollector`)**:
   - Must be wired into `ReaderViewModel` alongside Reading Statistics UI (D) so new reading sessions actually populate the charts.
5. **Security & Privacy**:
   - API keys (Gemini / OpenAI / OpenRouter) must be encrypted via Android Keystore and explicitly added to `AppSettings.SENSITIVE_BACKUP_KEYS` so they never leak into `.bk.zip` backups or Google Drive Sync.
   - Local Ollama (`http://127.0.0.1:11434` or `http://192.168.x.x:11434`) requires loopback/RFC1918 cleartext exception in `network_security_config.xml` (while keeping HTTPS required for public internet hosts).

---

## 3. Proposed Phased Plan (Recommended Rollout)

Breaking Phase 5 into **3 clean, testable releases** keeps risk low and lets you test each layer on your phone:

- **Phase 5A — Core Hub, Health, Migration, Stats & Anime4K GPU** *(zero APK bloat, highest daily value)*
  1. **Enhance Hub Dashboard** (replaces Phase 5 placeholder): Health overview + quick tools.
  2. **Neko-Style Hub & Pinned Home Source**: Enhanced Feed/Explore Hub with local `Continue Reading / Updates / Newly Added` + user-selected Home Source carousels (`Popular`, `Latest`, etc.) + library visibility filter.
  3. **Deduplication (B)**: On-add duplicate guard (`DuplicatesUseCase`) + Enhance Hub Library Duplicate Scanner (High Confidence vs Review, confirm before merge/remove).
  4. **Fix-It / Smart Replace & Manual Migration (C)**: Fix `MigrateUseCase` `Provider<Set<Scrobbler>>`, wire Manual Source Migration (with source picker + confirmation) and DropSauce-style Broken Source Repair / Smart Replace wizard (with preview & confirmation + background service).
  5. **Reading Statistics (D)**: Port & wire `StatsCollector` into `ReaderViewModel` (manga + novel) + full Statistics Dashboard (period/category filters, streaks, activity bars, top titles, per-manga stats).
  6. **Reader Upscaler Tier 1 (F-1)**: Wire existing `UpscaleEffect` (Anime4K AGSL GPU shader) into `ReaderActivity` + toggle & split-screen preview dialog.

- **Phase 5B — Pluggable AI Engine & "Where Was I?" Recaps (G)**
  1. Room `Migration40To41` for `manga_ai_recaps` (with honest `engineUsed` tracking).
  2. Flavor-safe **Gemini Nano (ML Kit GenAI Prompt API)** (`githubImplementation` + `fdroid` stub).
  3. **Ollama / OpenAI-Compatible / Gemini API** client with SSE streaming, Keystore-encrypted keys, LAN cleartext config, 90s cold-start timeout, and real connection testing.
  4. **AI Settings & Model Selector** (Off / Auto / Pinned backend) + **"Where Was I?" Recap Card** on `MangaDetailsScreen` (strict spoiler quarantine, honest attribution badge, manual refresh, plus optional novel text-context support).
  5. *(Conditional on your decision below)*: **On-device CPU/GPU Local LLM fallback** (`LiteRT-LM` or `llama.cpp`) with downloadable 1B–1.5B model manager + 60s idle auto-unload.

- **Phase 5C — Advanced Neural Reader Upscaler (F-2)** *(isolated so ML runtime & tiling don't destabilize 5A/5B)*
  1. Tiled background `PageUpscaleEngine` (LiteRT GPU/NPU delegate with CPU fallback + disk cache + strict Webtoon chunking & low-RAM guard).
  2. Downloadable lightweight anime models (`FSRCNNX-x2`, `Real-ESRGAN-animevideov3-x2`) managed in Reader/Enhance settings + hardware accelerator selector (`Auto NPU/GPU`, `GPU`, `CPU`).

---

## 4. Decisions Needed From You Before We Start

1. **Rollout order**: Are you happy splitting Phase 5 into **5A → 5B → 5C** (and **skipping E: Neko Multi-Source Merge**)?
2. **Neko Hub placement (A)**: Should the Neko-style **Pinned Home Source** carousels live inside the existing **Explore** tab (upgrading it into a Neko-style Browse Hub) and the **Feed Summary** (`Continue Reading / Updates / Newly Added`) live inside the **Feed** tab — or do you want a separate toggleable view?
3. **APK size vs Local CPU/GPU LLM (G) & Neural Upscaler (F-2)**:
   - Adding on-device Local LLM (LiteRT-LM / llama.cpp) + Neural Image Upscaler (LiteRT/ONNX) will increase APK size by roughly **+50–90 MB** (even with models downloaded separately).
   - Are you okay with switching our GitHub Actions beta build to **`arm64-v8a`-only** (or separate per-ABI APKs) to cut that bloat roughly in half?
   - And for **5B**, do you want **Gemini Nano + Ollama/OpenAI/Gemini API + Reading Trail first** and add the heavy **Local CPU/GPU LLM runtime** in the next step, or include Local CPU/GPU LLM right away in 5B?
4. **AI default state**: Should AI default to **Auto (Gemini Nano if supported, otherwise Off/Reading Trail until user configures a local model or endpoint)**, or **completely Off** until the user opts in inside Enhance/Settings?