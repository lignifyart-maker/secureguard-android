package com.secureguard.app.domain.model

data class WifiSecuritySnapshot(
    val isWifiActive: Boolean,
    val networkName: String,
    val securityLabel: String,
    val safetyLevel: WifiSafetyLevel,
    val summary: String,
    val detail: String,
    val gatewayAddress: String?,
    val localAddress: String?,
    val permissionLimited: Boolean
)
