package org.nemuri.app.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.parser.MangaLinkResolver
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.util.ext.resolveName
import org.koitharu.kotatsu.local.data.LocalStorageManager
import org.koitharu.kotatsu.local.data.importer.SingleMangaImporter
import org.koitharu.kotatsu.parsers.model.Manga
import java.util.Locale
import java.util.zip.ZipInputStream

enum class BackupRestoreKind {
	NEMURI_ZIP,
	MIHON_PROTO,
}

data class PendingBackupRestoreRequest(
	val uri: Uri,
	val kind: BackupRestoreKind,
	val displayName: String,
)

object PendingBackupRestore {
	private val _pending = MutableStateFlow<PendingBackupRestoreRequest?>(null)
	val pending = _pending.asStateFlow()

	fun post(request: PendingBackupRestoreRequest) {
		_pending.value = request
	}

	fun consume(): PendingBackupRestoreRequest? {
		val current = _pending.value
		_pending.value = null
		return current
	}
}

sealed interface ExternalIntentOutcome {
	data object NotHandled : ExternalIntentOutcome
	data class OpenBackupRestore(val request: PendingBackupRestoreRequest) : ExternalIntentOutcome
	data class OpenReader(val manga: Manga, val chapterId: Long? = null) : ExternalIntentOutcome
	data class OpenDetails(val manga: Manga) : ExternalIntentOutcome
	data class FallbackSearch(val query: String, val rawUrl: String) : ExternalIntentOutcome
	data class Error(val message: String) : ExternalIntentOutcome
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExternalIntentEntryPoint {
	fun singleMangaImporter(): SingleMangaImporter
	fun localStorageManager(): LocalStorageManager
	fun mangaLinkResolver(): MangaLinkResolver
	fun mangaRepositoryFactory(): MangaRepository.Factory
	@BaseHttpClient
	fun baseHttpClient(): OkHttpClient
}

object ExternalIntentRouter {

	private val MANGADEX_CHAPTER_UUID_REGEX = Regex(
		"""^/chapter/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})""",
	)

	fun isExternalFileOrWebDeepLink(intent: Intent?): Boolean {
		if (intent?.action != Intent.ACTION_VIEW) return false
		val data = intent.data ?: return false
		val scheme = data.scheme?.lowercase(Locale.ROOT) ?: return false
		return scheme == "content" || scheme == "file" || scheme == "http" || scheme == "https"
	}

	suspend fun resolveExternalViewIntent(
		context: Context,
		intent: Intent,
	): ExternalIntentOutcome = withContext(Dispatchers.IO) {
		val uri = intent.data ?: return@withContext ExternalIntentOutcome.NotHandled
		val appContext = context.applicationContext
		val ep = EntryPointAccessors.fromApplication(appContext, ExternalIntentEntryPoint::class.java)
		val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()

		when (scheme) {
			"content", "file" -> handleFileOrContentUri(appContext, uri, intent.type, ep)
			"http", "https" -> handleHttpDeepLink(uri, ep)
			else -> ExternalIntentOutcome.NotHandled
		}
	}

	private suspend fun handleFileOrContentUri(
		context: Context,
		uri: Uri,
		intentMimeType: String?,
		ep: ExternalIntentEntryPoint,
	): ExternalIntentOutcome {
		runCatching { ep.localStorageManager().takePermissions(uri) }
		val cr = context.contentResolver
		val displayName = runCatching { cr.resolveName(uri) }.getOrNull()
			?.takeIf { it.isNotBlank() }
			?: uri.lastPathSegment.orEmpty()
		val lowerName = displayName.lowercase(Locale.ROOT)
		val lowerPath = uri.path.orEmpty().lowercase(Locale.ROOT)
		val mime = (intentMimeType ?: runCatching { cr.getType(uri) }.getOrNull()).orEmpty().lowercase(Locale.ROOT)

		// 1. Check Mihon / Tachiyomi backup (.tachibk / .proto.gz)
		if (lowerName.endsWith(".tachibk") || lowerName.endsWith(".proto.gz") ||
			lowerPath.endsWith(".tachibk") || lowerPath.endsWith(".proto.gz") ||
			mime == "application/gzip" || mime == "application/x-gzip"
		) {
			return ExternalIntentOutcome.OpenBackupRestore(
				PendingBackupRestoreRequest(
					uri = uri,
					kind = BackupRestoreKind.MIHON_PROTO,
					displayName = displayName.ifBlank { "Mihon Backup" },
				),
			)
		}

		// 2. Check Nemuri / Kotatsu backup (.bk.zip or ZIP containing backup sections)
		if (lowerName.endsWith(".bk.zip") || lowerPath.endsWith(".bk.zip") ||
			(lowerName.endsWith(".zip") && !lowerName.endsWith(".cbz") && looksLikeNemuriBackupZip(context, uri))
		) {
			return ExternalIntentOutcome.OpenBackupRestore(
				PendingBackupRestoreRequest(
					uri = uri,
					kind = BackupRestoreKind.NEMURI_ZIP,
					displayName = displayName.ifBlank { "Nemuri Backup" },
				),
			)
		}

		// 3. Import & open local comic / novel (.cbz, .cbr, .epub, .zip, .pdf) directly in Reader
		return runCatching {
			val imported = ep.singleMangaImporter().import(uri)
			val first = imported.firstOrNull()?.manga
				?: return ExternalIntentOutcome.Error("No readable pages or chapters found in ${displayName.ifBlank { "file" }}")
			ExternalIntentOutcome.OpenReader(manga = first)
		}.getOrElse { err ->
			ExternalIntentOutcome.Error(err.message ?: "Failed to open ${displayName.ifBlank { "local file" }}")
		}
	}

