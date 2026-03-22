package com.secureguard.app.domain.model

enum class VpnProtectionState(
    val label: String
) {
    Off("Protection off"),
    Starting("Starting protection"),
    On("Protection on"),
    Error("Needs attention")
}
