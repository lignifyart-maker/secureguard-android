package com.secureguard.app.domain.usecase

import com.secureguard.app.data.source.local.WifiSecurityInspector
import com.secureguard.app.domain.model.WifiSecuritySnapshot
import javax.inject.Inject

class GetWifiSecuritySnapshotUseCase @Inject constructor(
    private val wifiSecurityInspector: WifiSecurityInspector
) {
    suspend operator fun invoke(): WifiSecuritySnapshot = wifiSecurityInspector.inspect()
}
