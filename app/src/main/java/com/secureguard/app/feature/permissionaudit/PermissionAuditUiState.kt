package com.secureguard.app.feature.permissionaudit

import com.secureguard.app.domain.model.AppScanResult

data class PermissionAuditUiState(
    val isLoading: Boolean = true,
    val apps: List<AppScanResult> = emptyList(),
    val errorMessage: String? = null,
    val lastScanLabel: String = "Never"
)
