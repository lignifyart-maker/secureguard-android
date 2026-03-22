package com.secureguard.app.domain.model

data class RecentConnectionTimeline(
    val summary: String,
    val items: List<RecentConnectionItem>
)
