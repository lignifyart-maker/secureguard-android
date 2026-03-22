package com.secureguard.app.domain.usecase

import com.secureguard.app.domain.model.AppScanResult
import com.secureguard.app.domain.model.RiskLevel
import com.secureguard.app.domain.model.SecurityOverview
import com.secureguard.app.domain.model.SecuritySuggestion
import com.secureguard.app.domain.model.WifiSafetyLevel
import com.secureguard.app.domain.model.WifiSecuritySnapshot
import javax.inject.Inject

class BuildSecurityOverviewUseCase @Inject constructor() {
    operator fun invoke(
        apps: List<AppScanResult>,
        wifiSnapshot: WifiSecuritySnapshot
    ): SecurityOverview {
        val criticalCount = apps.count { it.riskLevel == RiskLevel.Critical }
        val highCount = apps.count { it.riskLevel == RiskLevel.High }
        val mediumCount = apps.count { it.riskLevel == RiskLevel.Medium }

        val wifiPenalty = when (wifiSnapshot.safetyLevel) {
            WifiSafetyLevel.Risky -> 20
            WifiSafetyLevel.Caution -> 10
            WifiSafetyLevel.Unknown -> 6
            WifiSafetyLevel.Safe -> 0
        }

        val score = (100 - (criticalCount * 18) - (highCount * 10) - (mediumCount * 4) - wifiPenalty)
            .coerceIn(28, 100)

        val headline = when {
            score >= 86 -> "今天看起來很穩"
            score >= 70 -> "大致不錯，還有一點點要整理"
            score >= 55 -> "有幾個地方要注意"
            else -> "現在很適合做一次整理"
        }
        val scoreBandLabel = when {
            score >= 86 -> "很穩"
            score >= 70 -> "還不錯"
            score >= 55 -> "要留意"
            else -> "快整理"
        }
        val scoreDetail = when {
            wifiSnapshot.safetyLevel == WifiSafetyLevel.Risky -> "現在這個 Wi‑Fi 比 app 還更值得你注意。"
            criticalCount > 0 -> "有幾個 app 要的權限真的偏多。"
            highCount > 0 || mediumCount > 0 -> "有些 app 要的東西比想像中多。"
            else -> "目前沒有很明顯的大問題。"
        }

        val summary = when {
            criticalCount > 0 -> "有幾個 app 看起來特別需要你回頭看一下。"
            wifiSnapshot.safetyLevel == WifiSafetyLevel.Risky -> "你現在連的 Wi‑Fi 不太適合做重要操作。"
            highCount > 0 -> "有一些 app 值得你再檢查一次。"
            else -> "整體還行，但還是有一些小地方可以更乾淨。"
        }

        val primaryAction = when {
            wifiSnapshot.safetyLevel == WifiSafetyLevel.Risky ->
                "先不要做重要操作" to "像登入、付款、改密碼這種事，等換個更安心的網路再做。"
            criticalCount > 0 -> {
                val topCritical = apps.firstOrNull { it.riskLevel == RiskLevel.Critical }
                "先看 ${topCritical?.appName ?: "最可疑的 app"}" to
                    (topCritical?.riskReasons?.firstOrNull() ?: "它要求的權限看起來偏多。")
            }
            highCount > 0 ->
                "先檢查幾個高權限 app" to "把比較少用、但權限很多的 app 先看一下。"
            else ->
                "先維持現在這樣" to "目前沒看到急事，只要偶爾回來看看就行。"
        }

        val suggestions = buildList {
            if (wifiSnapshot.safetyLevel == WifiSafetyLevel.Risky) {
                add(
                    SecuritySuggestion(
                        title = "這個 Wi‑Fi 先別做重要事",
                        detail = "看影片和滑網頁可以，但先別在這裡登入或付款。",
                        categoryLabel = "網路",
                        priorityLabel = "現在"
                    )
                )
            }

            val suspiciousUtility = apps.firstOrNull { it.riskLevel == RiskLevel.Critical }
            if (suspiciousUtility != null) {
                add(
                    SecuritySuggestion(
                        title = "先看 ${suspiciousUtility.appName}",
                        detail = suspiciousUtility.riskReasons.firstOrNull() ?: "這個 app 看起來有點可疑。",
                        categoryLabel = "app",
                        priorityLabel = "現在"
                    )
                )
            }

            val microphoneCount = apps.count { "android.permission.RECORD_AUDIO" in it.riskyPermissions }
            if (microphoneCount > 0) {
                add(
                    SecuritySuggestion(
                        title = "收一下麥克風權限",
                        detail = "$microphoneCount 個 app 有麥克風權限，先留常用的就好。",
                        categoryLabel = "權限",
                        priorityLabel = "稍後"
                    )
                )
            }

            if (apps.none { it.riskLevel == RiskLevel.Critical || it.riskLevel == RiskLevel.High }) {
                add(
                    SecuritySuggestion(
                        title = "目前不用太緊張",
                        detail = "這次沒有看到特別高風險的 app。",
                        categoryLabel = "總覽",
                        priorityLabel = "知道就好"
                    )
                )
            }
        }.take(3)

        val watchApps = apps
            .sortedWith(compareByDescending<AppScanResult> { it.riskLevel.score }.thenBy { it.appName })
            .take(3)

        val closeCandidates = apps
            .filter { app -> looksSafeToCloseFirst(app) }
            .sortedWith(compareByDescending<AppScanResult> { it.riskLevel.score }.thenBy { it.appName })
            .take(3)

        return SecurityOverview(
            score = score,
            scoreBandLabel = scoreBandLabel,
            scoreDetail = scoreDetail,
            headline = headline,
            summary = summary,
            primaryActionTitle = primaryAction.first,
            primaryActionDetail = primaryAction.second,
            suggestions = suggestions,
            watchApps = watchApps,
            closeCandidates = closeCandidates
        )
    }

    private fun looksSafeToCloseFirst(app: AppScanResult): Boolean {
        val haystack = "${app.appName} ${app.packageName}".lowercase()
        val probablyOptional = optionalKeywords.any { keyword -> keyword in haystack }
        val definitelyCore = coreKeywords.any { keyword -> keyword in haystack }
        if (definitelyCore) return false
        if (!probablyOptional) return false
        return app.riskLevel == RiskLevel.Critical ||
            app.riskLevel == RiskLevel.High ||
            app.riskyPermissions.isNotEmpty()
    }

    private companion object {
        val optionalKeywords = listOf(
            "flashlight",
            "torch",
            "cleaner",
            "booster",
            "scanner",
            "weather",
            "wallpaper",
            "battery",
            "compass",
            "calculator",
            "junk",
            "optimizer"
        )

        val coreKeywords = listOf(
            "phone",
            "dialer",
            "message",
            "sms",
            "camera",
            "launcher",
            "system",
            "bank",
            "wallet",
            "maps",
            "mail"
        )
    }
}
