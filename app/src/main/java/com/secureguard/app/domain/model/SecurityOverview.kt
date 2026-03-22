package com.secureguard.app.domain.model

data class SecurityOverview(
    val score: Int,
    val headline: String,
    val summary: String,
    val primaryActionTitle: String,
    val primaryActionDetail: String,
    val suggestions: List<SecuritySuggestion>,
    val watchApps: List<AppScanResult>,
    val closeCandidates: List<AppScanResult>
)
