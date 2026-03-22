package com.secureguard.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "secureguard_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val lastScanTimestamp: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[lastScanTimestampKey]
    }

    suspend fun setLastScanTimestamp(value: Long) {
        context.dataStore.edit { preferences ->
            preferences[lastScanTimestampKey] = value
        }
    }

    private companion object {
        val lastScanTimestampKey = longPreferencesKey("last_scan_timestamp")
    }
}
