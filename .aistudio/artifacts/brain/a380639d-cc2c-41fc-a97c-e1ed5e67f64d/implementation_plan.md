# Kotatsu Neo — Core Functional Systems, Permissions & Reader Pipeline Plan

This plan outlines the next development phase to transition **Kotatsu Neo** from an interactive Alpha build into a fully functional Beta-ready reader and library manager with live extension catalog fetching, background chapter synchronization, offline CBZ archive reading/downloading, and real bitmap post-processing.

## User Review & Critical Decisions

Based on your selections, this phase focuses on three pillars:

1. **Core Functional Systems**
   - **Live Keiyoushi JSON Fetch & Extension Discovery**: Replace static extension mocks with live HTTP JSON index fetching via OkHttp + Kotlinx Serialization, paired with Android `PackageManager` discovery of installed Tachiyomi/Mihon and Kotatsu extension packages.
   - **Background Chapter Sync & Notifications**: Schedule periodic and on-demand background sync passes that compare remote chapter lists against bookmarked library entries in Room and dispatch Android system notifications for new chapter drops.
   - **Real CBZ/ZIP Archive Parser & Offline Downloader**: Parse local `.cbz`/`.zip` manga archives via `ZipInputStream`, allow importing local CBZ files via the system document picker, and package downloaded chapters into real `.cbz` archives stored in app-scoped offline storage.

2. **Android Permissions & System Integrations**
   - **`POST_NOTIFICATIONS` (Android 13+)**: Runtime permission prompt using Jetpack Compose `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` when enabling chapter notifications or starting batch downloads, backed by dedicated Notification Channels (`chapter_updates` and `downloads_progress`).
   - **`FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_DATA_SYNC`**: Foreground service integration for batch chapter downloads and library sync jobs with live progress notifications and pause/cancel actions.
   - **`QUERY_ALL_PACKAGES` & Extension Package Discovery**: Manifest declaration alongside targeted `<queries>` intent filters (`eu.kanade.tachiyomi.EXTENSION` and `app.kotatsu.parser.PROVIDE_MANGA`) so `PackageManager` can discover all installed extension APKs and inspect their metadata (`tachiyomi.extension.class`, version, NSFW flag, language, and icons) safely without violating Play System Integrity rules.

3. **Reader & Image Pipeline Upgrades**
   - **Real Bitmap Decoding from CBZ & Network Streams**: Decode real `Bitmap` instances from local `.cbz` archive entries and remote HTTP image streams with downsampling (`inSampleSize`) and Coil memory/disk caching.
   - **Double-Page Spread Detection & Auto-Split**: Automatically inspect decoded bitmap dimensions (`width > height * 1.25f`) to detect wide two-page manga spreads, displaying them full-bleed in landscape or splitting them into sequential Right-to-Left / Left-to-Right half-pages in portrait mode.
   - **Automatic White Margin Crop**: Fast pixel-border luminance/alpha scan on decoded page bitmaps that trims uniform white or black outer margins before rendering in the reader canvas.

---

## 1. Overview & Core Concept

Kotatsu Neo combines an expressive Material 3 interface and a dual-mode hybrid feed with a production-grade manga reading engine. In this phase, every core workflow—discovering extensions, syncing new chapter drops, downloading chapters for offline reading, and rendering processed manga pages—will operate on real Android system services, network streams, and binary archive structures.

---

## 2. User Experience & Visual Design

### Key User Flows
- **Extension Repository Sync & Package Inspection**:
  - Opening **Settings → Extensions** (or the Sources filter sheet) allows refreshing live JSON repository indexes (e.g., Keiyoushi / Kotatsu mirrors) over HTTPS with a real-time progress indicator, search filter, language chips, and installed-APK badge verification via `PackageManager`.
- **Chapter Update Sync & System Alerts**:
  - Tapping **Check for new chapters** in Settings or pulling to refresh on the **Feed** triggers an immediate sync worker. If `POST_NOTIFICATIONS` is not yet granted on Android 13+, a contextual Material 3 rationale banner and system permission dialog are presented. Newly detected chapters appear both in the top Horizontal Stories bar and as grouped Android notifications that deep-link into the reader.
- **Batch Offline Chapter Downloader & Local CBZ Import**:
  - Selecting **Download** on any manga or chapter enqueues a foreground data-sync job that streams chapter pages into a valid `.cbz` archive inside app storage, showing real byte/page progress in the system notification shade and inside **Settings → Downloads**. Users can also import `.cbz` files from their device via the system document picker.
