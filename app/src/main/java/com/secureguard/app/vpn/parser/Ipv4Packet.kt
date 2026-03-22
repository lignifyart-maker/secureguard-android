package com.secureguard.app.vpn.parser

data class Ipv4Packet(
    val protocolNumber: Int,
    val sourceIp: String,
    val destinationIp: String,
    val payloadOffset: Int,
    val totalLength: Int
)
