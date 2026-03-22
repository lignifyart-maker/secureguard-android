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
    private var lastLoggedUdpSignature: String? = null
    private var lastLoggedUdpAt: Long = 0L
    private var lastLoggedTcpSignature: String? = null
    private var lastLoggedTcpAt: Long = 0L

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
            _statusMessage.value = "保護模式已停止。"
        }
    }

    private fun startProtection() {
        if (tunnelInterface != null) {
            _serviceState.value = VpnProtectionState.On
            _statusMessage.value = "保護模式已經在執行中。"
            return
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("本機監看功能正在準備中。"))
        _serviceState.value = VpnProtectionState.Starting
        _statusMessage.value = "正在為這台裝置準備本機 VPN 保護。"

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
                _statusMessage.value = "保護模式已開啟。流量監看功能現在可以建立在這條本機通道上。"
                logNetworkEvent(
                    eventType = "VPN_STARTED",
                    riskLabel = "Info",
                    host = "local_protection"
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification("保護模式已開啟。"))
            } else {
                _serviceState.value = VpnProtectionState.Error
                _statusMessage.value = "SecureGuard 無法建立本機 VPN 通道。"
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
            _statusMessage.value = "SecureGuard 在啟動保護模式時發生問題。"
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
        _statusMessage.value = "保護模式已關閉。"
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
                    attributionLabel = "SecureGuard 服務事件",
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
            val tcpParser = runtimeEntryPoint.tcpSegmentParser()
            val dnsParser = runtimeEntryPoint.dnsPacketParser()
            val domainRiskClassifier = runtimeEntryPoint.domainRiskClassifier()
            val connectionOwnerResolver = runtimeEntryPoint.connectionOwnerResolver()
            val networkEventDao = runtimeEntryPoint.networkEventDao()
            val buffer = ByteArray(32767)

            while (isActive) {
                val count = inputStream.read(buffer)
                if (count <= 0) continue

                val ipv4Packet = ipv4Parser.parse(buffer, count) ?: continue
                when (ipv4Packet.protocolNumber) {
                    17 -> {
                        val udpDatagram = udpParser.parse(
                            buffer = buffer,
                            offset = ipv4Packet.payloadOffset,
                            availableLength = ipv4Packet.totalLength - ipv4Packet.payloadOffset
                        ) ?: continue

                        val now = System.currentTimeMillis()
                        val attribution = connectionOwnerResolver.resolveUdpOwner(
                            localIp = ipv4Packet.sourceIp,
                            localPort = udpDatagram.sourcePort,
                            remoteIp = ipv4Packet.destinationIp,
                            remotePort = udpDatagram.destinationPort
                        )
                        val isOutgoingDnsQuery = udpDatagram.destinationPort == 53
                        if (isOutgoingDnsQuery) {
                            val dnsQuestion = dnsParser.parseQuestion(
                                buffer = buffer,
                                offset = udpDatagram.payloadOffset,
                                length = udpDatagram.payloadLength
                            ) ?: continue

                            if (dnsQuestion.host == lastLoggedDnsHost && now - lastLoggedDnsAt < 1500L) {
                                continue
                            }
                            lastLoggedDnsHost = dnsQuestion.host
                            lastLoggedDnsAt = now

                            networkEventDao.insert(
                                NetworkEventEntity(
                                    packageName = attribution.packageName,
                                    appName = attribution.appName,
                                    attributionLabel = attribution.confidenceLabel,
                                    host = dnsQuestion.host,
                                    ipAddress = ipv4Packet.destinationIp,
                                    protocol = "UDP/53",
                                    eventType = "DNS_${dnsQuestion.queryTypeLabel}_QUERY",
                                    riskLabel = domainRiskClassifier.classify(dnsQuestion.host),
                                    createdAt = now
                                )
                            )
                            continue
                        }

                        val udpSignature = listOf(
                            attribution.packageName ?: attribution.appName,
                            ipv4Packet.destinationIp,
                            udpDatagram.destinationPort.toString()
                        ).joinToString("|")
                        if (udpSignature == lastLoggedUdpSignature && now - lastLoggedUdpAt < 2000L) {
                            continue
                        }
                        lastLoggedUdpSignature = udpSignature
                        lastLoggedUdpAt = now

                        networkEventDao.insert(
                            NetworkEventEntity(
                                packageName = attribution.packageName,
                                appName = attribution.appName,
                                attributionLabel = attribution.confidenceLabel,
                                host = null,
                                ipAddress = ipv4Packet.destinationIp,
                                protocol = "UDP/${udpDatagram.destinationPort}",
                                eventType = udpEventTypeFor(udpDatagram.destinationPort),
                                riskLabel = udpRiskLabelFor(udpDatagram.destinationPort),
                                createdAt = now
                            )
                        )
                    }

                    6 -> {
                        val tcpSegment = tcpParser.parse(
                            buffer = buffer,
                            offset = ipv4Packet.payloadOffset,
                            availableLength = ipv4Packet.totalLength - ipv4Packet.payloadOffset
                        ) ?: continue
                        val isOutgoingConnectAttempt = tcpSegment.isSyn && !tcpSegment.isAck
                        if (!isOutgoingConnectAttempt) continue

                        val now = System.currentTimeMillis()
                        val attribution = connectionOwnerResolver.resolveTcpOwner(
                            localIp = ipv4Packet.sourceIp,
                            localPort = tcpSegment.sourcePort,
                            remoteIp = ipv4Packet.destinationIp,
                            remotePort = tcpSegment.destinationPort
                        )
                        val tcpSignature = listOf(
                            attribution.packageName ?: attribution.appName,
                            ipv4Packet.destinationIp,
                            tcpSegment.destinationPort.toString()
                        ).joinToString("|")
                        if (tcpSignature == lastLoggedTcpSignature && now - lastLoggedTcpAt < 2500L) {
                            continue
                        }
                        lastLoggedTcpSignature = tcpSignature
                        lastLoggedTcpAt = now

                        networkEventDao.insert(
                            NetworkEventEntity(
                                packageName = attribution.packageName,
                                appName = attribution.appName,
                                attributionLabel = attribution.confidenceLabel,
                                host = null,
                                ipAddress = ipv4Packet.destinationIp,
                                protocol = "TCP/${tcpSegment.destinationPort}",
                                eventType = tcpEventTypeFor(tcpSegment.destinationPort),
                                riskLabel = tcpRiskLabelFor(tcpSegment.destinationPort),
                                createdAt = now
                            )
                        )
                    }

                    else -> continue
                }
            }
        }
    }

    private fun tcpEventTypeFor(destinationPort: Int): String = when (destinationPort) {
        443 -> "TCP_HTTPS_CONNECT"
        80 -> "TCP_HTTP_CONNECT"
        5228, 5229, 5230 -> "TCP_PUSH_CONNECT"
        else -> "TCP_APP_CONNECT"
    }

    private fun tcpRiskLabelFor(destinationPort: Int): String = when (destinationPort) {
        80 -> "Observed"
        443 -> "Routine"
        5228, 5229, 5230 -> "Routine"
        else -> "Observed"
    }

    private fun udpEventTypeFor(destinationPort: Int): String = when (destinationPort) {
        123 -> "UDP_NTP_TRAFFIC"
        443 -> "UDP_QUIC_TRAFFIC"
        3478, 3479, 5349, 5350 -> "UDP_STUN_TRAFFIC"
        else -> "UDP_APP_TRAFFIC"
    }

    private fun udpRiskLabelFor(destinationPort: Int): String = when (destinationPort) {
        123 -> "Routine"
        443 -> "Observed"
        3478, 3479, 5349, 5350 -> "Observed"
        else -> "Observed"
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
            .setContentTitle("SecureGuard 保護中")
            .setContentText(contentText)
            .setSubText("裝置端本機分析")
            .setContentIntent(pendingIntent)
            .addAction(0, "停止", stopIntent)
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
            "SecureGuard 保護中",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "SecureGuard 本機保護模式的前景通知。"
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

        private val _statusMessage = MutableStateFlow("保護模式已關閉。要開始本機網路監看時再開啟即可。")
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
