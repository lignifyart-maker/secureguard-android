package com.secureguard.app.vpn.parser

data class DnsQuestion(
    val host: String,
    val queryType: Int
) {
    val queryTypeLabel: String
        get() = when (queryType) {
            1 -> "A"
            28 -> "AAAA"
            5 -> "CNAME"
            15 -> "MX"
            else -> "TYPE$queryType"
        }
}
