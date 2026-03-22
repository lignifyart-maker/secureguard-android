package com.secureguard.app.domain.model

enum class WifiSafetyLevel(
    val label: String
) {
    Safe("看起來安心"),
    Caution("要多看一下"),
    Risky("先小心一點"),
    Unknown("還看不清楚")
}
