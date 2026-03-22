package com.secureguard.app.feature.permissionaudit

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NetworkWifi
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secureguard.app.domain.model.AppScanResult
import com.secureguard.app.domain.model.ConnectionFeedPreview
import com.secureguard.app.domain.model.RiskLevel
import com.secureguard.app.domain.model.RecentConnectionTimeline
import com.secureguard.app.domain.model.SecurityOverview
import com.secureguard.app.domain.model.SecuritySuggestion
import com.secureguard.app.domain.model.VpnProtectionState
import com.secureguard.app.domain.model.WifiSafetyLevel
import com.secureguard.app.domain.model.WifiSecuritySnapshot
import com.secureguard.app.vpn.LocalVpnService

@Composable
fun PermissionAuditRoute(
    viewModel: PermissionAuditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refresh()
    }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ContextCompat.startForegroundService(context, LocalVpnService.startIntent(context))
        }
    }
    PermissionAuditScreen(
        state = uiState,
        onRefresh = viewModel::refresh,
        onClearRecentActivity = viewModel::clearRecentActivity,
        onToggleRecentActivityExpanded = viewModel::toggleRecentActivityExpanded,
        onOpenRecentActivityHistory = viewModel::openRecentActivityHistory,
        onCloseRecentActivityHistory = viewModel::closeRecentActivityHistory,
        onRequestWifiPermission = {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onTrustNetwork = { trusted ->
            viewModel.setWifiTrusted(trusted)
        },
        onRemoveTrustedNetwork = { ssid ->
            viewModel.removeTrustedWifi(ssid)
        },
        onEnableProtection = {
            viewModel.dismissProtectionDisclosure()
            val intent = VpnService.prepare(context)
            if (intent != null) {
                vpnPermissionLauncher.launch(intent)
            } else {
                ContextCompat.startForegroundService(context, LocalVpnService.startIntent(context))
            }
        },
        onDisableProtection = {
            context.startService(LocalVpnService.stopIntent(context))
        },
        onShowDisclosure = viewModel::requestProtectionDisclosure,
        onDismissDisclosure = viewModel::dismissProtectionDisclosure
    )
}

@Composable
private fun VpnDisclosureDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("要開始保護了")
        },
        text = {
            Text(
                "它會幫你看看手機裡的新連線。資料只留在這支手機，不會送出去。"
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE98F6F))
            ) {
                Text("開始")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF2D9C7),
                    contentColor = Color(0xFF7A4E35)
                )
            ) {
                Text("等等")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionAuditScreen(
    state: PermissionAuditUiState,
    onRefresh: () -> Unit,
    onClearRecentActivity: () -> Unit,
    onToggleRecentActivityExpanded: () -> Unit,
    onOpenRecentActivityHistory: () -> Unit,
    onCloseRecentActivityHistory: () -> Unit,
    onRequestWifiPermission: () -> Unit,
    onTrustNetwork: (Boolean) -> Unit,
    onRemoveTrustedNetwork: (String) -> Unit,
    onShowDisclosure: () -> Unit,
    onDismissDisclosure: () -> Unit,
    onEnableProtection: () -> Unit,
    onDisableProtection: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    if (state.isRecentActivityHistoryOpen) {
                        IconButton(onClick = onCloseRecentActivityHistory) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "回到首頁"
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = if (state.isRecentActivityHistoryOpen) "全部動態" else "SecureGuard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "重看一次")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(innerPadding)
            state.errorMessage != null -> ErrorState(
                innerPadding = innerPadding,
                message = state.errorMessage,
                onRefresh = onRefresh
            )
            else -> AuditContent(
                state = state,
                innerPadding = innerPadding,
                onRefresh = onRefresh,
                onClearRecentActivity = onClearRecentActivity,
                onToggleRecentActivityExpanded = onToggleRecentActivityExpanded,
                onOpenRecentActivityHistory = onOpenRecentActivityHistory,
                onRequestWifiPermission = onRequestWifiPermission,
                onTrustNetwork = onTrustNetwork,
                onRemoveTrustedNetwork = onRemoveTrustedNetwork,
                onShowDisclosure = onShowDisclosure,
                onEnableProtection = onEnableProtection,
                onDisableProtection = onDisableProtection
            )
        }
    }
    if (state.showVpnDisclosure) {
        VpnDisclosureDialog(
            onConfirm = onEnableProtection,
            onDismiss = onDismissDisclosure
        )
    }
}

