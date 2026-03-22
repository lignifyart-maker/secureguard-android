package com.secureguard.app.vpn.attribution

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.system.OsConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionOwnerResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager: ConnectivityManager
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val packageManager: PackageManager
        get() = context.packageManager

    fun resolveUdpOwner(
        sourceIp: String,
        sourcePort: Int,
        destinationIp: String,
        destinationPort: Int
    ): AppAttribution {
        val local = InetSocketAddress(InetAddress.getByName(sourceIp), sourcePort)
        val remote = InetSocketAddress(InetAddress.getByName(destinationIp), destinationPort)
        val uid = runCatching {
            connectivityManager.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote)
        }.getOrDefault(Process.INVALID_UID)

        if (uid == Process.INVALID_UID) {
            return AppAttribution(
                packageName = null,
                appName = "Unknown app",
                confidenceLabel = "Owner not mapped yet"
            )
        }

        val packageName = packageManager.getPackagesForUid(uid)?.firstOrNull()
        val appName = packageName
            ?.let { safeAppLabel(it) }
            ?: "UID $uid"

        return AppAttribution(
            packageName = packageName,
            appName = appName,
            confidenceLabel = "Mapped from Android owner lookup"
        )
    }

    private fun safeAppLabel(packageName: String): String {
        return runCatching {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(packageName)
    }

    private object Process {
        const val INVALID_UID = -1
    }
}
