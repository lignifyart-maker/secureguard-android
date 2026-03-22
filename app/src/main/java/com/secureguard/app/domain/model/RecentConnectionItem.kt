package com.secureguard.app.domain.model

data class RecentConnectionItem(
    val title: String,
    val sourceLabel: String,
    val riskLabel: String,
    val eventLabel: String,
    val attributionLabel: String,
    val relativeTime: String
)
