package com.trustylistener.data.repository

import com.trustylistener.data.local.database.AppDatabase
import com.trustylistener.data.local.database.AudioEventEntity
import com.trustylistener.domain.model.AudioEvent
import com.trustylistener.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LogRepository using Room database
 */
@Singleton
class LogRepositoryImpl @Inject constructor(
    private val database: AppDatabase
) : LogRepository {

    private val dao = database.audioEventDao()

    override suspend fun saveEvent(event: AudioEvent): Long {
        return dao.insert(AudioEventEntity.fromDomain(event))
    }

    override fun getAllEvents(): Flow<List<AudioEvent>> {
        return dao.getAllEvents().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRecentEvents(limit: Int): List<AudioEvent> {
        return dao.getRecentEvents(limit).map { it.toDomain() }
    }

    override suspend fun getEventsByClass(className: String): List<AudioEvent> {
        return dao.getEventsByClass(className).map { it.toDomain() }
    }

    override suspend fun deleteEvent(id: Long) {
        dao.deleteEventById(id)
    }

    override suspend fun deleteAllEvents() {
        dao.deleteAllEvents()
    }

    override suspend fun exportToCsv(): String {
        val events = dao.getRecentEvents(10000) // Max 10k events
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val csv = StringBuilder()
        csv.appendLine("id,timestamp,class_name,score,audio_path")

        events.forEach { entity ->
            val date = dateFormat.format(Date(entity.timestamp))
            csv.appendLine(
                "${entity.id},${date},${entity.className},${entity.score},${entity.audioPath ?: ""}"
            )
        }

        return csv.toString()
    }

    override suspend fun getAudioPath(eventId: Long): String? {
        return dao.getEventById(eventId)?.audioPath
    }
}
