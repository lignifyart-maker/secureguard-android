package com.secureguard.app.domain.model

enum class VpnProtectionState(
    val label: String
) {
    Off("保護已關閉"),
    Starting("正在啟動保護"),
    On("保護已開啟"),
    Error("需要注意")
}
