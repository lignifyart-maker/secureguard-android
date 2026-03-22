package com.secureguard.app.domain.usecase

import com.secureguard.app.domain.model.ConnectionFeedPreview
import com.secureguard.app.domain.model.VpnProtectionState
import com.secureguard.app.domain.repository.NetworkEventRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveConnectionFeedPreviewUseCase @Inject constructor(
    private val networkEventRepository: NetworkEventRepository
) {
    operator fun invoke(vpnState: VpnProtectionState): Flow<ConnectionFeedPreview> {
        return networkEventRepository.observeRecentEvents().map { events ->
            val mostRecent = events.firstOrNull()
            when {
                mostRecent != null -> {
                    val target = mostRecent.host ?: mostRecent.ipAddress ?: "unknown target"
                    val appName = mostRecent.appName ?: "Unknown app"
                    ConnectionFeedPreview(
                        title = "$appName -> $target",
                        detail = "${mostRecent.protocol} ${mostRecent.eventType.lowercase()} / ${mostRecent.riskLabel}"
                    )
                }
                vpnState == VpnProtectionState.On ->
                    ConnectionFeedPreview(
                        title = "Listening for new traffic",
                        detail = "Protection mode is active. DNS and connection events can appear here once the tunnel parser is connected."
                    )
                vpnState == VpnProtectionState.Starting ->
                    ConnectionFeedPreview(
                        title = "Protection is starting",
                        detail = "SecureGuard is preparing the local tunnel before any connection events can show up."
                    )
                else ->
                    ConnectionFeedPreview(
                        title = "No live connections yet",
                        detail = "Turn on protection mode to start building a local connection feed for app traffic."
                    )
            }
        }
    }
}
