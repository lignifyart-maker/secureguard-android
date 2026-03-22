package com.secureguard.app.vpn.parser

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DomainRiskClassifier @Inject constructor() {
    fun classify(host: String): String {
        val value = host.lowercase()
        return when {
            trackerKeywords.any { it in value } -> "Tracker"
            sensitiveKeywords.any { it in value } -> "Sensitive"
            trustedKeywords.any { it in value } -> "Routine"
            else -> "Observed"
        }
    }

    private companion object {
        val trackerKeywords = listOf(
            "ads",
            "doubleclick",
            "tracking",
            "analytics",
            "beacon",
            "adjust",
            "appsflyer"
        )

        val sensitiveKeywords = listOf(
            "bank",
            "wallet",
            "pay",
            "login",
            "auth"
        )

        val trustedKeywords = listOf(
            "google",
            "apple",
            "microsoft",
            "line",
            "cloudflare"
        )
    }
}
