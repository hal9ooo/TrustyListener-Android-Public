package com.trustylistener.domain.usecase

import com.trustylistener.domain.repository.LogRepository
import javax.inject.Inject

/**
 * Use case for exporting logs to different formats
 */
class ExportLogsUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    /**
     * Export all events to CSV format
     */
    suspend operator fun invoke(): String {
        return logRepository.exportToCsv()
    }

    /**
     * Get audio file path for an event
     */
    suspend fun getAudioPath(eventId: Long): String? {
        return logRepository.getAudioPath(eventId)
    }

    /**
     * Delete all logs
     */
    suspend fun clearAll() {
        logRepository.deleteAllEvents()
    }
}