@Composable
private fun LoadingState(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = "正在準備首頁…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun ErrorState(
    innerPadding: PaddingValues,
    message: String,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "剛剛沒有順利完成",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRefresh,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE98F6F))
        ) {
            Text("再試一次")
        }
    }
}

@Composable
private fun AuditContent(
    state: PermissionAuditUiState,
    innerPadding: PaddingValues,
    onRefresh: () -> Unit,
    onClearRecentActivity: () -> Unit,
    onToggleRecentActivityExpanded: () -> Unit,
    onOpenRecentActivityHistory: () -> Unit,
    onRequestWifiPermission: () -> Unit,
    onTrustNetwork: (Boolean) -> Unit,
    onRemoveTrustedNetwork: (String) -> Unit,
    onShowDisclosure: () -> Unit,
    onEnableProtection: () -> Unit,
    onDisableProtection: () -> Unit
) {
    val criticalApps = state.apps.count { it.riskLevel == RiskLevel.Critical }
    val highApps = state.apps.count { it.riskLevel == RiskLevel.High }
    val notableApps = state.apps.count { it.riskLevel != RiskLevel.Safe }

    if (state.isRecentActivityHistoryOpen) {
        RecentActivityHistoryScreen(
            timeline = state.recentConnectionTimeline,
            vpnState = state.vpnProtectionState,
            isClearing = state.isClearingRecentActivity,
            statusMessage = state.recentActivityStatusMessage,
            innerPadding = innerPadding,
            onClear = onClearRecentActivity
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroCard(
                overview = state.securityOverview,
                totalApps = state.apps.size,
                notableApps = notableApps,
                lastScanLabel = state.lastScanLabel,
                onRefresh = onRefresh
            )
        }

        item {
            PrimaryActionCard(overview = state.securityOverview)
        }

        item {
            QuickStartCard(state = state.vpnProtectionState)
        }

        item {
            ProtectionModeCard(
                state = state.vpnProtectionState,
                statusMessage = state.vpnStatusMessage,
                capabilityNote = state.vpnCapabilityNote,
                onEnableProtection = onShowDisclosure,
                onDisableProtection = onDisableProtection
            )
        }

        item {
            ConnectionFeedCard(preview = state.connectionFeedPreview)
        }

        item {
            RecentActivityCard(
                timeline = state.recentConnectionTimeline,
                vpnState = state.vpnProtectionState,
                isClearing = state.isClearingRecentActivity,
                isExpanded = state.isRecentActivityExpanded,
                statusMessage = state.recentActivityStatusMessage,
                onClear = onClearRecentActivity,
                onOpenHistory = onOpenRecentActivityHistory,
                onToggleExpanded = onToggleRecentActivityExpanded
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStatusCard(
                    title = "要先看",
                    value = criticalApps.toString(),
                    tone = StatusTone.Warm
                )
                MiniStatusCard(
                    title = "再看一下",
                    value = highApps.toString(),
                    tone = StatusTone.Sun
                )
                MiniStatusCard(
                    title = "看過了",
                    value = state.apps.size.toString(),
                    tone = StatusTone.Calm
                )
            }
        }

        item {
            GentleChecklist(apps = state.apps)
        }

        item {
            SecuritySuggestionCard(overview = state.securityOverview)
        }

        if (state.securityOverview.watchApps.isNotEmpty()) {
            item {
                WatchAppsCard(apps = state.securityOverview.watchApps)
            }
        }

        if (state.securityOverview.closeCandidates.isNotEmpty()) {
            item {
                CloseCandidatesCard(apps = state.securityOverview.closeCandidates)
            }
        }

        item {
            WifiSafetyCard(
                snapshot = state.wifiSnapshot,
                trustedNetworks = state.trustedWifiNetworks,
                onRequestWifiPermission = onRequestWifiPermission,
                onTrustNetwork = onTrustNetwork,
                onRemoveTrustedNetwork = onRemoveTrustedNetwork
            )
        }

        item {
            Text(
                text = "可以先看的 app",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(state.apps.take(8), key = { it.packageName }) { app ->
            AppRiskCard(app = app)
        }
    }
}

@Composable
private fun ConnectionFeedCard(preview: ConnectionFeedPreview) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "現在手機在做什麼",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "這裡只講重點，不會塞很多難懂的字。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RiskBadgeText(
                text = preview.riskLabel,
                color = connectionFeedAccent(preview.riskLabel)
            )
            RiskBadgeText(
                text = preview.activityLabel,
                color = activityAccent(preview.activityLabel)
            )
            Text(
                text = preview.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = preview.sourceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = preview.targetLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = preview.eventLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = preview.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = preview.actionHint,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = "時間：${preview.relativeTime}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = preview.recentSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "最近 ${preview.recentCount} 筆",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentActivityCard(
    timeline: RecentConnectionTimeline,
    vpnState: VpnProtectionState,
    isClearing: Boolean,
    isExpanded: Boolean,
    statusMessage: String?,
    onClear: () -> Unit,
    onOpenHistory: () -> Unit,
    onToggleExpanded: () -> Unit
) {
    val visibleItems = if (isExpanded) timeline.items else timeline.items.take(3)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "最近發生的事",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (timeline.items.isNotEmpty()) {
                        TextButtonLike(
                            text = "全部",
                            onClick = onOpenHistory
                        )
                    }
                    if (timeline.hasMoreThanPreview) {
                        TextButtonLike(
                            text = if (isExpanded) "收起來" else "多看一點",
                            onClick = onToggleExpanded
                        )
                    }
                    if (timeline.items.isNotEmpty() || isClearing) {
                        TextButtonLike(
                            text = if (isClearing) "整理中…" else "清空",
                            enabled = !isClearing,
                            onClick = onClear
                        )
                    }
                }
            }
            if (isExpanded) {
                Text(
                    text = "現在看到 ${visibleItems.size} 筆",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (timeline.hasMoreThanPreview) {
                Text(
                    text = "先看最新 3 筆，共 ${timeline.items.size} 筆",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = timeline.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (statusMessage != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            if (timeline.items.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (vpnState == VpnProtectionState.On || vpnState == VpnProtectionState.Starting) {
                                "現在還沒看到新東西"
                            } else {
                                "你還沒開始保護"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (vpnState == VpnProtectionState.On || vpnState == VpnProtectionState.Starting) {
                                "先去用幾個 app，這裡就會慢慢出現新動態。"
                            } else {
                                "按下開始後，再去滑手機，這裡就會有內容。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            visibleItems.forEach { item ->
                RecentActivityItemCard(item = item)
            }
        }
    }
}

@Composable
private fun RecentActivityHistoryScreen(
    timeline: RecentConnectionTimeline,
    vpnState: VpnProtectionState,
    isClearing: Boolean,
    statusMessage: String?,
    innerPadding: PaddingValues,
    onClear: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "全部動態",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = timeline.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "這裡一共有 ${timeline.items.size} 筆",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (statusMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = statusMessage,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    if (timeline.items.isNotEmpty() || isClearing) {
                        TextButtonLike(
                            text = if (isClearing) "整理中…" else "清空全部",
                            enabled = !isClearing,
                            onClick = onClear
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ) {
                            Text(
                                text = if (vpnState == VpnProtectionState.On || vpnState == VpnProtectionState.Starting) {
                                    "保護已開啟。等你開始用手機，這裡就會慢慢出現內容。"
                                } else {
                                    "先開啟保護，再去用幾個 app，這裡才會有東西。"
                                },
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        items(timeline.items) { item ->
            RecentActivityItemCard(item = item)
        }
    }
}

@Composable
private fun RecentActivityItemCard(item: com.secureguard.app.domain.model.RecentConnectionItem) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RiskBadgeText(
                    text = item.riskLabel,
                    color = connectionFeedAccent(item.riskLabel)
                )
                EventChip(text = item.eventLabel)
                RiskBadgeText(
                    text = item.attributionStateLabel,
                    color = attributionAccent(item.attributionStateLabel)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.sourceLabel,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = item.attributionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProtectionModeCard(
    state: VpnProtectionState,
    statusMessage: String,
    capabilityNote: String,
    onEnableProtection: () -> Unit,
    onDisableProtection: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "保護開關",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            RiskBadgeText(
                text = "只在手機裡",
                color = Color(0xFF4A8C69)
            )
            Text(
                text = "它只幫你在這支手機裡看新連線，不會把資料送到外面。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RiskBadgeText(
                text = state.label,
                color = protectionAccentColor(state)
            )
            Text(
                text = protectionHelperText(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = capabilityNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                    onDisableProtection
                } else {
                    onEnableProtection
                },
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                        Color(0xFFF2A6A0)
                    } else {
                        Color(0xFFE98F6F)
                    }
                )
            ) {
                Text(
                    if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                        "先停一下"
                    } else {
                        "開始保護"
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickStartCard(state: VpnProtectionState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF6EFE4)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                    "它現在在做什麼"
                } else {
                    "三步驟就看得懂"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.75f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChecklistItem(
                        icon = Icons.Outlined.HealthAndSafety,
                        label = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                            "它已經開始看新連線"
                        } else {
                            "先按開始"
                        },
                        detail = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                            "它現在只是在這支手機裡幫你看動態。"
                        } else {
                            "先同意系統跳出來的提示。"
                        }
                    )
                    ChecklistItem(
                        icon = Icons.Outlined.NetworkWifi,
                        label = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                            "照平常一樣滑手機"
                        } else {
                            "打開幾個 app"
                        },
                        detail = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                            "像聊天、看影片、滑網頁都可以。"
                        } else {
                            "這樣畫面就不會空空的。"
                        }
                    )
                    ChecklistItem(
                        icon = Icons.Outlined.People,
                        label = "回來看結果",
                        detail = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                            "你會看到是哪個 app 有動靜。"
                        } else {
                            "它會用簡單的話告訴你剛剛發生什麼事。"
                        }
                    )
                }
            }
            Text(
                text = "這裡只講重點，不講難懂的技術細節。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeroCard(
    overview: SecurityOverview,
    totalApps: Int,
    notableApps: Int,
    lastScanLabel: String,
    onRefresh: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HealthAndSafety,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = heroHeadline(notableApps),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Text(
                text = "先看懂手機有沒有在偷偷忙。",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "它會把看不見的連線，變成你看得懂的小提醒。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            ScoreBubble(
                score = overview.score,
                scoreBandLabel = overview.scoreBandLabel,
                scoreDetail = overview.scoreDetail
            )
            Text(
                text = "$totalApps 個 app 裡，有 $notableApps 個值得你回頭看。上次檢查：$lastScanLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
            )

            Button(
                onClick = onRefresh,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD37D),
                    contentColor = Color(0xFF5E4300)
                )
            ) {
                Text("再看一次")
            }
        }
    }
}

