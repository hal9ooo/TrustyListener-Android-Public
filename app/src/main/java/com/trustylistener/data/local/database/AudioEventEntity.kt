package com.trustylistener.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trustylistener.domain.model.AudioEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room entity for audio events
 */
@Entity(tableName = "audio_events")
data class AudioEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val className: String,
    val score: Float,
    val audioPath: String?,
    val metadataJson: String // JSON string for Map<String, Float>
) {
    companion object {
        fun fromDomain(event: AudioEvent): AudioEventEntity {
            return AudioEventEntity(
                id = event.id,
                timestamp = event.timestamp,
                className = event.className,
                score = event.score,
                audioPath = event.audioPath,
                metadataJson = Json.encodeToString(event.metadata)
            )
        }
    }

    fun toDomain(): AudioEvent {
        return AudioEvent(
            id = id,
            timestamp = timestamp,
            className = className,
            score = score,
            audioPath = audioPath,
            metadata = Json.decodeFromString(metadataJson)
        )
    }
}
