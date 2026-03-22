package com.secureguard.app.core.di

import com.secureguard.app.core.database.dao.NetworkEventDao
import com.secureguard.app.vpn.parser.DnsPacketParser
import com.secureguard.app.vpn.parser.Ipv4PacketParser
import com.secureguard.app.vpn.parser.UdpDatagramParser
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VpnRuntimeEntryPoint {
    fun networkEventDao(): NetworkEventDao
    fun ipv4PacketParser(): Ipv4PacketParser
    fun udpDatagramParser(): UdpDatagramParser
    fun dnsPacketParser(): DnsPacketParser
}