@Composable
private fun ScoreBubble(
    score: Int,
    scoreBandLabel: String,
    scoreDetail: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "安全分數",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$score / 100",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = scoreBandLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = overviewScoreHint(scoreDetail = scoreDetail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun overviewScoreHint(scoreDetail: String): String = scoreDetail

@Composable
private fun PrimaryActionCard(overview: SecurityOverview) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "最值得先做的一步",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = overview.primaryActionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = overview.primaryActionDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStatusCard(
    title: String,
    value: String,
    tone: StatusTone
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tone.container),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = tone.content
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = tone.content
            )
        }
    }
}

@Composable
private fun GentleChecklist(apps: List<AppScanResult>) {
    val topApp = apps.firstOrNull()
    val microphoneApps = apps.count { "android.permission.RECORD_AUDIO" in it.riskyPermissions }
    val locationApps = apps.count { "android.permission.ACCESS_FINE_LOCATION" in it.riskyPermissions }
    val contactsApps = apps.count { "android.permission.READ_CONTACTS" in it.riskyPermissions }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Simple next steps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            ChecklistItem(
                icon = Icons.Outlined.Mic,
                label = "Microphone access",
                detail = if (microphoneApps == 0) {
                    "No scanned apps currently stand out for microphone use."
                } else {
                    "$microphoneApps apps asked for microphone access. Review the ones you rarely use."
                }
            )
            ChecklistItem(
                icon = Icons.Outlined.LocationOn,
                label = "Location access",
                detail = if (locationApps == 0) {
                    "Location access looks fairly quiet right now."
                } else {
                    "$locationApps apps asked for precise location. Keep only the ones you trust."
                }
            )
            ChecklistItem(
                icon = Icons.Outlined.People,
                label = "Contacts access",
                detail = if (contactsApps == 0) {
                    "No scanned apps are currently raising contact-sharing concerns."
                } else {
                    "$contactsApps apps asked to read contacts. Double-check messaging and utility apps first."
                }
            )

            topApp?.let {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Start here",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${it.appName} is the strongest candidate for a quick review.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = it.riskReasons.joinToString(separator = " / "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecuritySuggestionCard(overview: SecurityOverview) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Today, keep it simple",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            overview.suggestions.forEach { suggestion ->
                SuggestionRow(suggestion = suggestion)
            }
        }
    }
}

