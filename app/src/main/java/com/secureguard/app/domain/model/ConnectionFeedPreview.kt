package com.secureguard.app.domain.model

data class ConnectionFeedPreview(
    val title: String,
    val targetLabel: String,
    val detail: String,
    val actionHint: String,
    val activityLabel: String,
    val riskLabel: String,
    val relativeTime: String,
    val recentCount: Int
)
