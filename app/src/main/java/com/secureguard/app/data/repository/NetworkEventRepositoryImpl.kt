package com.secureguard.app.data.repository

import com.secureguard.app.core.database.dao.NetworkEventDao
import com.secureguard.app.core.database.entity.NetworkEventEntity
import com.secureguard.app.domain.repository.NetworkEventRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class NetworkEventRepositoryImpl @Inject constructor(
    private val networkEventDao: NetworkEventDao
) : NetworkEventRepository {
    override fun observeRecentEvents(limit: Int): Flow<List<NetworkEventEntity>> {
        return networkEventDao.observeRecent(limit)
    }

    override suspend fun clearAll() {
        networkEventDao.clearAll()
    }
}
