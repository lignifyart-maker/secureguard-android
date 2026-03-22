package com.secureguard.app.vpn.parser

data class DnsQuestion(
    val host: String,
    val queryType: Int
)
