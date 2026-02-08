package com.trustylistener.data.repository

import android.content.Context
import android.util.Log
import com.trustylistener.data.local.audio.AudioRecorder
import com.trustylistener.data.ml.YAMNetClassifier
import com.trustylistener.domain.model.ClassificationMode
import com.trustylistener.domain.model.ClassificationResult
import com.trustylistener.domain.repository.AudioRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Implementation of AudioRepository combining AudioRecorder and YAMNet
 */
@Singleton
class AudioRepositoryImpl @Inject constructor(
    private val context: Context,
    private val audioRecorder: AudioRecorder,
    private val classifier: YAMNetClassifier
) : AudioRepository {

    private val _classificationResults = MutableStateFlow<ClassificationResult?>(null)
    override val classificationResults: Flow<ClassificationResult> = 
        _classificationResults.filterNotNull()


    override val audioLevel: StateFlow<Float> = audioRecorder.audioLevel

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var classificationJob: Job? = null

    override suspend fun startRecording() {
        if (classifier.initializationError.value != null) {
            Log.e("AudioRepository", "Cannot start recording: ${classifier.initializationError.value}")
            return
        }

        if (!classifier.isInitialized.value) {
            // Wait for classifier to initialize or fail
            Log.d("AudioRepository", "Waiting for classifier initialization...")
            // We wait until either it becomes initialized OR an error appears
            while (!classifier.isInitialized.value && classifier.initializationError.value == null) {
                delay(100)
            }
            
            if (classifier.initializationError.value != null) {
                Log.e("AudioRepository", "Initialization failed, aborting record: ${classifier.initializationError.value}")
                return
            }
        }

        val audioDir = File(context.filesDir, "audio_clips").apply { mkdirs() }

        audioRecorder.startRecording { audioChunk ->
            // Classify in background with current mode
            classificationJob?.cancel()
            classificationJob = scope.launch {
                val mode = audioRecorder.getClassificationMode()
                val result = classifier.classify(audioChunk, mode)
                result?.let {
                    (_classificationResults as MutableStateFlow).value = it
                }
            }
        }
    }

    override suspend fun stopRecording() {
        classificationJob?.cancel()
        audioRecorder.stopRecording()
    }

    override fun isRecording(): Boolean = audioRecorder.isRecording.value
    
    // Classification mode support (real-time, no restart needed)
    private val _classificationMode = MutableStateFlow(ClassificationMode.BALANCED)
    override val classificationMode: StateFlow<ClassificationMode> = _classificationMode.asStateFlow()
    
    override fun setClassificationMode(mode: ClassificationMode) {
        _classificationMode.value = mode
        audioRecorder.setClassificationMode(mode)
        Log.d("AudioRepository", "Classification mode changed to: $mode (real-time)")
    }

    fun saveAudioClip(audioData: FloatArray, fileName: String): String {
        val file = File(context.filesDir, "audio_clips/$fileName")
        audioRecorder.saveToWav(audioData, file)
        return file.absolutePath
    }

    fun release() {
        scope.cancel()
        audioRecorder.release()
        classifier.close()
    }
}
