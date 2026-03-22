package com.secureguard.app.vpn.parser

data class UdpDatagram(
    val sourcePort: Int,
    val destinationPort: Int,
    val payloadOffset: Int,
    val payloadLength: Int
)
