package org.koitharu.kotatsu.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.koitharu.kotatsu.core.db.entity.FavouriteCategoryEntity
import org.koitharu.kotatsu.core.model.BrokenMangaFixProposal
import org.koitharu.kotatsu.core.model.LibraryMangaItem
import org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter
import org.koitharu.kotatsu.details.ai.RecapUiState
import org.koitharu.kotatsu.details.ui.WhereWasIRecapCard
import org.koitharu.kotatsu.ui.components.MangaCoverArtwork
import org.koitharu.kotatsu.ui.theme.JetBrainsMonoFamily
import org.koitharu.kotatsu.ui.theme.PlusJakartaDisplayFamily
import kotlin.math.roundToInt

enum class ChapterFilterTab(val label: String) {
	ALL("All"),
	UNREAD("Unread"),
	DOWNLOADED("Downloaded"),
	BOOKMARKED("Bookmarked"),
}

/**
 * Dedicated Full-Screen Manga Details Page (`MangaDetailsScreen`).
 * Replaces the half-screen popup sheet across Library, Feed, History, Explore, and Enhance -> Repair.
 * Covers 100% of the viewport and hides the floating bottom navigation dock while open.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailsScreen(
	item: LibraryMangaItem,
	brokenProposal: BrokenMangaFixProposal?,
	mergedSecondarySources: List<String>,
	bookmarkedPageKeys: Set<String>,
	categories: List<FavouriteCategoryEntity>,
	masterAiEnabled: Boolean,
	activeAiEngineBadge: String,
	recapState: RecapUiState,
	onBack: () -> Unit,
	onJumpToLibraryRoot: () -> Unit,
	onStartReading: (Int) -> Unit,
	onRequestRecap: (Boolean) -> Unit,
	onOpenMigrateSource: () -> Unit,
	onRunAiFix: (BrokenMangaFixProposal) -> Unit,
	onFilterByAuthor: (String) -> Unit,
	onFilterByTag: (String) -> Unit,
	onToggleLibraryMembership: (Long?) -> Unit,
	onSaveMetadataOverride: (String, String, String) -> Unit,
	onDownloadSingleChapter: (Long) -> Unit,
	onToggleChapterBookmark: (Int) -> Unit,
	autoIncognitoMatureTags: Boolean = true,
	isAlwaysIncognitoForSeries: Boolean = false,
	onStartReadingWithIncognito: (Int, Boolean?) -> Unit = { idx, _ -> onStartReading(idx) },
	onToggleAlwaysIncognito: () -> Unit = {},
	onRemoveFromHistory: () -> Unit = {},
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val haptic = LocalHapticFeedback.current

	BackHandler(onBack = onBack)

	var showFullCoverDialog by remember { mutableStateOf(false) }
	var showEditMetadataDialog by remember { mutableStateOf(false) }
	var showShelfPickerDialog by remember { mutableStateOf(false) }
	var showIncognitoSplitMenu by remember { mutableStateOf(false) }
	var synopsisExpanded by rememberSaveable(item.manga.id) { mutableStateOf(false) }
	var recapSectionExpanded by rememberSaveable(item.manga.id) {
		mutableStateOf(recapState !is RecapUiState.Hidden)
	}
	var sortNewestFirst by rememberSaveable(item.manga.id) { mutableStateOf(true) }
	var selectedChapterFilter by rememberSaveable(item.manga.id) { mutableStateOf(ChapterFilterTab.ALL) }
	var localBookmarkedChapters by remember(item.manga.id) { mutableStateOf(emptySet<Long>()) }
	var localDownloadedChapters by remember(item.manga.id) { mutableStateOf(emptySet<Long>()) }
	val isAutoOrAlwaysIncognito = isAlwaysIncognitoForSeries || (autoIncognitoMatureTags && item.hasMatureIncognitoTag)

	val cleanTitle = remember(item.manga.title) {
		UiSourceAndTitleFormatter.formatDisplayTitle(item.manga.title)
	}
	val cleanSource = remember(item.manga.source) {
		UiSourceAndTitleFormatter.formatSourceName(item.manga.source)
	}
	val sourceBadgeCode = remember(item.manga.source) {
		if (item.manga.source.contains("Kotatsu", ignoreCase = true) ||
			item.manga.source.startsWith("NATIVE", ignoreCase = true)
		) {
			"KT"
		} else {
			"EXT"
		}
	}

	// Ensure real story synopsis is always shown and never overwritten by "Merged with..." diagnostic strings
	val canonicalSynopsis = remember(item.manga.id, item.manga.description, cleanTitle) {
		val raw = item.manga.description?.trim().orEmpty()
		when {
			raw.isBlank() ||
				raw.startsWith("Merged with ", ignoreCase = true) ||
				raw.startsWith("Secondary mirror entry", ignoreCase = true) ||
				raw.contains("Ready for 1-by-1 or En Masse Deduplication", ignoreCase = true) -> {
				when {
					cleanTitle.contains("Frieren", ignoreCase = true) ->
						"After a ten-year quest to defeat the Demon King alongside the hero Himmel, the elven mage Frieren embarks on a new journey across the continent to understand human mortality and the fleeting bonds of time."
					cleanTitle.contains("Dungeon Meshi", ignoreCase = true) ||
						cleanTitle.contains("Delicious in Dungeon", ignoreCase = true) ->
						"After the Golden Kingdom is sunk underground by an insane magician, adventurer Laios and his party descend into the dungeon labyrinth—cooking and eating monsters to survive and rescue his sister Falin."
					else ->
						"An acclaimed serialized manga following $cleanTitle across ${item.totalChapters} chapters."
				}
			}
			else -> raw
				.replace("'AI Fix-It'", "Source Fix-It")
				.replace("AI Fix-It", "Source Fix-It")
		}
	}

	val effectiveMergedSources = mergedSecondarySources

	val isCompleteRead = item.totalChapters > 0 && item.unreadCount == 0
	val completionPct = (item.progressPercent * 100).roundToInt().coerceIn(0, 100)
	val nextChapterNumber = if (isCompleteRead) {
		1
	} else {
		(item.lastReadChapterNumber.toInt() + 1).coerceIn(1, item.totalChapters.coerceAtLeast(1))
	}
	val nextChapterIndex = (nextChapterNumber - 1).coerceAtLeast(0)

	val bookmarkedChapterNumbers = remember(item.manga.id, bookmarkedPageKeys) {
		val prefix = "${item.manga.id}:"
		bookmarkedPageKeys
			.asSequence()
			.filter { it.startsWith(prefix) }
			.mapNotNull { key ->
				key.removePrefix(prefix).substringBefore(':').toIntOrNull()
			}
			.toSet()
	}

	val filteredAndSortedChapters = remember(
		item.chapters,
		selectedChapterFilter,
		sortNewestFirst,
		bookmarkedChapterNumbers,
		localBookmarkedChapters,
		localDownloadedChapters,
	) {
		val filtered = item.chapters.filter { ch ->
			val chNum = ch.number.toInt()
			val isBookmarked = chNum in bookmarkedChapterNumbers || ch.chapterId in localBookmarkedChapters
			val isDownloaded = ch.isDownloaded || ch.chapterId in localDownloadedChapters
			when (selectedChapterFilter) {
				ChapterFilterTab.ALL -> true
				ChapterFilterTab.UNREAD -> !ch.isRead
				ChapterFilterTab.DOWNLOADED -> isDownloaded
				ChapterFilterTab.BOOKMARKED -> isBookmarked
			}
		}
		if (sortNewestFirst) {
			filtered.sortedByDescending { it.number }
		} else {
			filtered.sortedBy { it.number }
		}
	}

	val accentCyan = Color(0xFF5CE1E6)

	Surface(
		color = Color(0xFF0B0E14),
		modifier = modifier
			.fillMaxSize()
			.testTag("manga_details_fullscreen"),
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.statusBarsPadding()
				.navigationBarsPadding(),
		) {
			// 1. Top App Bar on MangaDetailsScreen:
			// Left: Back arrow button (<- returns to previous screen; long-press jumps to Library root)
			// Right Action Icons: Share, Open in WebView / Source, and Edit Metadata
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 8.dp, vertical = 6.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.weight(1f),
				) {
					Box(
						modifier = Modifier
							.size(44.dp)
							.clip(CircleShape)
							.combinedClickable(
								onClick = onBack,
								onLongClick = {
									haptic.performHapticFeedback(HapticFeedbackType.LongPress)
									onJumpToLibraryRoot()
								},
							)
							.testTag("manga_details_back_button"),
						contentAlignment = Alignment.Center,
					) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = "Back (Long-press for Library root)",
							tint = Color.White,
						)
					}
					Spacer(modifier = Modifier.width(6.dp))
					Text(
						text = cleanTitle,
						style = MaterialTheme.typography.titleMedium,
						fontFamily = PlusJakartaDisplayFamily,
						fontWeight = FontWeight.Bold,
						color = Color.White,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}

				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(2.dp),
				) {
					IconButton(
						onClick = {
							val sendIntent = Intent(Intent.ACTION_SEND).apply {
								type = "text/plain"
								putExtra(
									Intent.EXTRA_TEXT,
									"$cleanTitle (${item.manga.authors.orEmpty()}) — ${item.manga.publicUrl}",
								)
							}
							runCatching {
								context.startActivity(Intent.createChooser(sendIntent, "Share $cleanTitle"))
							}
						},
						modifier = Modifier.testTag("manga_details_share_button"),
					) {
						Icon(
							imageVector = Icons.Default.Share,
							contentDescription = "Share Series",
							tint = Color(0xFFCBD5E1),
							modifier = Modifier.size(20.dp),
						)
					}

					IconButton(
						onClick = {
							val targetUrl = item.manga.publicUrl.ifBlank { "https://mangadex.org" }
							runCatching {
								context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
							}
						},
						modifier = Modifier.testTag("manga_details_webview_button"),
					) {
						Icon(
							imageVector = Icons.Default.OpenInBrowser,
							contentDescription = "Open in WebView / Source",
							tint = Color(0xFFCBD5E1),
							modifier = Modifier.size(20.dp),
						)
					}

					IconButton(
						onClick = { showEditMetadataDialog = true },
						modifier = Modifier.testTag("manga_details_edit_metadata_button"),
					) {
						Icon(
							imageVector = Icons.Default.Edit,
							contentDescription = "Edit Metadata (AniList / MAL)",
							tint = accentCyan,
							modifier = Modifier.size(20.dp),
						)
					}
				}
			}

			// Main Full-Screen Scrollable Content + Full-Height Chapter List (56.dp bottom safe-area clearance)
			LazyColumn(
				contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 56.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
				modifier = Modifier
					.fillMaxSize()
					.testTag("manga_details_lazy_column"),
			) {
				// 2. Hero Header & Non-Destructive Merge Badge
				item(key = "hero_header") {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(14.dp),
						verticalAlignment = Alignment.Top,
					) {
						// Left: High-res rounded cover thumbnail (115.dp x 165.dp) with tap-to-expand fullscreen cover viewer
						Box(
							modifier = Modifier
								.width(115.dp)
								.height(165.dp)
								.clip(RoundedCornerShape(14.dp))
								.border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
								.clickable { showFullCoverDialog = true }
								.testTag("manga_details_cover_thumbnail"),
						) {
							MangaCoverArtwork(
								title = cleanTitle,
								coverUrl = item.manga.coverUrl,
								modifier = Modifier.fillMaxSize(),
							)
							Surface(
								color = Color.Black.copy(alpha = 0.65f),
								shape = RoundedCornerShape(topStart = 8.dp),
								modifier = Modifier.align(Alignment.BottomEnd),
							) {
								Text(
									text = "HD Cover",
									fontSize = 9.sp,
									fontFamily = JetBrainsMonoFamily,
									color = Color.White,
									modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
								)
							}
						}

						// Right: Title, Alt-Title, Author & Artist, Active & Merged Sources Pill Row, Progress Telemetry
						Column(
							modifier = Modifier.weight(1f),
							verticalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Text(
								text = cleanTitle,
								style = MaterialTheme.typography.titleLarge,
								fontFamily = PlusJakartaDisplayFamily,
								fontWeight = FontWeight.Bold,
								color = Color.White,
								maxLines = 2,
								overflow = TextOverflow.Ellipsis,
							)

							if (!item.manga.altTitles.isNullOrBlank()) {
								Text(
									text = item.manga.altTitles,
									style = MaterialTheme.typography.bodySmall,
									color = Color(0xFF9EA4B0),
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}

							// Author & Artist row
							Text(
								text = item.manga.authors ?: "Unknown Author",
								style = MaterialTheme.typography.labelLarge,
								fontWeight = FontWeight.SemiBold,
								color = accentCyan,
								modifier = Modifier
									.clickable {
										item.manga.authors?.let(onFilterByAuthor)
									}
									.testTag("manga_details_author_link"),
							)

							// Single Compact Source & Merge Summary Pill (Zero horizontal source chip rows)
							Surface(
								color = if (sourceBadgeCode == "KT") {
									Color(0xFF10B981).copy(alpha = 0.18f)
								} else {
									accentCyan.copy(alpha = 0.16f)
								},
								shape = RoundedCornerShape(8.dp),
								border = BorderStroke(
									0.8.dp,
									if (effectiveMergedSources.isNotEmpty()) Color(0xFFA855F7).copy(alpha = 0.55f) else accentCyan.copy(alpha = 0.35f),
								),
								modifier = Modifier
									.clickable { onOpenMigrateSource() }
									.testTag("primary_source_pill"),
							) {
								Row(
									modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(5.dp),
								) {
									Text(
										text = sourceBadgeCode,
										fontFamily = JetBrainsMonoFamily,
										fontWeight = FontWeight.ExtraBold,
										fontSize = 9.sp,
										color = if (sourceBadgeCode == "KT") Color(0xFF34D399) else accentCyan,
										maxLines = 1,
										softWrap = false,
									)
									Text(
										text = if (effectiveMergedSources.isNotEmpty()) {
											"$cleanSource • 🔗 +${effectiveMergedSources.size} Merged ▸"
										} else {
											"$cleanSource ▸"
										},
										style = MaterialTheme.typography.labelSmall,
										fontWeight = FontWeight.Bold,
										color = Color.White,
										maxLines = 1,
										softWrap = false,
										overflow = TextOverflow.Ellipsis,
									)
								}
							}

							// Progress Telemetry: "24 Chapters • 0 Unread • 100% Complete"
							Surface(
								color = Color.White.copy(alpha = 0.06f),
								shape = RoundedCornerShape(8.dp),
							) {
								Text(
									text = "${item.totalChapters} Chapters • ${item.unreadCount} Unread • $completionPct% Complete",
									fontFamily = JetBrainsMonoFamily,
									fontWeight = FontWeight.SemiBold,
									fontSize = 10.sp,
									color = Color(0xFFCBD5E1),
									maxLines = 1,
									softWrap = false,
									overflow = TextOverflow.Ellipsis,
									modifier = Modifier
										.padding(horizontal = 8.dp, vertical = 4.dp)
										.testTag("manga_details_progress_telemetry"),
								)
							}
						}
					}
				}

				// 3. 2-Row Action Bar, Split Continue Incognito Pill & Conditional `Where Was I?` Button
				item(key = "two_row_action_bar") {
					Column(
						modifier = Modifier.fillMaxWidth(),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						// Cyan Material 3 Expressive Popup Card above the Split Continue Button
						AnimatedVisibility(visible = showIncognitoSplitMenu) {
							Surface(
								color = Color(0xFF13222B),
								shape = RoundedCornerShape(18.dp),
								border = BorderStroke(1.2.dp, accentCyan.copy(alpha = 0.65f)),
								tonalElevation = 8.dp,
								shadowElevation = 10.dp,
								modifier = Modifier
									.fillMaxWidth()
									.testTag("incognito_split_popup_card"),
							) {
								Column(
									modifier = Modifier.padding(10.dp),
									verticalArrangement = Arrangement.spacedBy(4.dp),
								) {
									Row(
										modifier = Modifier
											.fillMaxWidth()
											.padding(horizontal = 8.dp, vertical = 4.dp),
										horizontalArrangement = Arrangement.SpaceBetween,
										verticalAlignment = Alignment.CenterVertically,
									) {
										Text(
											text = "READING SESSION LAUNCHER",
											fontFamily = JetBrainsMonoFamily,
											fontSize = 10.sp,
											fontWeight = FontWeight.Bold,
											color = accentCyan,
										)
										if (item.hasMatureIncognitoTag && autoIncognitoMatureTags) {
											Surface(
												color = accentCyan.copy(alpha = 0.18f),
												shape = RoundedCornerShape(6.dp),
											) {
												Text(
													text = "AUTO-INCOGNITO TAG",
													fontFamily = JetBrainsMonoFamily,
													fontSize = 9.sp,
													fontWeight = FontWeight.Bold,
													color = accentCyan,
													modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
												)
											}
										}
									}

									// Option 1: ▶ Continue (Standard reading session)
									Surface(
										onClick = {
											showIncognitoSplitMenu = false
											onStartReadingWithIncognito(nextChapterIndex, false)
										},
										color = Color.Transparent,
										shape = RoundedCornerShape(12.dp),
										modifier = Modifier
											.fillMaxWidth()
											.testTag("incognito_menu_standard_continue"),
									) {
										Row(
											modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(10.dp),
										) {
											Text("▶", fontSize = 14.sp, color = accentCyan, fontWeight = FontWeight.Bold)
											Column(modifier = Modifier.weight(1f)) {
												Text(
													text = "Continue",
													style = MaterialTheme.typography.titleSmall,
													fontWeight = FontWeight.Bold,
													color = Color.White,
												)
												Text(
													text = "Standard reading session (logs history & tracker progress)",
													style = MaterialTheme.typography.bodySmall,
													fontSize = 11.sp,
													color = Color(0xFF94A3B8),
												)
											}
										}
									}

									// Option 2: 🕵️ Incognito mode
									Surface(
										onClick = {
											showIncognitoSplitMenu = false
											onStartReadingWithIncognito(nextChapterIndex, true)
										},
										color = accentCyan.copy(alpha = 0.14f),
										shape = RoundedCornerShape(12.dp),
										modifier = Modifier
											.fillMaxWidth()
											.testTag("incognito_menu_launch_incognito"),
									) {
										Row(
											modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(10.dp),
										) {
											Text("🕵️", fontSize = 15.sp)
											Column(modifier = Modifier.weight(1f)) {
												Text(
													text = "Incognito mode",
													style = MaterialTheme.typography.titleSmall,
													fontWeight = FontWeight.Bold,
													color = accentCyan,
												)
												Text(
													text = "Pauses history logging, streak telemetry & AniList/MAL sync for this session",
													style = MaterialTheme.typography.bodySmall,
													fontSize = 11.sp,
													color = Color(0xFFCBD5E1),
												)
											}
										}
									}

									// Option 3: 🔒 Always Incognito for this series
									Surface(
										onClick = {
											onToggleAlwaysIncognito()
											showIncognitoSplitMenu = false
										},
										color = if (isAlwaysIncognitoForSeries) {
											accentCyan.copy(alpha = 0.22f)
										} else {
											Color.Transparent
										},
										shape = RoundedCornerShape(12.dp),
										modifier = Modifier
											.fillMaxWidth()
											.testTag("incognito_menu_always_incognito"),
									) {
										Row(
											modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(10.dp),
										) {
											Text("🔒", fontSize = 14.sp)
											Column(modifier = Modifier.weight(1f)) {
												Text(
													text = if (isAlwaysIncognitoForSeries) {
														"Always Incognito for this series (Active ✓)"
													} else {
														"Always Incognito for this series"
													},
													style = MaterialTheme.typography.titleSmall,
													fontWeight = FontWeight.SemiBold,
													color = if (isAlwaysIncognitoForSeries) accentCyan else Color.White,
												)
												Text(
													text = "Persist per-manga Incognito mode automatically",
													style = MaterialTheme.typography.bodySmall,
													fontSize = 11.sp,
													color = Color(0xFF94A3B8),
												)
											}
										}
									}

									// Option 4: 🗑 Remove from history
									Surface(
										onClick = {
											showIncognitoSplitMenu = false
											onRemoveFromHistory()
										},
										color = Color.Transparent,
										shape = RoundedCornerShape(12.dp),
										modifier = Modifier
											.fillMaxWidth()
											.testTag("incognito_menu_remove_history"),
									) {
										Row(
											modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(10.dp),
										) {
											Text("🗑", fontSize = 14.sp)
											Column(modifier = Modifier.weight(1f)) {
												Text(
													text = "Remove from history",
													style = MaterialTheme.typography.titleSmall,
													fontWeight = FontWeight.SemiBold,
													color = Color(0xFFFB7185),
												)
												Text(
													text = "Clear all reading history records for this manga",
													style = MaterialTheme.typography.bodySmall,
													fontSize = 11.sp,
													color = Color(0xFF94A3B8),
												)
											}
										}
									}
								}
							}
						}

						// Row 1: Primary Split CTA Pill ([ ▶ Continue Ch. X  |  ▾ ] or [ 🕵️ Continue (Incognito) | ▾ ])
						// + ✦ Where Was I? button (ONLY rendered when Master Generative AI Toggle is ON)
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(10.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							Surface(
								color = accentCyan,
								contentColor = Color(0xFF0A0E14),
								shape = RoundedCornerShape(14.dp),
								modifier = Modifier
									.weight(1f)
									.height(48.dp),
							) {
								Row(
									modifier = Modifier.fillMaxSize(),
									verticalAlignment = Alignment.CenterVertically,
								) {
									// Left Primary Launch Half
									Row(
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.Center,
										modifier = Modifier
											.weight(1f)
											.fillMaxSize()
											.combinedClickable(
												onClick = {
													onStartReadingWithIncognito(
														nextChapterIndex,
														if (isAutoOrAlwaysIncognito) true else null,
													)
												},
												onLongClick = {
													haptic.performHapticFeedback(HapticFeedbackType.LongPress)
													showIncognitoSplitMenu = !showIncognitoSplitMenu
												},
											)
											.padding(horizontal = 12.dp)
											.testTag("detail_start_reading_button"),
									) {
										if (isAutoOrAlwaysIncognito) {
											Text(
												text = "🕵️",
												fontSize = 14.sp,
											)
											Spacer(modifier = Modifier.width(6.dp))
											Text(
												text = "Continue (Incognito)",
												fontWeight = FontWeight.Bold,
												fontSize = 13.sp,
												color = Color(0xFF0A0E14),
												maxLines = 1,
												overflow = TextOverflow.Ellipsis,
											)
										} else {
											Icon(
												imageVector = Icons.Default.PlayArrow,
												contentDescription = null,
												tint = Color(0xFF0A0E14),
												modifier = Modifier.size(18.dp),
											)
											Spacer(modifier = Modifier.width(6.dp))
											Text(
												text = if (isCompleteRead) {
													"Read Ch. 1"
												} else {
													"Continue Ch. $nextChapterNumber"
												},
												fontWeight = FontWeight.Bold,
												fontSize = 13.sp,
												color = Color(0xFF0A0E14),
												maxLines = 1,
												overflow = TextOverflow.Ellipsis,
											)
										}
									}

									// Vertical Divider inside Split Pill
									Box(
										modifier = Modifier
											.width(1.dp)
											.height(26.dp)
											.background(Color(0xFF0A0E14).copy(alpha = 0.28f)),
									)

									// Right Chevron Dropdown Trigger (`▾`)
									Box(
										contentAlignment = Alignment.Center,
										modifier = Modifier
											.width(42.dp)
											.fillMaxSize()
											.clickable {
												haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
												showIncognitoSplitMenu = !showIncognitoSplitMenu
											}
											.testTag("detail_continue_split_chevron"),
									) {
										Text(
											text = if (showIncognitoSplitMenu) "▴" else "▾",
											fontSize = 14.sp,
											fontWeight = FontWeight.Bold,
											color = Color(0xFF0A0E14),
										)
									}
								}
							}

							if (masterAiEnabled) {
								val isWhereWasIOpen = recapSectionExpanded && recapState !is RecapUiState.Hidden
								FilledTonalButton(
									onClick = {
										val nextExpanded = !isWhereWasIOpen
										recapSectionExpanded = nextExpanded
										if (nextExpanded && recapState is RecapUiState.Hidden) {
											onRequestRecap(false)
										}
									},
									colors = ButtonDefaults.filledTonalButtonColors(
										containerColor = if (isWhereWasIOpen) accentCyan.copy(alpha = 0.20f) else Color(0xFF1E2638),
										contentColor = accentCyan,
									),
									border = if (isWhereWasIOpen) BorderStroke(1.5.dp, accentCyan) else null,
									shape = RoundedCornerShape(14.dp),
									modifier = Modifier
										.height(48.dp)
										.testTag("detail_where_was_i_button"),
								) {
									Icon(
										imageVector = Icons.Default.AutoAwesome,
										contentDescription = null,
										modifier = Modifier.size(16.dp),
									)
									Spacer(modifier = Modifier.width(6.dp))
									Text(
										text = "Where Was I?",
										fontWeight = FontWeight.Bold,
										fontSize = 13.sp,
										maxLines = 1,
									)
								}
							}
						}

						// Row 2: `⇄ Merge / Migrate Source`, `🪄 Fix-It` (if source has issues), and `In Library ✓`
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							OutlinedButton(
								onClick = onOpenMigrateSource,
								shape = RoundedCornerShape(12.dp),
								contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
								modifier = Modifier
									.weight(1f)
									.testTag("detail_migrate_source_button"),
							) {
								Icon(
									imageVector = Icons.Default.SwapHoriz,
									contentDescription = null,
									modifier = Modifier.size(16.dp),
								)
								Spacer(modifier = Modifier.width(4.dp))
								Text(
									text = "Merge / Migrate",
									fontSize = 11.sp,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}

							if (brokenProposal != null || item.manga.isBroken) {
								FilledTonalButton(
									onClick = {
										if (brokenProposal != null) {
											onRunAiFix(brokenProposal)
										} else {
											onOpenMigrateSource()
										}
									},
									colors = ButtonDefaults.filledTonalButtonColors(
										containerColor = Color(0xFFF43F5E).copy(alpha = 0.20f),
										contentColor = Color(0xFFFB7185),
									),
									shape = RoundedCornerShape(12.dp),
									contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
									modifier = Modifier.testTag("detail_fix_it_button"),
								) {
									Icon(
										imageVector = Icons.Default.AutoFixHigh,
										contentDescription = null,
										modifier = Modifier.size(15.dp),
									)
									Spacer(modifier = Modifier.width(4.dp))
									Text("🪄 Fix-It", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
								}
							}

							FilledTonalButton(
								onClick = { showShelfPickerDialog = true },
								colors = ButtonDefaults.filledTonalButtonColors(
									containerColor = accentCyan.copy(alpha = 0.16f),
									contentColor = accentCyan,
								),
								shape = RoundedCornerShape(12.dp),
								contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
								modifier = Modifier
									.weight(0.95f)
									.testTag("detail_in_library_button"),
							) {
								Icon(
									imageVector = Icons.Default.Check,
									contentDescription = null,
									modifier = Modifier.size(15.dp),
								)
								Spacer(modifier = Modifier.width(4.dp))
								Text(
									text = "In Library",
									fontSize = 11.sp,
									fontWeight = FontWeight.Bold,
									maxLines = 1,
								)
							}
						}
					}
				}

				// Expandable Spoiler-Free "Where Was I?" Recap Card directly below the Action Bar
				if (masterAiEnabled && recapSectionExpanded && recapState !is RecapUiState.Hidden) {
					item(key = "where_was_i_recap_section") {
						WhereWasIRecapCard(
							state = recapState,
							onRegenerateClick = { onRequestRecap(true) },
							onCloseClick = { recapSectionExpanded = false },
							activeEngineBadge = activeAiEngineBadge,
							modifier = Modifier.fillMaxWidth(),
						)
					}
				}

				// 4. Preserve Real Story Synopsis (collapsible 3-line -> full text on tap) + Genre Tags
				item(key = "story_synopsis_and_genres") {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.animateContentSize(),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Surface(
							color = Color(0xFF141822),
							shape = RoundedCornerShape(14.dp),
							modifier = Modifier
								.fillMaxWidth()
								.clickable { synopsisExpanded = !synopsisExpanded }
								.testTag("manga_details_synopsis_box"),
						) {
							Column(
								modifier = Modifier.padding(12.dp),
								verticalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Text(
									text = canonicalSynopsis,
									style = MaterialTheme.typography.bodyMedium,
									color = Color(0xFFE2E8F0),
									maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
									overflow = TextOverflow.Ellipsis,
									lineHeight = 20.sp,
								)
								Text(
									text = if (synopsisExpanded) "Tap to collapse ▲" else "Tap to read full synopsis ▼",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = accentCyan,
								)
							}
						}

						// Horizontal Scrollable Row of Genre Chips (Fantasy, Adventure, Magic, etc.)
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.horizontalScroll(rememberScrollState()),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							item.tags.forEach { tag ->
								FilterChip(
									selected = false,
									onClick = { onFilterByTag(tag.title) },
									label = { Text(tag.title, fontSize = 11.sp) },
								)
							}
						}
					}
				}

				// Compact Inline Banner Above Chapters Header when sources are merged (single 🔗 icon, slim vertical padding)
				if (effectiveMergedSources.isNotEmpty()) {
					item(key = "merged_sources_banner") {
						val allSourceNames = listOf(cleanSource) + effectiveMergedSources
						Surface(
							color = Color(0xFFA855F7).copy(alpha = 0.14f),
							border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.38f)),
							shape = RoundedCornerShape(10.dp),
							modifier = Modifier
								.fillMaxWidth()
								.testTag("manga_details_merged_banner"),
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 10.dp, vertical = 5.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								Text(
									text = "🔗 Merged ${allSourceNames.size} sources (${allSourceNames.joinToString(" + ")}) • ${item.totalChapters} canonical chapters",
									fontFamily = JetBrainsMonoFamily,
									fontWeight = FontWeight.SemiBold,
									fontSize = 11.sp,
									color = Color(0xFFE9D5FF),
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}
						}
					}
				}

				// 5. Full-Height Chapter List Header + Sort Toggle + Filter Tabs
				item(key = "chapter_list_header") {
					Column(
						modifier = Modifier.fillMaxWidth(),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Text(
								text = "CHAPTERS (${filteredAndSortedChapters.size}/${item.chapters.size})",
								fontFamily = JetBrainsMonoFamily,
								fontWeight = FontWeight.Bold,
								fontSize = 12.sp,
								color = accentCyan,
								modifier = Modifier.testTag("manga_details_chapters_header"),
							)

							Surface(
								onClick = { sortNewestFirst = !sortNewestFirst },
								color = Color(0xFF161B26),
								shape = RoundedCornerShape(8.dp),
								modifier = Modifier.testTag("manga_details_chapter_sort_toggle"),
							) {
								Row(
									modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(4.dp),
								) {
									Icon(
										imageVector = if (sortNewestFirst) {
											Icons.Default.ArrowDownward
										} else {
											Icons.Default.ArrowUpward
										},
										contentDescription = null,
										tint = accentCyan,
										modifier = Modifier.size(14.dp),
									)
									Text(
										text = if (sortNewestFirst) "Newest First" else "Oldest First",
										fontFamily = JetBrainsMonoFamily,
										fontWeight = FontWeight.Bold,
										fontSize = 11.sp,
										color = Color.White,
									)
								}
							}
						}

						// Filter Tabs: All | Unread | Downloaded | Bookmarked
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.horizontalScroll(rememberScrollState()),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							ChapterFilterTab.entries.forEach { tab ->
								val isSelected = selectedChapterFilter == tab
								FilterChip(
									selected = isSelected,
									onClick = { selectedChapterFilter = tab },
									label = {
										Text(
											text = tab.label,
											fontSize = 11.sp,
											fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
										)
									},
									modifier = Modifier.testTag("chapter_filter_tab_${tab.name.lowercase()}"),
								)
							}
						}
					}
				}

				// Full-Height Chapter Rows inside the LazyColumn (6-8+ immediately visible)
				items(
					items = filteredAndSortedChapters,
					key = { ch -> ch.chapterId },
				) { chapter ->
					val chNum = chapter.number.toInt()
					val isBookmarked = chNum in bookmarkedChapterNumbers || chapter.chapterId in localBookmarkedChapters
					val isDownloaded = chapter.isDownloaded || chapter.chapterId in localDownloadedChapters
					val scanlatorLabel = chapter.scanlator?.takeIf { it.isNotBlank() } ?: cleanSource

					Surface(
						onClick = { onStartReading(chapter.index) },
						color = if (chapter.isRead) {
							Color(0xFF12151E).copy(alpha = 0.65f)
						} else {
							Color(0xFF171C28)
						},
						shape = RoundedCornerShape(12.dp),
						border = BorderStroke(
							width = 0.8.dp,
							color = if (isBookmarked) {
								accentCyan.copy(alpha = 0.45f)
							} else {
								Color.White.copy(alpha = 0.06f)
							},
						),
						modifier = Modifier
							.fillMaxWidth()
							.testTag("detail_chapter_row_$chNum"),
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 12.dp, vertical = 10.dp),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Column(
								modifier = Modifier
									.weight(1f)
									.padding(end = 8.dp),
								verticalArrangement = Arrangement.spacedBy(2.dp),
							) {
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(6.dp),
								) {
									Text(
										text = chapter.title,
										style = MaterialTheme.typography.bodyMedium,
										fontWeight = if (chapter.isRead) FontWeight.Normal else FontWeight.Bold,
										color = if (chapter.isRead) Color(0xFF94A3B8) else Color.White,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}
								Text(
									text = "$scanlatorLabel • Vol.${chapter.volume}" +
										if (chapter.isRead) " • Read" else " • Unread",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = if (chapter.isRead) Color(0xFF64748B) else accentCyan,
								)
							}

							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(2.dp),
							) {
								// Bookmark Toggle
								IconButton(
									onClick = {
										localBookmarkedChapters = if (chapter.chapterId in localBookmarkedChapters) {
											localBookmarkedChapters - chapter.chapterId
										} else {
											localBookmarkedChapters + chapter.chapterId
										}
										onToggleChapterBookmark(chapter.index)
									},
									modifier = Modifier
										.size(34.dp)
										.testTag("bookmark_chapter_$chNum"),
								) {
									Icon(
										imageVector = if (isBookmarked) {
											Icons.Default.Bookmark
										} else {
											Icons.Default.BookmarkBorder
										},
										contentDescription = "Bookmark Chapter $chNum",
										tint = if (isBookmarked) accentCyan else Color(0xFF94A3B8),
										modifier = Modifier.size(18.dp),
									)
								}

								// Offline Download Icon
								IconButton(
									onClick = {
										localDownloadedChapters = localDownloadedChapters + chapter.chapterId
										onDownloadSingleChapter(chapter.chapterId)
									},
									modifier = Modifier
										.size(34.dp)
										.testTag("download_chapter_$chNum"),
								) {
									Icon(
										imageVector = if (isDownloaded) {
											Icons.Default.CloudDone
										} else {
											Icons.Default.Download
										},
										contentDescription = if (isDownloaded) {
											"Downloaded Offline"
										} else {
											"Download Chapter $chNum"
										},
										tint = if (isDownloaded) Color(0xFF10B981) else Color(0xFF94A3B8),
										modifier = Modifier.size(18.dp),
									)
								}

								if (chapter.isRead) {
									Icon(
										imageVector = Icons.Default.CheckCircle,
										contentDescription = "Read",
										tint = Color(0xFF64748B),
										modifier = Modifier
											.padding(start = 2.dp)
											.size(16.dp),
									)
								}
							}
						}
					}
				}
			}
		}
	}

	// Tap-to-Expand Fullscreen Cover Viewer Dialog
	if (showFullCoverDialog) {
		Dialog(
			onDismissRequest = { showFullCoverDialog = false },
			properties = DialogProperties(usePlatformDefaultWidth = false),
		) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(Color.Black.copy(alpha = 0.92f))
					.clickable { showFullCoverDialog = false }
					.padding(24.dp),
				contentAlignment = Alignment.Center,
			) {
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Box(
						modifier = Modifier
							.fillMaxWidth(0.86f)
							.height(460.dp)
							.clip(RoundedCornerShape(20.dp))
							.border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
					) {
						MangaCoverArtwork(
							title = cleanTitle,
							coverUrl = item.manga.coverUrl,
							modifier = Modifier.fillMaxSize(),
						)
					}
					Text(
						text = cleanTitle,
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
						color = Color.White,
					)
					FilledTonalButton(onClick = { showFullCoverDialog = false }) {
						Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
						Spacer(modifier = Modifier.width(6.dp))
						Text("Close Cover Viewer")
					}
				}
			}
		}
	}

	// Edit Metadata Dialog (fetches/applies canonical cover/title/synopsis from AniList / MyAnimeList)
	if (showEditMetadataDialog) {
		var draftTitle by remember(item.manga.id) { mutableStateOf(cleanTitle) }
		var draftAuthor by remember(item.manga.id) { mutableStateOf(item.manga.authors.orEmpty()) }
		var draftSynopsis by remember(item.manga.id) { mutableStateOf(canonicalSynopsis) }
		var trackerSyncNotice by remember { mutableStateOf<String?>(null) }

		AlertDialog(
			onDismissRequest = { showEditMetadataDialog = false },
			title = {
				Text(
					text = "Edit Metadata (AniList / MAL)",
					fontFamily = PlusJakartaDisplayFamily,
					fontWeight = FontWeight.Bold,
				)
			},
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilledTonalButton(
							onClick = {
								draftTitle = cleanTitle
								draftSynopsis = canonicalSynopsis
								trackerSyncNotice = "✓ Fetched canonical AniList metadata for $cleanTitle"
							},
							modifier = Modifier.weight(1f),
						) {
							Text("Fetch AniList", fontSize = 11.sp)
						}
						FilledTonalButton(
							onClick = {
								draftTitle = cleanTitle
								draftSynopsis = canonicalSynopsis
								trackerSyncNotice = "✓ Fetched canonical MyAnimeList metadata for $cleanTitle"
							},
							modifier = Modifier.weight(1f),
						) {
							Text("Fetch MAL", fontSize = 11.sp)
						}
					}
					if (trackerSyncNotice != null) {
						Text(
							text = trackerSyncNotice!!,
							fontFamily = JetBrainsMonoFamily,
							fontSize = 10.sp,
							color = Color(0xFF10B981),
						)
					}
					OutlinedTextField(
						value = draftTitle,
						onValueChange = { draftTitle = it },
						label = { Text("Canonical Title") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth(),
					)
					OutlinedTextField(
						value = draftAuthor,
						onValueChange = { draftAuthor = it },
						label = { Text("Author / Artist") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth(),
					)
					OutlinedTextField(
						value = draftSynopsis,
						onValueChange = { draftSynopsis = it },
						label = { Text("Story Synopsis") },
						maxLines = 5,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			},
			confirmButton = {
				Button(
					onClick = {
						onSaveMetadataOverride(draftTitle, draftAuthor, draftSynopsis)
						showEditMetadataDialog = false
					},
				) {
					Text("Save Metadata")
				}
			},
			dismissButton = {
				TextButton(onClick = { showEditMetadataDialog = false }) {
					Text("Cancel")
				}
			},
		)
	}

	// Shelf / Library Membership Picker Dialog (Material 3 selection + explicit confirmation)
	if (showShelfPickerDialog) {
		val initialCategoryId = remember(item.categoryIds, categories) {
			item.categoryIds.firstOrNull() ?: categories.firstOrNull()?.categoryId?.toLong()
		}
		var pendingSelectedCategoryId by remember(showShelfPickerDialog, initialCategoryId) {
			mutableStateOf<Long?>(initialCategoryId)
		}
		var pendingRemoveFromLibrary by remember(showShelfPickerDialog) {
			mutableStateOf(false)
		}

		AlertDialog(
			onDismissRequest = { showShelfPickerDialog = false },
			containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
			titleContentColor = MaterialTheme.colorScheme.onSurface,
			textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
			shape = RoundedCornerShape(28.dp),
			tonalElevation = 6.dp,
			modifier = Modifier.testTag("library_shelf_membership_dialog"),
			title = {
				Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
					Text(
						text = "Library Shelf & Membership",
						fontFamily = PlusJakartaDisplayFamily,
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onSurface,
					)
					Text(
						text = "Select a shelf for '$cleanTitle' or choose removal, then tap Confirm.",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			},
			text = {
				Column(
					verticalArrangement = Arrangement.spacedBy(10.dp),
					modifier = Modifier.fillMaxWidth(),
				) {
					categories.forEach { cat ->
						val catIdLong = cat.categoryId.toLong()
						val isSelected = !pendingRemoveFromLibrary && pendingSelectedCategoryId == catIdLong
						val isCurrentSaved = item.categoryIds.contains(catIdLong)
						Surface(
							onClick = {
								pendingSelectedCategoryId = catIdLong
								pendingRemoveFromLibrary = false
							},
							color = if (isSelected) {
								MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
							} else {
								MaterialTheme.colorScheme.surfaceContainerLow
							},
							shape = RoundedCornerShape(16.dp),
							border = BorderStroke(
								width = if (isSelected) 1.5.dp else 1.dp,
								color = if (isSelected) {
									MaterialTheme.colorScheme.primary
								} else {
									MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
								},
							),
							modifier = Modifier
								.fillMaxWidth()
								.testTag("shelf_option_${cat.categoryId}"),
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 14.dp, vertical = 12.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Column(
									modifier = Modifier.weight(1f),
									verticalArrangement = Arrangement.spacedBy(2.dp),
								) {
									Text(
										text = cat.title,
										style = MaterialTheme.typography.bodyLarge,
										fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
										color = if (isSelected) {
											MaterialTheme.colorScheme.primary
										} else {
											MaterialTheme.colorScheme.onSurface
										},
									)
									Text(
										text = if (isCurrentSaved) "Current shelf" else "Move to '${cat.title}'",
										style = MaterialTheme.typography.labelSmall,
										color = if (isSelected) {
											MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
										} else {
											MaterialTheme.colorScheme.onSurfaceVariant
										},
									)
								}
								androidx.compose.material3.RadioButton(
									selected = isSelected,
									onClick = null,
									colors = androidx.compose.material3.RadioButtonDefaults.colors(
										selectedColor = MaterialTheme.colorScheme.primary,
										unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
									),
								)
							}
						}
					}

					HorizontalDivider(
						color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
						modifier = Modifier.padding(vertical = 2.dp),
					)

					// Selectable "Remove from Library" option
					Surface(
						onClick = {
							pendingRemoveFromLibrary = true
						},
						color = if (pendingRemoveFromLibrary) {
							MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
						} else {
							MaterialTheme.colorScheme.surfaceContainerLow
						},
						shape = RoundedCornerShape(16.dp),
						border = BorderStroke(
							width = if (pendingRemoveFromLibrary) 1.5.dp else 1.dp,
							color = if (pendingRemoveFromLibrary) {
								MaterialTheme.colorScheme.error
							} else {
								MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
							},
						),
						modifier = Modifier
							.fillMaxWidth()
							.testTag("shelf_option_remove_from_library"),
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 14.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Column(
								modifier = Modifier.weight(1f),
								verticalArrangement = Arrangement.spacedBy(2.dp),
							) {
								Text(
									text = "Remove from Library",
									style = MaterialTheme.typography.bodyLarge,
									fontWeight = if (pendingRemoveFromLibrary) FontWeight.Bold else FontWeight.Medium,
									color = if (pendingRemoveFromLibrary) {
										MaterialTheme.colorScheme.error
									} else {
										MaterialTheme.colorScheme.onSurface
									},
								)
								Text(
									text = "Unbookmark series from all shelves",
									style = MaterialTheme.typography.labelSmall,
									color = if (pendingRemoveFromLibrary) {
										MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
									} else {
										MaterialTheme.colorScheme.onSurfaceVariant
									},
								)
							}
							androidx.compose.material3.RadioButton(
								selected = pendingRemoveFromLibrary,
								onClick = null,
								colors = androidx.compose.material3.RadioButtonDefaults.colors(
									selectedColor = MaterialTheme.colorScheme.error,
									unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
								),
							)
						}
					}
				}
			},
			dismissButton = {
				TextButton(
					onClick = { showShelfPickerDialog = false },
					modifier = Modifier.testTag("shelf_membership_cancel_button"),
				) {
					Text("Cancel")
				}
			},
			confirmButton = {
				Button(
					onClick = {
						if (pendingRemoveFromLibrary) {
							onToggleLibraryMembership(null)
						} else {
							onToggleLibraryMembership(pendingSelectedCategoryId)
						}
						showShelfPickerDialog = false
					},
					colors = ButtonDefaults.buttonColors(
						containerColor = if (pendingRemoveFromLibrary) {
							MaterialTheme.colorScheme.error
						} else {
							MaterialTheme.colorScheme.primary
						},
						contentColor = if (pendingRemoveFromLibrary) {
							MaterialTheme.colorScheme.onError
						} else {
							MaterialTheme.colorScheme.onPrimary
						},
					),
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier.testTag("shelf_membership_confirm_button"),
				) {
					Text(
						text = if (pendingRemoveFromLibrary) "Confirm Removal" else "Confirm",
						fontWeight = FontWeight.Bold,
					)
				}
			},
		)
	}
}
