package com.trustylistener.domain.usecase

import com.trustylistener.domain.model.AudioEvent
import com.trustylistener.domain.model.ClassificationResult
import com.trustylistener.domain.repository.AudioRepository
import com.trustylistener.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

/**
 * Use case for starting audio listening and logging significant events
 */
class StartListeningUseCase @Inject constructor(
    private val audioRepository: AudioRepository,
    private val logRepository: LogRepository
) {
    /**
     * Start listening and return flow of detected events
     */
    operator fun invoke(threshold: Float): Flow<AudioEvent> {
        return audioRepository.classificationResults
            .filter { it.isSignificant(threshold) }
            .map { result ->
                val event = AudioEvent(
                    timestamp = System.currentTimeMillis(),
                    className = result.topClass,
                    score = result.topScore,
                    metadata = result.predictions
                )
                // Save to database and get ID
                val id = logRepository.saveEvent(event)
                event.copy(id = id)
            }
    }

    suspend fun start() = audioRepository.startRecording()
    suspend fun stop() = audioRepository.stopRecording()
    fun isRecording() = audioRepository.isRecording()
}
