package com.trustylistener.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model for an audio event detected by YAMNet
 */
@Serializable
data class AudioEvent(
    val id: Long = 0,
    val timestamp: Long,
    val className: String,
    val score: Float,
    val audioPath: String? = null,
    val metadata: Map<String, Float> = emptyMap()
) {
    companion object {
        const val DEFAULT_THRESHOLD = 0.3f
    }
}

/**
 * Classification result from YAMNet
 */
@Serializable
data class ClassificationResult(
    val topClass: String,
    val topScore: Float,
    val predictions: Map<String, Float>,
    val confidenceQuality: Float = 1f,    // 0-1, based on entropy and margin
    val ensembleAgreement: Float = 1f     // % of windows that agree on top class
) {
    companion object {
        private val IGNORED_CLASSES = setOf(
            "Silence",
            "Static",
            "White noise",
            "Pink noise"
        )
    }

    fun isSignificant(threshold: Float = AudioEvent.DEFAULT_THRESHOLD): Boolean {
        return !IGNORED_CLASSES.contains(topClass) && topScore >= threshold
    }
}

/**
 * Represents the current state of the audio listener
 */
sealed class ListeningState {
    data object Idle : ListeningState()
    data object Listening : ListeningState()
    data class Detected(val event: AudioEvent) : ListeningState()
    data class Error(val message: String) : ListeningState()
}
