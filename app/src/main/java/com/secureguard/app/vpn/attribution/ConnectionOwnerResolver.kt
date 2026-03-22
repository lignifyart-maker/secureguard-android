package com.secureguard.app.vpn.attribution

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.system.OsConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.LinkedHashMap
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

    private val recentAttributionCache =
        object : LinkedHashMap<String, CachedAttribution>(RECENT_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedAttribution>?): Boolean {
                return size > RECENT_CACHE_SIZE
            }
        }

    fun resolveUdpOwner(
        localIp: String,
        localPort: Int,
        remoteIp: String,
        remotePort: Int
    ): AppAttribution {
        val cacheKey = cacheKey(localIp, localPort)
        val localAddress = runCatching { InetAddress.getByName(localIp) }.getOrNull()
        val remoteAddress = runCatching { InetAddress.getByName(remoteIp) }.getOrNull()
        if (localAddress == null || remoteAddress == null) {
            return cachedAttribution(cacheKey) ?: AppAttribution(
                packageName = null,
                appName = "Unknown app",
                confidenceLabel = "Address parse failed"
            )
        }

        val local = InetSocketAddress(localAddress, localPort)
        val remote = InetSocketAddress(remoteAddress, remotePort)
        val uid = runCatching {
            connectivityManager.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote)
        }.getOrDefault(Process.INVALID_UID)

        if (uid == Process.INVALID_UID) {
            return cachedAttribution(cacheKey) ?: AppAttribution(
                packageName = null,
                appName = "Unknown app",
                confidenceLabel = "Owner not mapped yet"
            )
        }

        val packageName = packageManager.getPackagesForUid(uid)?.firstOrNull()
        if (packageName == null) {
            val attribution = AppAttribution(
                packageName = null,
                appName = "UID $uid",
                confidenceLabel = "UID resolved without package"
            )
            rememberAttribution(cacheKey, attribution)
            return attribution
        }

        val appName = packageName
            .let { safeAppLabel(it) }

        val attribution = AppAttribution(
            packageName = packageName,
            appName = appName,
            confidenceLabel = "Mapped from Android owner lookup"
        )
        rememberAttribution(cacheKey, attribution)
        return attribution
    }

    private fun safeAppLabel(packageName: String): String {
        return runCatching {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(packageName)
    }

    private fun cacheKey(localIp: String, localPort: Int): String = "$localIp:$localPort"

    @Synchronized
    private fun rememberAttribution(key: String, attribution: AppAttribution) {
        recentAttributionCache[key] = CachedAttribution(
            attribution = attribution,
            createdAt = System.currentTimeMillis()
        )
    }

    @Synchronized
    private fun cachedAttribution(key: String): AppAttribution? {
        val cached = recentAttributionCache[key] ?: return null
        if (System.currentTimeMillis() - cached.createdAt > CACHE_TTL_MS) {
            recentAttributionCache.remove(key)
            return null
        }
        return cached.attribution.copy(confidenceLabel = "Matched from recent port history")
    }

    private data class CachedAttribution(
        val attribution: AppAttribution,
        val createdAt: Long
    )

    private object Process {
        const val INVALID_UID = -1
    }

    private companion object {
        const val CACHE_TTL_MS = 15_000L
        const val RECENT_CACHE_SIZE = 64
    }
}
