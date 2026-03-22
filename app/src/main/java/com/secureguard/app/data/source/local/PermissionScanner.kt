package com.secureguard.app.data.source.local

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.app.usage.UsageStatsManager
import android.os.Build
import android.os.Process
import androidx.core.content.pm.PackageInfoCompat
import com.secureguard.app.domain.model.AppScanResult
import com.secureguard.app.domain.model.RiskLevel
import java.io.File
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
        val usageMap = recentUsageMap()

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
                    apkSizeBytes = packageInfo.applicationInfo?.sourceDir
                        ?.let(::File)
                        ?.takeIf { it.exists() }
                        ?.length()
                        ?: 0L,
                    lastUsedAt = usageMap[packageInfo.packageName],
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

    private fun recentUsageMap(): Map<String, Long> {
        if (!canReadUsageStats()) return emptyMap()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - USAGE_LOOKBACK_MS
        return usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .orEmpty()
            .asSequence()
            .filter { it.lastTimeUsed > 0L }
            .associate { it.packageName to it.lastTimeUsed }
    }

    private fun canReadUsageStats(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
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
        if (Manifest.permission.RECORD_AUDIO in riskyPermissions) reasons += "它想用麥克風"
        if (Manifest.permission.READ_CONTACTS in riskyPermissions) reasons += "它想讀聯絡人"
        if (Manifest.permission.ACCESS_FINE_LOCATION in riskyPermissions) reasons += "它想知道你的位置"
        if (Manifest.permission.READ_SMS in riskyPermissions) reasons += "它想看簡訊"
        if (Manifest.permission.READ_CALL_LOG in riskyPermissions) reasons += "它想看通話紀錄"
        if (Manifest.permission.SYSTEM_ALERT_WINDOW in requestedPermissions) reasons += "它可以蓋在其他 app 上面"
        return reasons.ifEmpty { listOf("目前沒有明顯風險") }
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

        const val USAGE_LOOKBACK_MS = 1000L * 60 * 60 * 24 * 120
    }
}
