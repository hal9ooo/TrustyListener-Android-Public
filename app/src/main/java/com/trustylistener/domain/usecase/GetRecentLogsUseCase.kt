package com.trustylistener.domain.usecase

import com.trustylistener.domain.model.AudioEvent
import com.trustylistener.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for retrieving recent audio events
 */
class GetRecentLogsUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    /**
     * Get all events as a reactive Flow
     */
    operator fun invoke(): Flow<List<AudioEvent>> {
        return logRepository.getAllEvents()
    }

    /**
     * Get recent events with limit
     */
    suspend fun getRecent(limit: Int = 100): List<AudioEvent> {
        return logRepository.getRecentEvents(limit)
    }
}
