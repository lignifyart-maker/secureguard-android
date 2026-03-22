package com.secureguard.app.domain.usecase

import com.secureguard.app.domain.model.RecentConnectionItem
import com.secureguard.app.domain.model.RecentConnectionTimeline
import com.secureguard.app.domain.repository.NetworkEventRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveRecentConnectionTimelineUseCase @Inject constructor(
    private val networkEventRepository: NetworkEventRepository
) {
    operator fun invoke(limit: Int = 5): Flow<RecentConnectionTimeline> {
        return networkEventRepository.observeRecentEvents(limit).map { events ->
            RecentConnectionTimeline(
                items = events.map { event ->
                    RecentConnectionItem(
                        title = event.host ?: event.ipAddress ?: "Unknown target",
                        sourceLabel = event.appName ?: "Unknown app",
                        riskLabel = event.riskLabel
                    )
                }
            )
        }
    }
}
