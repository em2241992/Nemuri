package org.nemuri.app.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.prefs.QuickScrollButtonsMode
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.nemuriEffectsSpring
import org.nemuri.app.ui.theme.nemuriFastSpatialSpring
import kotlin.math.roundToInt

/**
 * Unified 120Hz scroll adapter bridging both [LazyListState] and [LazyGridState]
 * for [NemuriSmartScrollPill], [NemuriFastScroller], and [NemuriQuickScrollButtons].
 */
@Stable
interface NemuriScrollableAdapter {
	val firstVisibleItemIndex: Int
	val firstVisibleItemScrollOffset: Int
	val totalItemsCount: Int
	val visibleItemsCount: Int
	val viewportHeightPx: Int
	val isScrollInProgress: Boolean
	val canScrollBackward: Boolean
	val canScrollForward: Boolean
	val scrollFraction: Float

	fun targetIndexForFraction(fraction: Float): Int
	suspend fun scrollToItem(index: Int, scrollOffset: Int = 0)
	suspend fun animateScrollToItem(index: Int, scrollOffset: Int = 0)
	suspend fun animateScrollBy(deltaPx: Float)
}

private class LazyListScrollableAdapter(
	private val state: LazyListState,
) : NemuriScrollableAdapter {
	override val firstVisibleItemIndex: Int
		get() = state.firstVisibleItemIndex

	override val firstVisibleItemScrollOffset: Int
		get() = state.firstVisibleItemScrollOffset

	override val totalItemsCount: Int
		get() = state.layoutInfo.totalItemsCount

	override val visibleItemsCount: Int
		get() = state.layoutInfo.visibleItemsInfo.size

	override val viewportHeightPx: Int
		get() = state.layoutInfo.viewportSize.height

	override val isScrollInProgress: Boolean
		get() = state.isScrollInProgress

	override val canScrollBackward: Boolean
		get() = state.canScrollBackward

	override val canScrollForward: Boolean
		get() = state.canScrollForward

	override val scrollFraction: Float
		get() {
			val info = state.layoutInfo
			val total = info.totalItemsCount
			val visible = info.visibleItemsInfo
			if (total <= 0 || visible.isEmpty()) return 0f
			if (!state.canScrollBackward) return 0f
			if (!state.canScrollForward) return 1f
			val scrollableItems = (total - visible.size).coerceAtLeast(1)
			val avgHeight = (visible.sumOf { it.size }.toFloat() / visible.size).coerceAtLeast(1f)
			val subOffset = (state.firstVisibleItemScrollOffset / avgHeight).coerceIn(0f, 1f)
			return ((state.firstVisibleItemIndex + subOffset) / scrollableItems.toFloat()).coerceIn(0f, 1f)
		}

	override fun targetIndexForFraction(fraction: Float): Int {
		val total = state.layoutInfo.totalItemsCount
		if (total <= 1) return 0
		return (fraction.coerceIn(0f, 1f) * (total - 1)).roundToInt().coerceIn(0, total - 1)
	}

	override suspend fun scrollToItem(index: Int, scrollOffset: Int) {
		val total = state.layoutInfo.totalItemsCount
		if (total <= 0) return
		state.scrollToItem(index.coerceIn(0, total - 1), scrollOffset)
	}

	override suspend fun animateScrollToItem(index: Int, scrollOffset: Int) {
		val total = state.layoutInfo.totalItemsCount
		if (total <= 0) return
		state.animateScrollToItem(index.coerceIn(0, total - 1), scrollOffset)
	}

	override suspend fun animateScrollBy(deltaPx: Float) {
		state.animateScrollBy(deltaPx)
	}
}

