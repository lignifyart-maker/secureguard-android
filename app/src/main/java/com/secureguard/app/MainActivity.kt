package com.secureguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.secureguard.app.feature.permissionaudit.PermissionAuditRoute
import com.secureguard.app.ui.theme.SecureGuardTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecureGuardTheme {
                Surface {
                    PermissionAuditRoute()
                }
            }
        }
    }
}
