package com.secureguard.app.feature.permissionaudit

import com.secureguard.app.domain.model.AppScanResult
import com.secureguard.app.domain.model.ConnectionFeedPreview
import com.secureguard.app.domain.model.RecentConnectionTimeline
import com.secureguard.app.domain.model.SecurityOverview
import com.secureguard.app.domain.model.SecuritySuggestion
import com.secureguard.app.domain.model.VpnProtectionState
import com.secureguard.app.domain.model.WifiSafetyLevel
import com.secureguard.app.domain.model.WifiSecuritySnapshot

data class PermissionAuditUiState(
    val isLoading: Boolean = true,
    val apps: List<AppScanResult> = emptyList(),
    val hasUsageAccess: Boolean = false,
    val errorMessage: String? = null,
    val isCheckingForUpdate: Boolean = false,
    val updateStatusMessage: String? = null,
    val availableUpdate: AvailableUpdate? = null,
    val lastScanLabel: String = "從未",
    val trustedWifiNetworks: List<String> = emptyList(),
    val showVpnDisclosure: Boolean = false,
    val vpnProtectionState: VpnProtectionState = VpnProtectionState.Off,
    val vpnStatusMessage: String = "保護模式已關閉。要開始本機網路監看時再開啟即可。",
    val vpnCapabilityNote: String = "目前的保護模式可以觀察 DNS 通道事件與服務狀態。每個 app 的歸因與更完整的流量處理仍在持續補強中。",
    val connectionFeedPreview: ConnectionFeedPreview = ConnectionFeedPreview(
        title = "目前還沒有即時連線",
        sourceLabel = "SecureGuard 動態",
        targetLabel = "尚無目標",
        eventLabel = "等待中",
        detail = "開啟保護模式後，才會開始建立 app 流量的本機連線動態。",
        actionHint = "如果你想先看到清楚的流量概況，請先開啟保護模式。",
        activityLabel = "閒置中",
        recentSummary = "目前還沒有近期即時流量可供整理。",
        riskLabel = "閒置",
        relativeTime = "等待中",
        recentCount = 0
    ),
    val recentConnectionTimeline: RecentConnectionTimeline = RecentConnectionTimeline(
        summary = "目前還沒有近期事件。",
        items = emptyList()
    ),
    val isClearingRecentActivity: Boolean = false,
    val recentActivityStatusMessage: String? = null,
    val isRecentActivityExpanded: Boolean = false,
    val isRecentActivityHistoryOpen: Boolean = false,
    val wifiSnapshot: WifiSecuritySnapshot = WifiSecuritySnapshot(
        isWifiActive = false,
        networkName = "正在檢查網路...",
        canManageTrust = false,
        isTrustedNetwork = false,
        securityLabel = "未知",
        familiarityLabel = "正在檢查網路熟悉度...",
        safetyLevel = WifiSafetyLevel.Unknown,
        crowdLabel = "正在檢查這個 Wi‑Fi 是否偏向共享環境...",
        summary = "SecureGuard 正在準備你的網路檢查。",
        detail = "這個區塊會告訴你目前的 Wi‑Fi 看起來是開放還是受保護。",
        gatewayAddress = null,
        localAddress = null,
        dnsSummary = "正在檢查 DNS...",
        dnsAdvice = "完成網路檢查後，這裡會顯示簡短的 DNS 說明。",
        permissionLimited = false,
        nearbyDeviceCount = 0,
        nearbyDeviceConfidenceLabel = "正在準備估算...",
        nearbyDeviceSummary = "附近裝置可見度仍在載入中。",
        sensitiveActionAdvice = "完成掃描後，這裡會出現關於敏感操作的簡短建議。"
    ),
    val securityOverview: SecurityOverview = SecurityOverview(
        score = 72,
        scoreBandLabel = "準備中",
        scoreDetail = "SecureGuard 仍在整理構成這個分數的訊號。",
        headline = "正在檢查你手機的安全節奏",
        summary = "SecureGuard 正在準備一個簡單、好理解的重點總覽。",
        primaryActionTitle = "正在準備最值得先做的一步",
        primaryActionDetail = "App 正在判斷哪一件事最值得先處理，且影響最大。",
        suggestions = listOf(
            SecuritySuggestion(
                title = "正在準備建議",
                detail = "掃描完成後，這裡會出現幾個簡單而有效的建議。",
                categoryLabel = "總覽",
                priorityLabel = "準備中"
            )
        ),
        watchApps = emptyList(),
        closeCandidates = emptyList()
    )
)

data class AvailableUpdate(
    val versionLabel: String,
    val releaseTitle: String,
    val releaseUrl: String
)
