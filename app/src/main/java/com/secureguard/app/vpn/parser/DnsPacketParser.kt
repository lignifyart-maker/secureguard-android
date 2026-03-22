package com.secureguard.app.vpn.parser

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsPacketParser @Inject constructor() {
    fun parseQuestion(buffer: ByteArray, offset: Int, length: Int): DnsQuestion? {
        if (length < 12 || buffer.size < offset + 12) return null
        val questionCount = readUnsignedShort(buffer, offset + 4)
        if (questionCount <= 0) return null

        var cursor = offset + 12
        val labels = mutableListOf<String>()
        while (cursor < offset + length) {
            val labelLength = buffer[cursor].toInt() and 0xFF
            if (labelLength == 0) {
                cursor += 1
                break
            }
            if (labelLength and 0xC0 != 0) return null
            if (cursor + 1 + labelLength > offset + length) return null
            labels += buffer.copyOfRange(cursor + 1, cursor + 1 + labelLength).decodeToString()
            cursor += 1 + labelLength
        }

        if (cursor + 4 > offset + length) return null
        val queryType = readUnsignedShort(buffer, cursor)

        return DnsQuestion(
            host = labels.joinToString("."),
            queryType = queryType
        )
    }

    private fun readUnsignedShort(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }
}
