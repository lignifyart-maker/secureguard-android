package com.secureguard.app.domain.usecase

import com.secureguard.app.domain.model.AppScanResult
import com.secureguard.app.domain.repository.AppScannerRepository
import javax.inject.Inject

class ScanInstalledAppsUseCase @Inject constructor(
    private val repository: AppScannerRepository
) {
    suspend operator fun invoke(): List<AppScanResult> = repository.scanInstalledApps()
}
