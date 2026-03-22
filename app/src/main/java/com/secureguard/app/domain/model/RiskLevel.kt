package com.secureguard.app.domain.model

enum class RiskLevel(
    val score: Int,
    val label: String
) {
    Critical(4, "Critical"),
    High(3, "High"),
    Medium(2, "Medium"),
    Safe(1, "Safe")
}