private class LazyGridScrollableAdapter(
	private val state: LazyGridState,
) : NemuriScrollableAdapter {
	override val firstVisibleItemIndex: Int
		get() = state.firstVisibleItemIndex

	override val firstVisibleItemScrollOffset: Int
		get() = state.firstVisibleItemScrollOffset

	override val totalItemsCount: Int
		get() = state.layoutInfo.totalItemsCount

	override val visibleItemsCount: Int
		get() = state.layoutInfo.visibleItemsInfo.size

	override val viewportHeightPx: Int
		get() = state.layoutInfo.viewportSize.height

	override val isScrollInProgress: Boolean
		get() = state.isScrollInProgress

	override val canScrollBackward: Boolean
		get() = state.canScrollBackward

	override val canScrollForward: Boolean
		get() = state.canScrollForward

	private val estimatedColumns: Int
		get() {
			val visible = state.layoutInfo.visibleItemsInfo
			if (visible.isEmpty()) return 1
			val maxCol = visible.maxOfOrNull { it.column } ?: 0
			return (maxCol + 1).coerceAtLeast(1)
		}

	override val scrollFraction: Float
		get() {
			val info = state.layoutInfo
			val total = info.totalItemsCount
			val visible = info.visibleItemsInfo
			if (total <= 0 || visible.isEmpty()) return 0f
			if (!state.canScrollBackward) return 0f
			if (!state.canScrollForward) return 1f
			val cols = estimatedColumns
			val totalRows = (total + cols - 1) / cols
			val visibleRows = ((visible.size + cols - 1) / cols).coerceAtLeast(1)
			val scrollableRows = (totalRows - visibleRows).coerceAtLeast(1)
			val firstRow = state.firstVisibleItemIndex / cols
			val avgRowHeight = (visible.firstOrNull()?.size?.height ?: 1).toFloat().coerceAtLeast(1f)
			val subOffset = (state.firstVisibleItemScrollOffset / avgRowHeight).coerceIn(0f, 1f)
			return ((firstRow + subOffset) / scrollableRows.toFloat()).coerceIn(0f, 1f)
		}

	override fun targetIndexForFraction(fraction: Float): Int {
		val total = state.layoutInfo.totalItemsCount
		if (total <= 1) return 0
		val clamped = fraction.coerceIn(0f, 1f)
		if (clamped >= 0.995f) return total - 1
		val cols = estimatedColumns
		val totalRows = (total + cols - 1) / cols
		val targetRow = (clamped * (totalRows - 1).coerceAtLeast(0)).roundToInt()
		return (targetRow * cols).coerceIn(0, total - 1)
	}

	override suspend fun scrollToItem(index: Int, scrollOffset: Int) {
		val total = state.layoutInfo.totalItemsCount
		if (total <= 0) return
		state.scrollToItem(index.coerceIn(0, total - 1), scrollOffset)
	}

	override suspend fun animateScrollToItem(index: Int, scrollOffset: Int) {
		val total = state.layoutInfo.totalItemsCount
		if (total <= 0) return
		state.animateScrollToItem(index.coerceIn(0, total - 1), scrollOffset)
	}

	override suspend fun animateScrollBy(deltaPx: Float) {
		state.animateScrollBy(deltaPx)
	}
}

@Composable
fun rememberNemuriScrollAdapter(listState: LazyListState): NemuriScrollableAdapter =
	remember(listState) { LazyListScrollableAdapter(listState) }

@Composable
fun rememberNemuriScrollAdapter(gridState: LazyGridState): NemuriScrollableAdapter =
	remember(gridState) { LazyGridScrollableAdapter(gridState) }

@Composable
fun rememberThumbZoneBottomClearance(
	hasBottomDock: Boolean = true,
	avoidRightClassicFab: Boolean = false,
	extraBottom: Dp = 8.dp,
): Dp {
	val studio = LocalLayoutStudioPrefs.current
	return if (hasBottomDock) {
		val classicFabExtra = if (avoidRightClassicFab && studio.isClassicBottomBar) {
			if (studio.isNavBarCompact) 56.dp else 62.dp
		} else {
			0.dp
		}
		rememberRootTabBottomPadding(hasBottomDock = true, extraBottom = extraBottom + classicFabExtra)
	} else {
		rememberPushScreenBottomPadding(extraBottom = extraBottom + 12.dp)
	}
}

