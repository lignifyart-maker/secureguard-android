package com.secureguard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secureguard.app.core.database.entity.AppScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppScanDao {
    @Query("SELECT * FROM app_scan_results ORDER BY scannedAt DESC, riskLevel DESC, appName ASC")
    fun observeAll(): Flow<List<AppScanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AppScanEntity>)

    @Query("DELETE FROM app_scan_results")
    suspend fun clearAll()
}
