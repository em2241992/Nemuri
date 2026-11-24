package org.nemuri.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.scrobbling.common.domain.Scrobbler
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import org.nemuri.app.navigation.PendingScrobblerAuth
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject
import javax.inject.Provider

data class TrackerAccountItem(
	val service: ScrobblerService,
	val isAuthorized: Boolean,
	val userNickname: String?,
	val userAvatarUrl: String?,
	val oauthUrl: String,
	val isBusy: Boolean = false,
)

data class TrackersUiState(
	val isLoading: Boolean = true,
	val trackers: List<TrackerAccountItem> = emptyList(),
	val isDiscordRpcEnabled: Boolean = false,
	val isDiscordRpcSkipNsfw: Boolean = false,
	val hasDiscordToken: Boolean = false,
	val discordToken: String = "",
)

@HiltViewModel
class TrackersViewModel @Inject constructor(
	private val scrobblersProvider: Provider<Set<@JvmSuppressWildcards Scrobbler>>,
	private val settings: AppSettings,
	val toastManager: NemuriToastManager,
) : ViewModel() {

	private val trackerItemsFlow = MutableStateFlow<List<TrackerAccountItem>>(emptyList())
	private val loadingFlow = MutableStateFlow(true)
	private val busyServicesFlow = MutableStateFlow<Set<Int>>(emptySet())

	val uiState: StateFlow<TrackersUiState> = combine(
		loadingFlow,
		trackerItemsFlow,
		busyServicesFlow,
		settings.observeChanges().onStart { emit(null) },
	) { isLoading, items, busyIds, _ ->
		val token = settings.discordToken.orEmpty()
		TrackersUiState(
			isLoading = isLoading,
			trackers = items.map { it.copy(isBusy = it.service.id in busyIds) },
			isDiscordRpcEnabled = settings.isDiscordRpcEnabled,
			isDiscordRpcSkipNsfw = settings.isDiscordRpcSkipNsfw,
			hasDiscordToken = token.isNotEmpty(),
			discordToken = token,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = TrackersUiState(),
	)

	init {
		loadTrackers(refreshUsers = true)
		observePendingAuthCallbacks()
	}

	private fun observePendingAuthCallbacks() {
		viewModelScope.launch {
			PendingScrobblerAuth.pending.collect { pair ->
				val (serviceId, code) = pair ?: return@collect
				PendingScrobblerAuth.clear()
				authorizeWithCode(serviceId, code)
			}
		}
	}

	private fun loadTrackers(refreshUsers: Boolean) {
		viewModelScope.launch {
			val scrobblers = withContext(Dispatchers.IO) {
				scrobblersProvider.get().sortedBy { it.scrobblerService.id }
			}
			trackerItemsFlow.value = scrobblers.map { scrobbler ->
				val cached = scrobbler.cachedUser
				TrackerAccountItem(
					service = scrobbler.scrobblerService,
					isAuthorized = scrobbler.isEnabled,
					userNickname = cached?.nickname,
					userAvatarUrl = cached?.avatar,
					oauthUrl = scrobbler.oauthUrl,
				)
			}
			loadingFlow.value = false

			if (refreshUsers) {
				scrobblers.filter { it.isEnabled }.forEach { scrobbler ->
					launch(Dispatchers.IO) {
						runCatching {
							scrobbler.user.collect { user ->
								trackerItemsFlow.value = trackerItemsFlow.value.map { item ->
									if (item.service == scrobbler.scrobblerService) {
										item.copy(
											isAuthorized = scrobbler.isEnabled,
											userNickname = user.nickname,
											userAvatarUrl = user.avatar,
										)
									} else {
										item
									}
								}
							}
						}
					}
				}
			}
		}
	}

	fun authorizeKitsu(email: String, password: String) {
		val cleanEmail = email.trim()
		if (cleanEmail.isBlank() || password.isBlank()) {
			toastManager.show("Enter both Kitsu email and password")
			return
		}
		authorizeWithCode(ScrobblerService.KITSU.id, "$cleanEmail;$password")
	}

	private fun authorizeWithCode(serviceId: Int, code: String) {
		viewModelScope.launch {
			busyServicesFlow.value = busyServicesFlow.value + serviceId
			val result = runCatching {
				withContext(Dispatchers.IO) {
					val target = scrobblersProvider.get().firstOrNull { it.scrobblerService.id == serviceId }
						?: error("Tracker not available")
					target.authorize(code)
				}
			}
			result.onSuccess { user ->
				toastManager.show("Connected as ${user.nickname}")
				loadTrackers(refreshUsers = false)
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Tracker authentication failed")
			}
			busyServicesFlow.value = busyServicesFlow.value - serviceId
		}
	}

	fun logout(service: ScrobblerService) {
		viewModelScope.launch {
			busyServicesFlow.value = busyServicesFlow.value + service.id
			withContext(Dispatchers.IO) {
				scrobblersProvider.get().firstOrNull { it.scrobblerService == service }?.logout()
			}
			busyServicesFlow.value = busyServicesFlow.value - service.id
			loadTrackers(refreshUsers = false)
			toastManager.show("Disconnected tracker")
		}
	}

	// Discord RPC
	fun setDiscordRpcEnabled(enabled: Boolean) = settings.setDiscordRpcEnabled(enabled)
	fun setDiscordRpcSkipNsfw(skip: Boolean) = settings.setDiscordRpcSkipNsfw(skip)
	fun setDiscordToken(token: String?) {
		settings.discordToken = token
		toastManager.show(if (token.isNullOrBlank()) "Discord token cleared" else "Discord token saved")
	}
}