private data class SavedScrollPosition(
	val index: Int,
	val offset: Int,
)

private enum class SmartScrollPillMode {
	GO_TO_TOP,
	LAST_POSITION,
}

/**
 * DropSauce-inspired centered thumb-zone smart scroll capsule (`⌃ Top` / `⌄ Last position`).
 *
 * - Once scrolled deeper than 3 viewports (`firstVisibleItemIndex >= viewportItems * 3`),
 *   shows **`⌃ Top`**; tapping saves `(index, offset)` and jumps to `0`.
 * - Near the top (`firstVisibleItemIndex <= 2`) when a saved return position exists,
 *   shows **`⌄ Last position`**; tapping jumps back to the saved spot and clears it.
 * - Auto-fades out after `2.5s` idle (`delay(2500L)`) and reappears immediately when scrolling.
 */
@Composable
fun NemuriSmartScrollPill(
	adapter: NemuriScrollableAdapter,
	modifier: Modifier = Modifier,
	enabled: Boolean = LocalLayoutStudioPrefs.current.isLastPositionPillEnabled,
	visibleOverride: Boolean = true,
) {
	val haptic = LocalHapticFeedback.current
	val scope = rememberCoroutineScope()
	var savedReturnPosition by remember(adapter) { mutableStateOf<SavedScrollPosition?>(null) }
	var isIdleFaded by remember(adapter) { mutableStateOf(false) }

	val activeMode by remember(adapter) {
		derivedStateOf {
			val viewportItems = adapter.visibleItemsCount.coerceAtLeast(4)
			val firstIdx = adapter.firstVisibleItemIndex
			when {
				firstIdx >= viewportItems * 3 -> SmartScrollPillMode.GO_TO_TOP
				firstIdx <= 2 && savedReturnPosition != null -> SmartScrollPillMode.LAST_POSITION
				else -> null
			}
		}
	}

	val isScrolling by remember(adapter) {
		derivedStateOf { adapter.isScrollInProgress }
	}

	LaunchedEffect(activeMode, isScrolling) {
		when {
			activeMode == null -> {
				isIdleFaded = false
			}
			isScrolling -> {
				isIdleFaded = false
			}
			else -> {
				isIdleFaded = false
				delay(2500L)
				isIdleFaded = true
			}
		}
	}

	val shouldShow = enabled && visibleOverride && activeMode != null && !isIdleFaded
	// Retain last non-null mode label during exit fade animation
	var displayedMode by remember { mutableStateOf(SmartScrollPillMode.GO_TO_TOP) }
	activeMode?.let { displayedMode = it }

	AnimatedVisibility(
		visible = shouldShow,
		enter = fadeIn(nemuriEffectsSpring()) +
			slideInVertically(nemuriFastSpatialSpring()) { it / 2 } +
			scaleIn(nemuriFastSpatialSpring(), initialScale = 0.92f),
		exit = fadeOut(nemuriEffectsSpring()) +
			slideOutVertically(nemuriFastSpatialSpring()) { it / 3 } +
			scaleOut(nemuriFastSpatialSpring(), targetScale = 0.92f),
		modifier = modifier,
	) {
		val label = when (displayedMode) {
			SmartScrollPillMode.GO_TO_TOP -> "⌃ Top"
			SmartScrollPillMode.LAST_POSITION -> "⌄ Last position"
		}
		Surface(
			color = NemuriTheme.colors.dock.copy(alpha = 0.95f),
			shape = CircleShape,
			shadowElevation = 10.dp,
			border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.58f)),
			modifier = Modifier
				.clip(CircleShape)
				.clickable {
					haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
					when (displayedMode) {
						SmartScrollPillMode.GO_TO_TOP -> {
							savedReturnPosition = SavedScrollPosition(
								index = adapter.firstVisibleItemIndex,
								offset = adapter.firstVisibleItemScrollOffset,
							)
							isIdleFaded = false
							scope.launch {
								adapter.scrollToItem(0, 0)
							}
						}

						SmartScrollPillMode.LAST_POSITION -> {
							val target = savedReturnPosition ?: return@clickable
							savedReturnPosition = null
							scope.launch {
								adapter.scrollToItem(target.index, target.offset)
							}
						}
					}
				},
		) {
			Text(
				text = label,
				style = MaterialTheme.typography.labelMedium,
				fontSize = 12.sp,
				fontWeight = FontWeight.Bold,
				color = NemuriTheme.colors.cyan,
				maxLines = 1,
				softWrap = false,
				modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
			)
		}
	}
}

