package com.secureguard.app.domain.repository

import com.secureguard.app.domain.model.AppScanSnapshot

interface AppScannerRepository {
    suspend fun scanInstalledApps(): AppScanSnapshot
}
