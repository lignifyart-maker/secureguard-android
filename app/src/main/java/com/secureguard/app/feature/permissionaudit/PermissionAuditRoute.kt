package com.secureguard.app.feature.permissionaudit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secureguard.app.domain.model.AppScanResult
import com.secureguard.app.domain.model.RiskLevel

@Composable
fun PermissionAuditRoute(
    viewModel: PermissionAuditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    PermissionAuditScreen(
        state = uiState,
        onRefresh = viewModel::refresh
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionAuditScreen(
    state: PermissionAuditUiState,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SecureGuard") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh scan")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = onRefresh) {
                        Text("Retry scan")
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SummaryCard(
                            totalApps = state.apps.size,
                            riskyApps = state.apps.count { it.riskLevel != RiskLevel.Safe },
                            lastScanLabel = state.lastScanLabel
                        )
                    }

                    items(state.apps, key = { it.packageName }) { app ->
                        AppRiskCard(app = app)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    totalApps: Int,
    riskyApps: Int,
    lastScanLabel: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "App Permission Audit",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$riskyApps of $totalApps apps requested notable permissions.",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Last scan: $lastScanLabel",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AppRiskCard(app: AppScanResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = riskContainerColor(app.riskLevel)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                RiskBadge(level = app.riskLevel)
            }

            Text(
                text = app.riskReasons.joinToString(separator = " • "),
                style = MaterialTheme.typography.bodyMedium
            )

            if (app.riskyPermissions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.riskyPermissions.take(3).forEach { permission ->
                        AssistChip(
                            onClick = {},
                            label = { Text(permission.substringAfterLast('.')) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskBadge(level: RiskLevel) {
    Box(
        modifier = Modifier
            .background(
                color = riskBadgeColor(level),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = level.label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun riskContainerColor(level: RiskLevel): Color = when (level) {
    RiskLevel.Critical -> Color(0xFFFFE4DE)
    RiskLevel.High -> Color(0xFFFFF0D8)
    RiskLevel.Medium -> Color(0xFFF7F2D4)
    RiskLevel.Safe -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun riskBadgeColor(level: RiskLevel): Color = when (level) {
    RiskLevel.Critical -> Color(0xFFB42318)
    RiskLevel.High -> Color(0xFFD97706)
    RiskLevel.Medium -> Color(0xFF9A6700)
    RiskLevel.Safe -> Color(0xFF1D6B57)
}
