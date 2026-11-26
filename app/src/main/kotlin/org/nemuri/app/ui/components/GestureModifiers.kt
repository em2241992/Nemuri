package org.nemuri.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 2-finger pinch gesture to step grid column counts up/down without consuming single-finger scrolls or taps.
 */
fun Modifier.pinchToStepColumns(
	currentColumns: Int,
	minColumns: Int,
	maxColumns: Int,
	enabled: Boolean = true,
	onStepColumns: (Int) -> Unit,
): Modifier = composed {
	if (!enabled) return@composed this
	val haptic = LocalHapticFeedback.current
	val latestColumns by rememberUpdatedState(currentColumns)
	val latestCallback by rememberUpdatedState(onStepColumns)

	pointerInput(minColumns, maxColumns) {
		awaitEachGesture {
			awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
			var zoomAccumulator = 1f
			while (true) {
				val event = awaitPointerEvent(pass = PointerEventPass.Initial)
				val activePointers = event.changes.count { it.pressed }
				if (activePointers == 0) break
				if (activePointers >= 2) {
					val zoom = event.calculateZoom()
					if (zoom != 1f) {
						zoomAccumulator *= zoom
						when {
							zoomAccumulator >= 1.25f -> {
								val next = (latestColumns - 1).coerceIn(minColumns, maxColumns)
								if (next != latestColumns) {
									haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
									latestCallback(next)
								}
								zoomAccumulator = 1f
								event.changes.forEach { it.consume() }
							}

							zoomAccumulator <= 0.80f -> {
								val next = (latestColumns + 1).coerceIn(minColumns, maxColumns)
								if (next != latestColumns) {
									haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
									latestCallback(next)
								}
								zoomAccumulator = 1f
								event.changes.forEach { it.consume() }
							}
						}
					}
				} else {
					zoomAccumulator = 1f
				}
			}
		}
	}
}

/**
 * Drag-range multi-select after long-press across visible items in a LazyVerticalGrid.
 */
