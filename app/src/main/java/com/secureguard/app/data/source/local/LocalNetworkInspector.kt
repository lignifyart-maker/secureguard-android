package com.secureguard.app.data.source.local

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LocalNetworkInspector @Inject constructor() {
    suspend fun estimateVisibleDevices(
        gatewayAddress: String?,
        localAddress: String?
    ): LocalNetworkSnapshot = withContext(Dispatchers.IO) {
        val arpEntries = readArpEntries()
        if (arpEntries.isNotEmpty()) {
            val uniqueIps = arpEntries
                .map { it.ipAddress }
                .filterNot { it == gatewayAddress || it == localAddress }
                .distinct()

            val estimate = uniqueIps.size + listOfNotNull(gatewayAddress).size + 1
            val summary = when {
                estimate <= 2 -> "Only a few devices are visible on this Wi-Fi right now."
                estimate <= 6 -> "This Wi-Fi has a small number of nearby devices."
                else -> "Several devices are visible on this Wi-Fi. That is common on shared networks."
            }

            return@withContext LocalNetworkSnapshot(
                visibleDeviceCount = estimate,
                summary = summary,
                confidenceLabel = "Neighbor estimate"
            )
        }

        val fallbackCount = if (gatewayAddress != null && localAddress != null) 2 else 1
        LocalNetworkSnapshot(
            visibleDeviceCount = fallbackCount,
            summary = "SecureGuard could only confirm your phone and gateway, so this is a cautious estimate.",
            confidenceLabel = "Limited estimate"
        )
    }

    private fun readArpEntries(): List<ArpEntry> {
        val arpFile = File("/proc/net/arp")
        if (!arpFile.exists() || !arpFile.canRead()) return emptyList()

        return arpFile.readLines()
            .drop(1)
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                val ip = parts.getOrNull(0)
                val mac = parts.getOrNull(3)
                if (ip.isNullOrBlank() || mac.isNullOrBlank() || mac == "00:00:00:00:00:00") {
                    null
                } else {
                    ArpEntry(ipAddress = ip, macAddress = mac)
                }
            }
    }
}

data class LocalNetworkSnapshot(
    val visibleDeviceCount: Int,
    val summary: String,
    val confidenceLabel: String
)

private data class ArpEntry(
    val ipAddress: String,
    val macAddress: String
)
