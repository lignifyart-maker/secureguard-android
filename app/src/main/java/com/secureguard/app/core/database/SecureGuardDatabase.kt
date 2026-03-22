package com.secureguard.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.secureguard.app.core.database.dao.AppScanDao
import com.secureguard.app.core.database.entity.AppScanEntity

@Database(
    entities = [AppScanEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SecureGuardDatabase : RoomDatabase() {
    abstract fun appScanDao(): AppScanDao
}
