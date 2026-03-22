package com.secureguard.app.domain.repository

import com.secureguard.app.domain.model.AppScanResult

interface AppScannerRepository {
    suspend fun scanInstalledApps(): List<AppScanResult>
}
