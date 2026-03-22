package com.secureguard.app.vpn.parser

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UdpDatagramParser @Inject constructor() {
    fun parse(buffer: ByteArray, offset: Int, availableLength: Int): UdpDatagram? {
        if (availableLength < 8 || buffer.size < offset + 8) return null

        val sourcePort = readUnsignedShort(buffer, offset)
        val destinationPort = readUnsignedShort(buffer, offset + 2)
        val length = readUnsignedShort(buffer, offset + 4)
        val payloadLength = (length - 8).coerceAtLeast(0)

        return UdpDatagram(
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            payloadOffset = offset + 8,
            payloadLength = payloadLength.coerceAtMost(availableLength - 8)
        )
    }

    private fun readUnsignedShort(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }
}
