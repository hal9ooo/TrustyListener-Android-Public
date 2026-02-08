package com.trustylistener.data.local.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for audio events
 */
@Dao
interface AudioEventDao {
    @Insert
    suspend fun insert(event: AudioEventEntity): Long

    @Query("SELECT * FROM audio_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<AudioEventEntity>>

    @Query("SELECT * FROM audio_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<AudioEventEntity>

    @Query("SELECT * FROM audio_events WHERE className = :className ORDER BY timestamp DESC")
    suspend fun getEventsByClass(className: String): List<AudioEventEntity>

    @Query("SELECT * FROM audio_events WHERE id = :id")
    suspend fun getEventById(id: Long): AudioEventEntity?

    @Delete
    suspend fun deleteEvent(event: AudioEventEntity)

    @Query("DELETE FROM audio_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    @Query("DELETE FROM audio_events")
    suspend fun deleteAllEvents()

    @Query("SELECT COUNT(*) FROM audio_events")
    suspend fun getEventCount(): Int
}
