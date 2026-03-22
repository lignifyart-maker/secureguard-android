package com.secureguard.app.feature.permissionaudit

import com.secureguard.app.domain.model.AppScanResult
import com.secureguard.app.domain.model.WifiSafetyLevel
import com.secureguard.app.domain.model.WifiSecuritySnapshot

data class PermissionAuditUiState(
    val isLoading: Boolean = true,
    val apps: List<AppScanResult> = emptyList(),
    val errorMessage: String? = null,
    val lastScanLabel: String = "Never",
    val wifiSnapshot: WifiSecuritySnapshot = WifiSecuritySnapshot(
        isWifiActive = false,
        networkName = "Checking network...",
        securityLabel = "Unknown",
        safetyLevel = WifiSafetyLevel.Unknown,
        summary = "SecureGuard is preparing your network check.",
        detail = "This overview will show whether your current Wi-Fi looks open or protected.",
        gatewayAddress = null,
        localAddress = null,
        permissionLimited = false
    )
)
