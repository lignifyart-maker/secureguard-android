package com.secureguard.app.feature.permissionaudit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                            errorMessage = throwable.message ?: "Scan failed"
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
                                "Recent activity cleared."
                            } else {
                                "Recent activity is already empty."
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
                                ?: "Could not clear recent activity."
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
                            "目前的保護模式可以觀察 DNS 通道事件與服務狀態。每個 app 的歸因與更完整的流量處理仍在持續補強中。"
                        } else {
                            "目前的保護模式可以觀察 DNS 通道事件與服務狀態。可信任 Wi‑Fi 管理已啟用，現有 ${networks.size} 個已儲存網路。"
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
}
