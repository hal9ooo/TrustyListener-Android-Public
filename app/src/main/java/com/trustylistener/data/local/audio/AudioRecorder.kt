package com.trustylistener.data.local.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.trustylistener.domain.model.ClassificationMode

/**
 * Audio recorder using Android's AudioRecord API
 * Optimized for continuous streaming with YAMNet
 */
@Singleton
class AudioRecorder @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        const val BUFFER_SIZE_FACTOR = 2

        // YAMNet expects ~0.975s windows
        const val SAMPLES_PER_WINDOW = 15600
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    
    // Advanced audio preprocessor
    private val audioPreprocessor = AudioPreprocessor(SAMPLE_RATE)

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    
    // Current classification mode
    private var classificationMode = ClassificationMode.BALANCED

    private var audioBuffer = FloatArray(SAMPLES_PER_WINDOW)
    private var bufferIndex = 0

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Set the classification mode for audio processing
     */
    fun setClassificationMode(mode: ClassificationMode) {
        classificationMode = mode
        Log.d(TAG, "Classification mode set to: $mode")
    }
    
    /**
     * Get the current classification mode
     */
    fun getClassificationMode(): ClassificationMode = classificationMode

    @SuppressLint("MissingPermission")
    fun startRecording(
        onAudioChunk: (FloatArray) -> Unit
    ): Boolean {
        if (!hasPermission()) {
            Log.e(TAG, "Recording permission not granted")
            return false
        }

        if (_isRecording.value) {
            Log.w(TAG, "Already recording")
            return true
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // Better for ML: disables AGC and NS
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            return false
        }

        audioRecord?.startRecording()
        _isRecording.value = true

        // Reset buffer
        audioBuffer = FloatArray(SAMPLES_PER_WINDOW)
        bufferIndex = 0
        audioPreprocessor.reset()

        recordingJob = scope.launch {
            val chunkBuffer = FloatArray(SAMPLES_PER_WINDOW)

            while (isActive && _isRecording.value) {
                try {
                    // Read audio data
                    val readResult = audioRecord?.read(
                        chunkBuffer, 0, chunkBuffer.size,
                        AudioRecord.READ_BLOCKING
                    ) ?: 0

                    if (readResult > 0) {
                        // Calculate audio level for visualization
                        val rms = calculateRMS(chunkBuffer, readResult)
                        _audioLevel.value = rms

                        // Copy to our sliding window buffer
                        processAudioChunk(chunkBuffer, readResult, onAudioChunk)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading audio", e)
                    delay(10)
                }
            }
        }

        Log.d(TAG, "Recording started with Hanning window and Peak Normalization")
        return true
    }

    private fun processAudioChunk(
        chunk: FloatArray,
        length: Int,
        onWindowReady: (FloatArray) -> Unit
    ) {
        for (i in 0 until length) {
            audioBuffer[bufferIndex] = chunk[i]
            bufferIndex++

            // When buffer is full, emit and slide
            if (bufferIndex >= SAMPLES_PER_WINDOW) {
                // 0. Mode-aware Preprocessing
                val preprocessedBuffer = when (classificationMode) {
                    ClassificationMode.BALANCED -> audioPreprocessor.process(audioBuffer)
                    ClassificationMode.SENSITIVE -> audioPreprocessor.processSensitive(audioBuffer)
                    ClassificationMode.RAW -> audioPreprocessor.processMinimal(audioBuffer)
                }
                
                // 1. DC Offset Removal
                var sumOffset = 0.0f
                for (sample in preprocessedBuffer) sumOffset += sample
                val mean = sumOffset / SAMPLES_PER_WINDOW
                val dcRemovedBuffer = FloatArray(SAMPLES_PER_WINDOW) { i -> preprocessedBuffer[i] - mean }

                // 2. Peak Normalization
                // Find max amplitude in current window
                var maxAbs = 0.0f
                for (sample in dcRemovedBuffer) {
                    val abs = Math.abs(sample)
                    if (abs > maxAbs) maxAbs = abs
                }

                // Create processed copy
                val processedBuffer = FloatArray(SAMPLES_PER_WINDOW)
                val gain = if (maxAbs > 0.001f) Math.min(1.0f / maxAbs, 5.0f) else 1.0f
                
                for (j in 0 until SAMPLES_PER_WINDOW) {
                    // Apply gain only (No hanning window here, YAMNet does it internally per-frame)
                    processedBuffer[j] = dcRemovedBuffer[j] * gain
                }

                // Emit processed window
                onWindowReady(processedBuffer)

                // Slide buffer by 75% for better temporal resolution (Stride of 0.25s)
                val stride = SAMPLES_PER_WINDOW / 4
                val overlap = SAMPLES_PER_WINDOW - stride
                System.arraycopy(audioBuffer, stride, audioBuffer, 0, overlap)
                bufferIndex = overlap
            }
        }
    }

    private fun calculateRMS(buffer: FloatArray, length: Int): Float {
        var sum = 0.0f
        for (i in 0 until length) {
            sum += buffer[i] * buffer[i]
        }
        val rms = kotlin.math.sqrt(sum / length)
        return (rms / 0.5f).coerceIn(0f, 1f)
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null

        _isRecording.value = false
        _audioLevel.value = 0f

        Log.d(TAG, "Recording stopped")
    }

    /**
     * Save audio buffer to WAV file
     */
    fun saveToWav(audioData: FloatArray, outputFile: File) {
        outputFile.parentFile?.mkdirs()

        // Convert float to 16-bit PCM
        val pcmData = ShortArray(audioData.size)
        for (i in audioData.indices) {
            val sample = (audioData[i] * 32767).toInt()
            pcmData[i] = sample.coerceIn(-32768, 32767).toShort()
        }

        outputFile.outputStream().use { output ->
            // WAV header
            writeWavHeader(output, pcmData.size * 2)

            // PCM data
            pcmData.forEach { sample ->
                output.write(sample.toInt() and 0xFF)
                output.write((sample.toInt() shr 8) and 0xFF)
            }
        }
    }

    private fun writeWavHeader(output: java.io.OutputStream, pcmDataLength: Int) {
        val totalDataLen = pcmDataLength + 36
        val byteRate = SAMPLE_RATE * 2 // 16-bit mono

        output.write("RIFF".toByteArray())
        writeInt(output, totalDataLen)
        output.write("WAVE".toByteArray())
        output.write("fmt ".toByteArray())
        writeInt(output, 16) // Subchunk1Size
        writeShort(output, 1) // AudioFormat (PCM)
        writeShort(output, 1) // NumChannels (mono)
        writeInt(output, SAMPLE_RATE)
        writeInt(output, byteRate)
        writeShort(output, 2) // BlockAlign
        writeShort(output, 16) // BitsPerSample
        output.write("data".toByteArray())
        writeInt(output, pcmDataLength)
    }

    private fun writeInt(output: java.io.OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
        output.write((value shr 16) and 0xFF)
        output.write((value shr 24) and 0xFF)
    }

    private fun writeShort(output: java.io.OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
    }

    fun release() {
        stopRecording()
        scope.cancel()
    }
}
