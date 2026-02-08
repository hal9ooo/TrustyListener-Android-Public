package com.trustylistener.domain.repository

import com.trustylistener.domain.model.ClassificationMode
import com.trustylistener.domain.model.ClassificationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for audio capture operations
 */
interface AudioRepository {
    /**
     * Start capturing audio from the microphone
     */
    suspend fun startRecording()

    /**
     * Stop capturing audio
     */
    suspend fun stopRecording()

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean

    /**
     * Stream of audio classification results
     */
    val classificationResults: Flow<ClassificationResult>

    /**
     * Current audio level for visualization (0.0 - 1.0)
     */
    val audioLevel: Flow<Float>
    
    /**
     * Current classification mode
     */
    val classificationMode: StateFlow<ClassificationMode>
    
    /**
     * Set classification mode (takes effect immediately without stopping recording)
     */
    fun setClassificationMode(mode: ClassificationMode)
}
