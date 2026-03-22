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
                        title = event.host ?: event.ipAddress ?: "未知目標",
                        sourceLabel = event.appName ?: "未知 app",
                        riskLabel = riskLabelForUi(event.riskLabel),
                        eventLabel = eventLabelFor(event.eventType),
                        attributionStateLabel = attributionStateLabel(event.attributionLabel),
                        attributionLabel = attributionLabelFor(event.attributionLabel),
                        relativeTime = relativeTimeFrom(event.createdAt)
                    )
                }
            )
        }
    }

    private fun summaryFor(count: Int): String = when {
        count == 0 -> "現在還沒有新動態。"
        count == 1 -> "現在有 1 筆新動態。"
        count <= 4 -> "現在有幾筆新動態。"
        else -> "最近動得有點多。"
    }

    private fun eventLabelFor(eventType: String): String = when (eventType) {
        "VPN_STARTED" -> "保護開始"
        "VPN_STOPPED" -> "保護停止"
        "VPN_ERROR" -> "保護異常"
        "DNS_A_QUERY", "DNS_AAAA_QUERY", "DNS_CNAME_QUERY", "DNS_MX_QUERY" -> "網站查詢"
        "TCP_HTTPS_CONNECT", "TCP_HTTP_CONNECT", "TCP_PUSH_CONNECT", "TCP_APP_CONNECT" -> "網路連線"
        "UDP_QUIC_TRAFFIC", "UDP_NTP_TRAFFIC", "UDP_STUN_TRAFFIC", "UDP_APP_TRAFFIC" -> "資料傳送"
        else -> "新動態"
    }

    private fun riskLabelForUi(label: String): String = when (label) {
        "Tracker" -> "多看一眼"
        "Sensitive" -> "先留意"
        "Routine" -> "看起來正常"
        else -> "一般"
    }

    private fun attributionLabelFor(label: String?): String = when (label) {
        "Mapped from Android owner lookup" -> "這筆大致知道是哪個 app。"
        "Matched from recent port history" -> "這筆是用前面很近的紀錄補回來的。"
        "Owner not mapped yet" -> "這筆還沒完全認出是哪個 app。"
        "UID resolved without package" -> "只知道一部分來源。"
        "Address parse failed" -> "這筆資料不夠完整。"
        else -> "先看大方向就好。"
    }

    private fun attributionStateLabel(label: String?): String = when (label) {
        "Mapped from Android owner lookup" -> "已認出"
        "Matched from recent port history" -> "補回"
        "Owner not mapped yet" -> "未認出"
        "UID resolved without package" -> "部分"
        "Address parse failed" -> "不完整"
        else -> "一般"
    }

    private fun relativeTimeFrom(createdAt: Long): String {
        val seconds = ((System.currentTimeMillis() - createdAt) / 1000).coerceAtLeast(0)
        return when {
            seconds < 5 -> "剛剛"
            seconds < 60 -> "${seconds} 秒前"
            seconds < 3600 -> "${seconds / 60} 分鐘前"
            else -> "${seconds / 3600} 小時前"
        }
    }
}
