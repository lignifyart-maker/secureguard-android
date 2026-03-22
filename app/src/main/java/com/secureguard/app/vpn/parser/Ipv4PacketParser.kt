package com.secureguard.app.vpn.parser

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Ipv4PacketParser @Inject constructor() {
    fun parse(buffer: ByteArray, length: Int): Ipv4Packet? {
        if (length < 20) return null
        val version = (buffer[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null

        val headerLength = (buffer[0].toInt() and 0x0F) * 4
        if (headerLength < 20 || length < headerLength) return null

        val totalLength = ((buffer[2].toInt() and 0xFF) shl 8) or (buffer[3].toInt() and 0xFF)
        val protocolNumber = buffer[9].toInt() and 0xFF

        return Ipv4Packet(
            protocolNumber = protocolNumber,
            sourceIp = ipv4At(buffer, 12),
            destinationIp = ipv4At(buffer, 16),
            payloadOffset = headerLength,
            totalLength = totalLength.coerceAtMost(length)
        )
    }

    private fun ipv4At(buffer: ByteArray, offset: Int): String {
        return listOf(
            buffer[offset].toInt() and 0xFF,
            buffer[offset + 1].toInt() and 0xFF,
            buffer[offset + 2].toInt() and 0xFF,
            buffer[offset + 3].toInt() and 0xFF
        ).joinToString(".")
    }
}
