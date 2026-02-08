package com.trustylistener.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trustylistener.domain.model.ClassificationResult
import com.trustylistener.domain.model.ListeningState
import com.trustylistener.presentation.components.AudioVisualizer
import com.trustylistener.presentation.viewmodel.MainViewModel

/**
 * Main dashboard screen with controls and visualizer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToLogs: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val listeningState by viewModel.listeningState.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val currentDetection by viewModel.currentDetection.collectAsState()
    val threshold by viewModel.threshold.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TrustyListener") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (listeningState) {
                        is ListeningState.Idle -> viewModel.startListening()
                        else -> viewModel.stopListening()
                    }
                },
                containerColor = when (listeningState) {
                    is ListeningState.Idle -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
            ) {
                Icon(
                    imageVector = when (listeningState) {
                        is ListeningState.Idle -> Icons.Default.Mic
                        else -> Icons.Default.Stop
                    },
                    contentDescription = if (listeningState is ListeningState.Idle) "Start" else "Stop"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            StatusCard(listeningState)

            // Audio Visualizer
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AudioVisualizer(
                        audioLevel = audioLevel,
                        isActive = listeningState !is ListeningState.Idle,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Current Detection
            DetectionCard(currentDetection, threshold)

            // Threshold Slider
            ThresholdSlider(
                value = threshold,
                onValueChange = viewModel::setThreshold
            )

            // Quick Stats
            QuickStatsRow(
                onNavigateToLogs = onNavigateToLogs
            )
        }
    }
}

@Composable
fun StatusCard(state: ListeningState) {
    val (color, text, icon) = when (state) {
        is ListeningState.Idle -> Triple(
            Color.Gray,
            "In attesa",
            Icons.Default.MicOff
        )
        is ListeningState.Listening -> Triple(
            MaterialTheme.colorScheme.primary,
            "In ascolto...",
            Icons.Default.Mic
        )
        is ListeningState.Detected -> Triple(
            MaterialTheme.colorScheme.secondary,
            "Evento rilevato!",
            Icons.Default.Notifications
        )
        is ListeningState.Error -> Triple(
            MaterialTheme.colorScheme.error,
            "Errore",
            Icons.Default.Error
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DetectionCard(detection: ClassificationResult?, threshold: Float) {
    AnimatedVisibility(
        visible = detection != null && detection.isSignificant(threshold),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                detection?.let { result ->
                    Text(
                        text = result.topClass,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = result.topScore,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Confidenza: ${(result.topScore * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Top 5 predictions
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        result.predictions.toList().take(3).forEach { (className, score) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = className,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${(score * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThresholdSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Soglia rilevamento: ${(value * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.1f..1.0f,
            steps = 8
        )
    }
}

@Composable
fun QuickStatsRow(onNavigateToLogs: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onNavigateToLogs,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.List, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Eventi")
        }
    }
}
