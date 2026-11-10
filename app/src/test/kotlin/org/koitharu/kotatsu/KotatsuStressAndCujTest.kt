package org.koitharu.kotatsu

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koitharu.kotatsu.ai.AiLibraryEngine
import org.koitharu.kotatsu.ai.AiMangaPageUpscaler
import org.koitharu.kotatsu.backups.BackupFormat
import org.koitharu.kotatsu.backups.UniversalBackupRepository
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.ChapterEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteEntity
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.MangaMergeLinkEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity
import org.koitharu.kotatsu.core.db.entity.TitleNormalizer
import org.koitharu.kotatsu.core.model.AiUpscaleMode
import org.koitharu.kotatsu.core.model.AppColorSchemePreset
import org.koitharu.kotatsu.core.model.AppNavTab
import org.koitharu.kotatsu.core.model.AppThemeMode
import org.koitharu.kotatsu.core.model.BatchDeduplicationRule
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.model.NavBarStyle
import org.koitharu.kotatsu.core.parser.EmptyMangaRepository
import org.koitharu.kotatsu.core.parser.GlobalParserContextHolder
import org.koitharu.kotatsu.core.parser.KotatsuExternalProviderScanner
import org.koitharu.kotatsu.core.parser.KotatsuLibraryRepository
import org.koitharu.kotatsu.core.parser.LocalMangaRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.extensions.ExtensionCatalogManga
import org.koitharu.kotatsu.extensions.ExternalExtensionManager
import org.koitharu.kotatsu.main.ui.KotatsuNeoViewModel
import org.koitharu.kotatsu.mihon.LazyMihonMangaRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KotatsuStressAndCujTest {

	private lateinit var app: Application
	private lateinit var db: MangaDatabase
	private lateinit var libraryRepo: KotatsuLibraryRepository
	private lateinit var extensionManager: ExternalExtensionManager
	private lateinit var aiEngine: AiLibraryEngine
	private lateinit var backupRepo: UniversalBackupRepository

	@Before
	fun setUp() {
		app = ApplicationProvider.getApplicationContext()
		GlobalParserContextHolder.applicationContext = app
		app.getSharedPreferences("kotatsu_neo_prefs", Context.MODE_PRIVATE).edit().clear().commit()
		File(app.filesDir, "installed_extensions_registry.json").delete()
		File(app.filesDir, "repo_indexes").deleteRecursively()
		File(app.filesDir, "ln_plugins").deleteRecursively()
		File(app.filesDir, "cbz_imports").deleteRecursively()
		File(app.filesDir, "local_cbz").deleteRecursively()
		db = Room.inMemoryDatabaseBuilder(app, MangaDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		libraryRepo = KotatsuLibraryRepository(db)
		extensionManager = ExternalExtensionManager(app, db)
		aiEngine = AiLibraryEngine(db, extensionManager)
		backupRepo = UniversalBackupRepository(db)
	}

	@After
	fun tearDown() {
		db.close()
	}

	@Test
	fun testCleanStartupZeroMangaZeroSourcesZeroInfoAndFeedSafety() = runBlocking {
		// 1. Default startup (seedDebugMockData = false) MUST have 0 manga, 0 sources, 0 repos, 0 categories, 0 history
		libraryRepo.ensureSeeded(seedDebugMockData = false)
		assertTrue("Fresh library must have 0 manga", libraryRepo.observeLibraryItems().first().isEmpty())
		assertTrue("Fresh DB must have 0 categories", db.getFavouritesDao().getCategories().isEmpty())
		assertTrue("Fresh DB must have 0 extension repos", db.getAuxiliaryDao().getExtensionRepos().isEmpty())
		assertTrue("Fresh DB must have 0 history rows", db.getHistoryDao().getAllHistory().isEmpty())
		assertTrue(
			"Fresh extension manager must discover 0 sources when none are installed",
			extensionManager.discoverExtensions(emptyList(), includeDebugMock = false).isEmpty(),
		)
		assertTrue(
			"Fresh extension catalog must be empty when debug sandbox is disabled",
			extensionManager.getExtensionCatalogItems(includeDebugMock = false).isEmpty(),
		)

		val vm = KotatsuNeoViewModel(app)
		assertFalse("Debug sandbox must be OFF by default", vm.debugSandboxEnabled.value)
		assertTrue("ViewModel repair audit log must be empty on fresh launch", vm.repairAuditLog.value.isEmpty())
		assertTrue("ViewModel merged sources must be empty on fresh launch", vm.mergedSourcesByMangaId.value.isEmpty())
		assertTrue("ViewModel extension catalog must be empty on fresh launch", vm.extensionCatalog.isEmpty())

		// 2. Empty feed must produce 0 entries without crashing
		val emptyFeedEntries = org.koitharu.kotatsu.ui.screens.buildHybridFeedEntries(
			feedUpdates = emptyList(),
			libraryItems = emptyList(),
			extensionCatalog = emptyList(),
			libraryNormalizedTitles = emptySet(),
			defaultSource = "All Sources",
			searchQuery = "",
		)
		assertTrue("Hybrid feed must be completely empty when 0 manga and 0 sources exist", emptyFeedEntries.isEmpty())

		// 3. Duplicate URLs across real sources must produce strictly unique Lazy keys & zero fake comments/quotes
		val realCatalogWithDuplicateUrls = listOf(
			ExtensionCatalogManga(
				title = "Real Series Alpha",
				altTitle = null,
				author = "Author A",
				sourceName = "MIHON_1001",
				url = "/manga/shared-slug",
				coverSeed = "https://cdn.real.org/alpha.jpg",
				totalChapters = 14,
				rating = 0.9f,
				tags = listOf("Action"),
				description = "Real parsed description A.",
				lang = "EN",
				isDebugMock = false,
			),
			ExtensionCatalogManga(
				title = "Real Series Beta",
				altTitle = null,
				author = "Author B",
				sourceName = "KOTATSU_ORG_EXAMPLE",
				url = "/manga/shared-slug",
				coverSeed = "https://cdn.real.org/beta.jpg",
				totalChapters = 8,
				rating = 0.85f,
				tags = listOf("Drama"),
				description = "Real parsed description B.",
				lang = "EN",
				isDebugMock = false,
			),
		)
		val realEntries = org.koitharu.kotatsu.ui.screens.buildHybridFeedEntries(
			feedUpdates = emptyList(),
			libraryItems = emptyList(),
			extensionCatalog = realCatalogWithDuplicateUrls,
			libraryNormalizedTitles = emptySet(),
			defaultSource = "All Sources",
			searchQuery = "",
		)
		assertEquals(2, realEntries.size)
		assertEquals("Every HybridFeedEntry ID must be unique for Compose Lazy lists", 2, realEntries.map { it.id }.distinct().size)
		assertTrue("Non-mock entries must have 0 fake comments", realEntries.all { it.baseCommentCount == 0 && it.topCommentPreview.isEmpty() })
		assertTrue("Non-mock entries must not expand sandbox extra series", realEntries.none { it.isDebugMock })
	}

	@Test
	fun stressTestSqliteV28BulkInsertAndFtsSearch() = runBlocking {
		libraryRepo.ensureSeeded(seedDebugMockData = true)

		// Bulk insert 500 additional manga series to stress-test SQLite v28 + FTS4 virtual table
		val bulkCount = 500
		db.withTransaction {
			for (i in 1..bulkCount) {
				val id = 100_000L + i
				val title = "Stress Series #$i: Cyber Blade Chronicles Vol. ${i % 12}"
				val entity = MangaEntity(
					id = id,
					title = title,
					altTitles = "サイバーブレード $i",
					url = "https://ext.kotatsuneo.app/stress/$id",
					publicUrl = "https://ext.kotatsuneo.app/stress/$id",
					rating = 0.80f + (i % 20) * 0.01f,
					isNsfw = false,
					contentRating = "SAFE",
					coverUrl = "studio_cover://frieren",
					largeCoverUrl = "studio_cover://frieren",
					state = if (i % 3 == 0) "FINISHED" else "ONGOING",
					authors = "Author Studio #${i % 25}",
					source = if (i % 2 == 0) "EXT:MangaDex (Mihon EN)" else "EXT:ComicK (Mihon Multi)",
					description = "High-volume stress benchmark entry #$i testing SQLite v28 FTS4 token indexing.",
					normalizedTitle = TitleNormalizer.normalize(title),
				)
				db.getMangaDao().upsert(
					entity,
					listOf(
						TagEntity(
							id = 5000L + (i % 10),
							title = "Genre_${i % 10}",
							key = "genre_${i % 10}",
							source = entity.source,
						),
					),
				)
				db.getFavouritesDao().upsert(
					FavouriteEntity(
						mangaId = id,
						categoryId = ((i % 4) + 1).toLong(),
						sortKey = i,
						createdAt = System.currentTimeMillis() - i * 1000L,
					),
				)
			}
			db.openHelper.writableDatabase.execSQL("INSERT INTO manga_fts(manga_fts) VALUES('rebuild')")
		}

		val diag = libraryRepo.runSqliteDiagnostics()
		assertEquals(28, diag.databaseVersion)
		assertTrue("Expected at least 509 FTS indexed rows, got ${diag.ftsIndexCount}", diag.ftsIndexCount >= 509)

		// 1. Prefix FTS4 search
		val (frierenIds, frierenMicros) = libraryRepo.searchMangaIdsFast("Frier")
		assertTrue("FTS4 prefix search should match Sousou no Frieren", frierenIds.contains(1001L))
		assertTrue("FTS4 search latency should be fast (< 50,000 us), got $frierenMicros us", frierenMicros < 50_000L)

		// 2. Specific stress item search
		val (stressIds, _) = libraryRepo.searchMangaIdsFast("Cyber Blade 442")
		assertTrue("FTS4 should locate Stress Series #442", stressIds.contains(100_442L))

		// 3. Special characters / punctuation sanitization resilience (must not throw SQLiteException)
		val (specialIds, _) = libraryRepo.searchMangaIdsFast("\"Chainsaw*\" [Part-2]?! (Denji)")
		assertTrue("Sanitized FTS4 query with special chars should still match Chainsaw Man", specialIds.contains(1007L))
	}

	@Test
	fun stressTestKotatsuBakAndMihonTachibkRoundTripAndMalformedResilience() = runBlocking {
		libraryRepo.ensureSeeded(seedDebugMockData = true)
		val initialMangaCount = db.getMangaDao().getAllWithTags().size
		assertEquals(9, initialMangaCount)

		// 1. Export & Restore Kotatsu .bak (ZIP)
		val bakBytes = backupRepo.exportKotatsuBak()
		assertTrue("Kotatsu .bak ZIP must have PK header", bakBytes.size > 128 && bakBytes[0] == 0x50.toByte())
		val bakSummary = backupRepo.restoreUniversalBackup(
			bytes = bakBytes,
			fileNameHint = "stress_test.bak",
			checkAndSwapDuplicates = true,
		)
		assertEquals(BackupFormat.KOTATSU_BAK, bakSummary.format)
		assertTrue(bakSummary.categoriesRestored >= 4)
		assertTrue(bakSummary.chaptersRestored >= 150)

		// 2. Restore Sample Mihon .tachibk (GZIP Protobuf) with unmapped/broken source flagging
		val sampleTachibk = backupRepo.createSampleMihonTachibkPayload()
		val tachibkSummary = backupRepo.restoreUniversalBackup(
			bytes = sampleTachibk,
			fileNameHint = "mihon_sample.tachibk",
			checkAndSwapDuplicates = true,
		)
		assertEquals(BackupFormat.MIHON_TACHIBK, tachibkSummary.format)
		assertEquals(2, tachibkSummary.mangaRestored)
		assertEquals(1, tachibkSummary.unmappedMihonSourcesFlaggedForAiFix)

		// 3. Export & Restore Mihon .json portable backup with Check & Swap active
		val jsonPayload = backupRepo.exportMihonJson()
		assertTrue(jsonPayload.contains("backupManga"))
		val jsonSummary = backupRepo.restoreUniversalBackup(
			bytes = jsonPayload.toByteArray(Charsets.UTF_8),
			fileNameHint = "mihon_export.json",
			checkAndSwapDuplicates = true,
		)
		assertEquals(BackupFormat.MIHON_JSON, jsonSummary.format)
		assertTrue("Check & Swap should merge existing duplicates during JSON restore", jsonSummary.duplicatesSwappedOrMerged >= 9)

		// 4. Malformed / Empty Payload Resilience
		val emptySummary = backupRepo.restoreUniversalBackup(ByteArray(0), "empty.bak")
		assertEquals(0, emptySummary.mangaRestored)
		val corruptedZipSummary = backupRepo.restoreUniversalBackup(
			byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0xFF.toByte(), 0x12),
			"corrupted.bak",
		)
		assertEquals(0, corruptedZipSummary.mangaRestored)
	}

	@Test
	fun stressTestAiDeduplicationEnMasseAndFixItMigration() = runBlocking {
		libraryRepo.ensureSeeded(seedDebugMockData = true)
		val initialItems = libraryRepo.observeLibraryItems().first()
		assertEquals(9, initialItems.size)

		// 1. Detect initial cross-source duplicates (Frieren 1001/1002 & Dungeon Meshi 1003/1004)
		val duplicates = aiEngine.detectDuplicates(initialItems, emptySet())
		assertTrue("Should detect at least 2 cross-source duplicate pairs", duplicates.size >= 2)

		// 2. Resolve all duplicates En Masse using MOST_CHAPTERS
		val mergedCount = aiEngine.resolveAllDuplicatesEnMasse(duplicates, BatchDeduplicationRule.MOST_CHAPTERS)
		assertEquals(2, mergedCount)

		val afterDedupItems = libraryRepo.observeLibraryItems().first()
		assertEquals(7, afterDedupItems.size)

		// Verify Frieren winner (1001L) preserved union of categories (1L and 2L)
		val frierenWinner = afterDedupItems.find { it.manga.id == 1001L }
		assertNotNull(frierenWinner)
		assertTrue(frierenWinner!!.categoryIds.containsAll(setOf(1L, 2L)))

		// 3. Diagnose and repair broken series via Source Fix-It (using sandbox installed sources)
		val brokenProposals = aiEngine.diagnoseBrokenLibraryItems(afterDedupItems, includeDebugMock = true)
		assertEquals("Should detect 2 broken seeded series (Berserk & ORV)", 2, brokenProposals.size)

		val fixedCount = aiEngine.applyAllAiFixProposals(brokenProposals)
		assertEquals(2, fixedCount)

		val afterFixItems = libraryRepo.observeLibraryItems().first()
		val remainingBroken = aiEngine.diagnoseBrokenLibraryItems(afterFixItems, includeDebugMock = true)
		assertEquals("All broken series should be healthy after AI Fix-It batch", 0, remainingBroken.size)
	}

	@Test
	fun stressTestReaderColorMatrixAndBitmapUpscaler() {
		val srcBitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
		for (mode in AiUpscaleMode.entries) {
			val metrics = AiMangaPageUpscaler.computeMetrics(mode)
			assertNotNull(metrics.outputResolution)

			val matrix = AiMangaPageUpscaler.buildReaderColorMatrix(
				upscaleMode = mode,
				userBrightness = 0.15f,
				userContrast = 0.25f,
				invert = true,
				grayscale = true,
			)
			assertNotNull(matrix)

			val upscaled = AiMangaPageUpscaler.upscaleBitmap(srcBitmap, mode)
			if (mode == AiUpscaleMode.OFF) {
				assertEquals(40, upscaled.width)
				assertEquals(60, upscaled.height)
			} else {
				assertTrue(upscaled.width >= 40)
				assertTrue(upscaled.height >= 60)
			}
		}
	}

	@Test
	fun stressTestViewModelThemePersistenceNavLimitsAndKeiyoushiRepo() = runBlocking {
		val vm = KotatsuNeoViewModel(app)

		// 1. Test Appearance Theme Mode, Color Scheme Presets & AMOLED Pure Black
		for (preset in AppColorSchemePreset.entries) {
			vm.setColorSchemePreset(preset)
			assertEquals(preset, vm.colorSchemePreset.value)
		}
		vm.setThemeMode(AppThemeMode.LIGHT)
		assertEquals(AppThemeMode.LIGHT, vm.themeMode.value)
		assertFalse(vm.darkTheme.value)

		vm.setThemeMode(AppThemeMode.DARK)
		vm.togglePureOledBlack(true)
		assertEquals(AppThemeMode.DARK, vm.themeMode.value)
		assertTrue(vm.darkTheme.value)
		assertTrue(vm.pureOledBlack.value)

		// Recreate ViewModel to verify SharedPreferences persistence
		val vmReloaded = KotatsuNeoViewModel(app)
		assertEquals(AppThemeMode.DARK, vmReloaded.themeMode.value)
		assertTrue(vmReloaded.pureOledBlack.value)

		// 2. Test Navigation Island Tab Hard Limits (min 2, max 5, Library pinned)
		vm.setNavBarStyle(NavBarStyle.DEFAULT_DOCKED)
		assertEquals(NavBarStyle.DEFAULT_DOCKED, vm.navBarStyle.value)
		vm.setNavBarStyle(NavBarStyle.FLOATING_ISLAND)
		assertEquals(NavBarStyle.FLOATING_ISLAND, vm.navBarStyle.value)

		// Attempt to remove LIBRARY (must stay pinned)
		vm.toggleNavTab(AppNavTab.LIBRARY)
		assertTrue(AppNavTab.LIBRARY in vm.enabledNavTabs.value)

		// Remove tabs down to MIN_NAV_BAR_TABS (2)
		vm.toggleNavTab(AppNavTab.AI_STUDIO)
		vm.toggleNavTab(AppNavTab.EXPLORE)
		vm.toggleNavTab(AppNavTab.FEED)
		assertEquals(2, vm.enabledNavTabs.value.size)

		// Attempt to remove HISTORY when only 2 tabs remain (must enforce min 2 limit)
		vm.toggleNavTab(AppNavTab.HISTORY)
		assertEquals(2, vm.enabledNavTabs.value.size)

		// 3. Test Extension Repository URL normalization & real JSON parsing (zero spoofed fallback extensions)
		val keiyoushiRepo = extensionManager.addExtensionRepository(
			name = "Keiyoushi Official Repo",
			rawUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
			repoType = "MIHON_JSON",
		)
		assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo", keiyoushiRepo.baseUrl)
		val sampleIndexJson = """
			[
			  {"name":"Tachiyomi: MangaDex","pkg":"eu.kanade.tachiyomi.extension.en.mangadex","apk":"mangadex.apk","lang":"en","code":205,"version":"1.4.205","nsfw":0,"sources":[{"name":"MangaDex","lang":"en","id":"2499283573021220255","baseUrl":"https://mangadex.org"}]},
			  {"name":"Tachiyomi: Bato.to","pkg":"eu.kanade.tachiyomi.extension.en.batoto","apk":"batoto.apk","lang":"en","code":44,"version":"1.4.44","nsfw":0,"sources":[{"name":"Bato.to","lang":"en","id":"4401928374","baseUrl":"https://bato.to"}]}
			]
		""".trimIndent()
		val parsed = extensionManager.parseMihonIndexJson(sampleIndexJson, keiyoushiRepo)
		assertEquals(2, parsed.size)
		assertTrue("Uninstalled repo entries must NOT be spoofed as installed", parsed.none { it.isInstalled })
	}

	@Test
	fun stressTestDualModeHybridFeedAndGeminiSettingsSections() = runBlocking {
		val vm = KotatsuNeoViewModel(app)
		// Default end-user mode (debugSandboxEnabled = false) must have zero dummy catalog data
		assertFalse(vm.debugSandboxEnabled.value)
		assertTrue(
			"Default end-user catalogue must contain zero items",
			vm.extensionCatalog.isEmpty(),
		)

		// Enable Debug Sandbox Mode to test hybrid feed & catalog discovery
		vm.setDebugSandboxEnabled(true)
		libraryRepo.ensureSeeded(seedDebugMockData = true)
		val items = libraryRepo.observeLibraryItems().first { it.size >= 8 }
		val catalog = extensionManager.getExtensionCatalogItems(includeDebugMock = true)
		val normalizedSet = items.map { TitleNormalizer.normalize(it.manga.title) }.toSet()

		val hybridEntries = org.koitharu.kotatsu.ui.screens.buildHybridFeedEntries(
			feedUpdates = emptyList(),
			libraryItems = items,
			extensionCatalog = catalog,
			libraryNormalizedTitles = normalizedSet,
			defaultSource = "All Sources",
			searchQuery = "",
		)
		assertTrue("Hybrid feed in Debug Sandbox should combine 9 library updates + 4 expanded catalog entries", hybridEntries.size >= 12)
		assertTrue("Hybrid feed entries should include staggered aspect ratios for 2-column masonry", hybridEntries.map { it.aspectRatio }.distinct().size >= 3)
		assertTrue("Sandbox hybrid feed entries should include heat scores and comment counts", hybridEntries.all { it.baseHeatScore > 0 && it.baseCommentCount > 0 })

		val catalogDiscoveryEntries = hybridEntries.filter { it.catalogRef != null }
		assertTrue("2-Column Catalog Discovery grid should contain dedicated catalog series", catalogDiscoveryEntries.size >= 4)
		assertTrue("2-Column Catalog Discovery entries should carry AI Match % and total chapters", catalogDiscoveryEntries.all { it.matchPercent in 85..100 && it.totalChapters > 0 })

		val releaseBuckets = hybridEntries.map { it.releaseDateBucket }.distinct()
		assertTrue("1-Column Reddit [DISC] stream should group into release buckets", releaseBuckets.size >= 2)
		assertTrue("Sandbox entries should have scanlator and top comment previews", hybridEntries.all { it.scanlatorGroup.isNotBlank() && it.topCommentPreview.isNotBlank() })

		val settingsSections = org.koitharu.kotatsu.ui.screens.SettingsCategorySection.entries
		assertEquals(11, settingsSections.size)
	}

	@Test
	fun stressTestAtomicLibraryManagementAndStorageVacuum() = runBlocking {
		libraryRepo.ensureSeeded(seedDebugMockData = true)

		// 1. Verify TitleNormalizer memoized normalization & bigram cache consistency
		val norm1 = TitleNormalizer.normalize("Sousou no Frieren: Beyond Journey's End")
		val norm2 = TitleNormalizer.normalize("Sousou no Frieren: Beyond Journey's End")
		assertEquals(norm1, norm2)
		val sim = TitleNormalizer.similarity("Sousou no Frieren", "Frieren Beyond Journeys End")
		assertTrue(sim > 0.45f)

		// 2. Test atomic batchMoveToCategory (must replace previous category links instead of duplicating)
		libraryRepo.batchMoveToCategory(setOf(1001L), 3L)
		val afterMove = libraryRepo.observeLibraryItems().first().find { it.manga.id == 1001L }!!
		assertEquals(setOf(3L), afterMove.categoryIds)

		// 3. Test atomic batchMarkChaptersRead (syncs chapters, history.percent, and tracks.newChapters)
		libraryRepo.batchMarkChaptersRead(setOf(1001L), markRead = true)
		val afterMarkRead = libraryRepo.observeLibraryItems().first().find { it.manga.id == 1001L }!!
		assertEquals(0, afterMarkRead.unreadCount)
		assertEquals(1.0f, afterMarkRead.progressPercent, 0.01f)
		val track1001 = db.getAuxiliaryDao().getAllTracks().find { it.mangaId == 1001L }
		assertEquals(0, track1001?.newChapters ?: 0)

		libraryRepo.batchMarkChaptersRead(setOf(1001L), markRead = false)
		val afterMarkUnread = libraryRepo.observeLibraryItems().first().find { it.manga.id == 1001L }!!
		assertEquals(afterMarkUnread.totalChapters, afterMarkUnread.unreadCount)
		assertEquals(0.0f, afterMarkUnread.progressPercent, 0.01f)

		// 4. Test atomic batchSetDownloadedStatus (download & purge offline)
		libraryRepo.batchSetDownloadedStatus(setOf(1001L), isDownloaded = true)
		val afterDownload = libraryRepo.observeLibraryItems().first().find { it.manga.id == 1001L }!!
		assertEquals(afterDownload.totalChapters, afterDownload.downloadedCount)

		libraryRepo.batchSetDownloadedStatus(setOf(1001L), isDownloaded = false)
		val afterPurge = libraryRepo.observeLibraryItems().first().find { it.manga.id == 1001L }!!
		assertEquals(0, afterPurge.downloadedCount)

		// 5. Test atomic source migration
		val targetSeries = libraryRepo.observeLibraryItems().first().find { it.manga.id == 1006L }!!
		val migratedCount = libraryRepo.migrateMangaSourcesAtomically(
			listOf(Triple(targetSeries, "EXT:MangaDex (Mihon EN)", 45)),
		)
		assertEquals(1, migratedCount)
		val migratedItem = libraryRepo.observeLibraryItems().first().find { it.manga.id == 1006L }!!
		assertEquals("EXT:MangaDex (Mihon EN)", migratedItem.manga.source)
		assertTrue(migratedItem.totalChapters >= 12)

		// 6. Test atomic removeMangaFromLibrary & storage vacuum
		libraryRepo.removeMangaFromLibrary(setOf(1009L))
		val remainingIds = libraryRepo.observeLibraryItems().first().map { it.manga.id }.toSet()
		assertFalse(1009L in remainingIds)

		val pruned = libraryRepo.optimizeAndVacuumStorage()
		assertTrue(pruned >= 0)
		val diag = libraryRepo.runSqliteDiagnostics()
		assertEquals(10, diag.coveringIndexesCount)
		assertEquals(remainingIds.size, diag.ftsIndexCount)
	}

	@Test
	fun stressTestCbzReaderPipelineSpreadSplitMarginCropAndDataSync() = runBlocking {
		libraryRepo.ensureSeeded(seedDebugMockData = true)

		// 1. Test real CBZ archive import & ZipInputStream bitmap decoding
		val importedManga = extensionManager.importCbzArchive(
			inputStream = java.io.ByteArrayInputStream(ByteArray(0)),
			fileName = "Vagabond_Masterpiece_Vol1.cbz",
		)
		assertTrue(importedManga.url.startsWith("cbz://"))

		val localIndexList = db.getAuxiliaryDao().observeLocalIndex().first()
		val indexedCbz = localIndexList.find { it.mangaId == importedManga.id }
		assertNotNull("Imported CBZ must be registered in local_index", indexedCbz)

		val cbzFile = File(indexedCbz!!.path)
		assertTrue("CBZ file must exist on disk", cbzFile.exists() && cbzFile.length() > 512L)
		val totalCbzPages = org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.countPagesInCbzFile(cbzFile)
		assertEquals(12, totalCbzPages)

		val decodedFirstPage = org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.decodePageFromCbzFile(cbzFile, 0)
		assertNotNull("ZipInputStream should decode real Bitmap from page 1 of CBZ", decodedFirstPage)

		// 2. Test automatic white margin crop & double-page spread detection/splitting on page 6 (climax spread)
		val processedSpread = org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.loadAndProcessPage(
			context = app,
			mangaId = importedManga.id,
			mangaTitle = importedManga.title,
			chapterNumber = 1,
			pageNumber = 6,
			autoCropMargins = true,
			autoDetectSpread = true,
			isRtl = true,
			cbzUrlHint = importedManga.url,
		)
		assertTrue("Page 6 in CBZ should be auto-detected as a double-page spread", processedSpread.isDoubleSpread)
		assertTrue("White margin auto-crop should trim outer border pixels", processedSpread.cropMetrics.trimmedAreaPercent > 1.0f)
		assertNotNull("Spread should produce leftHalfBitmap", processedSpread.leftHalfBitmap)
		assertNotNull("Spread should produce rightHalfBitmap", processedSpread.rightHalfBitmap)

		// 3. Test Foreground DataSync batch CBZ downloader & background chapter sync coordinator
		val libraryItems = libraryRepo.observeLibraryItems().first()
		val completedDownloads = org.koitharu.kotatsu.core.sync.KotatsuSyncAndDownloadCoordinator.executeBatchCbzDownloads(
			context = app,
			db = db,
			items = libraryItems.take(2),
		)
		assertEquals(2, completedDownloads)
		val queue = org.koitharu.kotatsu.core.sync.KotatsuSyncAndDownloadCoordinator.downloadQueue.value
		assertTrue(queue.size >= 2)
		assertTrue(queue.all { it.sizeBytes > 0L })

		val syncedChapter = org.koitharu.kotatsu.core.sync.KotatsuSyncAndDownloadCoordinator.syncLibraryChaptersNow(
			context = app,
			db = db,
			libraryItems = libraryItems,
			notificationsEnabled = true,
		)
		assertNotNull("Background chapter sync should insert a new simulpub chapter", syncedChapter)
		val updatedTrack = db.getAuxiliaryDao().getAllTracks().find { it.mangaId == syncedChapter!!.mangaId }
		assertNotNull("Background chapter sync must also update TrackEntity for unread/new badges", updatedTrack)
		assertTrue("TrackEntity newChapters should be >= 1 after sync", (updatedTrack?.newChapters ?: 0) >= 1)
	}

	@Test
	fun stressTestCbzNaturalSortingZipSlipMacOsForksAndComicInfo(): Unit = runBlocking {
		// 1. Verify natural alphanumeric sorting (page_2.png < page_10.png)
		val messyEntries = listOf("ch1/page_10.png", "ch1/page_2.png", "ch1/page_1.png", "ch1/page_20.png")
		val sortedEntries = messyEntries.sortedWith(org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.naturalPageNameComparator)
		assertEquals(
			listOf("ch1/page_1.png", "ch1/page_2.png", "ch1/page_10.png", "ch1/page_20.png"),
			sortedEntries,
		)

		// 2. Verify Zip-Slip ("../"), macOS resource forks ("__MACOSX/"), and hidden files ("._001.png") are rejected
		assertFalse(org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.isValidArchiveImageEntry("../evil.png", false))
		assertFalse(org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.isValidArchiveImageEntry("__MACOSX/._001.png", false))
		assertFalse(org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.isValidArchiveImageEntry("vol1/.DS_Store", false))
		assertFalse(org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.isValidArchiveImageEntry("vol1/._page1.jpg", false))
		assertTrue(org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.isValidArchiveImageEntry("vol1/page_01.webp", false))

		// 3. Build a synthetic CBZ with out-of-order pages, __MACOSX junk, Zip-Slip entry, and rich ComicInfo.xml
		val customCbzFile = File(app.cacheDir, "Test_ComicInfo_Archive.cbz")
		java.io.FileOutputStream(customCbzFile).use { fos ->
			java.util.zip.ZipOutputStream(fos).use { zos ->
				val comicXml = """
					<?xml version="1.0" encoding="utf-8"?>
					<ComicInfo>
					  <Series>Blame! Master Edition</Series>
					  <Number>4</Number>
					  <PageCount>2</PageCount>
					  <Writer>Tsutomu Nihei</Writer>
					  <Summary>Killy traverses the Megastructure in search of Net Terminal Genes.</Summary>
					  <Manga>YesAndRightToLeft</Manga>
					</ComicInfo>
				""".trimIndent()
				zos.putNextEntry(java.util.zip.ZipEntry("ComicInfo.xml"))
				zos.write(comicXml.toByteArray())
				zos.closeEntry()

				// Junk entries that must be ignored
				zos.putNextEntry(java.util.zip.ZipEntry("__MACOSX/._page_1.png"))
				zos.write(ByteArray(16))
				zos.closeEntry()

				zos.putNextEntry(java.util.zip.ZipEntry("../traversal_attack.png"))
				zos.write(ByteArray(16))
				zos.closeEntry()

				// Out-of-order real pages: page_10.png (wide spread) written BEFORE page_2.png (single page)
				val spreadBmp = org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.createRealMangaPageBitmap(
					mangaTitle = "Blame!",
					chapterNumber = 4,
					pageNumber = 10,
					isWideSpread = true,
					baseWidth = 720,
					baseHeight = 500,
				)
				zos.putNextEntry(java.util.zip.ZipEntry("page_10.png"))
				zos.write(org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.encodeBitmapToPngBytes(spreadBmp))
				zos.closeEntry()

				val singleBmp = org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.createRealMangaPageBitmap(
					mangaTitle = "Blame!",
					chapterNumber = 4,
					pageNumber = 2,
					isWideSpread = false,
					baseWidth = 400,
					baseHeight = 560,
				)
				zos.putNextEntry(java.util.zip.ZipEntry("page_2.png"))
				zos.write(org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.encodeBitmapToPngBytes(singleBmp))
				zos.closeEntry()
			}
		}

		// Should count only the 2 valid images (ignoring ComicInfo.xml, __MACOSX, and ../traversal_attack.png)
		assertEquals(2, org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.countPagesInCbzFile(customCbzFile))

		// Natural sort must put page_2.png at index 0 (width 400 < height 560) and page_10.png at index 1 (width 720 > height 500)
		val firstDecoded = org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.decodePageFromCbzFile(customCbzFile, 0)!!
		val secondDecoded = org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.decodePageFromCbzFile(customCbzFile, 1)!!
		assertTrue("Index 0 must be page_2.png (portrait)", firstDecoded.width < firstDecoded.height)
		assertTrue("Index 1 must be page_10.png (wide spread)", secondDecoded.width > secondDecoded.height)

		// Verify ComicInfo.xml metadata extraction & importCbzArchive integration
		val imported = customCbzFile.inputStream().use {
			extensionManager.importCbzArchive(it, "Raw_Filename_Ignored.cbz")
		}
		assertEquals("Blame! Master Edition", imported.title)
		assertEquals("Tsutomu Nihei", imported.authors)
		assertTrue(imported.description.contains("Megastructure"))
		customCbzFile.delete()
	}

	@Test
	fun stressTestLargeKeiyoushiJsonAndEdgeCaseBitmaps(): Unit = runBlocking {
		// 1. Stress test parsing 600 Keiyoushi JSON entries (verifying 24-cap removal + blank-entry resilience)
		val sb = StringBuilder("[")
		for (i in 1..600) {
			if (i > 1) sb.append(",")
			sb.append(
				"""{"name":"Tachiyomi: Source $i","pkg":"eu.kanade.tachiyomi.extension.en.src$i","apk":"src$i.apk","lang":"en","version":"1.4.$i","nsfw":${if (i % 10 == 0) 1 else 0},"sources":[{"id":"${1000000L + i}"}]}""",
			)
		}
		sb.append("]")
		val dummyRepo = org.koitharu.kotatsu.core.db.entity.ExtensionRepoEntity(
			repoId = 99L,
			name = "Stress Repo",
			baseUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
			repoType = "MIHON_JSON",
			isEnabled = true,
			extensionCount = 600,
			lastSyncedAt = System.currentTimeMillis(),
		)
		val parsedExts = extensionManager.parseMihonIndexJson(sb.toString(), dummyRepo)
		assertEquals("Must parse all 600 extensions without 24-item truncation", 600, parsedExts.size)

		// 2. Solid white & solid black blank separator pages must not crash or over-crop in cropUniformMargins
		val solidWhite = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
		solidWhite.eraseColor(android.graphics.Color.WHITE)
		val (croppedWhite, metricsWhite) = org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.cropUniformMargins(solidWhite)
		assertEquals(400, croppedWhite.width)
		assertEquals(600, croppedWhite.height)
		assertEquals(0f, metricsWhite.trimmedAreaPercent, 0.01f)

		val tinyBitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
		val (croppedTiny, _) = org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.cropUniformMargins(tinyBitmap)
		assertEquals(12, croppedTiny.width)
	}

	@Test
	fun stressTestExtensionDownloaderFileProviderAndDynamicCatalog(): Unit = runBlocking {
		// 1. Verify production mode (includeDebugMock = false) has 0 spoofed extensions and 0 fake catalog items
		assertTrue(
			"Default end-user catalog (includeDebugMock = false) must have zero dummy items",
			extensionManager.getExtensionCatalogItems(includeDebugMock = false).isEmpty(),
		)
		assertTrue(
			"Default end-user extension discovery (includeDebugMock = false) must have zero spoofed extensions",
			extensionManager.discoverExtensions(emptyList(), includeDebugMock = false).isEmpty(),
		)

		// 2. Verify Debug Sandbox isolation (includeDebugMock = true)
		val initialCatalogSize = extensionManager.getExtensionCatalogItems(includeDebugMock = true).size
		val initialDiscovered = extensionManager.discoverExtensions(emptyList(), includeDebugMock = true)
		val aquaBefore = initialDiscovered.find { it.pkgName == "eu.kanade.tachiyomi.extension.en.aquamanga" }
		assertNotNull(aquaBefore)
		assertTrue("Aqua Manga in Debug Sandbox should initially flag hasUpdate", aquaBefore!!.hasUpdate)
		assertEquals("1.4.58", aquaBefore.latestVersionName)

		val asuraCandidate = initialDiscovered.find { it.pkgName == "eu.kanade.tachiyomi.extension.en.asurascans" }
		assertNotNull("Asura Scans should be in Sandbox Available list", asuraCandidate)
		assertFalse(asuraCandidate!!.isInstalled)

		val progressSteps = mutableListOf<Int>()
		val installTask = extensionManager.downloadAndInstallExtension(
			ext = asuraCandidate,
			includeDebugMock = true,
		) { task ->
			progressSteps.add(task.progressPercent)
		}
		assertEquals(org.koitharu.kotatsu.core.model.ExtensionInstallStatus.INSTALLED, installTask.status)
		assertEquals(100, installTask.progressPercent)
		assertTrue("Progress callbacks should report intermediate steps", progressSteps.isNotEmpty())

		// Production catalog MUST STILL BE EMPTY even after sandbox install!
		assertTrue(
			"Sandbox install must never leak into production catalog (includeDebugMock = false)",
			extensionManager.getExtensionCatalogItems(includeDebugMock = false).isEmpty(),
		)

		val afterInstallDiscovered = extensionManager.discoverExtensions(emptyList(), includeDebugMock = true)
		val asuraAfter = afterInstallDiscovered.find { it.pkgName == "eu.kanade.tachiyomi.extension.en.asurascans" }!!
		assertTrue("Asura Scans must be marked installed inside Debug Sandbox", asuraAfter.isInstalled)

		val expandedCatalog = extensionManager.getExtensionCatalogItems(includeDebugMock = true)
		assertTrue("Sandbox install should expand sandbox catalog", expandedCatalog.size > initialCatalogSize)

		// 3. Verify GitHub repository shorthand normalization
		assertEquals(
			"https://raw.githubusercontent.com/keiyoushi/extensions/repo",
			extensionManager.normalizeGitHubRepoUrl("keiyoushi/extensions"),
		)
		assertEquals(
			"https://raw.githubusercontent.com/keiyoushi/extensions/repo",
			extensionManager.normalizeGitHubRepoUrl("https://github.com/keiyoushi/extensions/tree/repo"),
		)
		assertEquals(
			"https://raw.githubusercontent.com/Suwayomi/tachiyomi-extension/repo",
			extensionManager.normalizeGitHubRepoUrl("Suwayomi/tachiyomi-extension@repo/index.min.json"),
		)

		// 4. Verify Creator Program compliant zero-permission HTTPS APK download/install intent & share intent
		val apkInstallerIntent = extensionManager.createApkPackageInstallerIntent(asuraAfter)
		assertEquals(android.content.Intent.ACTION_VIEW, apkInstallerIntent.action)
		val unknownSourcesIntent = extensionManager.createManageUnknownAppSourcesIntent()
		assertEquals("package:${app.packageName}", unknownSourcesIntent.dataString)
		val shareIntent = extensionManager.createFileProviderShareIntent(asuraAfter)
		assertNotNull("createFileProviderShareIntent must build a chooser intent for cached sandbox archive", shareIntent)

		// 5. Update Aqua Manga in sandbox and verify hasUpdate clears
		extensionManager.downloadAndInstallExtension(aquaBefore, includeDebugMock = true)
		val aquaAfterUpdate = extensionManager.discoverExtensions(emptyList(), includeDebugMock = true)
			.find { it.pkgName == "eu.kanade.tachiyomi.extension.en.aquamanga" }!!
		assertFalse("Aqua Manga hasUpdate must be false after updating", aquaAfterUpdate.hasUpdate)
		assertEquals("1.4.58", aquaAfterUpdate.versionName)

		// 6. Uninstall Asura Scans in sandbox and verify it returns to Available
		val uninstalled = extensionManager.uninstallExtension(asuraAfter)
		assertTrue(uninstalled.getOrDefault(false))
		val afterUninstall = extensionManager.discoverExtensions(emptyList(), includeDebugMock = true)
			.find { it.pkgName == "eu.kanade.tachiyomi.extension.en.asurascans" }!!
		assertFalse("Asura Scans must return to Available after uninstall", afterUninstall.isInstalled)
	}

	@Test
	fun stressTestPhase1To3NemuriHardeningMergeEngineAndExtensionParity(): Unit = runBlocking {
		libraryRepo.ensureSeeded(seedDebugMockData = true)

		// 1. Phase 1.1: TitleNormalizer.toNormalizedSlug & toCanonicalChapterKey
		val slugA = org.koitharu.kotatsu.core.util.TitleNormalizer.toNormalizedSlug("The Dangers in My Heart [Official] (Full Color)")
		val slugB = org.koitharu.kotatsu.core.util.TitleNormalizer.toNormalizedSlug("Dangers in My Heart! (Digital Manga Edition)")
		assertEquals("thedangersinmyheart".removePrefix("the"), slugA)
		assertEquals(slugA, slugB)
		assertEquals(1450, org.koitharu.kotatsu.core.util.TitleNormalizer.toCanonicalChapterKey(14.5f, null))
		assertEquals(4250, org.koitharu.kotatsu.core.util.TitleNormalizer.toCanonicalChapterKey(-1f, "Ch. 42.5 - Special Epilogue"))

		// 2. Phase 1.2: Verify seeded manga_aliases, manga_merge_links, and library_fast_view
		val aliases = db.getAuxiliaryDao().getAliasesForManga(1001L)
		assertTrue("Seeded Frieren should have alt-title alias in manga_aliases", aliases.isNotEmpty())
		val mergeLinks = db.getAuxiliaryDao().getMergeLinksForParent(1001L)
		assertEquals(1, mergeLinks.size)
		assertEquals("Kirei Cake", mergeLinks.first().preferredScanlator)
		val fastViews = db.getAuxiliaryDao().observeAllFastViews().first()
		assertTrue("library_fast_view should be populated for seeded manga", fastViews.size >= 9)

		// 3. MangaRepository.Factory (Pure Architecture — zero HybridMangaRepositoryFactory or cross-source MangaDex injection)
		val factory = MangaRepository.Factory(app)
		val localRepo = factory.createAsync(MangaSource("LOCAL"))
		val mihonLazyRepo = factory.createAsync(MangaSource("MIHON_2499283573021220255"))
		val uninstalledRepo = factory.createAsync(MangaSource("UNINSTALLED_EXTERNAL_SOURCE"))
		assertTrue("LOCAL source must resolve to LocalMangaRepository", localRepo is LocalMangaRepository)
		assertTrue("MIHON_ source must resolve to LazyMihonMangaRepository", mihonLazyRepo is LazyMihonMangaRepository)
		assertTrue("Uninstalled source must resolve to EmptyMangaRepository (zero fake manga)", uninstalledRepo is EmptyMangaRepository)
		assertTrue("Uninstalled source getList must return emptyList", uninstalledRepo.getList(0).isEmpty())
		assertTrue("KotatsuExternalProviderScanner must return empty list when no provider APKs installed", KotatsuExternalProviderScanner.discoverProviders(app).isEmpty())

		// 4. MergedChapterAggregator (Multi-Source Chapter Merge & Deduplicator)
		val primarySource = MangaSource("MIHON_1001")
		val secondarySource = MangaSource("MIHON_1002")
		val primaryChapters = listOf(
			org.koitharu.kotatsu.parsers.model.MangaChapter(
				id = 101L,
				name = "Chapter 10",
				number = 10f,
				volume = 1,
				url = "/ch/10-speedscan",
				scanlator = "SpeedScan",
				uploadDate = 1_700_000_000_000L,
				branch = null,
				source = primarySource,
			),
			org.koitharu.kotatsu.parsers.model.MangaChapter(
				id = 102L,
				name = "Chapter 11",
				number = 11f,
				volume = 1,
				url = "/ch/11",
				scanlator = "SpeedScan",
				uploadDate = 1_700_086_400_000L,
				branch = null,
				source = primarySource,
			),
		)
		val secondaryChapters = listOf(
			org.koitharu.kotatsu.parsers.model.MangaChapter(
				id = 201L,
				name = "Chapter 10: High Quality",
				number = 10f,
				volume = 1,
				url = "/ch/10-hq",
				scanlator = "Kirei Cake",
				uploadDate = 1_699_000_000_000L,
				branch = null,
				source = secondarySource,
			),
			org.koitharu.kotatsu.parsers.model.MangaChapter(
				id = 203L,
				name = "Chapter 12",
				number = 12f,
				volume = 1,
				url = "/ch/12",
				scanlator = "Kirei Cake",
				uploadDate = 1_700_172_800_000L,
				branch = null,
				source = secondarySource,
			),
		)
		val aggregator = org.koitharu.kotatsu.details.domain.MergedChapterAggregator(factory)
		val dummyManga = org.koitharu.kotatsu.parsers.model.Manga(
			id = 1001L,
			title = "Sousou no Frieren",
			url = "/frieren",
			source = primarySource,
			chapters = primaryChapters,
		)
		val mergedChapters = aggregator.fetchMergedChapters(
			listOf(
				org.koitharu.kotatsu.details.domain.MergeSourceCandidate(
					manga = dummyManga,
					priorityWeight = 100,
					preferredScanlator = null,
				),
				org.koitharu.kotatsu.details.domain.MergeSourceCandidate(
					manga = dummyManga.copy(id = 1002L, source = secondarySource, chapters = secondaryChapters),
					priorityWeight = 90,
					preferredScanlator = "Kirei Cake",
				),
			),
		)
		assertEquals("Should merge and deduplicate into 3 chapters (10, 11, 12)", 3, mergedChapters.size)
		val ch10Winner = mergedChapters.first { it.number == 10f }
		assertEquals("Chapter 10 must select preferred scanlator Kirei Cake", "Kirei Cake", ch10Winner.scanlator)

		// 5. Phase 3.1: pruneOrphanedShortcutIcons
		val shortcutDir = File(app.filesDir, "shortcuts").apply { mkdirs() }
		val orphanFile = File(shortcutDir, "expired_manga_99999.png").apply { writeBytes(ByteArray(32)) }
		assertTrue(orphanFile.exists())
		org.koitharu.kotatsu.core.os.pruneOrphanedShortcutIcons(app)
		assertFalse("Orphaned launcher shortcut icon must be deleted", orphanFile.exists())

		// 6. Phase 3.2: HistoryRepository.markChapterReadFromFeed accurate progress calculation
		val historyRepo = org.koitharu.kotatsu.history.data.HistoryRepository(db)
		db.getChaptersHistoryDao().deleteByMangaId(1009L)
		historyRepo.markChapterReadFromFeed(mangaId = 1009L, chapterId = 1009_001L, totalChapterCount = 12)
		val hist1009 = db.getHistoryDao().find(1009L)
		assertNotNull(hist1009)
		assertTrue("Progress percent must be < 1.0f when not all chapters are read", hist1009!!.percent < 1.0f)

		// 7. Module 2: Dual Coil ImageLoader & MangaCoverRequest.build
		val coverLoader = org.koitharu.kotatsu.core.ui.image.NemuriImageLoaderModule.provideCoverImageLoader(app)
		val pageLoader = org.koitharu.kotatsu.core.ui.image.NemuriImageLoaderModule.providePageImageLoader(app)
		assertEquals(500L * 1024L * 1024L, coverLoader.diskCache?.maxSizeBytes)
		assertEquals(1536L * 1024L * 1024L, pageLoader.diskCache?.maxSizeBytes)
		assertTrue(coverLoader.diskCache!!.directory.absolutePath.contains("covers_persistent_cache"))
		assertTrue(pageLoader.diskCache!!.directory.absolutePath.contains("reader_pages_lru"))

		val coverReq = org.koitharu.kotatsu.core.ui.image.MangaCoverRequest.build(
			context = app,
			manga = dummyManga.copy(coverUrl = "https://cdn.nemuri.org/covers/1001.jpg"),
		)
		assertEquals("nemuri_cover_1001", coverReq.memoryCacheKey)
		assertEquals("nemuri_cover_1001", coverReq.placeholderMemoryCacheKey)
		assertEquals(450, coverReq.size.width)
		assertEquals(675, coverReq.size.height)
		assertTrue(coverReq.allowHardware)
		assertFalse(coverReq.crossfadeEnabled)

		// 8. Module 3: SmartFeedSyncCoordinator Cadence Filter & Batch Sync
		val feedCoordinator = org.koitharu.kotatsu.tracker.work.SmartFeedSyncCoordinator(
			db = db,
			repositoryFactory = factory,
		)
		val sampleMangaEntity = db.getMangaDao().find(1001L)!!.manga
		val finishedEntity = sampleMangaEntity.copy(state = "FINISHED")
		assertTrue(
			"FINISHED manga must be skipped during routine sync",
			feedCoordinator.shouldSkipBySmartCadence(finishedEntity, null, System.currentTimeMillis(), isManualForceRefresh = false),
		)
		assertFalse(
			"Manual force refresh must not skip FINISHED manga",
			feedCoordinator.shouldSkipBySmartCadence(finishedEntity, null, System.currentTimeMillis(), isManualForceRefresh = true),
		)
		val highUnreadFastView = org.koitharu.kotatsu.core.db.entity.LibraryFastViewEntity(
			mangaId = 1001L,
			unreadCount = 20,
			lastPolledAt = System.currentTimeMillis() - 3_600_000L, // 1 hour ago (< 24h)
		)
		assertTrue(
			"Manga with >=15 unread chapters polled <24h ago must be skipped",
			feedCoordinator.shouldSkipBySmartCadence(sampleMangaEntity, highUnreadFastView, System.currentTimeMillis(), isManualForceRefresh = false),
		)
		val syncResult = feedCoordinator.syncLibraryFeed(isManualForceRefresh = false)
		assertTrue(syncResult.totalLibraryCount >= 9)

		// 9. Module 4: SilentFailoverChapterLoader & ReaderPagePreloader backed by real LocalMangaRepository CBZ archive
		val cbzBackupManga = extensionManager.importCbzArchive(
			inputStream = java.io.ByteArrayInputStream(ByteArray(0)),
			fileName = "Frieren_Local_Backup_Ch10.cbz",
		)
		db.withTransaction {
			db.getMangaDao().upsertManga(
				cbzBackupManga.copy(
					source = "LOCAL",
					normalizedSlug = sampleMangaEntity.normalizedSlug,
				),
			)
			db.getChaptersDao().upsertAll(
				listOf(
					ChapterEntity(
						chapterId = cbzBackupManga.id * 1000L + 10L,
						mangaId = cbzBackupManga.id,
						title = "Chapter 10",
						number = 10f,
						volume = 1,
						url = cbzBackupManga.url,
						scanlator = "Local Vault",
						uploadDate = System.currentTimeMillis(),
						branch = null,
						index = 9,
					),
					ChapterEntity(
						chapterId = cbzBackupManga.id * 1000L + 11L,
						mangaId = cbzBackupManga.id,
						title = "Chapter 11",
						number = 11f,
						volume = 1,
						url = cbzBackupManga.url,
						scanlator = "Local Vault",
						uploadDate = System.currentTimeMillis(),
						branch = null,
						index = 10,
					),
				),
			)
			db.getAuxiliaryDao().upsertMergeLink(
				MangaMergeLinkEntity(
					parentMangaId = 1001L,
					childMangaId = cbzBackupManga.id,
					sourceName = "LOCAL",
					priorityWeight = 10,
					preferredScanlator = "Local Vault",
					createdAt = System.currentTimeMillis(),
				),
			)
		}

		val brokenPrimarySource = MangaSource("MIHON_BROKEN_SOURCE")
		val healthyLocalSource = MangaSource("LOCAL")
		val failoverLoader = org.koitharu.kotatsu.reader.domain.SilentFailoverChapterLoader(
			db = db,
			repositoryFactory = factory,
		)
		val failingChapter10 = org.koitharu.kotatsu.parsers.model.MangaChapter(
			id = 999_010L,
			name = "Chapter 10",
			number = 10f,
			source = brokenPrimarySource,
		)
		val recoveredPages = failoverLoader.getPagesWithFailover(failingChapter10, parentMangaId = 1001L)
		assertTrue("SilentFailoverChapterLoader must recover real CBZ pages from linked LOCAL fallback source", recoveredPages.isNotEmpty())

		val preloader = org.koitharu.kotatsu.reader.domain.ReaderPagePreloader(
			context = app,
			pageImageLoader = pageLoader,
			chapterLoader = failoverLoader,
		)
		val currentPages = (1..10).map { idx ->
			org.koitharu.kotatsu.parsers.model.MangaPage(
				id = 100L + idx,
				url = "https://cdn.nemuri.org/ch10/page_$idx.jpg",
				source = healthyLocalSource,
			)
		}
		val nextCh11 = org.koitharu.kotatsu.parsers.model.MangaChapter(
			id = cbzBackupManga.id * 1000L + 11L,
			name = "Chapter 11",
			number = 11f,
			url = cbzBackupManga.url,
			source = healthyLocalSource,
		)
		// Trigger at currentIndex = 7 (which is >= pages.size - 3 == 7), pre-resolving nextCh11
		preloader.prefetchWindowSuspend(
			currentChapter = failingChapter10.copy(id = 1001_010L),
			pages = currentPages,
			currentIndex = 7,
			nextChapter = nextCh11,
		)
		assertNotNull("N+1 page must be in memory cache", pageLoader.memoryCache?.get("page_1001010_8"))
		val preloadedNext = preloader.consumePreloadedChapter(nextCh11.id)
		assertNotNull("Next chapter must be preloaded at totalPages - 3 seam", preloadedNext)
		assertTrue(preloadedNext!!.isNotEmpty())
		assertNotNull("First page of next chapter must be pre-decoded into memory cache", pageLoader.memoryCache?.get("page_${nextCh11.id}_0"))

		// 10. Module 4.3 & Module 5: CompletionFilterState Tri-State cycle & WidePageProcessor
		var triState = org.koitharu.kotatsu.list.ui.CompletionFilterState.ALL
		triState = triState.next()
		assertEquals(org.koitharu.kotatsu.list.ui.CompletionFilterState.COMPLETED_ONLY, triState)
		triState = triState.next()
		assertEquals(org.koitharu.kotatsu.list.ui.CompletionFilterState.ONGOING_ONLY, triState)
		triState = triState.next()
		assertEquals(org.koitharu.kotatsu.list.ui.CompletionFilterState.ALL, triState)

		val wideSpreadBmp = org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.createRealMangaPageBitmap(
			mangaTitle = "Spread Test",
			chapterNumber = 1,
			pageNumber = 2,
			isWideSpread = true,
			baseWidth = 800,
			baseHeight = 500,
		)
		val spreadFile = File(app.cacheDir, "wide_spread_test.png")
		spreadFile.writeBytes(org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.encodeBitmapToPngBytes(wideSpreadBmp))
		assertTrue("800x500 image file must be detected as wide spread", org.koitharu.kotatsu.reader.domain.WidePageProcessor.isWideSpread(spreadFile))
		val rtlFirstHalf = org.koitharu.kotatsu.reader.domain.WidePageProcessor.decodeSplitHalf(
			imageFile = spreadFile,
			isFirstHalfInReadingOrder = true,
			isRtlReading = true,
		)
		assertNotNull(rtlFirstHalf)
		assertEquals(400, rtlFirstHalf!!.width)
		assertEquals(500, rtlFirstHalf.height)
		val rotated = org.koitharu.kotatsu.reader.domain.WidePageProcessor.rotateWideBitmap(wideSpreadBmp)
		assertEquals(500, rotated.width)
		assertEquals(800, rotated.height)
		spreadFile.delete()
	}

	@Test
	fun stressTestPhase2TachibkSmartMergeTrackerSyncCloudflareAndLocalScanner(): Unit = runBlocking {
		libraryRepo.ensureSeeded(seedDebugMockData = true)

		// 1. Module 1: TachibkSmartMergeImporter (Tier 1/2/3 Source Resolution + Cross-Source Merge Links)
		val importer = org.koitharu.kotatsu.backups.domain.TachibkSmartMergeImporter(db)
		val tier1 = importer.resolveBestSource(2499283573021220255L, "MangaDex (EN)", emptySet())
		assertEquals("MANGADEX", tier1.primarySourceName)
		assertEquals(org.koitharu.kotatsu.backups.domain.SourceResolutionTier.TIER_1_NATIVE_KOTATSU, tier1.tier)

		val tier2 = importer.resolveBestSource(555L, "CustomInstalledExt", setOf(555L))
		assertEquals("MIHON_555", tier2.primarySourceName)
		assertEquals(org.koitharu.kotatsu.backups.domain.SourceResolutionTier.TIER_2_INSTALLED_MIHON_EXTENSION, tier2.tier)

		val tier3 = importer.resolveBestSource(999L, "ObscureScanlator", emptySet())
		assertEquals(org.koitharu.kotatsu.backups.domain.SourceResolutionTier.TIER_3_UNINSTALLED_STUB_SOURCE, tier3.tier)

		val backupPayload = org.koitharu.kotatsu.backups.domain.BackupPayload(
			backupCategories = listOf(
				org.koitharu.kotatsu.backups.domain.BackupCategory(name = "Imported Classics", order = 10),
			),
			backupSources = listOf(
				org.koitharu.kotatsu.backups.domain.BackupSource(sourceId = 777L, name = "Asura Scans (EN)"),
				org.koitharu.kotatsu.backups.domain.BackupSource(sourceId = 888L, name = "RareMangaStub"),
			),
			backupManga = listOf(
				// Matches existing Sousou no Frieren (1001L) by slug -> must link into manga_merge_links and advance read progress!
				org.koitharu.kotatsu.backups.domain.BackupManga(
					source = 777L,
					url = "/series/sousou-no-frieren",
					title = "Sousou no Frieren [Official]",
					author = "Kanehito Yamada",
					status = 1,
					chapters = (1..30).map { chNum ->
						org.koitharu.kotatsu.backups.domain.BackupChapter(
							url = "/ch/$chNum",
							name = "Chapter $chNum",
							read = chNum <= 30,
							chapterNumber = chNum.toFloat(),
						)
					},
					tracking = listOf(
						org.koitharu.kotatsu.backups.domain.BackupTracking(
							syncId = 2,
							mediaId = 118586L,
							title = "Frieren: Beyond Journey's End",
							lastChapterRead = 30f,
						),
					),
				),
				// Brand-new series on uninstalled source -> preserved under Tier 3 stub
				org.koitharu.kotatsu.backups.domain.BackupManga(
					source = 888L,
					url = "/manga/witch-hat-atelier",
					title = "Witch Hat Atelier",
					author = "Kamome Shirahama",
					description = "Coco discovers the secret of magic.",
					genre = listOf("Fantasy", "Seinen"),
					status = 1,
					categories = listOf(10),
					chapters = listOf(
						org.koitharu.kotatsu.backups.domain.BackupChapter(
							url = "/wha/1",
							name = "Chapter 1",
							read = true,
							chapterNumber = 1f,
						),
						org.koitharu.kotatsu.backups.domain.BackupChapter(
							url = "/wha/2",
							name = "Chapter 2",
							read = false,
							chapterNumber = 2f,
						),
					),
				),
			),
		)

		val tachibkBytes = importer.exportToTachibkBytes(backupPayload)
		assertTrue("Exported .tachibk must have GZIP magic bytes", tachibkBytes.size > 32 && tachibkBytes[0] == 0x1F.toByte())
		val importReport = importer.importFromTachibkStream(
			inputStream = java.io.ByteArrayInputStream(tachibkBytes),
			installedMihonSourceIds = setOf(555L),
		)
		assertEquals(2, importReport.totalIncomingManga)
		assertEquals("Witch Hat Atelier should be newly inserted", 1, importReport.newlyInsertedManga)
		assertEquals("Sousou no Frieren should be linked as a cross-source merge child", 1, importReport.deduplicatedMergedLinksCreated)
		assertEquals(1, importReport.promotedToNativeKotatsuCount)
		assertEquals(1, importReport.preservedAsUninstalledStubCount)

		// Verify parent Frieren (1001L) had chapters 29 & 30 marked read from the merged backup
		val frierenFastView = db.getAuxiliaryDao().getFastView(1001L)
		assertNotNull(frierenFastView)
		assertEquals(30, frierenFastView!!.readChapters)

		// 2. Module 2: TrackerMetadataHydrator & ReverseTrackerSyncUseCase
		val trackingRepo = org.koitharu.kotatsu.tracker.domain.TrackingRepository(db)
		trackingRepo.registerRemoteMetadataOverride(
			mangaId = 1007L,
			metadata = org.koitharu.kotatsu.details.domain.CanonicalTrackerMetadata(
				canonicalId = "al:105778",
				primaryTitle = "Chainsaw Man",
				alternativeTitles = listOf("チェンソーマン", "CSM Part 2: Academy Saga"),
				description = "Official AniList synopsis for Chainsaw Man.",
				highResCoverUrl = "https://cdn.anilist.co/img/csm_xl.jpg",
				author = "Tatsuki Fujimoto",
				status = "ONGOING",
			),
		)
		val hydrator = org.koitharu.kotatsu.details.domain.TrackerMetadataHydrator(db, trackingRepo)
		val hydratedMeta = hydrator.fetchAndApplyCanonicalMetadata(
			mangaId = 1007L,
			overrideTitle = true,
			overrideCover = true,
			overrideDescription = true,
		).getOrThrow()
		assertEquals("al:105778", hydratedMeta.canonicalId)
		val updated1007 = db.getMangaDao().find(1007L)!!.manga
		assertEquals("al:105778", updated1007.canonicalId)
		assertEquals("https://cdn.anilist.co/img/csm_xl.jpg", updated1007.coverUrl)
		val aliases1007 = db.getMangaAliasesDao().findByMangaId(1007L)
		assertTrue("Hydrator must populate manga_aliases with alternative titles", aliases1007.size >= 2)

		// Reverse Tracker Sync: Solo Leveling: Ragnarok (1008L) has 15/22 read locally; remote tracker says 19 read
		val reverseSync = org.koitharu.kotatsu.tracker.domain.ReverseTrackerSyncUseCase(db, trackingRepo)
		val revSummary = reverseSync.syncFromRemoteTracker(mangaId = 1008L, explicitRemoteLastChapterRead = 19f)
		assertEquals(15f, revSummary.previousLocalHighestChapter, 0.01f)
		assertEquals(4, revSummary.newlyMarkedChaptersCount)
		assertEquals(3, revSummary.updatedUnreadCount)

		// 3. Module 3: CloudflareClearancePool Per-Domain Mutex (10 concurrent requests -> 1 headless solve)
		val loaderCtx = org.koitharu.kotatsu.parsers.MangaLoaderContext()
		val cfPool = org.koitharu.kotatsu.core.network.CloudflareClearancePool(app, loaderCtx)
		cfPool.customHeadlessSolver = { _, _ ->
			kotlinx.coroutines.delay(40L)
			"cf_clearance=verified_token_xyz987; path=/; domain=asuracomic.net"
		}
		val concurrentResults = coroutineScope {
			(1..10).map { idx ->
				async(Dispatchers.IO) {
					cfPool.acquireClearanceForUrl("https://asuracomic.net/series/chapter-$idx")
				}
			}.awaitAll()
		}
		assertTrue("All 10 concurrent coroutines must receive solved clearance", concurrentResults.all { it.solved && it.cfClearanceValue == "verified_token_xyz987" })
		assertEquals("Per-domain Mutex must execute headless solver exactly ONCE across 10 concurrent walls", 1, cfPool.totalHeadlessSolvesExecuted)

		// 4. Module 4: MihonLocalStorageScanner (Zero-copy /local & /downloads + PDF zero-copy rendering)
		val mihonRoot = File(app.cacheDir, "MihonTestRoot").apply { deleteRecursively(); mkdirs() }
		val mangaFolder = File(mihonRoot, "local/Lone Wolf and Cub").apply { mkdirs() }
		File(mangaFolder, "details.json").writeText(
			"""{"title":"Lone Wolf and Cub","author":"Kazuo Koike","artist":"Goseki Kojima","description":"Ogami Itto and Daigoro walk the assassin's road.","status":2,"genre":["Historical","Seinen","Samurai"]}""",
		)
		val ch1Dir = File(mangaFolder, "Chapter 1 - Theoni").apply { mkdirs() }
		val sampleBmp = org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.createRealMangaPageBitmap(
			mangaTitle = "Lone Wolf",
			chapterNumber = 1,
			pageNumber = 1,
			isWideSpread = false,
			baseWidth = 300,
			baseHeight = 450,
		)
		File(ch1Dir, "001.png").writeBytes(org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.encodeBitmapToPngBytes(sampleBmp))

		// Create a real 2-page PDF chapter inside mangaFolder
		val pdfFile = File(mangaFolder, "Chapter 2 - Winter Road.pdf")
		runCatching {
			val pdfDoc = android.graphics.pdf.PdfDocument()
			for (p in 1..2) {
				val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 450, p).create()
				val page = pdfDoc.startPage(pageInfo)
				page.canvas.drawColor(android.graphics.Color.LTGRAY)
				pdfDoc.finishPage(page)
			}
			java.io.FileOutputStream(pdfFile).use { pdfDoc.writeTo(it) }
			pdfDoc.close()
		}
		if (!pdfFile.exists() || pdfFile.length() < 16L) {
			pdfFile.writeText("%PDF-1.4\n1 0 obj << /Type /Page >> endobj\n2 0 obj << /Type /Page >> endobj\n%%EOF")
		}

		val localScanner = org.koitharu.kotatsu.local.data.MihonLocalStorageScanner(db)
		val scannedList = localScanner.scanAndIndexRootDirectories(listOf(mihonRoot), importIntoDatabase = true)
		assertEquals(1, scannedList.size)
		val scannedLoneWolf = scannedList.first()
		assertEquals("Lone Wolf and Cub", scannedLoneWolf.title)
		assertEquals("Kazuo Koike", scannedLoneWolf.author)
		assertEquals("FINISHED", scannedLoneWolf.status)
		assertEquals(2, scannedLoneWolf.chapters.size)
		mihonRoot.deleteRecursively()
	}

	@Test
	fun stressTestOnDeviceGeminiNanoRecapQuarantineAndNlSearchAst(): Unit = runBlocking {
		libraryRepo.ensureSeeded(seedDebugMockData = true)

		val aiEngine = org.koitharu.kotatsu.core.ai.NemuriAiCoreEngine()
		val recapUseCase = org.koitharu.kotatsu.details.ai.WhereWasIRecapUseCase(db, aiEngine)
		val nlSearchUseCase = org.koitharu.kotatsu.search.ai.NaturalLanguageLibrarySearchUseCase(db, aiEngine)

		// 1. Verify Strict Hardware Spoiler Quarantine:
		// Solo Leveling: Ragnarok (1008L) has 22 chapters, read up to chapter 15 (chapterId = 100815L)
		val m1008 = db.getMangaDao().find(1008L)!!
		val chapters1008 = db.getChaptersDao().findAll(1008L).sortedBy { it.number }
		val pausedCh15 = chapters1008[14] // Chapter 15 (0-indexed 14)
		val quarantined = recapUseCase.buildSpoilerQuarantinedContext(
			manga = m1008.manga,
			tags = m1008.tags,
			allChaptersSorted = chapters1008,
			pausedChapterId = pausedCh15.chapterId,
		)
		assertNotNull(quarantined)
		val (stoppedCh, trail, promptPayload) = quarantined!!
		assertEquals(15f, stoppedCh.number, 0.01f)
		assertEquals("Quarantined trail must be capped at last 12 read chapters (Ch 4..15)", 12, trail.size)
		assertEquals(4f, trail.first().number, 0.01f)
		assertEquals(15f, trail.last().number, 0.01f)
		for (unreadFutureCh in 16..22) {
			assertFalse(
				"Prompt payload MUST NEVER contain future unread Chapter $unreadFutureCh",
				promptPayload.contains("Ch. $unreadFutureCh:"),
			)
		}

		// 2. Verify Recap generation, 0ms SQLite cache (manga_ai_recaps), and live NPU streaming
		val statesFirstRun = mutableListOf<org.koitharu.kotatsu.details.ai.RecapUiState>()
		recapUseCase.observeOrGenerateRecap(mangaId = 1008L, forceShow = true, forceRegenerate = false).collect {
			statesFirstRun.add(it)
		}
		val readyState = statesFirstRun.last() as org.koitharu.kotatsu.details.ai.RecapUiState.Ready
		assertFalse("First run should generate fresh recap", readyState.fromCache)
		assertTrue("Recap must contain stopping point Chapter 15", readyState.markdown.contains("15"))

		// Second call without forceRegenerate must hit 0ms SQLite cache (fromCache = true)
		val statesCachedRun = mutableListOf<org.koitharu.kotatsu.details.ai.RecapUiState>()
		recapUseCase.observeOrGenerateRecap(mangaId = 1008L, forceShow = true, forceRegenerate = false).collect {
			statesCachedRun.add(it)
		}
		val cachedReady = statesCachedRun.single() as org.koitharu.kotatsu.details.ai.RecapUiState.Ready
		assertTrue("Second run at same chapter must be served in 0ms from manga_ai_recaps", cachedReady.fromCache)
		assertEquals(readyState.markdown, cachedReady.markdown)

		// 3. Verify Two-Stage Natural Language Library Search (Stage 1 Heuristic + Stage 2 Gemini Nano JSON AST)
		val query = "ongoing fantasy manga with under 10 unread that I haven't read in a week"
		val fastAst = nlSearchUseCase.parseFastHeuristic(query)
		assertEquals(org.koitharu.kotatsu.search.ai.SmartCompletionFilter.ONGOING_ONLY, fastAst.completionState)
		assertEquals(10, fastAst.maxUnread)
		assertEquals(7, fastAst.stalledDaysMin)
		assertTrue(fastAst.ftsKeywords.contains("fantasy"))

		// Simulate Gemini Nano AICore structured JSON output for Stage 2 semantic expansion
		com.google.mlkit.genai.prompt.Generation.customModelOverride = object : com.google.mlkit.genai.prompt.GenerativeModel {
			override suspend fun checkStatus(): Int = com.google.mlkit.genai.prompt.FeatureStatus.AVAILABLE
			override fun download() = kotlinx.coroutines.flow.emptyFlow<com.google.mlkit.genai.prompt.DownloadStatus>()
			override fun generateContentStream(request: com.google.mlkit.genai.prompt.GenerateContentRequest) =
				kotlinx.coroutines.flow.flowOf("• Premise: Hook.\n", "• Arc: Climactic.\n", "• Stopped: Ch. 15.")
			override suspend fun generateContent(request: com.google.mlkit.genai.prompt.GenerateContentRequest) =
				com.google.mlkit.genai.prompt.GenerateContentResponse(
					candidates = listOf(
						com.google.mlkit.genai.prompt.Candidate(
							text = """
							{
							  "ftsKeywords": ["frieren"],
							  "includedTags": ["Fantasy", "Adventure"],
							  "excludedTags": ["Horror"],
							  "completionState": "ONGOING_ONLY",
							  "minUnread": 1,
							  "maxUnread": 10,
							  "stalledDaysMin": null,
							  "sortOrder": "UNREAD_ASC"
							}
							""".trimIndent(),
						),
					),
				)
			override fun close() {}
		}

		try {
			val nanoAst = nlSearchUseCase.parseWithGeminiNano("show me ongoing adventure dungeon fantasy series with under 10 unread")
			assertEquals(org.koitharu.kotatsu.search.ai.SmartCompletionFilter.ONGOING_ONLY, nanoAst.completionState)
			assertEquals(listOf("Fantasy", "Adventure"), nanoAst.includedTags)
			assertEquals(listOf("Horror"), nanoAst.excludedTags)
			assertEquals(org.koitharu.kotatsu.search.ai.SmartSortOrder.UNREAD_ASC, nanoAst.sortOrder)

			val matchedEntities = nlSearchUseCase.executeSmartSearch(nanoAst)
			assertTrue("Compiled SQLite query must return matching library manga (Sousou no Frieren)", matchedEntities.any { it.id == 1001L })
		} finally {
			com.google.mlkit.genai.prompt.Generation.customModelOverride = null
		}
	}

	@Test
	fun stressTestExtensionStoreRepoParserAndSourceBridges(): Unit = runBlocking {
		// 1. Verify Extension Store URL normalization (GitHub blob, tree, shorthand owner/repo@branch)
		val normBlob = org.koitharu.kotatsu.extensions.store.normalizeExtensionStoreUrl(
			"https://github.com/keiyoushi/extensions/blob/repo/index.min.json",
		)
		assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json", normBlob)

		val normShorthand = org.koitharu.kotatsu.extensions.store.normalizeExtensionStoreUrl(
			"keiyoushi/extensions@repo",
		)
		assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo", normShorthand)

		// 2. Verify ExtensionRepoRepository parses Mihon index.min.json & LNReader .js plugin arrays
		val repoRepository = extensionManager.repoRepository
		val mihonJson = """
			[
			  {
			    "name": "Tachiyomi: MangaDex",
			    "pkg": "eu.kanade.tachiyomi.extension.en.mangadex",
			    "apk": "tachiyomi-en.mangadex-v1.4.205.apk",
			    "lang": "en",
			    "code": 205,
			    "version": "1.4.205",
			    "nsfw": 0,
			    "sources": [
			      {"name": "MangaDex", "lang": "en", "id": "2499283573021220255", "baseUrl": "https://mangadex.org"}
			    ]
			  }
			]
		""".trimIndent()
		val mihonEntries = repoRepository.parseJsonArrayEntries(
			mihonJson,
			"https://raw.githubusercontent.com/keiyoushi/extensions/repo",
		)
		assertEquals(1, mihonEntries.size)
		assertEquals("MangaDex", org.koitharu.kotatsu.extensions.store.extensionDisplayName(mihonEntries.first()))
		assertEquals(
			"https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/tachiyomi-en.mangadex-v1.4.205.apk",
			repoRepository.resolveApkUrl("https://raw.githubusercontent.com/keiyoushi/extensions/repo", mihonEntries.first()),
		)

		val lnReaderJson = """
			[
			  {
			    "id": "royalroad",
			    "name": "RoyalRoad",
			    "site": "https://www.royalroad.com",
			    "lang": "English",
			    "version": "1.1.2",
			    "url": "https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/english/royalroad.js",
			    "iconUrl": "https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/icons/royalroad.png"
			  }
			]
		""".trimIndent()
		val lnEntries = repoRepository.parseJsonArrayEntries(
			lnReaderJson,
			"https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/.dist",
		)
		assertEquals(1, lnEntries.size)
		assertTrue(lnEntries.first().pkg.startsWith("lnreader.plugin."))
		assertEquals("en", lnEntries.first().lang)

		// 3. Verify language display & autonym resolvers
		assertEquals("English", org.koitharu.kotatsu.extensions.store.getExternalExtensionLanguageDisplayName("en"))
		assertEquals("日本語", org.koitharu.kotatsu.extensions.store.getExternalExtensionLanguageAutonym("ja"))
		assertEquals(
			"en",
			org.koitharu.kotatsu.extensions.store.resolveActiveMihonLanguage(
				setOf("en", "ja"),
				java.util.Locale.ENGLISH,
			),
		)

		// 4. Verify MangaRepository.Factory source routing (LocalMangaRepository, LazyMihonMangaRepository, EmptyMangaRepository)
		val factory = MangaRepository.Factory(app)
		val localRepo = factory.createAsync(MangaSource("LOCAL"))
		val mihonRepo = factory.createAsync(MangaSource("MIHON_2499283573021220255"))
		val uninstalledLnRepo = factory.createAsync(MangaSource("LNREADER_ROYALROAD"))
		assertEquals("LOCAL", localRepo.source.name)
		assertEquals("MIHON_2499283573021220255", mihonRepo.source.name)
		assertEquals("LNREADER_ROYALROAD", uninstalledLnRepo.source.name)
		assertTrue("Uninstalled LNReader plugin must return empty list until installed", uninstalledLnRepo.getList(0).isEmpty())

		// 5. Verify deprecated Keiyoushi index.min.json 2-item stub ("Outdated App" & "Update to Mihon 0.20.1+") is rejected
		val deprecatedStubJson = """
			[
			  {
			    "name": "Tachiyomi: Outdated App",
			    "pkg": "eu.kanade.tachiyomi.extension.all.keiyoushi",
			    "apk": "tachiyomi-all.keiyoushi-v1.4.1.apk",
			    "lang": "all",
			    "code": 1,
			    "version": "1.4.1",
			    "nsfw": 1
			  },
			  {
			    "name": "Tachiyomi: Update to Mihon 0.20.1+",
			    "pkg": "eu.kanade.tachiyomi.extension.all.mihon",
			    "apk": "tachiyomi-all.mihon-v1.4.1.apk",
			    "lang": "all",
			    "code": 1,
			    "version": "1.4.1",
			    "nsfw": 1
			  }
			]
		""".trimIndent()
		val stubParsed = repoRepository.parseJsonArrayEntries(
			deprecatedStubJson,
			"https://raw.githubusercontent.com/keiyoushi/extensions/repo",
		)
		assertTrue("Deprecated 2-item Keiyoushi stub must be completely filtered out", stubParsed.isEmpty())
	}
}
