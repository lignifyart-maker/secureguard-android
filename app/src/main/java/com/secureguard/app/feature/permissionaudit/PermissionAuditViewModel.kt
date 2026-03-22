package com.secureguard.app.feature.permissionaudit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.app.BuildConfig
import com.secureguard.app.core.datastore.SettingsDataStore
import com.secureguard.app.domain.usecase.BuildSecurityOverviewUseCase
import com.secureguard.app.domain.usecase.ClearNetworkEventsUseCase
import com.secureguard.app.domain.usecase.GetWifiSecuritySnapshotUseCase
import com.secureguard.app.domain.usecase.ObserveConnectionFeedPreviewUseCase
import com.secureguard.app.domain.usecase.ObserveRecentConnectionTimelineUseCase
import com.secureguard.app.domain.usecase.ScanInstalledAppsUseCase
import com.secureguard.app.domain.model.VpnProtectionState
import com.secureguard.app.vpn.LocalVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

@HiltViewModel
class PermissionAuditViewModel @Inject constructor(
    private val scanInstalledAppsUseCase: ScanInstalledAppsUseCase,
    private val getWifiSecuritySnapshotUseCase: GetWifiSecuritySnapshotUseCase,
    private val buildSecurityOverviewUseCase: BuildSecurityOverviewUseCase,
    private val clearNetworkEventsUseCase: ClearNetworkEventsUseCase,
    private val observeConnectionFeedPreviewUseCase: ObserveConnectionFeedPreviewUseCase,
    private val observeRecentConnectionTimelineUseCase: ObserveRecentConnectionTimelineUseCase,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(PermissionAuditUiState())
    val uiState: StateFlow<PermissionAuditUiState> = _uiState.asStateFlow()
    private var connectionFeedJob: Job? = null
    private var recentTimelineJob: Job? = null

    init {
        observeLastScan()
        observeTrustedWifiNetworks()
        observeVpnState()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val apps = scanInstalledAppsUseCase()
                val trustedNetworks = settingsDataStore.getTrustedWifiNetworks()
                val wifiSnapshot = getWifiSecuritySnapshotUseCase(trustedNetworks)
                Triple(
                    apps,
                    wifiSnapshot,
                    buildSecurityOverviewUseCase(apps, wifiSnapshot)
                )
            }
                .onSuccess { (apps, wifiSnapshot, securityOverview) ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            apps = apps,
                            wifiSnapshot = wifiSnapshot,
                            securityOverview = securityOverview,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "這次沒有順利整理好，等一下再試一次。"
                        )
                    }
                }
        }
    }

    fun setWifiTrusted(trusted: Boolean) {
        viewModelScope.launch {
            val snapshot = uiState.value.wifiSnapshot
            if (!snapshot.canManageTrust) return@launch
            settingsDataStore.setWifiTrusted(snapshot.networkName, trusted)
            refresh()
        }
    }

    fun removeTrustedWifi(ssid: String) {
        viewModelScope.launch {
            settingsDataStore.setWifiTrusted(ssid, false)
            refresh()
        }
    }

    fun requestProtectionDisclosure() {
        _uiState.update { it.copy(showVpnDisclosure = true) }
    }

    fun dismissProtectionDisclosure() {
        _uiState.update { it.copy(showVpnDisclosure = false) }
    }

    fun clearRecentActivity() {
        viewModelScope.launch {
            if (_uiState.value.isClearingRecentActivity) return@launch
            val hadItems = _uiState.value.recentConnectionTimeline.items.isNotEmpty()
            _uiState.update {
                it.copy(
                    isClearingRecentActivity = true,
                    recentActivityStatusMessage = null
                )
            }
            runCatching {
                clearNetworkEventsUseCase()
            }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isClearingRecentActivity = false,
                            recentActivityStatusMessage = if (hadItems) {
                                "剛剛的動態已經幫你清空了。"
                            } else {
                                "這裡本來就沒有東西。"
                            }
                        )
                    }
                    delay(2500)
                    _uiState.update { current ->
                        if (current.recentConnectionTimeline.items.isEmpty()) {
                            current.copy(recentActivityStatusMessage = null)
                        } else {
                            current
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isClearingRecentActivity = false,
                            recentActivityStatusMessage = throwable.message
                                ?: "這次沒有清掉，等一下再試一次。"
                        )
                    }
                }
        }
    }

    fun toggleRecentActivityExpanded() {
        _uiState.update { current ->
            current.copy(isRecentActivityExpanded = !current.isRecentActivityExpanded)
        }
    }

    fun openRecentActivityHistory() {
        _uiState.update { current ->
            current.copy(isRecentActivityHistoryOpen = true)
        }
    }

    fun closeRecentActivityHistory() {
        _uiState.update { current ->
            current.copy(isRecentActivityHistoryOpen = false)
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.isCheckingForUpdate) return@launch
            _uiState.update {
                it.copy(
                    isCheckingForUpdate = true,
                    updateStatusMessage = null,
                    availableUpdate = null
                )
            }
            runCatching {
                val connection = URL(LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connection.inputStream.bufferedReader().use { it.readText() }
            }
                .map { body ->
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name").ifBlank { json.optString("name") }
                    val releaseUrl = json.optString("html_url")
                    val releaseTitle = json.optString("name").ifBlank { tag }
                    Triple(tag, releaseTitle, releaseUrl)
                }
                .onSuccess { (tag, title, url) ->
                    val latestVersion = tag.removePrefix("v")
                    val hasUpdate = latestVersion.isNotBlank() &&
                        latestVersion != BuildConfig.VERSION_NAME &&
                        url.isNotBlank()
                    _uiState.update {
                        it.copy(
                            isCheckingForUpdate = false,
                            updateStatusMessage = if (hasUpdate) {
                                "找到新版 $latestVersion"
                            } else {
                                "目前已經是最新版"
                            },
                            availableUpdate = if (hasUpdate) {
                                AvailableUpdate(
                                    versionLabel = latestVersion,
                                    releaseTitle = title,
                                    releaseUrl = url
                                )
                            } else {
                                null
                            }
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isCheckingForUpdate = false,
                            updateStatusMessage = "現在還查不到新版本，等一下再試一次。"
                        )
                    }
                }
        }
    }

    fun dismissUpdateStatus() {
        _uiState.update { current ->
            current.copy(updateStatusMessage = null, availableUpdate = null)
        }
    }

    private fun observeLastScan() {
        viewModelScope.launch {
            settingsDataStore.lastScanTimestamp.collect { timestamp ->
                val label = timestamp?.let {
                    DateFormat.getDateTimeInstance(
                        DateFormat.MEDIUM,
                        DateFormat.SHORT
                    ).format(Date(it))
                } ?: "從未"
                _uiState.update { it.copy(lastScanLabel = label) }
            }
        }
    }

    private fun observeTrustedWifiNetworks() {
        viewModelScope.launch {
            settingsDataStore.trustedWifiNetworks.collect { networks ->
                _uiState.update { current ->
                    current.copy(
                        trustedWifiNetworks = networks.sorted(),
                        vpnCapabilityNote = if (networks.isEmpty()) {
                            "現在會先幫你看 DNS 和一部分連線動態，也會盡量告訴你是哪個 app 在動。"
                        } else {
                            "現在會先幫你看 DNS 和一部分連線動態，已記住 ${networks.size} 個安心 Wi‑Fi。"
                        }
                    )
                }
            }
        }
    }

    private fun observeVpnState() {
        viewModelScope.launch {
            LocalVpnService.serviceState.collect { state ->
                _uiState.update { current ->
                    current.copy(vpnProtectionState = state)
                }
                observeConnectionFeed(state)
            }
        }
        viewModelScope.launch {
            LocalVpnService.statusMessage.collect { message ->
                _uiState.update { current ->
                    current.copy(vpnStatusMessage = message)
                }
            }
        }
        observeRecentTimeline()
    }

    private fun observeConnectionFeed(vpnState: VpnProtectionState) {
        connectionFeedJob?.cancel()
        connectionFeedJob = viewModelScope.launch {
            observeConnectionFeedPreviewUseCase(vpnState).collect { preview ->
                _uiState.update { current ->
                    current.copy(connectionFeedPreview = preview)
                }
            }
        }
    }

    private fun observeRecentTimeline() {
        recentTimelineJob?.cancel()
        recentTimelineJob = viewModelScope.launch {
            observeRecentConnectionTimelineUseCase().collect { timeline ->
                _uiState.update { current ->
                    current.copy(
                        recentConnectionTimeline = timeline,
                        isRecentActivityExpanded = if (timeline.hasMoreThanPreview) {
                            current.isRecentActivityExpanded
                        } else {
                            false
                        },
                        recentActivityStatusMessage = if (timeline.items.isNotEmpty()) {
                            null
                        } else {
                            current.recentActivityStatusMessage
                        }
                    )
                }
            }
        }
    }

    private companion object {
        const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/lignifyart-maker/secureguard-android/releases/latest"
    }
}
