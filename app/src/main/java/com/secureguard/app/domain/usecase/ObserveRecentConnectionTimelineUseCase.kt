package com.secureguard.app.domain.usecase

import com.secureguard.app.domain.model.RecentConnectionItem
import com.secureguard.app.domain.model.RecentConnectionTimeline
import com.secureguard.app.domain.repository.NetworkEventRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveRecentConnectionTimelineUseCase @Inject constructor(
    private val networkEventRepository: NetworkEventRepository
) {
    operator fun invoke(limit: Int = 20): Flow<RecentConnectionTimeline> {
        return networkEventRepository.observeRecentEvents(limit).map { events ->
            RecentConnectionTimeline(
                summary = summaryFor(events.size),
                hasMoreThanPreview = events.size > 3,
                items = events.map { event ->
                    RecentConnectionItem(
                        title = event.host ?: event.ipAddress ?: "Unknown target",
                        sourceLabel = event.appName ?: "Unknown app",
                        riskLabel = event.riskLabel,
                        eventLabel = eventLabelFor(event.eventType),
                        attributionStateLabel = attributionStateLabel(event.attributionLabel),
                        attributionLabel = humanizeAttribution(
                            event.attributionLabel ?: "Attribution not available"
                        ),
                        relativeTime = relativeTimeFrom(event.createdAt)
                    )
                }
            )
        }
    }

    private fun summaryFor(count: Int): String = when {
        count == 0 -> "No recent events yet."
        count == 1 -> "One recent event is ready to review."
        count <= 4 -> "A small set of recent events is ready to review."
        else -> "This recent activity list is starting to get busy."
    }

    private fun eventLabelFor(eventType: String): String = when (eventType) {
        "VPN_STARTED" -> "VPN started"
        "VPN_STOPPED" -> "VPN stopped"
        "VPN_ERROR" -> "VPN issue"
        "DNS_A_QUERY" -> "IPv4 lookup"
        "DNS_AAAA_QUERY" -> "IPv6 lookup"
        "DNS_CNAME_QUERY" -> "Alias lookup"
        "DNS_MX_QUERY" -> "Mail lookup"
        else -> "DNS lookup"
    }

    private fun humanizeAttribution(label: String): String = when (label) {
        "Mapped from Android owner lookup" -> "Android matched this app"
        "Matched from recent port history" -> "SecureGuard reused a very recent app match"
        "Owner not mapped yet" -> "Android has not mapped it yet"
        "UID resolved without package" -> "UID matched without a package name"
        "Address parse failed" -> "Address details were incomplete"
        else -> label
    }

    private fun attributionStateLabel(label: String?): String = when (label) {
        "Mapped from Android owner lookup" -> "Mapped"
        "Matched from recent port history" -> "Recovered"
        "Owner not mapped yet" -> "Pending"
        "UID resolved without package" -> "Partial"
        "Address parse failed" -> "Fallback"
        else -> "Unknown"
    }

    private fun relativeTimeFrom(createdAt: Long): String {
        val seconds = ((System.currentTimeMillis() - createdAt) / 1000).coerceAtLeast(0)
        return when {
            seconds < 5 -> "just now"
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            else -> "${seconds / 3600}h ago"
        }
    }
}