	private suspend fun handleHttpDeepLink(
		uri: Uri,
		ep: ExternalIntentEntryPoint,
	): ExternalIntentOutcome {
		val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
		val path = uri.path.orEmpty()

		// Special support for mangadex.org/chapter/<uuid> -> resolve parent manga + target chapter
		if ((host == "mangadex.org" || host == "www.mangadex.org") && path.startsWith("/chapter/")) {
			val chapterUuid = MANGADEX_CHAPTER_UUID_REGEX.find(path)?.groupValues?.getOrNull(1)
			if (!chapterUuid.isNullOrBlank()) {
				val resolvedChapter = runCatching {
					resolveMangaDexChapterDeepLink(chapterUuid, ep)
				}.getOrNull()
				if (resolvedChapter != null) {
					return resolvedChapter
				}
			}
		}

		return runCatching {
			val manga = ep.mangaLinkResolver().resolve(uri)
			ExternalIntentOutcome.OpenDetails(manga)
		}.getOrElse {
			val slug = uri.pathSegments
				.lastOrNull { seg -> seg.length > 2 && !seg.all(Char::isDigit) }
				?.replace('-', ' ')
				?.replace('_', ' ')
				?.trim()
				.orEmpty()
			if (slug.isNotBlank()) {
				ExternalIntentOutcome.FallbackSearch(query = slug, rawUrl = uri.toString())
			} else {
				ExternalIntentOutcome.Error("No installed extension matched ${uri.host ?: uri}")
			}
		}
	}

	private suspend fun resolveMangaDexChapterDeepLink(
		chapterUuid: String,
		ep: ExternalIntentEntryPoint,
	): ExternalIntentOutcome? {
		val apiUrl = "https://api.mangadex.org/chapter/$chapterUuid"
		val req = Request.Builder().url(apiUrl).get().build()
		val mangaUuid = ep.baseHttpClient().newCall(req).execute().use { resp ->
			if (!resp.isSuccessful) return null
			val json = JSONObject(resp.body.string())
			val rels = json.optJSONObject("data")?.optJSONArray("relationships") ?: return null
			var found: String? = null
			for (i in 0 until rels.length()) {
				val rel = rels.optJSONObject(i) ?: continue
				if (rel.optString("type") == "manga") {
					found = rel.optString("id").takeIf { it.isNotBlank() }
					break
				}
			}
			found
		} ?: return null

		val titleUri = Uri.parse("https://mangadex.org/title/$mangaUuid")
		val manga = ep.mangaLinkResolver().resolve(titleUri)
		val details = runCatching {
			ep.mangaRepositoryFactory().create(manga.source).getDetails(manga)
		}.getOrNull()
		val targetChapter = details?.chapters?.firstOrNull { ch ->
			ch.url.contains(chapterUuid, ignoreCase = true)
		}
		return if (targetChapter != null) {
			ExternalIntentOutcome.OpenReader(manga = details, chapterId = targetChapter.id)
		} else {
			ExternalIntentOutcome.OpenDetails(details ?: manga)
		}
	}

	private fun looksLikeNemuriBackupZip(context: Context, uri: Uri): Boolean = runCatching {
		val backupEntryNames = setOf("favourites", "favourites.json", "history", "history.json", "categories", "categories.json", "settings", "settings.json")
		context.contentResolver.openInputStream(uri)?.use { raw ->
			ZipInputStream(raw).use { zis ->
				var count = 0
				while (count < 16) {
					val entry = zis.nextEntry ?: break
					val name = entry.name.substringAfterLast('/').lowercase(Locale.ROOT)
					if (name in backupEntryNames) {
						return@runCatching true
					}
					count++
				}
			}
		}
		false
	}.getOrDefault(false)
}
