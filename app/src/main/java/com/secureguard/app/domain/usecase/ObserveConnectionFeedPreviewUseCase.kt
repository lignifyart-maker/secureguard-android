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
                    val target = mostRecent.host ?: mostRecent.ipAddress ?: "未知目標"
                    val appName = mostRecent.appName ?: "未知 app"
                    val title = when (mostRecent.eventType) {
                        "VPN_STARTED" -> "保護已開始"
                        "VPN_STOPPED" -> "保護已停止"
                        "VPN_ERROR" -> "保護暫時出問題"
                        "TCP_HTTPS_CONNECT" -> "$appName 正在連安全網站"
                        "TCP_HTTP_CONNECT" -> "$appName 正在連網站"
                        "TCP_PUSH_CONNECT" -> "$appName 正在接收背景消息"
                        "TCP_APP_CONNECT" -> "$appName 正在連線"
                        "UDP_QUIC_TRAFFIC" -> "$appName 正在傳資料"
                        "UDP_NTP_TRAFFIC" -> "$appName 正在對時間"
                        "UDP_STUN_TRAFFIC" -> "$appName 正在通話或找裝置"
                        "UDP_APP_TRAFFIC" -> "$appName 正在傳送資料"
                        else -> when (mostRecent.riskLabel) {
                            "Tracker" -> "有 app 連到像追蹤用的地方"
                            "Sensitive" -> "有 app 連到比較敏感的地方"
                            else -> "$appName 正在查 $target"
                        }
                    }
                    val detail = when (mostRecent.eventType) {
                        "VPN_STARTED" -> "現在開始幫你看手機裡的新連線。"
                        "VPN_STOPPED" -> "現在暫時不會再看到新動態。"
                        "VPN_ERROR" -> "保護沒有順利跑起來，等一下可以再試一次。"
                        "TCP_HTTPS_CONNECT" -> "$appName 連到了 $target。"
                        "TCP_HTTP_CONNECT" -> "$appName 連到了 $target。"
                        "TCP_PUSH_CONNECT" -> "$appName 正在和 $target 保持背景連線。"
                        "TCP_APP_CONNECT" -> "$appName 正在和 $target 傳資料。"
                        "UDP_QUIC_TRAFFIC" -> "$appName 正在和 $target 快速傳資料。"
                        "UDP_NTP_TRAFFIC" -> "$appName 正在和 $target 對時間。"
                        "UDP_STUN_TRAFFIC" -> "$appName 正在和 $target 做通話或裝置探索。"
                        "UDP_APP_TRAFFIC" -> "$appName 正在和 $target 傳資料。"
                        else -> when (mostRecent.riskLabel) {
                            "Tracker" -> "$appName 查了 $target，這看起來像追蹤或廣告流量。"
                            "Sensitive" -> "$appName 查了 $target，這可能和比較敏感的服務有關。"
                            else -> simpleDetailFor(mostRecent.eventType, target)
                        }
                    }
                    val actionHint = when (mostRecent.riskLabel) {
                        "Tracker" -> "如果這個 app 不常用，可以先留意它。"
                        "Sensitive" -> "如果你正要登入或付款，先確認這是你要用的 app。"
                        "Routine" -> "這看起來很普通，先不用緊張。"
                        else -> "先看懂大方向就好，不用每一筆都研究。"
                    }
                    ConnectionFeedPreview(
                        title = title,
                        sourceLabel = sourceLabelFor(appName, mostRecent.attributionLabel),
                        targetLabel = target,
                        eventLabel = eventLabelFor(mostRecent.eventType),
                        detail = detail,
                        actionHint = actionHint,
                        activityLabel = activityLabelFor(events.size),
                        recentSummary = recentSummaryFor(events.size),
                        riskLabel = riskLabelForUi(mostRecent.riskLabel),
                        relativeTime = relativeTimeFrom(mostRecent.createdAt),
                        recentCount = events.size
                    )
                }

                vpnState == VpnProtectionState.On ->
                    ConnectionFeedPreview(
                        title = "正在等新動態",
                        sourceLabel = "SecureGuard 動態",
                        targetLabel = "還沒有新目標",
                        eventLabel = "等待中",
                        detail = "你可以先去用幾個 app，這裡就會開始出現新內容。",
                        actionHint = "先打開瀏覽器、聊天或影片 app 試試看。",
                        activityLabel = "安靜",
                        recentSummary = "現在還沒有看到新動態。",
                        riskLabel = "已準備",
                        relativeTime = "現在",
                        recentCount = 0
                    )

                vpnState == VpnProtectionState.Starting ->
                    ConnectionFeedPreview(
                        title = "保護正在打開",
                        sourceLabel = "SecureGuard 動態",
                        targetLabel = "先等一下",
                        eventLabel = "啟動中",
                        detail = "還差一點點，等它準備好就會開始顯示新動態。",
                        actionHint = "幾秒後再回來看就好。",
                        activityLabel = "暖機中",
                        recentSummary = "動態正在準備中。",
                        riskLabel = "啟動中",
                        relativeTime = "現在",
                        recentCount = 0
                    )

                else ->
                    ConnectionFeedPreview(
                        title = "還沒有開始看連線",
                        sourceLabel = "SecureGuard 動態",
                        targetLabel = "先開啟保護",
                        eventLabel = "未開始",
                        detail = "開啟保護後，這裡才會出現 app 的連線動態。",
                        actionHint = "先按下開啟保護，再去用幾個 app。",
                        activityLabel = "未開始",
                        recentSummary = "目前沒有可看的新動態。",
                        riskLabel = "未開始",
                        relativeTime = "等待中",
                        recentCount = 0
                    )
            }
        }
    }

    private fun simpleDetailFor(eventType: String, target: String): String = when (eventType) {
        "DNS_A_QUERY", "DNS_AAAA_QUERY", "DNS_CNAME_QUERY", "DNS_MX_QUERY" -> "剛剛查了 $target。"
        else -> "剛剛連到了 $target。"
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

    private fun sourceLabelFor(appName: String, attributionLabel: String?): String = when {
        appName == "SecureGuard" -> "來源：SecureGuard"
        appName == "未知 app" && !attributionLabel.isNullOrBlank() -> "來源：還沒完全認出是哪個 app"
        appName == "未知 app" -> "來源：還沒認出是哪個 app"
        else -> "來源：$appName"
    }

    private fun riskLabelForUi(riskLabel: String): String = when (riskLabel) {
        "Tracker" -> "多看一眼"
        "Sensitive" -> "先留意"
        "Routine" -> "看起來正常"
        "Ready" -> "已準備"
        "Starting" -> "啟動中"
        else -> "一般"
    }

    private fun activityLabelFor(recentCount: Int): String = when {
        recentCount <= 1 -> "安靜"
        recentCount <= 4 -> "有一點動靜"
        recentCount <= 8 -> "有點忙"
        else -> "很忙"
    }

    private fun recentSummaryFor(recentCount: Int): String = when {
        recentCount <= 1 -> "目前只看到很少的新動態。"
        recentCount <= 4 -> "最近有幾筆新動態。"
        recentCount <= 8 -> "最近動得比較明顯。"
        else -> "最近這台手機很忙。"
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