- **Enhanced Reader Pipeline (Spread Split & Auto-Crop)**:
  - Inside the **Manga Reader**, pages decoded from CBZ archives or remote URLs pass through an on-device bitmap transformer. Wide double-page spreads display a subtle pill badge (`2P Spread • Auto-Split`) and can be read as two sequential single pages or rotated full-width. Toggling **Crop Margins** in the reader HUD trims outer whitespace in real time.

### Visual Identity & Theme
- Preserves the **Neo-Brutalist Swiss Grids + Atmospheric Minimalism** aesthetic (`#06070B` obsidian canvas, `#00E5B8` Kotatsu Teal default accent, and full support for all 7 dynamic color palettes across the Floating Island Navigation Bar, Continue Reading pill, and Settings icon badges).

### Interactive Feedback & Motion
- Live determinate progress bars during repository JSON syncing and multi-page CBZ archive packing.
- Smooth crossfade transitions when switching between uncropped pages, margin-cropped pages, and split double-page spreads.

---

## 3. Key Product Decisions & Trade-Offs

- **Play-Compliant Extension APK Discovery**:
  - Rather than executing arbitrary external `.dex` code at runtime (which violates Google Play System Integrity / Dynamic Code Loading policies), Kotatsu Neo queries installed extension packages via `PackageManager` (`QUERY_ALL_PACKAGES` + `<queries>`) to read their manifest metadata, icons, supported languages, and `ContentProvider` URIs, while executing actual network catalog/page parsing through built-in Kotlin HTTP/JSON parsers and live repository indexes.
- **Zero-Permission Storage for CBZ Archives**:
  - Offline downloads are stored as standard `.cbz` (ZIP) archives in app-specific external/internal directories (`context.getExternalFilesDir("downloads")`), and external `.cbz` imports use `ActivityResultContracts.OpenDocument`, avoiding restricted broad storage permissions (`MANAGE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE`).
- **Memory-Safe Bitmap Post-Processing**:
  - Margin auto-crop and double-page spread splitting run on `Dispatchers.Default` using `BitmapFactory.Options` bounds checking and region decoding (`BitmapRegionDecoder` / sub-bitmap slicing) to prevent `OutOfMemoryError` on high-resolution scanlations.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                         │
│  Hybrid Feed  │  Manga Reader (Zoom/Crop/Spread)  │  Settings & Queue   │
└───────┬─────────────────────────┬───────────────────────────┬───────────┘
        │                         │                           │
        ▼                         ▼                           ▼
┌───────────────────┐   ┌───────────────────────┐   ┌─────────────────────┐
│ ExtensionRepoMgr  │   │  ReaderBitmapPipeline │   │  Sync & Download    │
│ • Live HTTP JSON  │   │  • CBZ ZipInputStream │   │  Coordinator        │
│ • PackageManager  │   │  • Margin Auto-Crop   │   │  • Foreground Svc   │
│   APK Scanner     │   │  • 2P Spread Splitter │   │  • NotificationMgr  │
└───────┬───────────┘   └─────────┬─────────────┘   └─────────┬───────────┘
        │                         │                           │
        └─────────────────────────┼───────────────────────────┘
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                Room Database (v3) + DataStore Preferences               │
│   MangaEntity • ChapterEntity • DownloadTaskEntity • ExtensionEntity    │
└─────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State
- **Download & Archive State**:
  - Tracks per-chapter download state (`QUEUED`, `DOWNLOADING`, `COMPLETED`, `FAILED`, `PAUSED`), local `.cbz` file path, total byte size, and page count.
- **Reader Page Processing State**:
  - `ReaderPageSlice`: Represents either a full single page or the left/right half of an auto-split double-page spread, paired with computed crop bounds (`Rect`) when white-margin trimming is active.
- **Extension Catalog State**:
  - Combines remotely fetched JSON extension descriptors (`pkg`, `name`, `lang`, `version`, `nsfw`, `sources`) with locally installed APK package status from `PackageManager`.

### Interactive Component & State Mapping
- **Permission Request Coordinator**:
  - Checks `ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)` on API 33+ and triggers `ActivityResultContracts.RequestPermission()` when the user enables chapter notifications or starts a background download.
- **Foreground DataSync Service**:
  - Declared with `android:foregroundServiceType="dataSync"`, posting ongoing progress notifications via `NotificationCompat.Builder` and updating Room download records as pages are written into `.cbz` output streams.
- **Reader Bitmap Processor**:
  - Scans top, bottom, left, and right edges of decoded bitmaps at a 4px stride to detect uniform light/dark margins (>92% luminance or <8% luminance) and crops to the content bounding box; splits aspect ratios `> 1.25f` into two ordered page slices based on RTL/LTR reading direction.
