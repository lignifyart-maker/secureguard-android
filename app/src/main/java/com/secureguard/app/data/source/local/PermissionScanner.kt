package com.secureguard.app.data.source.local

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.secureguard.app.domain.model.AppScanResult
import com.secureguard.app.domain.model.RiskLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PermissionScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun scanInstalledApps(): List<AppScanResult> = withContext(Dispatchers.IO) {
        val packages = context.packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)

        packages
            .asSequence()
            .filterNot { it.isSystemApp() }
            .map { packageInfo ->
                val requestedPermissions = packageInfo.requestedPermissions.orEmpty().toList()
                val riskyPermissions = requestedPermissions.filter { permission ->
                    permission in highRiskPermissions
                }
                AppScanResult(
                    packageName = packageInfo.packageName,
                    appName = packageInfo.safeLabel(context.packageManager),
                    versionName = packageInfo.versionName.orEmpty(),
                    versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                    requestedPermissions = requestedPermissions,
                    riskyPermissions = riskyPermissions,
                    riskLevel = evaluateRisk(packageInfo, riskyPermissions, requestedPermissions),
                    riskReasons = buildRiskReasons(riskyPermissions, requestedPermissions)
                )
            }
            .sortedWith(
                compareByDescending<AppScanResult> { it.riskLevel.score }
                    .thenBy { it.appName.lowercase() }
            )
            .toList()
    }

    private fun evaluateRisk(
        packageInfo: PackageInfo,
        riskyPermissions: List<String>,
        requestedPermissions: List<String>
    ): RiskLevel {
        if (riskyPermissions.isEmpty()) return RiskLevel.Safe

        val appLabel = packageInfo.safeLabel(context.packageManager)
        val utilityStyle = utilityKeywords.any { keyword ->
            "$appLabel ${packageInfo.packageName}".lowercase().contains(keyword)
        }

        return when {
            utilityStyle && riskyPermissions.any { it in utilityMismatchPermissions } -> RiskLevel.Critical
            riskyPermissions.size >= 3 -> RiskLevel.High
            Manifest.permission.SYSTEM_ALERT_WINDOW in requestedPermissions -> RiskLevel.High
            else -> RiskLevel.Medium
        }
    }

    private fun buildRiskReasons(
        riskyPermissions: List<String>,
        requestedPermissions: List<String>
    ): List<String> {
        val reasons = mutableListOf<String>()
        if (Manifest.permission.RECORD_AUDIO in riskyPermissions) reasons += "requests microphone access"
        if (Manifest.permission.READ_CONTACTS in riskyPermissions) reasons += "requests contact access"
        if (Manifest.permission.ACCESS_FINE_LOCATION in riskyPermissions) reasons += "requests precise location"
        if (Manifest.permission.READ_SMS in riskyPermissions) reasons += "requests SMS access"
        if (Manifest.permission.READ_CALL_LOG in riskyPermissions) reasons += "requests call log access"
        if (Manifest.permission.SYSTEM_ALERT_WINDOW in requestedPermissions) reasons += "can draw over other apps"
        return reasons.ifEmpty { listOf("no notable risk indicators") }
    }

    private fun PackageInfo.isSystemApp(): Boolean {
        val flags = applicationInfo?.flags ?: 0
        return flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
            flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
    }

    private fun PackageInfo.safeLabel(packageManager: PackageManager): String {
        return applicationInfo
            ?.loadLabel(packageManager)
            ?.toString()
            .orEmpty()
            .ifBlank { packageName }
    }

    private companion object {
        val highRiskPermissions = setOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.READ_CALENDAR
        )

        val utilityMismatchPermissions = setOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val utilityKeywords = listOf(
            "flashlight",
            "torch",
            "calculator",
            "cleaner",
            "booster",
            "scanner",
            "weather",
            "compass"
        )
    }
}
