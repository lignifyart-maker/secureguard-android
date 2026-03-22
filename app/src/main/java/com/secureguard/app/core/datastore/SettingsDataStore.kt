package com.secureguard.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "secureguard_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val lastScanTimestamp: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[lastScanTimestampKey]
    }

    val trustedWifiNetworks: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[trustedWifiNetworksKey] ?: emptySet()
    }

    suspend fun setLastScanTimestamp(value: Long) {
        context.dataStore.edit { preferences ->
            preferences[lastScanTimestampKey] = value
        }
    }

    suspend fun getTrustedWifiNetworks(): Set<String> = trustedWifiNetworks.first()

    suspend fun setWifiTrusted(ssid: String, trusted: Boolean) {
        if (ssid.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[trustedWifiNetworksKey].orEmpty().toMutableSet()
            if (trusted) {
                current += ssid
            } else {
                current -= ssid
            }
            preferences[trustedWifiNetworksKey] = current
        }
    }

    private companion object {
        val lastScanTimestampKey = longPreferencesKey("last_scan_timestamp")
        val trustedWifiNetworksKey = stringSetPreferencesKey("trusted_wifi_networks")
    }
}