fun <T> Modifier.gridDragRangeSelect(
	gridState: LazyGridState,
	orderedIds: List<T>,
	selectedIds: Set<T>,
	enabled: Boolean = true,
	onSelectionChange: (Set<T>) -> Unit,
): Modifier = composed {
	if (!enabled) return@composed this
	val haptic = LocalHapticFeedback.current
	val scope = rememberCoroutineScope()
	val edgeBandPx = with(LocalDensity.current) { 56.dp.toPx() }
	val latestIds by rememberUpdatedState(orderedIds)
	val latestSelected by rememberUpdatedState(selectedIds)
	val latestCallback by rememberUpdatedState(onSelectionChange)

	pointerInput(gridState) {
		var autoScrollJob: Job? = null
		fun stopAutoScroll() {
			autoScrollJob?.cancel()
			autoScrollJob = null
		}

		fun findGridIndexAt(position: Offset): Int? {
			val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
				position.x >= info.offset.x &&
					position.x <= info.offset.x + info.size.width &&
					position.y >= info.offset.y &&
					position.y <= info.offset.y + info.size.height
			}
			val id = hit?.key ?: return null
			val idx = latestIds.indexOfFirst { it == id }
			return idx.takeIf { it >= 0 }
		}

		detectDragGesturesAfterLongPress(
			onDragStart = { startOffset ->
				val anchorIdx = findGridIndexAt(startOffset) ?: return@detectDragGesturesAfterLongPress
				val anchorId = latestIds[anchorIdx]
				val targetSelect = anchorId !in latestSelected
				haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				latestCallback(
					if (targetSelect) latestSelected + anchorId else latestSelected - anchorId,
				)

				autoScrollJob?.cancel()
				autoScrollJob = null
			},
			onDragCancel = { stopAutoScroll() },
			onDragEnd = { stopAutoScroll() },
			onDrag = { _, _ -> },
		)
	}.let { baseModifier ->
		// Track pointer coordinates synchronously via stateful closure inside a single detectDragGesturesAfterLongPress block
		val haptic2 = LocalHapticFeedback.current
		val scope2 = rememberCoroutineScope()
		val edgePx = with(LocalDensity.current) { 56.dp.toPx() }
		val idsState by rememberUpdatedState(orderedIds)
		val selState by rememberUpdatedState(selectedIds)
		val cbState by rememberUpdatedState(onSelectionChange)

		this.pointerInput(gridState, enabled) {
			var anchorIndex = -1
			var lastHoveredIndex = -1
			var baseSelection = emptySet<T>()
			var selectMode = true
			var currentPointer = Offset.Zero
			var autoScrollJob: Job? = null

			fun findIdIndexAt(pos: Offset): Int? {
				val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
					pos.x >= item.offset.x &&
						pos.x <= item.offset.x + item.size.width &&
						pos.y >= item.offset.y &&
						pos.y <= item.offset.y + item.size.height
				} ?: return null
				val idx = idsState.indexOfFirst { it == info.key }
				return idx.takeIf { it >= 0 }
			}

			fun applyRange(targetIndex: Int) {
				if (anchorIndex < 0 || targetIndex < 0 || idsState.isEmpty()) return
				val start = minOf(anchorIndex, targetIndex).coerceIn(0, idsState.lastIndex)
				val end = maxOf(anchorIndex, targetIndex).coerceIn(0, idsState.lastIndex)
				val rangeIds = idsState.subList(start, end + 1)
				val updated = if (selectMode) {
					baseSelection + rangeIds
				} else {
					baseSelection - rangeIds.toSet()
				}
				cbState(updated)
			}

			fun updateAutoScroll() {
				val height = gridState.layoutInfo.viewportSize.height.toFloat()
				val speed = when {
					height <= 0f -> 0f
					currentPointer.y < edgePx -> -((edgePx - currentPointer.y) / edgePx).coerceIn(0.2f, 1f) * 26f
					currentPointer.y > height - edgePx -> (((currentPointer.y - (height - edgePx)) / edgePx).coerceIn(0.2f, 1f)) * 26f
					else -> 0f
				}
				if (speed == 0f) {
					autoScrollJob?.cancel()
					autoScrollJob = null
				} else if (autoScrollJob?.isActive != true) {
					autoScrollJob = scope2.launch {
						while (isActive) {
							val curHeight = gridState.layoutInfo.viewportSize.height.toFloat()
							val curSpeed = when {
								curHeight <= 0f -> 0f
								currentPointer.y < edgePx -> -((edgePx - currentPointer.y) / edgePx).coerceIn(0.2f, 1f) * 26f
								currentPointer.y > curHeight - edgePx -> (((currentPointer.y - (curHeight - edgePx)) / edgePx).coerceIn(0.2f, 1f)) * 26f
								else -> 0f
							}
							if (curSpeed == 0f) break
							gridState.scrollBy(curSpeed)
							val idx = findIdIndexAt(currentPointer)
							if (idx != null && idx != lastHoveredIndex) {
								lastHoveredIndex = idx
								haptic2.performHapticFeedback(HapticFeedbackType.TextHandleMove)
								applyRange(idx)
							}
							delay(16L)
						}
					}
				}
			}

			detectDragGesturesAfterLongPress(
				onDragStart = { startOffset ->
					currentPointer = startOffset
					val startIdx = findIdIndexAt(startOffset) ?: return@detectDragGesturesAfterLongPress
					anchorIndex = startIdx
					lastHoveredIndex = startIdx
					baseSelection = selState
					val anchorId = idsState[startIdx]
					selectMode = anchorId !in baseSelection
					haptic2.performHapticFeedback(HapticFeedbackType.LongPress)
					applyRange(startIdx)
				},
				onDragEnd = {
					anchorIndex = -1
					lastHoveredIndex = -1
					autoScrollJob?.cancel()
					autoScrollJob = null
				},
				onDragCancel = {
					anchorIndex = -1
					lastHoveredIndex = -1
					autoScrollJob?.cancel()
					autoScrollJob = null
				},
				onDrag = { change, _ ->
					if (anchorIndex < 0) return@detectDragGesturesAfterLongPress
					currentPointer = change.position
					change.consume()
					val hovered = findIdIndexAt(change.position)
					if (hovered != null && hovered != lastHoveredIndex) {
						lastHoveredIndex = hovered
						haptic2.performHapticFeedback(HapticFeedbackType.TextHandleMove)
						applyRange(hovered)
					}
					updateAutoScroll()
				},
			)
		}
	}
}

/**
 * Drag-range multi-select after long-press across visible items in a LazyColumn.
 */