/**
 * Draggable 120Hz vertical scrubber with auto-fade and floating callout bubble (`A–Z`, `Today`, `Ch. X`).
 *
 * Honors [LayoutStudioPrefs.isFastScrollEnabled] and [LayoutStudioPrefs.isFastScrollLeftHanded].
 * Only activates on lists/grids with `totalItems > visibleItems * 2`, and detaches touch input
 * once faded out so right/left edge card buttons never lose taps when idle.
 */
@Composable
fun BoxScope.NemuriFastScroller(
	adapter: NemuriScrollableAdapter,
	modifier: Modifier = Modifier,
	enabled: Boolean = LocalLayoutStudioPrefs.current.isFastScrollEnabled,
	isLeftHanded: Boolean = LocalLayoutStudioPrefs.current.isFastScrollLeftHanded,
	topTrackPadding: Dp = 8.dp,
	bottomTrackPadding: Dp = 88.dp,
	visibleOverride: Boolean = true,
	labelForIndex: ((Int) -> String?)? = null,
) {
	val density = LocalDensity.current
	val haptic = LocalHapticFeedback.current
	val latestLabelProvider by rememberUpdatedState(labelForIndex)

	val isLongEnough by remember(adapter) {
		derivedStateOf {
			val visible = adapter.visibleItemsCount
			visible > 0 && adapter.totalItemsCount > visible * 2
		}
	}
	val isScrolling by remember(adapter) {
		derivedStateOf { adapter.isScrollInProgress }
	}

	var isDragging by remember(adapter) { mutableStateOf(false) }
	var dragFraction by remember(adapter) { mutableFloatStateOf(0f) }
	var pendingScrollIndex by remember(adapter) { mutableIntStateOf(-1) }
	var isRecentlyActive by remember(adapter) { mutableStateOf(false) }
	var trackHeightPx by remember { mutableIntStateOf(0) }
	var lastCalloutLabel by remember { mutableStateOf<String?>(null) }
	var lastHapticUptimeMs by remember { mutableLongStateOf(0L) }

	LaunchedEffect(isScrolling, isDragging, isLongEnough) {
		if (!isLongEnough) {
			isRecentlyActive = false
			return@LaunchedEffect
		}
		if (isScrolling || isDragging) {
			isRecentlyActive = true
		} else if (isRecentlyActive) {
			delay(1800L)
			if (!adapter.isScrollInProgress && !isDragging) {
				isRecentlyActive = false
			}
		}
	}

	LaunchedEffect(adapter) {
		snapshotFlow { pendingScrollIndex }
			.filter { it >= 0 }
			.collect { targetIdx ->
				adapter.scrollToItem(targetIdx, 0)
			}
	}

	val showScroller = enabled && visibleOverride && isLongEnough && (isRecentlyActive || isDragging)
	val thumbHeightDp = 44.dp
	val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
	val edgeAlignment = if (isLeftHanded) Alignment.CenterStart else Alignment.CenterEnd

	AnimatedVisibility(
		visible = showScroller,
		enter = fadeIn(nemuriEffectsSpring()),
		exit = fadeOut(nemuriEffectsSpring()),
		modifier = modifier
			.align(edgeAlignment)
			.fillMaxHeight()
			.padding(top = topTrackPadding, bottom = bottomTrackPadding),
	) {
		val activeFraction = if (isDragging) dragFraction else adapter.scrollFraction
		val usableTravelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
		val thumbOffsetY = (activeFraction.coerceIn(0f, 1f) * usableTravelPx).roundToInt()

		val currentCallout = if (isDragging && latestLabelProvider != null) {
			val targetIdx = adapter.targetIndexForFraction(activeFraction)
			latestLabelProvider?.invoke(targetIdx)?.takeIf { it.isNotBlank() }
		} else {
			null
		}

		Box(modifier = Modifier.fillMaxHeight()) {
			Box(
				modifier = Modifier
					.align(if (isLeftHanded) Alignment.TopStart else Alignment.TopEnd)
					.fillMaxHeight()
					.width(26.dp)
					.onSizeChanged { trackHeightPx = it.height }
					.pointerInput(adapter, trackHeightPx, thumbHeightPx) {
						if (trackHeightPx <= 0) return@pointerInput
						awaitEachGesture {
							val down = awaitFirstDown(requireUnconsumed = false)
							down.consume()
							val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
							fun yToFraction(y: Float): Float = ((y - thumbHeightPx * 0.5f) / travel).coerceIn(0f, 1f)

							fun scrubTo(fraction: Float, forceHaptic: Boolean) {
								dragFraction = fraction
								val idx = adapter.targetIndexForFraction(fraction)
								if (idx != pendingScrollIndex) {
									pendingScrollIndex = idx
								}
								val nextLabel = latestLabelProvider?.invoke(idx)?.takeIf { it.isNotBlank() }
								val now = SystemClock.uptimeMillis()
								if (forceHaptic || (nextLabel != null && nextLabel != lastCalloutLabel && now - lastHapticUptimeMs >= 36L)) {
									lastHapticUptimeMs = now
									haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
								}
								lastCalloutLabel = nextLabel
							}

							isDragging = true
							isRecentlyActive = true
							scrubTo(yToFraction(down.position.y), forceHaptic = true)

							try {
								while (true) {
									val event = awaitPointerEvent()
									val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
									if (change == null || !change.pressed) break
									change.consume()
									scrubTo(yToFraction(change.position.y), forceHaptic = false)
								}
							} finally {
								isDragging = false
								pendingScrollIndex = -1
								lastCalloutLabel = null
							}
						}
					},
			) {
				// Subtle vertical track rail
				Box(
					modifier = Modifier
						.align(if (isLeftHanded) Alignment.CenterStart else Alignment.CenterEnd)
						.padding(horizontal = 6.dp)
						.width(3.dp)
						.fillMaxHeight()
						.clip(CircleShape)
						.background(NemuriTheme.colors.border.copy(alpha = if (isDragging) 0.68f else 0.36f)),
				)

				// 120Hz Scrubber Thumb Capsule
				val thumbWidth by animateDpAsState(
					targetValue = if (isDragging) 7.dp else 5.dp,
					animationSpec = nemuriFastSpatialSpring(),
					label = "fastScrollThumbWidth",
				)
				Box(
					modifier = Modifier
						.offset { IntOffset(0, thumbOffsetY) }
						.align(if (isLeftHanded) Alignment.TopStart else Alignment.TopEnd)
						.padding(horizontal = 4.dp)
						.width(thumbWidth)
						.height(thumbHeightDp)
						.clip(CircleShape)
						.background(
							if (isDragging) NemuriTheme.colors.cyan else NemuriTheme.colors.cyan.copy(alpha = 0.82f),
							CircleShape,
						),
				)
			}

			// Floating Callout Bubble beside thumb while dragging (unconstrained outside 26.dp rail)
			if (currentCallout != null) {
				Box(
					modifier = Modifier
						.align(if (isLeftHanded) Alignment.TopStart else Alignment.TopEnd)
						.padding(
							start = if (isLeftHanded) 30.dp else 0.dp,
							end = if (isLeftHanded) 0.dp else 30.dp,
						)
						.offset { IntOffset(0, thumbOffsetY) }
						.height(thumbHeightDp),
					contentAlignment = Alignment.Center,
				) {
					Surface(
						color = NemuriTheme.colors.dock.copy(alpha = 0.97f),
						shape = RoundedCornerShape(12.dp),
						shadowElevation = 12.dp,
						border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.68f)),
						modifier = Modifier.widthIn(min = 42.dp, max = 180.dp),
					) {
						Box(
							contentAlignment = Alignment.Center,
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
						) {
							Text(
								text = currentCallout,
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.ExtraBold,
								color = NemuriTheme.colors.cyan,
								maxLines = 1,
								softWrap = false,
							)
						}
					}
				}
			}
		}
	}
}

