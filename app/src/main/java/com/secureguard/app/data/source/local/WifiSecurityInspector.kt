package com.secureguard.app.data.source.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.secureguard.app.domain.model.WifiSafetyLevel
import com.secureguard.app.domain.model.WifiSecuritySnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class WifiSecurityInspector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localNetworkInspector: LocalNetworkInspector
) {
    suspend fun inspect(trustedNetworks: Set<String>): WifiSecuritySnapshot = withContext(Dispatchers.IO) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val wifiActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!wifiActive) {
            return@withContext WifiSecuritySnapshot(
                isWifiActive = false,
                networkName = "Not on Wi-Fi",
                canManageTrust = false,
                isTrustedNetwork = false,
                securityLabel = "Cellular or offline",
                familiarityLabel = "Not on Wi-Fi",
                safetyLevel = WifiSafetyLevel.Safe,
                crowdLabel = "Not a shared Wi-Fi right now",
                summary = "You are not currently on Wi-Fi.",
                detail = "Public Wi-Fi risks are lower right now because your phone is not using a Wi-Fi network.",
                gatewayAddress = null,
                localAddress = null,
                permissionLimited = false,
                nearbyDeviceCount = 0,
                nearbyDeviceSummary = "No Wi-Fi neighbors to inspect right now.",
                sensitiveActionAdvice = "Sensitive actions are usually safer on cellular than open public Wi-Fi."
            )
        }

        val hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities?.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        }

        val networkName = resolveSsid(wifiInfo, hasLocationPermission)
        val canManageTrust = hasLocationPermission && networkName != "Wi-Fi network"
        val isTrustedNetwork = canManageTrust && networkName in trustedNetworks
        val securityLabel = resolveSecurityLabel(wifiInfo)
        val gatewayAddress = wifiManager.dhcpInfo?.gateway?.takeIf { it != 0 }?.let(::intToIp)
        val localAddress = wifiManager.dhcpInfo?.ipAddress?.takeIf { it != 0 }?.let(::intToIp)
        val localNetworkSnapshot = localNetworkInspector.estimateVisibleDevices(
            gatewayAddress = gatewayAddress,
            localAddress = localAddress
        )
        val crowdLabel = when {
            localNetworkSnapshot.visibleDeviceCount <= 3 -> "Feels like a smaller private network"
            localNetworkSnapshot.visibleDeviceCount <= 7 -> "Looks like a lightly shared Wi-Fi"
            else -> "Looks like a shared or busy Wi-Fi"
        }
        val familiarityLabel = when {
            isTrustedNetwork -> "Trusted by you"
            localNetworkSnapshot.visibleDeviceCount <= 3 -> "Feels familiar"
            else -> "Treat as shared"
        }

        val safetyLevel = when (securityLabel) {
            "Open network" -> WifiSafetyLevel.Risky
            "Old encryption" -> WifiSafetyLevel.Caution
            "Unknown security" -> WifiSafetyLevel.Caution
            else -> WifiSafetyLevel.Safe
        }

        val summary = if (isTrustedNetwork && safetyLevel == WifiSafetyLevel.Safe) {
            "This Wi-Fi is on your trusted list and appears protected."
        } else {
            when (safetyLevel) {
                WifiSafetyLevel.Risky -> "This Wi-Fi looks open. Avoid banking or sensitive logins here."
                WifiSafetyLevel.Caution -> "We could not fully confirm Wi-Fi protection."
                WifiSafetyLevel.Safe -> "This Wi-Fi appears to use basic protection."
                WifiSafetyLevel.Unknown -> "Wi-Fi safety could not be determined."
            }
        }

        val detail = when {
            !hasLocationPermission ->
                "Grant location permission later if you want SecureGuard to show more Wi-Fi details like the network name."
            isTrustedNetwork ->
                "You marked this network as trusted. That does not make it invulnerable, but it helps SecureGuard explain it with less alarm."
            securityLabel == "Open network" ->
                "Open Wi-Fi can make it easier for nearby attackers to observe or tamper with traffic from weaker apps."
            securityLabel == "Unknown security" ->
                "Android did not expose the current Wi-Fi protection type clearly on this device, so treat unfamiliar networks carefully."
            securityLabel == "Old encryption" ->
                "This Wi-Fi uses older protection. It is better than open Wi-Fi, but not as reassuring as a modern protected network."
            localNetworkSnapshot.visibleDeviceCount >= 8 ->
                "A busier Wi-Fi is normal in cafes, offices, and shared places, but it is a good reason to be more careful with sensitive actions."
            else ->
                "This does not guarantee nobody is snooping, but it is a better sign than an open hotspot."
        }

        val sensitiveActionAdvice = when {
            securityLabel == "Open network" ->
                "Skip banking, password changes, and other sensitive logins on this network if you can."
            securityLabel == "Old encryption" ->
                "This Wi-Fi is usable for routine browsing, but save banking and other sensitive actions for a stronger network if possible."
            isTrustedNetwork ->
                "This is one of your trusted networks, so routine use is fine. Stay careful with fake login prompts like you would anywhere else."
            localNetworkSnapshot.visibleDeviceCount >= 8 ->
                "This looks like a shared Wi-Fi, so keep sensitive actions brief or wait for a more trusted connection."
            localNetworkSnapshot.visibleDeviceCount <= 3 ->
                "This looks calmer than a public hotspot, though it is still smart to use apps with HTTPS and trusted logins."
            else ->
                "This network seems reasonably normal, but still be cautious with unfamiliar websites and login prompts."
        }

        WifiSecuritySnapshot(
            isWifiActive = true,
            networkName = networkName,
            canManageTrust = canManageTrust,
            isTrustedNetwork = isTrustedNetwork,
            securityLabel = securityLabel,
            familiarityLabel = familiarityLabel,
            safetyLevel = safetyLevel,
            crowdLabel = crowdLabel,
            summary = summary,
            detail = detail,
            gatewayAddress = gatewayAddress,
            localAddress = localAddress,
            permissionLimited = !hasLocationPermission,
            nearbyDeviceCount = localNetworkSnapshot.visibleDeviceCount,
            nearbyDeviceSummary = localNetworkSnapshot.summary,
            sensitiveActionAdvice = sensitiveActionAdvice
        )
    }

    private fun resolveSsid(
        wifiInfo: WifiInfo?,
        hasLocationPermission: Boolean
    ): String {
        if (!hasLocationPermission) return "Wi-Fi network"
        val ssid = wifiInfo?.ssid?.trim('"').orEmpty()
        if (ssid.isBlank() || ssid == WifiManager.UNKNOWN_SSID) return "Wi-Fi network"
        return ssid
    }

    private fun resolveSecurityLabel(wifiInfo: WifiInfo?): String {
        if (wifiInfo == null) return "Unknown security"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (wifiInfo.currentSecurityType) {
                WifiInfo.SECURITY_TYPE_OPEN,
                WifiInfo.SECURITY_TYPE_OWE -> "Open network"
                WifiInfo.SECURITY_TYPE_WEP -> "Old encryption"
                WifiInfo.SECURITY_TYPE_PSK,
                WifiInfo.SECURITY_TYPE_SAE,
                WifiInfo.SECURITY_TYPE_EAP,
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT,
                WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2,
                WifiInfo.SECURITY_TYPE_PASSPOINT_R3,
                WifiInfo.SECURITY_TYPE_DPP -> "Protected Wi-Fi"
                else -> "Unknown security"
            }
        }
        return "Unknown security"
    }

    private fun intToIp(value: Int): String {
        return listOf(
            value and 0xff,
            value shr 8 and 0xff,
            value shr 16 and 0xff,
            value shr 24 and 0xff
        ).joinToString(".")
    }
}
