package com.secureguard.app.domain.model

enum class RiskLevel(
    val score: Int,
    val label: String
) {
    Critical(4, "優先處理"),
    High(3, "多看一下"),
    Medium(2, "可以留意"),
    Safe(1, "目前正常")
}
