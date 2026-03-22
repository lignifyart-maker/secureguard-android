package com.secureguard.app.feature.permissionaudit

import com.secureguard.app.domain.model.AppScanResult
import com.secureguard.app.domain.model.ConnectionFeedPreview
import com.secureguard.app.domain.model.SecurityOverview
import com.secureguard.app.domain.model.SecuritySuggestion
import com.secureguard.app.domain.model.VpnProtectionState
import com.secureguard.app.domain.model.WifiSafetyLevel
import com.secureguard.app.domain.model.WifiSecuritySnapshot

data class PermissionAuditUiState(
    val isLoading: Boolean = true,
    val apps: List<AppScanResult> = emptyList(),
    val errorMessage: String? = null,
    val lastScanLabel: String = "Never",
    val trustedWifiNetworks: List<String> = emptyList(),
    val showVpnDisclosure: Boolean = false,
    val vpnProtectionState: VpnProtectionState = VpnProtectionState.Off,
    val vpnStatusMessage: String = "Protection mode is off. Turn it on when you want local network monitoring.",
    val vpnCapabilityNote: String = "Current protection mode can observe DNS tunnel events and service state. Per-app attribution and full flow handling are still being built.",
    val connectionFeedPreview: ConnectionFeedPreview = ConnectionFeedPreview(
        title = "No live connections yet",
        sourceLabel = "SecureGuard feed",
        targetLabel = "No target yet",
        eventLabel = "Waiting",
        detail = "Turn on protection mode to start building a local connection feed for app traffic.",
        actionHint = "When you want a calm traffic overview, turn protection mode on first.",
        activityLabel = "Idle",
        riskLabel = "Idle",
        relativeTime = "waiting",
        recentCount = 0
    ),
    val wifiSnapshot: WifiSecuritySnapshot = WifiSecuritySnapshot(
        isWifiActive = false,
        networkName = "Checking network...",
        canManageTrust = false,
        isTrustedNetwork = false,
        securityLabel = "Unknown",
        familiarityLabel = "Checking network familiarity...",
        safetyLevel = WifiSafetyLevel.Unknown,
        crowdLabel = "Checking whether this Wi-Fi feels shared...",
        summary = "SecureGuard is preparing your network check.",
        detail = "This overview will show whether your current Wi-Fi looks open or protected.",
        gatewayAddress = null,
        localAddress = null,
        dnsSummary = "Checking DNS...",
        dnsAdvice = "A short DNS explanation will appear here after the network check.",
        permissionLimited = false,
        nearbyDeviceCount = 0,
        nearbyDeviceConfidenceLabel = "Preparing estimate...",
        nearbyDeviceSummary = "Nearby device visibility is still loading.",
        sensitiveActionAdvice = "A simple recommendation about sensitive actions will appear here after the scan."
    ),
    val securityOverview: SecurityOverview = SecurityOverview(
        score = 72,
        headline = "Checking your phone's safety rhythm",
        summary = "SecureGuard is preparing a simple overview of what matters most.",
        primaryActionTitle = "Preparing the best next step",
        primaryActionDetail = "The app is figuring out the calmest, highest-impact thing to do first.",
        suggestions = listOf(
            SecuritySuggestion(
                title = "Preparing suggestions",
                detail = "A few calm, high-impact tips will show up here after the scan finishes.",
                categoryLabel = "Overview",
                priorityLabel = "Preparing"
            )
        ),
        watchApps = emptyList(),
        closeCandidates = emptyList()
    )
)
