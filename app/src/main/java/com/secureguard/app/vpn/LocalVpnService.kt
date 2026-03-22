package com.secureguard.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.EntryPointAccessors
import com.secureguard.app.MainActivity
import com.secureguard.app.core.database.entity.NetworkEventEntity
import com.secureguard.app.core.di.NetworkEventEntryPoint
import com.secureguard.app.domain.model.VpnProtectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocalVpnService : VpnService() {
    private var tunnelInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopProtection()
            else -> startProtection()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tunnelInterface?.close()
        tunnelInterface = null
        serviceScope.cancel()
        super.onDestroy()
        if (_serviceState.value != VpnProtectionState.Off) {
            _serviceState.value = VpnProtectionState.Off
            _statusMessage.value = "Protection mode stopped."
        }
    }

    private fun startProtection() {
        if (tunnelInterface != null) {
            _serviceState.value = VpnProtectionState.On
            _statusMessage.value = "Protection mode is already active."
            return
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Local monitoring is getting ready."))
        _serviceState.value = VpnProtectionState.Starting
        _statusMessage.value = "Preparing local VPN protection on this device."

        runCatching {
            Builder()
                .setSession("SecureGuard Local Protection")
                .addAddress("10.42.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
                .establish()
        }.onSuccess { parcelFileDescriptor ->
            if (parcelFileDescriptor != null) {
                tunnelInterface = parcelFileDescriptor
                _serviceState.value = VpnProtectionState.On
                _statusMessage.value = "Protection mode is on. Traffic monitoring features can build on this tunnel."
                logNetworkEvent(
                    eventType = "VPN_STARTED",
                    riskLabel = "Info",
                    host = "local_protection"
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification("Protection mode is on."))
            } else {
                _serviceState.value = VpnProtectionState.Error
                _statusMessage.value = "SecureGuard could not establish the local VPN tunnel."
                logNetworkEvent(
                    eventType = "VPN_ERROR",
                    riskLabel = "Caution",
                    host = "local_protection"
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.onFailure {
            _serviceState.value = VpnProtectionState.Error
            _statusMessage.value = "SecureGuard hit a problem while starting protection mode."
            logNetworkEvent(
                eventType = "VPN_ERROR",
                riskLabel = "Caution",
                host = "local_protection"
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopProtection() {
        tunnelInterface?.close()
        tunnelInterface = null
        logNetworkEvent(
            eventType = "VPN_STOPPED",
            riskLabel = "Info",
            host = "local_protection"
        )
        _serviceState.value = VpnProtectionState.Off
        _statusMessage.value = "Protection mode is off."
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun logNetworkEvent(
        eventType: String,
        riskLabel: String,
        host: String
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            NetworkEventEntryPoint::class.java
        )
        serviceScope.launch {
            entryPoint.networkEventDao().insert(
                NetworkEventEntity(
                    packageName = null,
                    appName = "SecureGuard",
                    host = host,
                    ipAddress = null,
                    protocol = "VPN",
                    eventType = eventType,
                    riskLabel = riskLabel,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("SecureGuard Protection")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "SecureGuard Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground notification for SecureGuard local protection mode."
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.secureguard.app.vpn.action.START"
        const val ACTION_STOP = "com.secureguard.app.vpn.action.STOP"
        private const val NOTIFICATION_CHANNEL_ID = "secureguard_protection"
        private const val NOTIFICATION_ID = 3001

        private val _serviceState = MutableStateFlow(VpnProtectionState.Off)
        val serviceState = _serviceState.asStateFlow()

        private val _statusMessage = MutableStateFlow("Protection mode is off. Turn it on when you want local network monitoring.")
        val statusMessage = _statusMessage.asStateFlow()

        fun startIntent(context: Context): Intent {
            return Intent(context, LocalVpnService::class.java).apply {
                action = ACTION_START
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, LocalVpnService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
