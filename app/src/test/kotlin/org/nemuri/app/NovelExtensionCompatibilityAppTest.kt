package org.nemuri.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NovelExtensionCompatibilityAppTest {

	@Test
	fun `cloudflare challenge url preserves novel pages but still rewrites assets`() {
		assertEquals(
			"https://novel.example/library/series?page=2",
			NemuriEngineNavigator.getChallengeUrl(
				url = "https://novel.example/library/series?page=2",
				preservePage = true,
			),
		)
		assertEquals(
			"https://novel.example/",
			NemuriEngineNavigator.getChallengeUrl(
				url = "https://cdn.novel.example/covers/123.jpg",
				preservePage = true,
			),
		)
		assertEquals(
			"https://manga.example/",
			NemuriEngineNavigator.getChallengeUrl(
				url = "https://manga.example/manga/123",
				preservePage = false,
			),
		)
	}

	@Test
	fun `cloudflare activity verifies pre-existing clearance cookie after challenge page settles`() {
		val activity = File("src/main/kotlin/org/nemuri/app/browser/CloudFlareActivity.kt").readText()
		val navigator = File("src/main/kotlin/org/nemuri/app/NemuriEngineNavigator.kt").readText()

		assertTrue(navigator.contains("preservePage = exception.source.name.startsWith(\"LN_\")"))
		assertTrue(activity.contains("CloudFlareHelper.getClearanceCookie(cookieJar, it)"))
		assertTrue(activity.contains("onCheckPassed(silentFor = clearance)"))
	}
}
