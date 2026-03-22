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
            score >= 86 -> "Looking calm today"
            score >= 70 -> "Mostly okay, with a few things to tidy"
            score >= 55 -> "A few risks deserve attention"
            else -> "A stronger safety cleanup would help"
        }

        val summary = when {
            criticalCount > 0 ->
                "$criticalCount app${plural(criticalCount)} stand out as unusual for the permissions they requested."
            wifiSnapshot.safetyLevel == WifiSafetyLevel.Risky ->
                "Your current Wi-Fi looks open, so this is not the best place for sensitive activity."
            highCount > 0 ->
                "$highCount app${plural(highCount)} deserve a closer look before you forget about them."
            else ->
                "Nothing alarming jumped out immediately, but SecureGuard still found some things worth keeping tidy."
        }

        val primaryAction = when {
            wifiSnapshot.safetyLevel == WifiSafetyLevel.Risky ->
                "Pause sensitive activity" to "This network looks open, so save banking, password changes, and important logins for later."
            criticalCount > 0 -> {
                val topCritical = apps.firstOrNull { it.riskLevel == RiskLevel.Critical }
                "Review ${topCritical?.appName ?: "the riskiest app"}" to
                    (topCritical?.riskReasons?.joinToString(separator = " / ")
                        ?: "One app stands out as unusually demanding for its category.")
            }
            highCount > 0 ->
                "Check a few permission-heavy apps" to "Some apps asked for more access than usual. A quick cleanup would reduce noise and risk."
            else ->
                "Keep the current setup tidy" to "Nothing looks urgent right now. A light review of apps you rarely use is enough."
        }

        val suggestions = buildList {
            if (wifiSnapshot.safetyLevel == WifiSafetyLevel.Risky) {
                add(
                    SecuritySuggestion(
                        title = "Avoid sensitive logins on this Wi-Fi",
                        detail = "Open networks are fine for casual browsing, but avoid banking or password changes here.",
                        priorityLabel = "Do now"
                    )
                )
            }

            val suspiciousUtility = apps.firstOrNull {
                it.riskLevel == RiskLevel.Critical
            }
            if (suspiciousUtility != null) {
                add(
                    SecuritySuggestion(
                        title = "Review ${suspiciousUtility.appName}",
                        detail = suspiciousUtility.riskReasons.joinToString(separator = " / "),
                        priorityLabel = "Do now"
                    )
                )
            }

            val microphoneCount = apps.count { "android.permission.RECORD_AUDIO" in it.riskyPermissions }
            if (microphoneCount > 0) {
                add(
                    SecuritySuggestion(
                        title = "Trim microphone access",
                        detail = "$microphoneCount app${plural(microphoneCount)} asked for microphone access. Keep only the ones you truly use.",
                        priorityLabel = "Soon"
                    )
                )
            }

            if (apps.none { it.riskLevel == RiskLevel.Critical || it.riskLevel == RiskLevel.High }) {
                add(
                    SecuritySuggestion(
                        title = "You can relax a bit",
                        detail = "There are no standout high-risk apps in this scan, so your next check can stay lightweight.",
                        priorityLabel = "Good to know"
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
            headline = headline,
            summary = summary,
            primaryActionTitle = primaryAction.first,
            primaryActionDetail = primaryAction.second,
            suggestions = suggestions,
            watchApps = watchApps,
            closeCandidates = closeCandidates
        )
    }

    private fun plural(count: Int): String = if (count == 1) "" else "s"

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
