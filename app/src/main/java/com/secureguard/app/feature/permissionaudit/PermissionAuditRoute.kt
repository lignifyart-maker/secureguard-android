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
                title = {
                    Text(
                        text = "SecureGuard",
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
                text = "Preparing your safety check-in...",
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
                text = preview.relativeTime,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Recent events: ${preview.recentCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            Text(
                text = "SecureGuard uses Android's local VPN interface only for on-device analysis. Your traffic is not sent to an outside security server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RiskBadgeText(
                text = state.label,
                color = protectionAccentColor(state)
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
                        "Stop protection"
                    } else {
                        "Turn on protection"
                    }
                )
            }
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
                text = overview.headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = overview.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            ScoreBubble(score = overview.score)
            Text(
                text = "$notableApps of $totalApps apps are worth a second look. Last scan: $lastScanLabel",
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
                Text("Run another check")
            }
        }
    }
}

@Composable
private fun ScoreBubble(score: Int) {
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
        }
    }
}

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
                text = "Safe to close first",
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
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.wrapContentWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge
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
