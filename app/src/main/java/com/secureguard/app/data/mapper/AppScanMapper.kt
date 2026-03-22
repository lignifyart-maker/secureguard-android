package com.secureguard.app.data.mapper

import com.secureguard.app.core.database.entity.AppScanEntity
import com.secureguard.app.domain.model.AppScanResult

fun AppScanResult.toEntity(scannedAt: Long): AppScanEntity = AppScanEntity(
    packageName = packageName,
    appName = appName,
    versionName = versionName,
    versionCode = versionCode,
    requestedPermissions = requestedPermissions,
    riskyPermissions = riskyPermissions,
    riskLevel = riskLevel.name,
    riskReasons = riskReasons,
    scannedAt = scannedAt
)
