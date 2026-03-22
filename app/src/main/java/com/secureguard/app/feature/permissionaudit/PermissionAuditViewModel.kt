package com.secureguard.app.feature.permissionaudit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.app.core.datastore.SettingsDataStore
import com.secureguard.app.domain.usecase.BuildSecurityOverviewUseCase
import com.secureguard.app.domain.usecase.GetWifiSecuritySnapshotUseCase
import com.secureguard.app.domain.usecase.ScanInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
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
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(PermissionAuditUiState())
    val uiState: StateFlow<PermissionAuditUiState> = _uiState.asStateFlow()

    init {
        observeLastScan()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val apps = scanInstalledAppsUseCase()
                val wifiSnapshot = getWifiSecuritySnapshotUseCase()
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

    private fun observeLastScan() {
        viewModelScope.launch {
            settingsDataStore.lastScanTimestamp.collect { timestamp ->
                val label = timestamp?.let {
                    DateFormat.getDateTimeInstance(
                        DateFormat.MEDIUM,
                        DateFormat.SHORT
                    ).format(Date(it))
                } ?: "Never"
                _uiState.update { it.copy(lastScanLabel = label) }
            }
        }
    }
}
