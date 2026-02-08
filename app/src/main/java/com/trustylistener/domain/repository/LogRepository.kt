package com.trustylistener.domain.repository

import com.trustylistener.domain.model.AudioEvent
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for event logging operations
 */
interface LogRepository {
    /**
     * Save an audio event to the database
     */
    suspend fun saveEvent(event: AudioEvent): Long

    /**
     * Get all events as a Flow for reactive updates
     */
    fun getAllEvents(): Flow<List<AudioEvent>>

    /**
     * Get recent events with limit
     */
    suspend fun getRecentEvents(limit: Int = 100): List<AudioEvent>

    /**
     * Get events by class name
     */
    suspend fun getEventsByClass(className: String): List<AudioEvent>

    /**
     * Delete a specific event
     */
    suspend fun deleteEvent(id: Long)

    /**
     * Delete all events
     */
    suspend fun deleteAllEvents()

    /**
     * Export events to CSV format
     */
    suspend fun exportToCsv(): String

    /**
     * Get the audio file path for an event
     */
    suspend fun getAudioPath(eventId: Long): String?
}
