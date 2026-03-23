package com.secureguard.app.domain.model

data class AppScanSnapshot(
    val apps: List<AppScanResult>,
    val hasUsageAccess: Boolean
)