fun <T> Modifier.listDragRangeSelect(
	listState: LazyListState,
	orderedIds: List<T>,
	selectedIds: Set<T>,
	enabled: Boolean = true,
	keyMapper: (Any) -> T? = { key -> @Suppress("UNCHECKED_CAST") (key as? T) },
	onSelectionChange: (Set<T>) -> Unit,
): Modifier = composed {
	if (!enabled) return@composed this
	val haptic = LocalHapticFeedback.current
	val scope = rememberCoroutineScope()
	val edgePx = with(LocalDensity.current) { 56.dp.toPx() }
	val idsState by rememberUpdatedState(orderedIds)
	val selState by rememberUpdatedState(selectedIds)
	val cbState by rememberUpdatedState(onSelectionChange)
	val mapperState by rememberUpdatedState(keyMapper)

	pointerInput(listState, enabled) {
		var anchorIndex = -1
		var lastHoveredIndex = -1
		var baseSelection = emptySet<T>()
		var selectMode = true
		var currentPointer = Offset.Zero
		var autoScrollJob: Job? = null

		fun findIdIndexAt(pos: Offset): Int? {
			val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
				pos.y >= item.offset && pos.y <= item.offset + item.size
			} ?: return null
			val mappedId = mapperState(info.key) ?: return null
			val idx = idsState.indexOfFirst { it == mappedId }
			return idx.takeIf { it >= 0 }
		}

		fun applyRange(targetIndex: Int) {
			if (anchorIndex < 0 || targetIndex < 0 || idsState.isEmpty()) return
			val start = minOf(anchorIndex, targetIndex).coerceIn(0, idsState.lastIndex)
			val end = maxOf(anchorIndex, targetIndex).coerceIn(0, idsState.lastIndex)
			val rangeIds = idsState.subList(start, end + 1)
			val updated = if (selectMode) {
				baseSelection + rangeIds
			} else {
				baseSelection - rangeIds.toSet()
			}
			cbState(updated)
		}

		fun updateAutoScroll() {
			val height = listState.layoutInfo.viewportSize.height.toFloat()
			val speed = when {
				height <= 0f -> 0f
				currentPointer.y < edgePx -> -((edgePx - currentPointer.y) / edgePx).coerceIn(0.2f, 1f) * 26f
				currentPointer.y > height - edgePx -> (((currentPointer.y - (height - edgePx)) / edgePx).coerceIn(0.2f, 1f)) * 26f
				else -> 0f
			}
			if (speed == 0f) {
				autoScrollJob?.cancel()
				autoScrollJob = null
			} else if (autoScrollJob?.isActive != true) {
				autoScrollJob = scope.launch {
					while (isActive) {
						val curHeight = listState.layoutInfo.viewportSize.height.toFloat()
						val curSpeed = when {
							curHeight <= 0f -> 0f
							currentPointer.y < edgePx -> -((edgePx - currentPointer.y) / edgePx).coerceIn(0.2f, 1f) * 26f
							currentPointer.y > curHeight - edgePx -> (((currentPointer.y - (curHeight - edgePx)) / edgePx).coerceIn(0.2f, 1f)) * 26f
							else -> 0f
						}
						if (curSpeed == 0f) break
						listState.scrollBy(curSpeed)
						val idx = findIdIndexAt(currentPointer)
						if (idx != null && idx != lastHoveredIndex) {
							lastHoveredIndex = idx
							haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
							applyRange(idx)
						}
						delay(16L)
					}
				}
			}
		}

		detectDragGesturesAfterLongPress(
			onDragStart = { startOffset ->
				currentPointer = startOffset
				val startIdx = findIdIndexAt(startOffset) ?: return@detectDragGesturesAfterLongPress
				anchorIndex = startIdx
				lastHoveredIndex = startIdx
				baseSelection = selState
				val anchorId = idsState[startIdx]
				selectMode = anchorId !in baseSelection
				haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				applyRange(startIdx)
			},
			onDragEnd = {
				anchorIndex = -1
				lastHoveredIndex = -1
				autoScrollJob?.cancel()
				autoScrollJob = null
			},
			onDragCancel = {
				anchorIndex = -1
				lastHoveredIndex = -1
				autoScrollJob?.cancel()
				autoScrollJob = null
			},
			onDrag = { change, _ ->
				if (anchorIndex < 0) return@detectDragGesturesAfterLongPress
				currentPointer = change.position
				change.consume()
				val hovered = findIdIndexAt(change.position)
				if (hovered != null && hovered != lastHoveredIndex) {
					lastHoveredIndex = hovered
					haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
					applyRange(hovered)
				}
				updateAutoScroll()
			},
		)
	}
}
