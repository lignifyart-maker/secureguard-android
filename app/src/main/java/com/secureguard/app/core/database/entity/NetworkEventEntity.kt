package com.secureguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_events")
data class NetworkEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String?,
    val appName: String?,
    val host: String?,
    val ipAddress: String?,
    val protocol: String,
    val eventType: String,
    val riskLabel: String,
    val createdAt: Long
)
