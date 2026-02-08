package com.trustylistener.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trustylistener.domain.model.AudioEvent
import com.trustylistener.domain.model.ClassificationMode
import com.trustylistener.domain.model.ClassificationResult
import com.trustylistener.domain.model.ListeningState
import com.trustylistener.domain.usecase.ExportLogsUseCase
import com.trustylistener.domain.usecase.GetRecentLogsUseCase
import com.trustylistener.domain.usecase.StartListeningUseCase
import com.trustylistener.service.ListeningService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main ViewModel for the application
 * Manages service connection and UI state
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val startListeningUseCase: StartListeningUseCase,
    private val getRecentLogsUseCase: GetRecentLogsUseCase,
    private val exportLogsUseCase: ExportLogsUseCase
) : AndroidViewModel(application) {

    private val _listeningState = MutableStateFlow<ListeningState>(ListeningState.Idle)
    val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _logs = MutableStateFlow<List<AudioEvent>>(emptyList())
    val logs: StateFlow<List<AudioEvent>> = _logs.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _currentDetection = MutableStateFlow<ClassificationResult?>(null)
    val currentDetection: StateFlow<ClassificationResult?> = _currentDetection.asStateFlow()

    private val _threshold = MutableStateFlow(0.3f)
    val threshold: StateFlow<Float> = _threshold.asStateFlow()
    
    private val _classificationMode = MutableStateFlow(ClassificationMode.BALANCED)
    val classificationMode: StateFlow<ClassificationMode> = _classificationMode.asStateFlow()

    private var listeningService: ListeningService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ListeningService.LocalBinder
            listeningService = binder.getService()
            serviceBound = true

            // Collect service state
            viewModelScope.launch {
                listeningService?.isListening?.collect { isListening ->
                    _listeningState.value = if (isListening) {
                        ListeningState.Listening
                    } else {
                        ListeningState.Idle
                    }
                }
            }

            viewModelScope.launch {
                listeningService?.audioLevel?.collect { level ->
                    _audioLevel.value = level
                }
            }

            viewModelScope.launch {
                listeningService?.currentDetection?.collect { detection ->
                    _currentDetection.value = detection
                    detection?.let {
                        if (it.isSignificant(_threshold.value)) {
                            _listeningState.value = ListeningState.Detected(
                                AudioEvent(
                                    timestamp = System.currentTimeMillis(),
                                    className = it.topClass,
                                    score = it.topScore,
                                    metadata = it.predictions
                                )
                            )
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            listeningService = null
            serviceBound = false
        }
    }

    init {
        // Load logs
        viewModelScope.launch {
            getRecentLogsUseCase().collect { events ->
                _logs.value = events
            }
        }
    }

    fun bindService() {
        val intent = Intent(getApplication(), ListeningService::class.java)
        getApplication<Application>().bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun unbindService() {
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
    }

    fun startListening() {
        val intent = Intent(getApplication(), ListeningService::class.java).apply {
            action = ListeningService.ACTION_START
            putExtra(ListeningService.EXTRA_THRESHOLD, _threshold.value)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun stopListening() {
        val intent = Intent(getApplication(), ListeningService::class.java).apply {
            action = ListeningService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    fun setThreshold(value: Float) {
        val newVal = value.coerceIn(0.1f, 1.0f)
        _threshold.value = newVal
        // Update the service threshold in real-time if running
        if (listeningState.value is ListeningState.Listening || listeningState.value is ListeningState.Detected) {
            startListening()
        }
    }
    
    /**
     * Set classification mode (takes effect immediately, no restart needed)
     */
    fun setClassificationMode(mode: ClassificationMode) {
        _classificationMode.value = mode
        listeningService?.setClassificationMode(mode)
    }

    fun exportLogs(): String {
        var csv = ""
        viewModelScope.launch {
            csv = exportLogsUseCase()
        }
        return csv
    }

    fun clearLogs() {
        viewModelScope.launch {
            exportLogsUseCase.clearAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindService()
    }
}
