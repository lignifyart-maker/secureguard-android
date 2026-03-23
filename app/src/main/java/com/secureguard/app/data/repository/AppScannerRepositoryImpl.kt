package com.secureguard.app.data.repository

import com.secureguard.app.core.database.dao.AppScanDao
import com.secureguard.app.core.datastore.SettingsDataStore
import com.secureguard.app.data.mapper.toEntity
import com.secureguard.app.data.source.local.PermissionScanner
import com.secureguard.app.domain.model.AppScanSnapshot
import com.secureguard.app.domain.repository.AppScannerRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppScannerRepositoryImpl @Inject constructor(
    private val permissionScanner: PermissionScanner,
    private val appScanDao: AppScanDao,
    private val settingsDataStore: SettingsDataStore
) : AppScannerRepository {
    override suspend fun scanInstalledApps(): AppScanSnapshot {
        val scannedAt = System.currentTimeMillis()
        val snapshot = permissionScanner.scanInstalledApps()
        appScanDao.clearAll()
        appScanDao.upsertAll(snapshot.apps.map { it.toEntity(scannedAt) })
        settingsDataStore.setLastScanTimestamp(scannedAt)
        return snapshot
    }
}
