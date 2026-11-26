package org.nemuri.app.ui.explore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.kanade.tachiyomi.source.model.Filter
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.nemuri.app.browser.CloudFlareActivity
import org.nemuri.app.browser.NemuriBrowserActivity
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.MangaCompactGridCard
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriErrorState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.rememberSyncedTextField
import org.nemuri.app.ui.theme.NemuriTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceBrowseScreen(
	sourceName: String,
	initialQuery: String? = null,
	onBack: () -> Unit,
	onOpenDetails: (Manga) -> Unit,
	onFindAlternative: (String) -> Unit = {},
	viewModel: SourceBrowseViewModel = viewModel(key = "browse:$sourceName:${initialQuery.orEmpty()}"),
) {
	val context = LocalContext.current
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	LaunchedEffect(sourceName, initialQuery) {
		viewModel.bind(sourceName, initialQuery)
	}

	val cfLauncher = rememberLauncherForActivityResult(CloudFlareActivity.Contract()) { success ->
		viewModel.onChallengeResolved(success)
	}
	val browserLauncher = rememberLauncherForActivityResult(NemuriBrowserActivity.Contract()) { success ->
		viewModel.onChallengeResolved(success)
	}

	// Auto-launch CloudFlare or Browser verification once when a source load hits a challenge.
	var lastLaunchedCfUrl by remember { mutableStateOf<String?>(null) }
	var lastLaunchedInteractiveUrl by remember { mutableStateOf<String?>(null) }

	LaunchedEffect(uiState.pendingCloudFlare) {
		val ex = uiState.pendingCloudFlare ?: run {
			lastLaunchedCfUrl = null
			return@LaunchedEffect
		}
		if (lastLaunchedCfUrl != ex.url) {
			lastLaunchedCfUrl = ex.url
			cfLauncher.launch(ex)
		}
	}

	LaunchedEffect(uiState.pendingInteractive) {
		val ex = uiState.pendingInteractive ?: run {
			lastLaunchedInteractiveUrl = null
			return@LaunchedEffect
		}
		if (lastLaunchedInteractiveUrl != ex.url) {
			lastLaunchedInteractiveUrl = ex.url
			browserLauncher.launch(ex)
		}
	}

	var isSearchExpanded by rememberSaveable(sourceName) { mutableStateOf(!initialQuery.isNullOrBlank()) }
	var showFilterSheet by remember { mutableStateOf(false) }
	var showLanguageVariantSheet by remember { mutableStateOf(false) }

	val hasFilterControls = uiState.supportsDynamicFilters && uiState.dynamicFilters.isNotEmpty() ||
		uiState.staticFilters.availableTags.isNotEmpty() ||
		uiState.staticFilters.availableStates.isNotEmpty() ||
		uiState.staticFilters.availableContentRatings.isNotEmpty() ||
		uiState.staticFilters.availableContentTypes.isNotEmpty()

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(NemuriTheme.colors.background)
			.statusBarsPadding()
			.navigationBarsPadding(),
	) {
		// Header Bar
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 6.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(onClick = onBack) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.ArrowBack,
					contentDescription = "Back",
					tint = NemuriTheme.colors.textPrimary,
				)
			}

			if (isSearchExpanded && uiState.isSearchSupported) {
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(14.dp),
					modifier = Modifier
						.weight(1f)
						.height(40.dp)
						.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp)),
				) {
					val queryField = rememberSyncedTextField(uiState.query, viewModel::setQuery)
					Row(
						modifier = Modifier.padding(horizontal = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Box(modifier = Modifier.weight(1f)) {
							if (queryField.fieldValue.text.isEmpty()) {
								Text(
									text = "Search ${uiState.sourceTitle}…",
									style = MaterialTheme.typography.bodySmall,
									color = NemuriTheme.colors.textDim,
									maxLines = 1,
								)
							}
							BasicTextField(
								value = queryField.fieldValue,
								onValueChange = queryField::onValueChange,
								singleLine = true,
								textStyle = MaterialTheme.typography.bodySmall.copy(color = NemuriTheme.colors.textPrimary),
								cursorBrush = SolidColor(NemuriTheme.colors.cyan),
								keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
								keyboardActions = KeyboardActions(onSearch = { viewModel.submitSearch() }),
								modifier = Modifier.fillMaxWidth(),
							)
						}
						IconButton(
							onClick = {
								if (queryField.fieldValue.text.isNotEmpty() || uiState.query.isNotEmpty()) {
									queryField.clear()
									viewModel.clearSearch()
								} else {
									isSearchExpanded = false
								}
							},
							modifier = Modifier.size(28.dp),
						) {
							Icon(
								imageVector = Icons.Default.Close,
								contentDescription = "Clear search",
								tint = NemuriTheme.colors.textMuted,
								modifier = Modifier.size(16.dp),
							)
						}
					}
				}
				IconButton(onClick = viewModel::submitSearch) {
					Icon(
						imageVector = Icons.Default.Search,
						contentDescription = "Run search",
						tint = NemuriTheme.colors.cyan,
					)
				}
				if (uiState.languageVariants.size > 1) {
					IconButton(onClick = { showLanguageVariantSheet = true }) {
						Icon(
							imageVector = Icons.Default.Language,
							contentDescription = "Switch source language",
							tint = NemuriTheme.colors.cyan,
						)
					}
				}
			} else {
				Column(
					modifier = Modifier
						.weight(1f)
						.padding(horizontal = 4.dp),
				) {
					Text(
						text = uiState.sourceTitle.ifBlank { sourceName },
						style = MaterialTheme.typography.titleMedium,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					val subtitle = uiState.languageSubtitle
					if (!subtitle.isNullOrBlank()) {
						if (uiState.languageVariants.size > 1) {
							Surface(
								color = NemuriTheme.colors.tealContainer,
								shape = RoundedCornerShape(999.dp),
								modifier = Modifier
									.padding(top = 2.dp)
									.border(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
									.clip(RoundedCornerShape(999.dp))
									.clickable { showLanguageVariantSheet = true },
							) {
								Row(
									modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(4.dp),
								) {
									Icon(
										imageVector = Icons.Default.Language,
										contentDescription = null,
										tint = NemuriTheme.colors.cyan,
										modifier = Modifier.size(12.dp),
									)
									Text(
										text = subtitle,
										style = MaterialTheme.typography.labelSmall,
										color = NemuriTheme.colors.cyan,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
									Icon(
										imageVector = Icons.Default.ArrowDropDown,
										contentDescription = "Switch source language",
										tint = NemuriTheme.colors.cyan,
										modifier = Modifier.size(15.dp),
									)
								}
							}
						} else {
							Text(
								text = subtitle,
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.cyan,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
					}
				}
				if (uiState.isSearchSupported) {
					IconButton(onClick = { isSearchExpanded = true }) {
						Icon(
							imageVector = Icons.Default.Search,
							contentDescription = "Search source",
							tint = NemuriTheme.colors.textSecondary,
						)
					}
				}
			}

			val homeUrl = uiState.homeUrl
			if (!homeUrl.isNullOrBlank()) {
				IconButton(
					onClick = {
						runCatching {
							context.startActivity(
								AppRouter.browserIntent(context, homeUrl, uiState.resolvedSource, uiState.sourceTitle),
							)
						}
					},
				) {
					Icon(
						imageVector = Icons.Default.OpenInBrowser,
						contentDescription = "Open in WebView",
						tint = NemuriTheme.colors.textSecondary,
					)
				}
			}

			if (hasFilterControls) {
				IconButton(onClick = { showFilterSheet = true }) {
					BadgedBox(
						badge = {
							if (uiState.activeFilterCount > 0) {
								Badge(
									containerColor = NemuriTheme.colors.cyan,
									contentColor = NemuriTheme.colors.onCyan,
								) {
									Text(uiState.activeFilterCount.toString())
								}
							}
						},
					) {
						Icon(
							imageVector = Icons.Default.FilterList,
							contentDescription = "Filters",
							tint = if (uiState.activeFilterCount > 0) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
						)
					}
				}
			}
		}

		// Sort Order Chips
		if (uiState.availableSortOrders.isNotEmpty()) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.horizontalScroll(rememberScrollState())
					.padding(horizontal = 12.dp, vertical = 4.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				uiState.availableSortOrders.forEach { order ->
					val selected = uiState.selectedSortOrder == order
					Surface(
						color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(999.dp),
						modifier = Modifier
							.border(
								1.dp,
								if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								RoundedCornerShape(999.dp),
							)
							.clip(RoundedCornerShape(999.dp))
							.clickable { viewModel.selectSortOrder(order) },
					) {
						Text(
							text = order.displayLabel(),
							style = MaterialTheme.typography.labelMedium,
							color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
						)
					}
				}
			}
		}

		HorizontalDivider(color = NemuriTheme.colors.border, modifier = Modifier.padding(top = 4.dp))

		when {
			uiState.isInitialLoading -> {
				NemuriLoadingState(message = "Loading ${uiState.sourceTitle.ifBlank { "catalog" }}…")
			}

			uiState.items.isEmpty() && uiState.pendingCloudFlare != null -> {
				val cf = uiState.pendingCloudFlare!!
				ChallengeActionView(
					title = "Cloudflare verification required",
					message = uiState.errorMessage ?: "Solve the challenge in WebView to continue browsing.",
					buttonLabel = "Solve in WebView",
					onAction = { cfLauncher.launch(cf) },
					onRetry = viewModel::retry,
					onFindAlternative = { onFindAlternative(uiState.query.ifBlank { uiState.sourceTitle }) },
				)
			}

			uiState.items.isEmpty() && uiState.pendingInteractive != null -> {
				val interactive = uiState.pendingInteractive!!
				ChallengeActionView(
					title = "Login or action required",
					message = uiState.errorMessage ?: "Complete login or verification in WebView, then close the page.",
					buttonLabel = "Solve in WebView",
					onAction = { browserLauncher.launch(interactive) },
					onRetry = viewModel::retry,
					onFindAlternative = { onFindAlternative(uiState.query.ifBlank { uiState.sourceTitle }) },
				)
			}

			uiState.items.isEmpty() && uiState.pendingChallengeUrl != null -> {
				val url = uiState.pendingChallengeUrl!!
				ChallengeActionView(
					title = "HTTP 403 / Verification required",
					message = uiState.errorMessage ?: "Solve verification in WebView, retry, or search another source.",
					buttonLabel = "Solve in WebView",
					onAction = {
						runCatching {
							context.startActivity(
								AppRouter.browserIntent(context, url, uiState.resolvedSource, uiState.sourceTitle),
							)
						}
					},
					onRetry = viewModel::retry,
					onFindAlternative = { onFindAlternative(uiState.query.ifBlank { uiState.sourceTitle }) },
				)
			}

			uiState.items.isEmpty() && uiState.errorMessage != null -> {
				NemuriErrorState(
					message = uiState.errorMessage!!,
					onRetry = viewModel::retry,
				)
			}

			uiState.items.isEmpty() -> {
				NemuriEmptyState(
					icon = Icons.Default.SearchOff,
					title = "No titles found",
					subtitle = if (uiState.activeFilterCount > 0 || uiState.query.isNotBlank()) {
						"Try clearing filters or using another search query."
					} else {
						"This listing returned no results."
					},
					actionLabel = "Reload",
					onAction = viewModel::retry,
				)
			}

			else -> {
				LazyVerticalGrid(
					columns = GridCells.Adaptive(minSize = 108.dp),
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(12.dp),
					horizontalArrangement = Arrangement.spacedBy(10.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					itemsIndexed(uiState.items, key = { _, manga -> manga.id }) { index, manga ->
						if (index >= uiState.items.lastIndex - 6 && uiState.hasMorePages && !uiState.isLoadingMore) {
							LaunchedEffect(uiState.items.size) {
								viewModel.loadNextPage()
							}
						}
						MangaCompactGridCard(
							manga = manga,
							inLibraryBadge = manga.id in uiState.favouriteIds,
							onClick = {
								MangaTransferCache.put(manga)
								onOpenDetails(manga)
							},
						)
					}

					if (uiState.isLoadingMore) {
						item(span = { GridItemSpan(maxLineSpan) }) {
							Box(
								modifier = Modifier
									.fillMaxWidth()
									.padding(vertical = 18.dp),
								contentAlignment = Alignment.Center,
							) {
								CircularProgressIndicator(
									color = NemuriTheme.colors.cyan,
									strokeWidth = 2.5.dp,
									modifier = Modifier.size(24.dp),
								)
							}
						}
					} else if (uiState.errorMessage != null) {
						item(span = { GridItemSpan(maxLineSpan) }) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(vertical = 10.dp),
								horizontalArrangement = Arrangement.Center,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Text(
									text = uiState.errorMessage!!,
									style = MaterialTheme.typography.bodySmall,
									color = NemuriTheme.colors.danger,
									modifier = Modifier.weight(1f),
								)
								Spacer(modifier = Modifier.width(8.dp))
								OutlinedButton(onClick = viewModel::loadNextPage) {
									Icon(
										imageVector = Icons.Default.Refresh,
										contentDescription = null,
										modifier = Modifier.size(16.dp),
									)
									Spacer(modifier = Modifier.width(4.dp))
									Text("Retry")
								}
							}
						}
					}
				}
			}
		}
	}

	if (showFilterSheet) {
		NemuriModalSheet(
			title = "Source Filters",
			subtitle = uiState.sourceTitle,
			onDismiss = { showFilterSheet = false },
		) {
			SourceFilterSheetContent(
				uiState = uiState,
				viewModel = viewModel,
				onApply = {
					viewModel.applyFilters()
					showFilterSheet = false
				},
				onReset = {
					viewModel.resetFilters()
					showFilterSheet = false
				},
			)
		}
	}

	if (showLanguageVariantSheet && uiState.languageVariants.size > 1) {
		NemuriModalSheet(
			title = "Source Language",
			subtitle = "${uiState.sourceTitle} • ${uiState.languageVariants.size} languages available",
			onDismiss = { showLanguageVariantSheet = false },
		) {
			LazyColumn(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 420.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
				contentPadding = PaddingValues(bottom = 12.dp),
			) {
				items(uiState.languageVariants, key = { it.languageCode }) { variant ->
					Surface(
						color = if (variant.isActive) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier
							.fillMaxWidth()
							.border(
								1.dp,
								if (variant.isActive) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								RoundedCornerShape(12.dp),
							)
							.clip(RoundedCornerShape(12.dp))
							.clickable {
								viewModel.selectSourceLanguage(variant.languageCode)
								showLanguageVariantSheet = false
							},
					) {
						Row(
							modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = variant.displayName,
									style = MaterialTheme.typography.titleSmall,
									color = if (variant.isActive) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
								)
								Text(
									text = variant.languageCode.uppercase(),
									style = MaterialTheme.typography.labelSmall,
									color = NemuriTheme.colors.textMuted,
								)
							}
							if (variant.isActive) {
								Icon(
									imageVector = Icons.Default.Check,
									contentDescription = null,
									tint = NemuriTheme.colors.cyan,
									modifier = Modifier.size(18.dp),
								)
							}
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceFilterSheetContent(
	uiState: SourceBrowseUiState,
	viewModel: SourceBrowseViewModel,
	onApply: () -> Unit,
	onReset: () -> Unit,
) {
	val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

	Column(modifier = Modifier.fillMaxWidth()) {
		LazyColumn(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(max = 420.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
			contentPadding = PaddingValues(bottom = 12.dp),
		) {
			if (uiState.supportsDynamicFilters) {
				items(uiState.dynamicFilters, key = { it.path }) { node ->
					DynamicFilterItem(
						node = node,
						expandedGroups = expandedGroups,
						viewModel = viewModel,
					)
				}
			} else {
				val sf = uiState.staticFilters
				if (sf.availableStates.isNotEmpty()) {
					item {
						FilterGroupHeader("Publication Status")
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							sf.availableStates.forEach { mangaState ->
								val selected = mangaState in sf.selectedStates
								StaticFilterChip(
									label = mangaState.name.lowercase().replaceFirstChar { it.uppercase() },
									selected = selected,
									onClick = { viewModel.toggleStaticState(mangaState) },
								)
							}
						}
					}
				}
				if (sf.availableContentRatings.isNotEmpty()) {
					item {
						FilterGroupHeader("Content Rating")
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							sf.availableContentRatings.forEach { rating ->
								StaticFilterChip(
									label = rating.name.lowercase().replaceFirstChar { it.uppercase() },
									selected = rating in sf.selectedRatings,
									onClick = { viewModel.toggleStaticRating(rating) },
								)
							}
						}
					}
				}
				if (sf.availableContentTypes.isNotEmpty()) {
					item {
						FilterGroupHeader("Format")
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							sf.availableContentTypes.forEach { type ->
								StaticFilterChip(
									label = type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
									selected = type in sf.selectedContentTypes,
									onClick = { viewModel.toggleStaticContentType(type) },
								)
							}
						}
					}
				}
				if (sf.availableTags.isNotEmpty()) {
					item {
						FilterGroupHeader("Genres & Tags")
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							sf.availableTags.forEach { tag ->
								val isIncluded = tag in sf.selectedTags
								val isExcluded = tag in sf.excludedTags
								StaticFilterChip(
									label = if (isExcluded) "− ${tag.title}" else tag.title,
									selected = isIncluded || isExcluded,
									isExcluded = isExcluded,
									onClick = { viewModel.toggleStaticTag(tag) },
								)
							}
						}
					}
				}
			}
		}

		HorizontalDivider(color = NemuriTheme.colors.border, modifier = Modifier.padding(vertical = 8.dp))

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			OutlinedButton(
				onClick = onReset,
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier.weight(1f),
			) {
				Text("Reset")
			}
			Button(
				onClick = onApply,
				colors = ButtonDefaults.buttonColors(
					containerColor = NemuriTheme.colors.cyan,
					contentColor = NemuriTheme.colors.onCyan,
				),
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier.weight(1f),
			) {
				Text("Apply Filters", fontWeight = FontWeight.Bold)
			}
		}
	}
}

@Composable
private fun DynamicFilterItem(
	node: DynamicFilterNode,
	expandedGroups: MutableMap<String, Boolean>,
	viewModel: SourceBrowseViewModel,
) {
	when (node) {
		is DynamicFilterNode.Header -> {
			Text(
				text = node.name,
				style = MaterialTheme.typography.titleSmall,
				color = NemuriTheme.colors.cyan,
				modifier = Modifier.padding(top = 6.dp),
			)
		}

		is DynamicFilterNode.Separator -> {
			HorizontalDivider(color = NemuriTheme.colors.border, modifier = Modifier.padding(vertical = 4.dp))
		}

		is DynamicFilterNode.CheckBox -> {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(10.dp))
					.clickable { viewModel.updateDynamicCheckBox(node.path, !node.checked) }
					.padding(vertical = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Checkbox(
					checked = node.checked,
					onCheckedChange = { viewModel.updateDynamicCheckBox(node.path, it) },
					colors = CheckboxDefaults.colors(checkedColor = NemuriTheme.colors.cyan),
				)
				Text(
					text = node.name,
					style = MaterialTheme.typography.bodyMedium,
					color = NemuriTheme.colors.textPrimary,
				)
			}
		}

		is DynamicFilterNode.TriState -> {
			val triState = when (node.state) {
				Filter.TriState.STATE_INCLUDE -> ToggleableState.On
				Filter.TriState.STATE_EXCLUDE -> ToggleableState.Indeterminate
				else -> ToggleableState.Off
			}
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(10.dp))
					.clickable { viewModel.cycleDynamicTriState(node.path) }
					.padding(vertical = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				TriStateCheckbox(
					state = triState,
					onClick = { viewModel.cycleDynamicTriState(node.path) },
					colors = CheckboxDefaults.colors(checkedColor = NemuriTheme.colors.cyan),
				)
				Text(
					text = when (node.state) {
						Filter.TriState.STATE_EXCLUDE -> "${node.name} (Exclude)"
						else -> node.name
					},
					style = MaterialTheme.typography.bodyMedium,
					color = if (node.state == Filter.TriState.STATE_EXCLUDE) NemuriTheme.colors.danger else NemuriTheme.colors.textPrimary,
				)
			}
		}

		is DynamicFilterNode.Select -> {
			var expanded by remember { mutableStateOf(false) }
			val currentText = node.options.getOrNull(node.selectedIndex).orEmpty()
			Box(modifier = Modifier.fillMaxWidth()) {
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier
						.fillMaxWidth()
						.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp))
						.clip(RoundedCornerShape(12.dp))
						.clickable { expanded = true },
				) {
					Row(
						modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = node.name,
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.textMuted,
							)
							Text(
								text = currentText.ifBlank { "Default" },
								style = MaterialTheme.typography.bodyMedium,
								color = NemuriTheme.colors.textPrimary,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
						Icon(
							imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
							contentDescription = null,
							tint = NemuriTheme.colors.textMuted,
						)
					}
				}
				DropdownMenu(
					expanded = expanded,
					onDismissRequest = { expanded = false },
					containerColor = NemuriTheme.colors.sheet,
				) {
					node.options.forEachIndexed { idx, label ->
						DropdownMenuItem(
							text = {
								Text(
									text = label,
									color = if (idx == node.selectedIndex) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
								)
							},
							onClick = {
								viewModel.updateDynamicSelect(node.path, idx)
								expanded = false
							},
						)
					}
				}
			}
		}

		is DynamicFilterNode.Sort -> {
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text(
					text = node.name,
					style = MaterialTheme.typography.labelLarge,
					color = NemuriTheme.colors.textSecondary,
					modifier = Modifier.padding(top = 4.dp),
				)
				node.options.forEachIndexed { idx, label ->
					val isSelected = node.selectedIndex == idx
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(10.dp))
							.clickable { viewModel.updateDynamicSort(node.path, idx) }
							.padding(horizontal = 8.dp, vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Text(
							text = label,
							style = MaterialTheme.typography.bodyMedium,
							color = if (isSelected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
						)
						if (isSelected) {
							Icon(
								imageVector = if (node.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
								contentDescription = null,
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.size(16.dp),
							)
						}
					}
				}
			}
		}

		is DynamicFilterNode.Text -> {
			val filterTextField = rememberSyncedTextField(node.value) { viewModel.updateDynamicText(node.path, it) }
			OutlinedTextField(
				value = filterTextField.fieldValue,
				onValueChange = filterTextField::onValueChange,
				label = { Text(node.name) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
			)
		}

		is DynamicFilterNode.Group -> {
			val expanded = expandedGroups[node.path] ?: false
			Surface(
				color = NemuriTheme.colors.surfaceElevated,
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier
					.fillMaxWidth()
					.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp)),
			) {
				Column(modifier = Modifier.fillMaxWidth()) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable { expandedGroups[node.path] = !expanded }
							.padding(horizontal = 12.dp, vertical = 12.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Text(
							text = node.name,
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
						)
						Icon(
							imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
							contentDescription = null,
							tint = NemuriTheme.colors.textMuted,
						)
					}
					if (expanded) {
						Column(
							modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
							verticalArrangement = Arrangement.spacedBy(4.dp),
						) {
							node.children.forEach { child ->
								DynamicFilterItem(
									node = child,
									expandedGroups = expandedGroups,
									viewModel = viewModel,
								)
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun FilterGroupHeader(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.labelLarge,
		color = NemuriTheme.colors.cyan,
		modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
	)
}

@Composable
private fun StaticFilterChip(
	label: String,
	selected: Boolean,
	isExcluded: Boolean = false,
	onClick: () -> Unit,
) {
	val bg = when {
		isExcluded -> NemuriTheme.colors.danger.copy(alpha = 0.16f)
		selected -> NemuriTheme.colors.tealContainer
		else -> NemuriTheme.colors.surfaceElevated
	}
	val stroke = when {
		isExcluded -> NemuriTheme.colors.danger
		selected -> NemuriTheme.colors.cyan
		else -> NemuriTheme.colors.border
	}
	val fg = when {
		isExcluded -> NemuriTheme.colors.danger
		selected -> NemuriTheme.colors.cyan
		else -> NemuriTheme.colors.textSecondary
	}
	Surface(
		color = bg,
		shape = RoundedCornerShape(999.dp),
		modifier = Modifier
			.border(1.dp, stroke, RoundedCornerShape(999.dp))
			.clip(RoundedCornerShape(999.dp))
			.clickable(onClick = onClick),
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelMedium,
			color = fg,
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeActionView(
	title: String,
	message: String,
	buttonLabel: String,
	onAction: () -> Unit,
	onRetry: () -> Unit,
	onFindAlternative: () -> Unit,
) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.padding(24.dp),
		contentAlignment = Alignment.Center,
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Icon(
				imageVector = Icons.Default.Security,
				contentDescription = null,
				tint = NemuriTheme.colors.amber,
				modifier = Modifier.size(44.dp),
			)
			Text(
				text = title,
				style = MaterialTheme.typography.titleMedium,
				color = NemuriTheme.colors.textPrimary,
			)
			Text(
				text = message,
				style = MaterialTheme.typography.bodyMedium,
				color = NemuriTheme.colors.textMuted,
			)
			FlowRow(
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Button(
					onClick = onAction,
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
					shape = RoundedCornerShape(12.dp),
				) {
					Text(buttonLabel)
				}
				OutlinedButton(
					onClick = onRetry,
					shape = RoundedCornerShape(12.dp),
				) {
					Text("Retry")
				}
				OutlinedButton(
					onClick = onFindAlternative,
					shape = RoundedCornerShape(12.dp),
				) {
					Text("Find Alternative")
				}
			}
		}
	}
}

private fun SortOrder.displayLabel(): String = when (this) {
	SortOrder.POPULARITY -> "Popular"
	SortOrder.POPULARITY_ASC -> "Popularity ↑"
	SortOrder.UPDATED -> "Latest"
	SortOrder.UPDATED_ASC -> "Oldest Update"
	SortOrder.NEWEST -> "Newest"
	SortOrder.NEWEST_ASC -> "Oldest Added"
	SortOrder.RATING -> "Top Rated"
	SortOrder.RATING_ASC -> "Lowest Rated"
	SortOrder.ALPHABETICAL -> "A–Z"
	SortOrder.ALPHABETICAL_DESC -> "Z–A"
	SortOrder.ADDED -> "Recently Added"
	SortOrder.ADDED_ASC -> "Added ↑"
	SortOrder.RELEVANCE -> "Relevance"
	SortOrder.POPULARITY_HOUR -> "Popular (Hour)"
	SortOrder.POPULARITY_TODAY -> "Popular (Today)"
	SortOrder.POPULARITY_WEEK -> "Popular (Week)"
	SortOrder.POPULARITY_MONTH -> "Popular (Month)"
	SortOrder.POPULARITY_YEAR -> "Popular (Year)"
}