/**
 * Toggleable `▲ / ▼` Quick-Nav Capsule tucked cleanly in the bottom-right or bottom-left thumb zone.
 *
 * - **Tap `▲` / `▼`:** steps ~1 viewport (`0.88 * viewportHeightPx`) up or down smoothly.
 * - **Long-Press `▲` / `▼`:** triggers haptic tick ([HapticFeedbackType.LongPress]) and jumps straight to Top (`0`) or Bottom (`lastIndex`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NemuriQuickScrollButtons(
	adapter: NemuriScrollableAdapter,
	modifier: Modifier = Modifier,
	mode: QuickScrollButtonsMode = LocalLayoutStudioPrefs.current.quickScrollButtonsMode,
	visibleOverride: Boolean = true,
) {
	val haptic = LocalHapticFeedback.current
	val scope = rememberCoroutineScope()

	val shouldShow by remember(adapter, mode, visibleOverride) {
		derivedStateOf {
			if (!visibleOverride) return@derivedStateOf false
			val total = adapter.totalItemsCount
			val canScrollEither = adapter.canScrollBackward || adapter.canScrollForward
			when (mode) {
				QuickScrollButtonsMode.OFF -> false
				QuickScrollButtonsMode.LONG_LISTS_ONLY -> total >= 24 && canScrollEither
				QuickScrollButtonsMode.ALWAYS -> total > 0 && canScrollEither
			}
		}
	}

	val canUp by remember(adapter) { derivedStateOf { adapter.canScrollBackward } }
	val canDown by remember(adapter) { derivedStateOf { adapter.canScrollForward } }

	AnimatedVisibility(
		visible = shouldShow,
		enter = fadeIn(nemuriEffectsSpring()) + scaleIn(nemuriFastSpatialSpring(), initialScale = 0.88f),
		exit = fadeOut(nemuriEffectsSpring()) + scaleOut(nemuriFastSpatialSpring(), targetScale = 0.88f),
		modifier = modifier,
	) {
		Surface(
			color = NemuriTheme.colors.dock.copy(alpha = 0.93f),
			shape = RoundedCornerShape(999.dp),
			shadowElevation = 8.dp,
			border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.38f)),
			modifier = Modifier
				.width(38.dp)
				.clip(RoundedCornerShape(999.dp)),
		) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
			) {
				Box(
					contentAlignment = Alignment.Center,
					modifier = Modifier
						.fillMaxWidth()
						.height(34.dp)
						.alpha(if (canUp) 1f else 0.35f)
						.combinedClickable(
							enabled = canUp,
							onClick = {
								val step = (adapter.viewportHeightPx * 0.88f).coerceAtLeast(240f)
								scope.launch { adapter.animateScrollBy(-step) }
							},
							onLongClick = {
								haptic.performHapticFeedback(HapticFeedbackType.LongPress)
								scope.launch { adapter.scrollToItem(0, 0) }
							},
						),
				) {
					Icon(
						imageVector = Icons.Rounded.KeyboardArrowUp,
						contentDescription = "Step up (long-press for top)",
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(20.dp),
					)
				}

				HorizontalDivider(
					thickness = 1.dp,
					color = NemuriTheme.colors.border,
					modifier = Modifier.padding(horizontal = 6.dp),
				)

				Box(
					contentAlignment = Alignment.Center,
					modifier = Modifier
						.fillMaxWidth()
						.height(34.dp)
						.alpha(if (canDown) 1f else 0.35f)
						.combinedClickable(
							enabled = canDown,
							onClick = {
								val step = (adapter.viewportHeightPx * 0.88f).coerceAtLeast(240f)
								scope.launch { adapter.animateScrollBy(step) }
							},
							onLongClick = {
								val lastIdx = (adapter.totalItemsCount - 1).coerceAtLeast(0)
								haptic.performHapticFeedback(HapticFeedbackType.LongPress)
								scope.launch { adapter.scrollToItem(lastIdx, 0) }
							},
						),
				) {
					Icon(
						imageVector = Icons.Rounded.KeyboardArrowDown,
						contentDescription = "Step down (long-press for bottom)",
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(20.dp),
					)
				}
			}
		}
	}
}

/**
 * Convenience overlay composing [NemuriFastScroller], [NemuriQuickScrollButtons], and
 * [NemuriSmartScrollPill] (plus optional centered bottom content stacked cleanly with `8.dp` gap)
 * inside a [BoxScope] with zero element collisions.
 */
