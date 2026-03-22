package com.secureguard.app.domain.model

enum class WifiSafetyLevel(
    val label: String
) {
    Safe("Looking safe"),
    Caution("Needs attention"),
    Risky("Risky network"),
    Unknown("Unknown")
}
