package com.secureguard.app.domain.repository

import com.secureguard.app.core.database.entity.NetworkEventEntity
import kotlinx.coroutines.flow.Flow

interface NetworkEventRepository {
    fun observeRecentEvents(limit: Int = 20): Flow<List<NetworkEventEntity>>
}
