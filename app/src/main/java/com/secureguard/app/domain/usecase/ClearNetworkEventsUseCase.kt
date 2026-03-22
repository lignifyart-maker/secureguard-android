package com.secureguard.app.domain.usecase

import com.secureguard.app.domain.repository.NetworkEventRepository
import javax.inject.Inject

class ClearNetworkEventsUseCase @Inject constructor(
    private val networkEventRepository: NetworkEventRepository
) {
    suspend operator fun invoke() {
        networkEventRepository.clearAll()
    }
}
