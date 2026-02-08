package com.trustylistener.data.ml

import android.content.Context
import android.util.Log
import com.trustylistener.domain.model.ClassificationMode
import com.trustylistener.domain.model.ClassificationResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

import kotlin.math.*

/**
 * YAMNet audio classifier using TensorFlow Lite
 * Enhanced with multi-window ensemble and confidence-weighted fusion
 */
@Singleton
class YAMNetClassifier @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "YAMNetClassifier"
        private const val MODEL_FILE = "yamnet.tflite"
        private const val SAMPLE_RATE = 16000
        private const val NUM_CLASSES = 521
        
        // Mode configurations
        // BALANCED mode
        private const val BALANCED_WINDOWS = 3
        private val BALANCED_OFFSETS = intArrayOf(0, 3900, 7800)
        private const val BALANCED_EMA_ALPHA = 0.6f
        
        // SENSITIVE mode (better for specific sounds)
        private const val SENSITIVE_WINDOWS = 2
        private val SENSITIVE_OFFSETS = intArrayOf(0, 3900)
        private const val SENSITIVE_EMA_ALPHA = 0.75f
        
        // RAW mode (no ensemble, no smoothing)
        private const val RAW_WINDOWS = 1
        private val RAW_OFFSETS = intArrayOf(0)
        private const val RAW_EMA_ALPHA = 1.0f  // No smoothing
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val classNames: List<String> by lazy { loadClassNames() }
    private val mutex = kotlinx.coroutines.sync.Mutex()

    // Exponential moving average for temporal smoothing
    private var emaScores: FloatArray? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized
    
    private val _initializationError = MutableStateFlow<String?>(null)
    val initializationError: StateFlow<String?> = _initializationError

    init {
        initialize()
    }

    private fun initialize() {
        try {
            _initializationError.value = null
            val modelBuffer = loadModelFile()
            
            // Check for TFLite signature (TFL3 at offset 4)
            if (modelBuffer.capacity() < 8 || 
                modelBuffer.get(4).toInt().toChar() != 'T' || 
                modelBuffer.get(5).toInt().toChar() != 'F' || 
                modelBuffer.get(6).toInt().toChar() != 'L' || 
                modelBuffer.get(7).toInt().toChar() != '3') {
                throw IllegalArgumentException("Il file assets/yamnet.tflite non è un modello TFLite valido. Assicurati di aver scaricato la versione .tflite e non quella per PC (.pb).")
            }

            val options = Interpreter.Options().apply {
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    Log.d(TAG, "GPU delegate supported, enabling...")
                    try {
                        gpuDelegate = GpuDelegate()
                        addDelegate(gpuDelegate)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to initialize GPU delegate, falling back to CPU", t)
                        gpuDelegate = null
                    }
                }
                setNumThreads(4)
                setUseXNNPACK(true)
            }

            interpreter = Interpreter(modelBuffer, options)

            // Log model info for debugging
            logModelTensors()

            _isInitialized.value = true
            Log.d(
                TAG,
                "YAMNet initialized successfully with ${if (gpuDelegate != null) "GPU" else "CPU"}"
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize YAMNet", e)
            _initializationError.value = e.message ?: e.toString()
            _isInitialized.value = false
        }
    }

    private fun logModelTensors() {
        interpreter?.let { tflite ->
            val inputCount = tflite.inputTensorCount
            val outputCount = tflite.outputTensorCount
            Log.d(TAG, "Model info: Inputs=$inputCount, Outputs=$outputCount")
            for (i in 0 until inputCount) {
                val tensor = tflite.getInputTensor(i)
                Log.d(
                    TAG,
                    "Input $i: ${tensor.name()}, Shape=${
                        tensor.shape().contentToString()
                    }, Type=${tensor.dataType()}"
                )
            }
            for (i in 0 until outputCount) {
                val tensor = tflite.getOutputTensor(i)
                Log.d(
                    TAG,
                    "Output $i: ${tensor.name()}, Shape=${
                        tensor.shape().contentToString()
                    }, Type=${tensor.dataType()}"
                )
            }
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadClassNames(): List<String> {
        return try {
            context.assets.open("yamnet_class_map.csv").bufferedReader().useLines { lines ->
                lines.drop(1) // Skip header
                    .map { line ->
                        val parts = line.split(",")
                        parts.getOrNull(2)?.trim('"') ?: "Unknown"
                    }
                    .toList()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load class names", e)
            emptyList()
        }
    }

    /**
     * Classify audio waveform using mode-specific configuration
     * @param audioData FloatArray of audio samples at 16kHz (15600 samples = ~0.975s)
     * @param mode Classification mode (BALANCED, SENSITIVE, or RAW)
     * @return ClassificationResult with top class, scores, and quality metrics
     */
    suspend fun classify(
        audioData: FloatArray,
        mode: ClassificationMode = ClassificationMode.BALANCED
    ): ClassificationResult? {
        val tflite = interpreter ?: return null
        
        // Get mode-specific configuration
        val (windowOffsets, emaAlpha) = when (mode) {
            ClassificationMode.BALANCED -> BALANCED_OFFSETS to BALANCED_EMA_ALPHA
            ClassificationMode.SENSITIVE -> SENSITIVE_OFFSETS to SENSITIVE_EMA_ALPHA
            ClassificationMode.RAW -> RAW_OFFSETS to RAW_EMA_ALPHA
        }

        return mutex.withLock {
            try {
                // Multi-window ensemble: run inference on overlapping windows
                val ensembleScores = mutableListOf<FloatArray>()
                val windowSize = 15600  // YAMNet window size
                
                for (offset in windowOffsets) {
                    // Extract window with padding if necessary
                    val window = extractWindow(audioData, offset, windowSize)
                    
                    // Run single inference
                    val scores = runSingleInference(tflite, window)
                    if (scores != null) {
                        ensembleScores.add(scores)
                    }
                }
                
                if (ensembleScores.isEmpty()) return@withLock null
                
                // Fuse ensemble results (only if more than one window)
                val fusedScores = if (ensembleScores.size > 1) {
                    fuseEnsembleScores(ensembleScores)
                } else {
                    ensembleScores[0]
                }
                
                // Apply exponential moving average for temporal stability (skip if alpha=1)
                val smoothedScores = if (emaAlpha < 1.0f) {
                    applyEMAWithAlpha(fusedScores, emaAlpha)
                } else {
                    fusedScores  // RAW mode: no smoothing
                }
                
                // Calculate ensemble agreement
                val agreement = if (ensembleScores.size > 1) {
                    calculateEnsembleAgreement(ensembleScores)
                } else {
                    1f  // Single window always agrees
                }
                
                // Calculate confidence quality based on entropy and margin
                val quality = calculateConfidenceQuality(smoothedScores)
                
                // Process final results
                processScores(smoothedScores, quality, agreement)
            } catch (e: Exception) {
                Log.e(TAG, "Inference failed: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Extract a window from audio data with zero-padding if needed
     */
    private fun extractWindow(audioData: FloatArray, offset: Int, size: Int): FloatArray {
        val window = FloatArray(size)
        val availableFromOffset = maxOf(0, audioData.size - offset)
        val copyLength = minOf(size, availableFromOffset)
        
        if (offset < audioData.size && copyLength > 0) {
            System.arraycopy(audioData, offset, window, 0, copyLength)
        }
        // Rest remains zero (zero-padding)
        return window
    }
    
    /**
     * Run single inference on TFLite interpreter
     */
    private fun runSingleInference(tflite: Interpreter, audioData: FloatArray): FloatArray? {
        return try {
            val inputShape = tflite.getInputTensor(0).shape()
            val input: Any = if (inputShape.size > 1 && inputShape[0] == 1) {
                arrayOf(audioData)
            } else {
                audioData
            }

            val outputs = mutableMapOf<Int, Any>()
            val scores = Array(1) { FloatArray(NUM_CLASSES) }
            outputs[0] = scores

            if (tflite.outputTensorCount > 1) {
                outputs[1] = Array(1) { FloatArray(1024) }
            }
            if (tflite.outputTensorCount > 2) {
                outputs[2] = Array(1) { Array(64) { FloatArray(96) } }
            }

            tflite.runForMultipleInputsOutputs(arrayOf(input), outputs)
            scores[0]
        } catch (e: Exception) {
            Log.e(TAG, "Single inference failed", e)
            null
        }
    }
    
    /**
     * Fuse multiple window scores with confidence-weighted averaging
     */
    private fun fuseEnsembleScores(ensembleScores: List<FloatArray>): FloatArray {
        if (ensembleScores.size == 1) return ensembleScores[0]
        
        // Calculate confidence weight for each window based on max score
        val weights = ensembleScores.map { scores ->
            scores.maxOrNull() ?: 0f
        }
        val totalWeight = weights.sum().coerceAtLeast(0.001f)
        
        // Weighted average
        val fused = FloatArray(NUM_CLASSES)
        for ((windowIdx, scores) in ensembleScores.withIndex()) {
            val weight = weights[windowIdx] / totalWeight
            for (i in 0 until NUM_CLASSES) {
                fused[i] += scores[i] * weight
            }
        }
        return fused
    }
    
    /**
     * Apply exponential moving average for temporal smoothing
     * @param alpha Smoothing factor (1.0 = no smoothing, 0 = max smoothing)
     */
    private fun applyEMAWithAlpha(currentScores: FloatArray, alpha: Float): FloatArray {
        val previous = emaScores
        if (previous == null) {
            emaScores = currentScores.copyOf()
            return currentScores
        }
        
        val smoothed = FloatArray(NUM_CLASSES)
        for (i in 0 until NUM_CLASSES) {
            smoothed[i] = alpha * currentScores[i] + (1 - alpha) * previous[i]
        }
        emaScores = smoothed
        return smoothed
    }
    
    /**
     * Calculate ensemble agreement: fraction of windows agreeing on top class
     */
    private fun calculateEnsembleAgreement(ensembleScores: List<FloatArray>): Float {
        if (ensembleScores.size <= 1) return 1f
        
        val topClasses = ensembleScores.map { scores ->
            scores.indices.maxByOrNull { scores[it] } ?: 0
        }
        
        val mostCommon = topClasses.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0
        val agreementCount = topClasses.count { it == mostCommon }
        
        return agreementCount.toFloat() / ensembleScores.size
    }
    
    /**
     * Calculate confidence quality based on entropy and margin
     * Higher quality = more confident, lower entropy, larger margin
     */
    private fun calculateConfidenceQuality(scores: FloatArray): Float {
        // Normalize scores to probabilities (softmax already applied in YAMNet)
        val sum = scores.sum().coerceAtLeast(0.001f)
        val probs = scores.map { it / sum }
        
        // Calculate entropy (lower = more confident)
        var entropy = 0.0
        for (p in probs) {
            if (p > 0.0001f) {
                entropy -= p * ln(p.toDouble())
            }
        }
        val maxEntropy = ln(NUM_CLASSES.toDouble())
        val normalizedEntropy = (entropy / maxEntropy).toFloat()
        
        // Calculate margin (top1 - top2 score)
        val sorted = probs.sortedDescending()
        val margin = if (sorted.size >= 2) sorted[0] - sorted[1] else sorted[0]
        
        // Combine: low entropy and high margin = high quality
        val entropyScore = 1f - normalizedEntropy.coerceIn(0f, 1f)
        val marginScore = margin.coerceIn(0f, 1f)
        
        return (entropyScore * 0.4f + marginScore * 0.6f).coerceIn(0f, 1f)
    }

    private fun processScores(
        scores: FloatArray,
        confidenceQuality: Float,
        ensembleAgreement: Float
    ): ClassificationResult {
        // Find top class
        var topIndex = 0
        var topScore = scores[0]
        for (i in scores.indices) {
            if (scores[i] > topScore) {
                topScore = scores[i]
                topIndex = i
            }
        }

        val topClass = classNames.getOrElse(topIndex) { "Unknown" }

        // Get top 5 predictions
        val predictions = scores
            .withIndex()
            .sortedByDescending { it.value }
            .take(5)
            .associate { (index, score) ->
                classNames.getOrElse(index) { "Class_$index" } to score
            }

        return ClassificationResult(
            topClass = topClass,
            topScore = topScore,
            predictions = predictions,
            confidenceQuality = confidenceQuality,
            ensembleAgreement = ensembleAgreement
        )
    }

    /**
     * Calculate audio level for visualization
     */
    fun calculateAudioLevel(audioData: FloatArray): Float {
        var sum = 0.0
        for (sample in audioData) {
            sum += sample * sample
        }
        val rms = kotlin.math.sqrt(sum / audioData.size)
        // Normalize to 0-1 range (assuming max RMS of 0.5)
        return (rms / 0.5).coerceIn(0.0, 1.0).toFloat()
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
        _isInitialized.value = false
    }
}