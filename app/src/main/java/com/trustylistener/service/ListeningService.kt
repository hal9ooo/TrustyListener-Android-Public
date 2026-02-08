package com.trustylistener.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.trustylistener.MainActivity
import com.trustylistener.R
import com.trustylistener.domain.model.AudioEvent
import com.trustylistener.domain.model.ClassificationMode
import com.trustylistener.domain.model.ClassificationResult
import com.trustylistener.domain.repository.AudioRepository
import com.trustylistener.domain.repository.LogRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Foreground service for continuous audio listening
 * Survives app backgrounding and device sleep
 */
@AndroidEntryPoint
class ListeningService : Service() {

    companion object {
        private const val TAG = "ListeningService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "trustylistener_channel"
        private const val WAKE_LOCK_TAG = "TrustyListener::WakeLock"

        const val ACTION_START = "com.trustylistener.ACTION_START"
        const val ACTION_STOP = "com.trustylistener.ACTION_STOP"
        const val EXTRA_THRESHOLD = "threshold"
        const val EXTRA_CLASSIFICATION_MODE = "classification_mode"
    }

    @Inject
    lateinit var audioRepository: AudioRepository

    @Inject
    lateinit var logRepository: LogRepository

    private val binder = LocalBinder()
    private var serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _currentDetection = MutableStateFlow<ClassificationResult?>(null)
    val currentDetection: StateFlow<ClassificationResult?> = _currentDetection

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    private val _threshold = MutableStateFlow(AudioEvent.DEFAULT_THRESHOLD)
    val threshold: StateFlow<Float> = _threshold
    
    private val _classificationMode = MutableStateFlow(ClassificationMode.BALANCED)
    val classificationMode: StateFlow<ClassificationMode> = _classificationMode

    inner class LocalBinder : Binder() {
        fun getService(): ListeningService = this@ListeningService
    }
    
    /**
     * Set classification mode in real-time (no restart needed)
     */
    fun setClassificationMode(mode: ClassificationMode) {
        _classificationMode.value = mode
        audioRepository.setClassificationMode(mode)
        Log.d(TAG, "Classification mode set to: $mode (real-time, no restart)")
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val thresholdVal = intent.getFloatExtra(EXTRA_THRESHOLD, AudioEvent.DEFAULT_THRESHOLD)
                _threshold.value = thresholdVal
                startListening()
            }
            ACTION_STOP -> stopListening()
        }
        return START_STICKY // Restart if killed
    }

    private fun startListening() {
        if (_isListening.value) {
            Log.d(TAG, "Already listening, threshold updated to ${_threshold.value}")
            return
        }

        val notification = createNotification("In ascolto...", "Rilevamento eventi audio attivo")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        _isListening.value = true

        serviceScope.launch {
            // Monitor audio level
            launch {
                audioRepository.audioLevel.collect { level ->
                    _audioLevel.value = level
                }
            }

            // Start recording
            audioRepository.startRecording()

            // Monitor classifications and save significant ones
            audioRepository.classificationResults.collect { result ->
                _currentDetection.value = result
                
                val currentThreshold = _threshold.value
                if (result.isSignificant(currentThreshold)) {
                    Log.d(TAG, "Significant event detected: ${result.topClass} (${result.topScore}) with threshold $currentThreshold")
                    
                    // Save to database
                    val event = AudioEvent(
                        timestamp = System.currentTimeMillis(),
                        className = result.topClass,
                        score = result.topScore,
                        metadata = result.predictions
                    )
                    logRepository.saveEvent(event)
                    
                    // Update notification
                    val notification = createNotification(
                        "Evento rilevato!", 
                        "Rilevato: ${result.topClass} (${(result.topScore * 100).toInt()}%)"
                    )
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
        }

        Log.d(TAG, "Listening started with threshold: ${_threshold.value}")
    }

    private fun stopListening() {
        serviceScope.launch {
            audioRepository.stopRecording()
            _isListening.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        Log.d(TAG, "Listening stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TrustyListener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servizio di ascolto audio in background"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ListeningService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        wakeLock?.acquire(10*60*1000L) // 10 minutes, will be refreshed
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        releaseWakeLock()
        if (_isListening.value) {
            // Restart service if it was listening (unexpected kill)
            val intent = Intent(this, ListeningService::class.java).apply {
                action = ACTION_START
            }
            startService(intent)
        }
    }
}
