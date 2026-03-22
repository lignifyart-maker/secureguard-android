package com.secureguard.app.domain.model

data class AppScanResult(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val requestedPermissions: List<String>,
    val riskyPermissions: List<String>,
    val riskLevel: RiskLevel,
    val riskReasons: List<String>
)
