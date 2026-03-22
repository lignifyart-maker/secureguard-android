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
            Text("Before protection mode starts")
        },
        text = {
            Text(
                "SecureGuard uses Android's VpnService only for on-device security analysis. Your traffic stays on this phone and is not uploaded to an outside security server."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Continue")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Not now")
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
                                contentDescription = "Back to dashboard"
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = if (state.isRecentActivityHistoryOpen) {
                            "Recent activity history"
                        } else {
                            "SecureGuard"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh scan")
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
                text = "Preparing your on-device safety dashboard...",
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
            text = "We could not finish the safety check just now.",
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
        Button(onClick = onRefresh) {
            Text("Try again")
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
                    title = "High alert",
                    value = criticalApps.toString(),
                    tone = StatusTone.Warm
                )
                MiniStatusCard(
                    title = "Needs review",
                    value = highApps.toString(),
                    tone = StatusTone.Sun
                )
                MiniStatusCard(
                    title = "All scanned",
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
                text = "Apps worth checking first",
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
                text = "Live connection feed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "This is your plain-language traffic view. It highlights recent DNS and UDP events instead of raw packet data.",
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
                text = "Seen ${preview.relativeTime}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = preview.recentSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${preview.recentCount} recent event(s)",
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
                    text = "Recent activity",
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
                            text = "History",
                            onClick = onOpenHistory
                        )
                    }
                    if (timeline.hasMoreThanPreview) {
                        TextButtonLike(
                            text = if (isExpanded) "Collapse" else "View all",
                            onClick = onToggleExpanded
                        )
                    }
                    if (timeline.items.isNotEmpty() || isClearing) {
                        TextButtonLike(
                            text = if (isClearing) "Clearing..." else "Clear",
                            enabled = !isClearing,
                            onClick = onClear
                        )
                    }
                }
            }
            if (isExpanded) {
                Text(
                    text = "Showing ${visibleItems.size} recent event(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (timeline.hasMoreThanPreview) {
                Text(
                    text = "Showing the latest 3 of ${timeline.items.size} event(s)",
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
                                "No recent activity yet"
                            } else {
                                "Protection mode is off"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (vpnState == VpnProtectionState.On || vpnState == VpnProtectionState.Starting) {
                                "SecureGuard is watching for new DNS events. Recent app lookups will appear here once traffic starts moving."
                            } else {
                                "Turn on protection mode to start collecting recent DNS lookups and app attribution in this panel."
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
                        text = "Full recent activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = timeline.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${timeline.items.size} event(s) currently in local history",
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
                            text = if (isClearing) "Clearing..." else "Clear history",
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
                                    "Protection is on. As new DNS and UDP traffic appears, it will build up here."
                                } else {
                                    "Turn on local protection and use a few apps to start building recent history."
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
                text = "Protection mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            RiskBadgeText(
                text = "Local only",
                color = Color(0xFF4A8C69)
            )
            Text(
                text = "SecureGuard uses Android's local VPN interface to watch traffic on this phone only. Your traffic is not uploaded to an outside security server.",
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                        Color(0xFFC55A54)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(
                    if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                        "Stop local protection"
                    } else {
                        "Start local protection"
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
                    "What SecureGuard is doing now"
                } else {
                    "Get useful results in 3 steps"
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
                            "Protection is running on this phone"
                        } else {
                            "Start local protection"
                        },
                        detail = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                            "SecureGuard is using Android's local VPN mode to inspect traffic on-device only."
                        } else {
                            "Approve Android's VPN prompt once so SecureGuard can start watching traffic locally."
                        }
                    )
                    ChecklistItem(
                        icon = Icons.Outlined.NetworkWifi,
                        label = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                            "Use your phone normally for a minute"
                        } else {
                            "Open a few apps after enabling it"
                        },
                        detail = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                            "Open a browser, chat app, or video app to generate real DNS and UDP activity."
                        } else {
                            "A browser, chat app, or streaming app will give the dashboard something real to show."
                        }
                    )
                    ChecklistItem(
                        icon = Icons.Outlined.People,
                        label = "Read the activity feed",
                        detail = if (state == VpnProtectionState.On || state == VpnProtectionState.Starting) {
                            "The feed will show recent app targets and event types when Android can map them."
                        } else {
                            "Recent activity will show app names, targets, and event types instead of raw packet data."
                        }
                    )
                }
            }
            Text(
                text = "This is a calm local monitor, not a remote VPN service or a raw packet sniffer.",
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
                text = "See which apps are talking, before it feels invisible.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "SecureGuard turns Android's local VPN mode into an on-device activity dashboard, so you can spot DNS lookups, app traffic hints, and risky patterns without sending your traffic to a cloud scanner.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            ScoreBubble(
                score = overview.score,
                scoreBandLabel = overview.scoreBandLabel,
                scoreDetail = overview.scoreDetail
            )
            Text(
                text = "$notableApps of $totalApps installed apps are worth a second look. Last scan: $lastScanLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
            )

            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Refresh device check")
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
                text = "Safety score",
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
                text = "Best next step",
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
                text = "Safe to close first (${apps.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "These look more like optional utility apps than core phone tools, so they are good first candidates to review or close when you want a lighter phone.",
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
                        text = "Current network vibe",
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
                text = "Protection: ${snapshot.securityLabel}",
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
                            "Trusted network"
                        } else {
                            "Mark as trusted"
                        }
                    )
                }
            }
            if (trustedNetworks.isNotEmpty()) {
                Text(
                    text = "Saved trusted Wi-Fi",
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
                                text = "Remove",
                                onClick = { onRemoveTrustedNetwork(trustedSsid) }
                            )
                        }
                    }
                }
            }
            snapshot.gatewayAddress?.let {
                Text(
                    text = "Gateway: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            snapshot.localAddress?.let {
                Text(
                    text = "Local address: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "DNS: ${snapshot.dnsSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = snapshot.dnsAdvice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Visible devices: ${snapshot.nearbyDeviceCount}",
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
                    Text("Allow Wi-Fi details")
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
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        modifier = Modifier.wrapContentWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
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
    notableApps == 0 -> "Everything looks calm"
    notableApps <= 2 -> "Only a few apps need a look"
    notableApps <= 5 -> "A gentle cleanup could help"
    else -> "Several apps deserve attention"
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
    VpnProtectionState.Off -> "Turn this on when you want local DNS and VPN activity hints."
    VpnProtectionState.Starting -> "SecureGuard is opening the local tunnel and preparing the first events."
    VpnProtectionState.On -> "Protection mode is actively collecting lightweight on-device event signals."
    VpnProtectionState.Error -> "Something interrupted local protection mode, so event hints may pause until it starts again."
}

private fun connectionFeedAccent(label: String): Color = when (label) {
    "Tracker" -> Color(0xFFC55A54)
    "Sensitive" -> Color(0xFFDD8B42)
    "Routine" -> Color(0xFF4A8C69)
    "Ready" -> Color(0xFF4A8C69)
    "Starting" -> Color(0xFFB27A1F)
    else -> Color(0xFF6F7C92)
}

private fun activityAccent(label: String): Color = when (label) {
    "Quiet" -> Color(0xFF4A8C69)
    "Light activity" -> Color(0xFF7B9E68)
    "Busy" -> Color(0xFFDD8B42)
    "Very busy" -> Color(0xFFC55A54)
    "Warming up" -> Color(0xFFB27A1F)
    else -> Color(0xFF6F7C92)
}

private fun suggestionPriorityColor(label: String): Color = when (label) {
    "Do now" -> Color(0xFFC55A54)
    "Soon" -> Color(0xFFDD8B42)
    "Good to know" -> Color(0xFF4A8C69)
    else -> Color(0xFF6F7C92)
}

private fun attributionAccent(label: String): Color = when (label) {
    "Mapped" -> Color(0xFF4A8C69)
    "Pending" -> Color(0xFFB27A1F)
    "Partial" -> Color(0xFFDD8B42)
    "Fallback" -> Color(0xFFC55A54)
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
