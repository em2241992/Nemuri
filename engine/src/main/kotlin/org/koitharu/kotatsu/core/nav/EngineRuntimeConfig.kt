package org.koitharu.kotatsu.core.nav

object EngineRuntimeConfig {

	@Volatile
	var applicationId: String = "org.nemuri.app"
		private set

	@Volatile
	var versionCode: Int = 1
		private set

	@Volatile
	var versionName: String = "0.1.0-alpha1"
		private set

	fun init(
		applicationId: String,
		versionCode: Int,
		versionName: String,
	) {
		this.applicationId = applicationId
		this.versionCode = versionCode
		this.versionName = versionName
	}
}
