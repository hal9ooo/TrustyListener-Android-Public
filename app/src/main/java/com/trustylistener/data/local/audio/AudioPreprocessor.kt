package com.trustylistener.data.local.audio

import kotlin.math.*

/**
 * Advanced audio preprocessor for improving YAMNet classification accuracy.
 * Implements signal processing techniques optimized for real-time audio classification.
 */
class AudioPreprocessor(
    private val sampleRate: Int = 16000
) {
    companion object {
        private const val TAG = "AudioPreprocessor"
        
        // Pre-emphasis coefficient (standard for speech/audio processing)
        private const val PRE_EMPHASIS_ALPHA = 0.97f
        
        // High-pass filter cutoff frequency (removes low-frequency rumble)
        private const val HIGH_PASS_CUTOFF_HZ = 80.0
        
        // Noise gate parameters
        private const val NOISE_FLOOR_DECAY = 0.995f  // Slow decay for noise floor estimation
        private const val NOISE_FLOOR_ATTACK = 0.1f   // Fast attack when noise increases
        private const val NOISE_GATE_THRESHOLD_DB = -40f
        private const val NOISE_GATE_RATIO = 4f       // Compression ratio below threshold
    }
    
    // State for pre-emphasis filter
    private var lastSample = 0f
    
    // State for Butterworth high-pass filter (2nd order)
    private var hpX1 = 0.0
    private var hpX2 = 0.0
    private var hpY1 = 0.0
    private var hpY2 = 0.0
    
    // Butterworth coefficients (calculated for 80Hz cutoff at 16kHz sample rate)
    private val hpA0: Double
    private val hpA1: Double
    private val hpA2: Double
    private val hpB1: Double
    private val hpB2: Double
    
    // Adaptive noise floor estimation
    private var noiseFloorRms = 0.01f
    private var isNoiseFloorInitialized = false
    
    init {
        // Calculate Butterworth high-pass filter coefficients
        val omega = 2.0 * PI * HIGH_PASS_CUTOFF_HZ / sampleRate
        val cosOmega = cos(omega)
        val sinOmega = sin(omega)
        val alpha = sinOmega / (2.0 * sqrt(2.0)) // Q = sqrt(2)/2 for Butterworth
        
        val a0 = 1.0 + alpha
        hpA0 = ((1.0 + cosOmega) / 2.0) / a0
        hpA1 = (-(1.0 + cosOmega)) / a0
        hpA2 = ((1.0 + cosOmega) / 2.0) / a0
        hpB1 = (-2.0 * cosOmega) / a0
        hpB2 = (1.0 - alpha) / a0
    }
    
    /**
     * Process audio buffer with full preprocessing pipeline (BALANCED mode).
     * Pipeline order:
     * 1. High-pass filter (remove rumble < 80Hz)
     * 2. Pre-emphasis (boost high frequencies)
     * 3. Noise gate (reduce background noise)
     */
    fun process(audioData: FloatArray): FloatArray {
        return processWithFilters(audioData, applyNoiseGate = true, noiseGateRatio = NOISE_GATE_RATIO)
    }
    
    /**
     * Process with gentler noise gate (SENSITIVE mode).
     * Better for detecting specific sounds like singing, coughs, etc.
     */
    fun processSensitive(audioData: FloatArray): FloatArray {
        return processWithFilters(audioData, applyNoiseGate = true, noiseGateRatio = 2f)
    }
    
    /**
     * Process with only high-pass and pre-emphasis, no noise gate (RAW mode).
     * Closest to original YAMNet behavior.
     */
    fun processMinimal(audioData: FloatArray): FloatArray {
        return processWithFilters(audioData, applyNoiseGate = false, noiseGateRatio = 1f)
    }
    
    private fun processWithFilters(
        audioData: FloatArray,
        applyNoiseGate: Boolean,
        noiseGateRatio: Float
    ): FloatArray {
        val result = FloatArray(audioData.size)
        
        for (i in audioData.indices) {
            var sample = audioData[i].toDouble()
            
            // 1. Butterworth high-pass filter (removes low-frequency rumble)
            val hpOutput = hpA0 * sample + hpA1 * hpX1 + hpA2 * hpX2 - hpB1 * hpY1 - hpB2 * hpY2
            hpX2 = hpX1
            hpX1 = sample
            hpY2 = hpY1
            hpY1 = hpOutput
            sample = hpOutput
            
            // 2. Pre-emphasis filter (boosts high frequencies)
            val preEmphOutput = sample.toFloat() - PRE_EMPHASIS_ALPHA * lastSample
            lastSample = sample.toFloat()
            
            result[i] = preEmphOutput
        }
        
        // 3. Apply noise gate if enabled
        return if (applyNoiseGate) {
            applyNoiseGateWithRatio(result, noiseGateRatio)
        } else {
            result
        }
    }
    
    /**
     * Applies adaptive noise gate to reduce background noise.
     * Uses RMS-based noise floor estimation with asymmetric attack/decay.
     * @param ratio Compression ratio (higher = more aggressive gating)
     */
    private fun applyNoiseGateWithRatio(audioData: FloatArray, ratio: Float): FloatArray {
        // Calculate current RMS
        var sumSquared = 0.0
        for (sample in audioData) {
            sumSquared += sample * sample
        }
        val currentRms = sqrt(sumSquared / audioData.size).toFloat()
        
        // Update noise floor with asymmetric tracking
        if (!isNoiseFloorInitialized) {
            noiseFloorRms = currentRms
            isNoiseFloorInitialized = true
        } else {
            noiseFloorRms = if (currentRms < noiseFloorRms) {
                // Noise decreased - follow quickly
                noiseFloorRms * NOISE_FLOOR_DECAY + currentRms * (1 - NOISE_FLOOR_DECAY)
            } else {
                // Noise increased - follow slowly (assume it's signal, not noise)
                noiseFloorRms * (1 - NOISE_FLOOR_ATTACK) + currentRms * NOISE_FLOOR_ATTACK
            }
        }
        
        // Convert noise floor to gate threshold (add margin above noise floor)
        val gateThreshold = noiseFloorRms * 2.0f
        
        // Apply soft noise gate
        val result = FloatArray(audioData.size)
        if (currentRms < gateThreshold) {
            // Below threshold - apply soft attenuation (not hard gate)
            val attenuation = (currentRms / gateThreshold).pow(1f / ratio)
            for (i in audioData.indices) {
                result[i] = audioData[i] * attenuation
            }
        } else {
            // Above threshold - pass through
            audioData.copyInto(result)
        }
        
        return result
    }
    
    /**
     * Get current estimated noise floor (for debugging/visualization)
     */
    fun getNoiseFloorDb(): Float {
        return if (noiseFloorRms > 0) {
            20f * log10(noiseFloorRms)
        } else {
            -100f
        }
    }
    
    /**
     * Reset filter states (call when starting new recording session)
     */
    fun reset() {
        lastSample = 0f
        hpX1 = 0.0
        hpX2 = 0.0
        hpY1 = 0.0
        hpY2 = 0.0
        noiseFloorRms = 0.01f
        isNoiseFloorInitialized = false
    }
}