@Composable
fun BoxScope.NemuriListNavigationOverlay(
	adapter: NemuriScrollableAdapter,
	hasBottomDock: Boolean = true,
	visibleOverride: Boolean = true,
	enableSmartPill: Boolean = true,
	extraQuickButtonsBottomOffset: Dp = 0.dp,
	topTrackPadding: Dp = 8.dp,
	labelForIndex: ((Int) -> String?)? = null,
	bottomCenterAccessory: (@Composable () -> Unit)? = null,
) {
	val layoutPrefs = LocalLayoutStudioPrefs.current
	val isLeftHanded = layoutPrefs.isFastScrollLeftHanded
	val centerClearance = rememberThumbZoneBottomClearance(
		hasBottomDock = hasBottomDock,
		avoidRightClassicFab = false,
	)
	val cornerClearance = rememberThumbZoneBottomClearance(
		hasBottomDock = hasBottomDock,
		avoidRightClassicFab = !isLeftHanded,
	) + extraQuickButtonsBottomOffset

	val quickButtonsShown by remember(adapter, layoutPrefs.quickScrollButtonsMode, visibleOverride) {
		derivedStateOf {
			if (!visibleOverride) return@derivedStateOf false
			val total = adapter.totalItemsCount
			val canScroll = adapter.canScrollBackward || adapter.canScrollForward
			when (layoutPrefs.quickScrollButtonsMode) {
				QuickScrollButtonsMode.OFF -> false
				QuickScrollButtonsMode.LONG_LISTS_ONLY -> total >= 24 && canScroll
				QuickScrollButtonsMode.ALWAYS -> total > 0 && canScroll
			}
		}
	}

	val fastScrollBottomPad = if (quickButtonsShown) cornerClearance + 78.dp else cornerClearance + 8.dp

	NemuriFastScroller(
		adapter = adapter,
		isLeftHanded = isLeftHanded,
		topTrackPadding = topTrackPadding,
		bottomTrackPadding = fastScrollBottomPad,
		visibleOverride = visibleOverride,
		labelForIndex = labelForIndex,
	)

	NemuriQuickScrollButtons(
		adapter = adapter,
		visibleOverride = visibleOverride,
		modifier = Modifier
			.align(if (isLeftHanded) Alignment.BottomStart else Alignment.BottomEnd)
			.padding(
				start = if (isLeftHanded) 12.dp else 0.dp,
				end = if (isLeftHanded) 0.dp else 12.dp,
				bottom = cornerClearance,
			),
	)

	if ((enableSmartPill && layoutPrefs.isLastPositionPillEnabled) || bottomCenterAccessory != null) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(
					start = 56.dp,
					end = 56.dp,
					bottom = centerClearance,
				),
		) {
			if (enableSmartPill) {
				NemuriSmartScrollPill(
					adapter = adapter,
					visibleOverride = visibleOverride,
				)
			}
			bottomCenterAccessory?.invoke()
		}
	}
}
