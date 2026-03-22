package com.secureguard.app.vpn.parser

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TcpSegmentParser @Inject constructor() {
    fun parse(buffer: ByteArray, offset: Int, availableLength: Int): TcpSegment? {
        if (availableLength < 20 || buffer.size < offset + 20) return null

        val sourcePort = readUnsignedShort(buffer, offset)
        val destinationPort = readUnsignedShort(buffer, offset + 2)
        val dataOffset = ((buffer[offset + 12].toInt() ushr 4) and 0x0F) * 4
        if (dataOffset < 20 || dataOffset > availableLength) return null

        val flags = buffer[offset + 13].toInt() and 0xFF
        return TcpSegment(
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            isSyn = flags and 0x02 != 0,
            isAck = flags and 0x10 != 0
        )
    }

    private fun readUnsignedShort(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }
}
