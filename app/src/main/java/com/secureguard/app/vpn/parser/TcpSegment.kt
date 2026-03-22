package com.secureguard.app.vpn.parser

data class TcpSegment(
    val sourcePort: Int,
    val destinationPort: Int,
    val isSyn: Boolean,
    val isAck: Boolean
)