@Composable
private fun WatchAppsCard(apps: List<AppScanResult>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Keep an eye on these (${apps.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "These apps are not necessarily malicious, but they are the strongest candidates for a closer look first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            apps.forEach { app ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = app.riskLevel.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = app.riskReasons.joinToString(separator = " / "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CloseCandidatesCard(apps: List<AppScanResult>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "適合優先關閉 (${apps.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "這些 app 看起來比較像可有可無的工具型 app，不像手機核心功能，所以若你想先減少噪音或風險，它們是很好的優先檢查或關閉對象。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            apps.forEach { app ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = app.riskLevel.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = app.riskReasons.joinToString(separator = " / "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: SecuritySuggestion) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RiskBadgeText(
                text = suggestion.priorityLabel,
                color = suggestionPriorityColor(suggestion.priorityLabel)
            )
            Text(
                text = suggestion.categoryLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = suggestion.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WifiSafetyCard(
    snapshot: WifiSecuritySnapshot,
    trustedNetworks: List<String>,
    onRequestWifiPermission: () -> Unit,
    onTrustNetwork: (Boolean) -> Unit,
    onRemoveTrustedNetwork: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = wifiContainerColor(snapshot.safetyLevel)
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            shape = CircleShape
                        )
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NetworkWifi,
                        contentDescription = null,
                        tint = wifiAccentColor(snapshot.safetyLevel)
                    )
                }
                Column {
                    Text(
                        text = "目前網路狀態",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = snapshot.networkName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            RiskBadgeText(
                text = snapshot.safetyLevel.label,
                color = wifiAccentColor(snapshot.safetyLevel)
            )
            RiskBadgeText(
                text = snapshot.familiarityLabel,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = snapshot.summary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            RiskBadgeText(
                text = snapshot.crowdLabel,
                color = wifiAccentColor(snapshot.safetyLevel)
            )
            Text(
                text = snapshot.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "保護狀態：${snapshot.securityLabel}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (snapshot.canManageTrust) {
                Button(
                    onClick = { onTrustNetwork(!snapshot.isTrustedNetwork) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (snapshot.isTrustedNetwork) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Text(
                        if (snapshot.isTrustedNetwork) {
                            "已信任網路"
                        } else {
                            "標記為信任"
                        }
                    )
                }
            }
            if (trustedNetworks.isNotEmpty()) {
                Text(
                    text = "已儲存的可信任 Wi‑Fi",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                trustedNetworks.forEach { trustedSsid ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = trustedSsid,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButtonLike(
                                text = "移除",
                                onClick = { onRemoveTrustedNetwork(trustedSsid) }
                            )
                        }
                    }
                }
            }
            snapshot.gatewayAddress?.let {
                Text(
                    text = "閘道：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            snapshot.localAddress?.let {
                Text(
                    text = "本機位址：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "DNS：${snapshot.dnsSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = snapshot.dnsAdvice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "可見裝置：${snapshot.nearbyDeviceCount}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = snapshot.nearbyDeviceConfidenceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = snapshot.nearbyDeviceSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            ) {
                Text(
                    text = snapshot.sensitiveActionAdvice,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (snapshot.permissionLimited) {
                Button(
                    onClick = onRequestWifiPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("允許 Wi‑Fi 詳細資訊")
                }
            }
        }
    }
}

@Composable
private fun TextButtonLike(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) {
            Color(0xFFFFE3D4)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        modifier = Modifier.wrapContentWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (enabled) {
                Color(0xFF9C5B3D)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun EventChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ChecklistItem(
    icon: ImageVector,
    label: String,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = CircleShape
                )
                .padding(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppRiskCard(app: AppScanResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = riskContainerColor(app.riskLevel)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RiskBadge(level = app.riskLevel)

            Text(
                text = app.riskReasons.joinToString(separator = " / "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                app.riskyPermissions.take(3).forEach { permission ->
                    PermissionPill(label = permission.substringAfterLast('.'))
                }
            }
        }
    }
}

@Composable
private fun PermissionPill(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RiskBadge(level: RiskLevel) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = riskBadgeColor(level)
    ) {
        Text(
            text = level.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun RiskBadgeText(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

private fun heroHeadline(notableApps: Int): String = when {
    notableApps == 0 -> "目前很平穩"
    notableApps <= 2 -> "有幾個地方可再看一下"
    notableApps <= 5 -> "現在很適合做點小整理"
    else -> "今天需要多留意一點"
}

@Composable
private fun riskContainerColor(level: RiskLevel): Color = when (level) {
    RiskLevel.Critical -> Color(0xFFFFE3E1)
    RiskLevel.High -> Color(0xFFFFF0D9)
    RiskLevel.Medium -> Color(0xFFFFF8D7)
    RiskLevel.Safe -> MaterialTheme.colorScheme.surface
}

@Composable
private fun riskBadgeColor(level: RiskLevel): Color = when (level) {
    RiskLevel.Critical -> Color(0xFFC85C5C)
    RiskLevel.High -> Color(0xFFDD8B42)
    RiskLevel.Medium -> Color(0xFFC6A64D)
    RiskLevel.Safe -> Color(0xFF5D9971)
}

@Composable
private fun wifiContainerColor(level: WifiSafetyLevel): Color = when (level) {
    WifiSafetyLevel.Safe -> Color(0xFFDFF5EC)
    WifiSafetyLevel.Caution -> Color(0xFFFFF3D8)
    WifiSafetyLevel.Risky -> Color(0xFFFFE4DF)
    WifiSafetyLevel.Unknown -> MaterialTheme.colorScheme.surface
}

private fun wifiAccentColor(level: WifiSafetyLevel): Color = when (level) {
    WifiSafetyLevel.Safe -> Color(0xFF4A8C69)
    WifiSafetyLevel.Caution -> Color(0xFFB27A1F)
    WifiSafetyLevel.Risky -> Color(0xFFC55A54)
    WifiSafetyLevel.Unknown -> Color(0xFF6F7C92)
}

private fun protectionAccentColor(state: VpnProtectionState): Color = when (state) {
    VpnProtectionState.Off -> Color(0xFF6F7C92)
    VpnProtectionState.Starting -> Color(0xFFB27A1F)
    VpnProtectionState.On -> Color(0xFF4A8C69)
    VpnProtectionState.Error -> Color(0xFFC55A54)
}

private fun protectionHelperText(state: VpnProtectionState): String = when (state) {
    VpnProtectionState.Off -> "按下開始後，它才會幫你看新連線。"
    VpnProtectionState.Starting -> "快好了，再等一下。"
    VpnProtectionState.On -> "現在它正在幫你看新動態。"
    VpnProtectionState.Error -> "剛剛有點卡住，等一下可以再試一次。"
}

private fun connectionFeedAccent(label: String): Color = when (label) {
    "多看一眼" -> Color(0xFFC55A54)
    "先留意" -> Color(0xFFDD8B42)
    "看起來正常" -> Color(0xFF4A8C69)
    "已準備" -> Color(0xFF4A8C69)
    "啟動中" -> Color(0xFFB27A1F)
    "未開始" -> Color(0xFF6F7C92)
    "一般" -> Color(0xFF6F7C92)
    "Tracker" -> Color(0xFFC55A54)
    "Sensitive" -> Color(0xFFDD8B42)
    "Routine" -> Color(0xFF4A8C69)
    "Ready" -> Color(0xFF4A8C69)
    "Starting" -> Color(0xFFB27A1F)
    else -> Color(0xFF6F7C92)
}

private fun activityAccent(label: String): Color = when (label) {
    "安靜" -> Color(0xFF4A8C69)
    "有一點動靜" -> Color(0xFF7B9E68)
    "有點忙" -> Color(0xFFDD8B42)
    "很忙" -> Color(0xFFC55A54)
    "暖機中" -> Color(0xFFB27A1F)
    else -> Color(0xFF6F7C92)
}

private fun suggestionPriorityColor(label: String): Color = when (label) {
    "現在" -> Color(0xFFC55A54)
    "稍後" -> Color(0xFFDD8B42)
    "知道就好" -> Color(0xFF4A8C69)
    else -> Color(0xFF6F7C92)
}

private fun attributionAccent(label: String): Color = when (label) {
    "已認出" -> Color(0xFF4A8C69)
    "未認出" -> Color(0xFFB27A1F)
    "部分" -> Color(0xFFDD8B42)
    "不完整" -> Color(0xFFC55A54)
    "補回" -> Color(0xFF7B9E68)
    else -> Color(0xFF6F7C92)
}

private data class StatusTone(
    val container: Color,
    val content: Color
) {
    companion object {
        val Warm = StatusTone(
            container = Color(0xFFFFE2DE),
            content = Color(0xFF9D3C3C)
        )
        val Sun = StatusTone(
            container = Color(0xFFFFF0D6),
            content = Color(0xFF9A5C14)
        )
        val Calm = StatusTone(
            container = Color(0xFFDDF5EC),
            content = Color(0xFF326B57)
        )
    }
}
