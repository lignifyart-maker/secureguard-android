package com.secureguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_scan_results")
data class AppScanEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val requestedPermissions: List<String>,
    val riskyPermissions: List<String>,
    val riskLevel: String,
    val riskReasons: List<String>,
    val scannedAt: Long
)
