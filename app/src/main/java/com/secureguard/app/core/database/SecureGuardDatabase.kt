package com.secureguard.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.secureguard.app.core.database.dao.AppScanDao
import com.secureguard.app.core.database.dao.NetworkEventDao
import com.secureguard.app.core.database.entity.AppScanEntity
import com.secureguard.app.core.database.entity.NetworkEventEntity

@Database(
    entities = [AppScanEntity::class, NetworkEventEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SecureGuardDatabase : RoomDatabase() {
    abstract fun appScanDao(): AppScanDao
    abstract fun networkEventDao(): NetworkEventDao
}
