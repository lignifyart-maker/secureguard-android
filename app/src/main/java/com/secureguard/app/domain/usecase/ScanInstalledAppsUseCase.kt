package com.secureguard.app.domain.usecase

import com.secureguard.app.domain.model.AppScanSnapshot
import com.secureguard.app.domain.repository.AppScannerRepository
import javax.inject.Inject

class ScanInstalledAppsUseCase @Inject constructor(
    private val repository: AppScannerRepository
) {
    suspend operator fun invoke(): AppScanSnapshot = repository.scanInstalledApps()
}
