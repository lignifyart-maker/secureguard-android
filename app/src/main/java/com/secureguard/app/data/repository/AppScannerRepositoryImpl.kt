package com.secureguard.app.data.repository

import com.secureguard.app.core.database.dao.AppScanDao
import com.secureguard.app.core.datastore.SettingsDataStore
import com.secureguard.app.data.mapper.toEntity
import com.secureguard.app.data.source.local.PermissionScanner
import com.secureguard.app.domain.model.AppScanResult
import com.secureguard.app.domain.repository.AppScannerRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppScannerRepositoryImpl @Inject constructor(
    private val permissionScanner: PermissionScanner,
    private val appScanDao: AppScanDao,
    private val settingsDataStore: SettingsDataStore
) : AppScannerRepository {
    override suspend fun scanInstalledApps(): List<AppScanResult> {
        val scannedAt = System.currentTimeMillis()
        val results = permissionScanner.scanInstalledApps()
        appScanDao.clearAll()
        appScanDao.upsertAll(results.map { it.toEntity(scannedAt) })
        settingsDataStore.setLastScanTimestamp(scannedAt)
        return results
    }
}
