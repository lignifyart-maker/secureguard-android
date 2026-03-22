package com.secureguard.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification.CATEGORY_SERVICE
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.EntryPointAccessors
import com.secureguard.app.MainActivity
import com.secureguard.app.core.database.entity.NetworkEventEntity
import com.secureguard.app.core.di.NetworkEventEntryPoint
import com.secureguard.app.core.di.VpnRuntimeEntryPoint
import com.secureguard.app.domain.model.VpnProtectionState
import java.io.FileInputStream
import java.net.Inet4Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocalVpnService : VpnService() {
    private var tunnelInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readLoopJob: Job? = null
    private var lastLoggedDnsHost: String? = null
    private var lastLoggedDnsAt: Long = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopProtection()
            else -> startProtection()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        readLoopJob?.cancel()
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
            val builder = Builder()
                .setSession("SecureGuard Local Protection")
                .addAddress("10.42.0.2", 32)
            addDnsRoutes(builder)
            builder.establish()
        }.onSuccess { parcelFileDescriptor ->
            if (parcelFileDescriptor != null) {
                tunnelInterface = parcelFileDescriptor
                startReadLoop(parcelFileDescriptor)
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
        readLoopJob?.cancel()
        readLoopJob = null
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
        host: String,
        protocol: String = "VPN"
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
                    protocol = protocol,
                    eventType = eventType,
                    riskLabel = riskLabel,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun startReadLoop(parcelFileDescriptor: ParcelFileDescriptor) {
        readLoopJob?.cancel()
        readLoopJob = serviceScope.launch {
            val inputStream = FileInputStream(parcelFileDescriptor.fileDescriptor)
            val runtimeEntryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                VpnRuntimeEntryPoint::class.java
            )
            val ipv4Parser = runtimeEntryPoint.ipv4PacketParser()
            val udpParser = runtimeEntryPoint.udpDatagramParser()
            val dnsParser = runtimeEntryPoint.dnsPacketParser()
            val domainRiskClassifier = runtimeEntryPoint.domainRiskClassifier()
            val networkEventDao = runtimeEntryPoint.networkEventDao()
            val buffer = ByteArray(32767)

            while (isActive) {
                val count = inputStream.read(buffer)
                if (count <= 0) continue

                val ipv4Packet = ipv4Parser.parse(buffer, count) ?: continue
                if (ipv4Packet.protocolNumber != 17) continue

                val udpDatagram = udpParser.parse(
                    buffer = buffer,
                    offset = ipv4Packet.payloadOffset,
                    availableLength = ipv4Packet.totalLength - ipv4Packet.payloadOffset
                ) ?: continue

                val isDnsTraffic = udpDatagram.sourcePort == 53 || udpDatagram.destinationPort == 53
                if (!isDnsTraffic) continue

                val dnsQuestion = dnsParser.parseQuestion(
                    buffer = buffer,
                    offset = udpDatagram.payloadOffset,
                    length = udpDatagram.payloadLength
                ) ?: continue

                val now = System.currentTimeMillis()
                if (dnsQuestion.host == lastLoggedDnsHost && now - lastLoggedDnsAt < 1500L) {
                    continue
                }
                lastLoggedDnsHost = dnsQuestion.host
                lastLoggedDnsAt = now

                networkEventDao.insert(
                    NetworkEventEntity(
                        packageName = null,
                        appName = "Unknown app",
                        host = dnsQuestion.host,
                        ipAddress = ipv4Packet.destinationIp,
                        protocol = "UDP/53",
                        eventType = "DNS_${dnsQuestion.queryTypeLabel}_QUERY",
                        riskLabel = domainRiskClassifier.classify(dnsQuestion.host),
                        createdAt = now
                    )
                )
            }
        }
    }

    private fun addDnsRoutes(builder: Builder) {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkProperties: LinkProperties = connectivityManager.getLinkProperties(connectivityManager.activeNetwork)
            ?: return
        linkProperties.dnsServers
            .filterIsInstance<Inet4Address>()
            .forEach { dnsServer ->
                builder.addRoute(dnsServer.hostAddress ?: return@forEach, 32)
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
        val stopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("SecureGuard Protection")
            .setContentText(contentText)
            .setSubText("Local on-device analysis")
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopIntent)
            .setCategory(CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
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
