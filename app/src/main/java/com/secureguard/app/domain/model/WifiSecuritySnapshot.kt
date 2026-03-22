package com.secureguard.app.domain.model

data class WifiSecuritySnapshot(
    val isWifiActive: Boolean,
    val networkName: String,
    val canManageTrust: Boolean,
    val isTrustedNetwork: Boolean,
    val securityLabel: String,
    val familiarityLabel: String,
    val safetyLevel: WifiSafetyLevel,
    val crowdLabel: String,
    val summary: String,
    val detail: String,
    val gatewayAddress: String?,
    val localAddress: String?,
    val permissionLimited: Boolean,
    val nearbyDeviceCount: Int,
    val nearbyDeviceConfidenceLabel: String,
    val nearbyDeviceSummary: String,
    val sensitiveActionAdvice: String
)
