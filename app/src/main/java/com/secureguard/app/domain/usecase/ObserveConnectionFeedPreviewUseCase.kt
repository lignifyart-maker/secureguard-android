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
                    val title = when (mostRecent.eventType) {
                        "VPN_STARTED" -> "Protection mode started"
                        "VPN_STOPPED" -> "Protection mode stopped"
                        "VPN_ERROR" -> "Protection mode needs attention"
                        else -> {
                            when (mostRecent.riskLabel) {
                                "Tracker" -> "A tracking-style domain was contacted"
                                "Sensitive" -> "A more sensitive service was contacted"
                                else -> "$appName checked $target"
                            }
                        }
                    }
                    val detail = when (mostRecent.eventType) {
                        "VPN_STARTED" -> "SecureGuard created a local tunnel and is ready to observe DNS traffic."
                        "VPN_STOPPED" -> "Local protection mode was turned off."
                        "VPN_ERROR" -> "SecureGuard could not keep the local tunnel alive."
                        else -> when (mostRecent.riskLabel) {
                            "Tracker" -> "$appName asked about $target, which looks like tracking or ad traffic."
                            "Sensitive" -> "$appName checked $target, which touches a more sensitive service."
                            else -> "${describeQueryEvent(mostRecent.eventType)} / ${humanizeRisk(mostRecent.riskLabel)}"
                        }
                    }
                    val actionHint = when (mostRecent.riskLabel) {
                        "Tracker" -> "If this app feels optional, it is a good one to review or close first."
                        "Sensitive" -> "If you were about to sign in or bank, pause and make sure the app is the one you expected."
                        "Routine" -> "This looks routine, so you usually do not need to act on it."
                        else -> "Keep an eye on this feed if you want a better sense of what your phone is doing."
                    }
                    val activityLabel = activityLabelFor(events.size)
                    val recentSummary = recentSummaryFor(events.size)
                    ConnectionFeedPreview(
                        title = title,
                        sourceLabel = sourceLabelFor(
                            appName = appName,
                            attributionLabel = mostRecent.attributionLabel
                        ),
                        targetLabel = target,
                        eventLabel = eventLabelFor(mostRecent.eventType),
                        detail = detail,
                        actionHint = actionHint,
                        activityLabel = activityLabel,
                        recentSummary = recentSummary,
                        riskLabel = mostRecent.riskLabel,
                        relativeTime = relativeTimeFrom(mostRecent.createdAt),
                        recentCount = events.size
                    )
                }
                vpnState == VpnProtectionState.On ->
                    ConnectionFeedPreview(
                        title = "Listening for new traffic",
                        sourceLabel = "SecureGuard feed",
                        targetLabel = "Waiting for the first target",
                        eventLabel = "DNS watch",
                        detail = "Protection mode is active. DNS and connection events can appear here once the tunnel parser is connected.",
                        actionHint = "Leave protection mode on for a moment and this card will start to fill in.",
                        activityLabel = "Quiet",
                        recentSummary = "No recent connection events yet.",
                        riskLabel = "Ready",
                        relativeTime = "now",
                        recentCount = 0
                    )
                vpnState == VpnProtectionState.Starting ->
                    ConnectionFeedPreview(
                        title = "Protection is starting",
                        sourceLabel = "SecureGuard feed",
                        targetLabel = "Tunnel is still warming up",
                        eventLabel = "VPN startup",
                        detail = "SecureGuard is preparing the local tunnel before any connection events can show up.",
                        actionHint = "Give it a few seconds before expecting any live event hints.",
                        activityLabel = "Warming up",
                        recentSummary = "The feed is still getting ready.",
                        riskLabel = "Starting",
                        relativeTime = "now",
                        recentCount = 0
                    )
                else ->
                    ConnectionFeedPreview(
                        title = "No live connections yet",
                        sourceLabel = "SecureGuard feed",
                        targetLabel = "No target yet",
                        eventLabel = "Waiting",
                        detail = "Turn on protection mode to start building a local connection feed for app traffic.",
                        actionHint = "When you want a calm traffic overview, turn protection mode on first.",
                        activityLabel = "Idle",
                        recentSummary = "There is no recent live traffic to summarize yet.",
                        riskLabel = "Idle",
                        relativeTime = "waiting",
                        recentCount = 0
                    )
            }
        }
    }

    private fun humanizeRisk(riskLabel: String): String = when (riskLabel) {
        "Tracker" -> "looks like tracking or ad traffic"
        "Sensitive" -> "touches a more sensitive domain"
        "Routine" -> "looks routine"
        else -> "was observed"
    }

    private fun describeQueryEvent(eventType: String): String = when (eventType) {
        "DNS_A_QUERY" -> "asked for an IPv4 address"
        "DNS_AAAA_QUERY" -> "asked for an IPv6 address"
        "DNS_CNAME_QUERY" -> "followed a domain alias"
        "DNS_MX_QUERY" -> "looked up mail routing"
        else -> "generated a DNS lookup"
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

    private fun sourceLabelFor(appName: String, attributionLabel: String?): String = when {
        appName == "SecureGuard" -> "Source: SecureGuard"
        appName == "Unknown app" && !attributionLabel.isNullOrBlank() ->
            "Source: app not mapped yet / ${humanizeAttribution(attributionLabel)}"
        appName == "Unknown app" -> "Source: app not mapped yet"
        !attributionLabel.isNullOrBlank() -> "Source: $appName / ${humanizeAttribution(attributionLabel)}"
        else -> "Source: $appName"
    }

    private fun humanizeAttribution(label: String): String = when (label) {
        "Mapped from Android owner lookup" -> "Android matched this app"
        "Matched from recent port history" -> "SecureGuard reused a very recent app match"
        "Owner not mapped yet" -> "Android has not mapped it yet"
        "UID resolved without package" -> "Android resolved a UID but no package name"
        "Address parse failed" -> "Address details were incomplete"
        else -> label
    }

    private fun activityLabelFor(recentCount: Int): String = when {
        recentCount <= 1 -> "Quiet"
        recentCount <= 4 -> "Light activity"
        recentCount <= 8 -> "Busy"
        else -> "Very busy"
    }

    private fun recentSummaryFor(recentCount: Int): String = when {
        recentCount <= 1 -> "Only one very recent event is in view."
        recentCount <= 4 -> "A small handful of recent events are in view."
        recentCount <= 8 -> "This feed has picked up a noticeable burst of recent events."
        else -> "This feed is fairly busy right now."
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
