package org.nemuri.app.ui.details

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.core.prefs.DetailsUiMode

class MangaDetailsScrollAnchorTest {

	private val sampleChapterIds = listOf(101L, 102L, 103L, 104L)

	@Test
	fun heroAndActionsAnchorsResetToTopAcrossAllModes() {
		for (mode in DetailsUiMode.entries) {
			assertEquals(
				DetailsScrollTarget(0, 0),
				resolveDetailsModeSwitchScrollTarget(
					anchorKey = "details_hero",
					anchorOffset = 48,
					targetMode = mode,
					hasInlineBanner = false,
					hasSynopsisOrTags = true,
					hasAiRecap = true,
					hasBranches = false,
					chapterIds = sampleChapterIds,
					hasRelatedManga = false,
				),
			)
			assertEquals(
				DetailsScrollTarget(0, 0),
				resolveDetailsModeSwitchScrollTarget(
					anchorKey = "details_actions",
					anchorOffset = 24,
					targetMode = mode,
					hasInlineBanner = false,
					hasSynopsisOrTags = true,
					hasAiRecap = true,
					hasBranches = false,
					chapterIds = sampleChapterIds,
					hasRelatedManga = false,
				),
			)
		}
	}

	@Test
	fun switchingBetweenModesPreservesUnifiedChapterAnchorIndexAndOffset() {
		for (mode in DetailsUiMode.entries) {
			val target = resolveDetailsModeSwitchScrollTarget(
				anchorKey = 103L,
				anchorOffset = 37,
				targetMode = mode,
				hasInlineBanner = true,
				hasSynopsisOrTags = true,
				hasAiRecap = true,
				hasBranches = true,
				chapterIds = sampleChapterIds,
				hasRelatedManga = true,
			)
			// All 3 modes use unified 1-item header: hero(0), banner(1), synopsis(2), recap(3), branches(4), chapters_header(5), ch101(6), ch102(7), ch103(8)
			assertEquals(DetailsScrollTarget(8, 37), target)
		}
	}

	@Test
	fun handlesOptionalSectionsAndClampsNegativeScrollOffset() {
		val target = resolveDetailsModeSwitchScrollTarget(
			anchorKey = "details_chapters_header",
			anchorOffset = -12,
			targetMode = DetailsUiMode.EXPRESSIVE,
			hasInlineBanner = false,
			hasSynopsisOrTags = false,
			hasAiRecap = false,
			hasBranches = false,
			chapterIds = emptyList(),
			hasRelatedManga = false,
		)
		// With no optional sections: hero(0), chapters_header(1)
		assertEquals(DetailsScrollTarget(1, 0), target)
	}
}
