package com.secureguard.app.feature.permissionaudit

import com.secureguard.app.domain.model.AppScanResult
import com.secureguard.app.domain.model.SecurityOverview
import com.secureguard.app.domain.model.SecuritySuggestion
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
        permissionLimited = false,
        nearbyDeviceCount = 0,
        nearbyDeviceSummary = "Nearby device visibility is still loading."
    ),
    val securityOverview: SecurityOverview = SecurityOverview(
        score = 72,
        headline = "Checking your phone's safety rhythm",
        summary = "SecureGuard is preparing a simple overview of what matters most.",
        suggestions = listOf(
            SecuritySuggestion(
                title = "Preparing suggestions",
                detail = "A few calm, high-impact tips will show up here after the scan finishes."
            )
        ),
        watchApps = emptyList(),
        closeCandidates = emptyList()
    )
)
