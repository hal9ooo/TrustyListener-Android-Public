package com.trustylistener.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trustylistener.domain.model.ClassificationMode
import com.trustylistener.presentation.viewmodel.MainViewModel

/**
 * Settings screen for app configuration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val threshold by viewModel.threshold.collectAsState()
    val classificationMode by viewModel.classificationMode.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    var exportContent by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Detection Settings
            SettingsSection(title = "Rilevamento") {
                ThresholdSetting(
                    value = threshold,
                    onValueChange = viewModel::setThreshold
                )
                
                HorizontalDivider()
                
                ClassificationModeSetting(
                    selectedMode = classificationMode,
                    onModeChange = viewModel::setClassificationMode
                )
            }

            // Data Management
            SettingsSection(title = "Dati") {
                ListItem(
                    headlineContent = { Text("Esporta log (CSV)") },
                    supportingContent = { Text("Esporta tutti gli eventi in formato CSV") },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                    modifier = Modifier.clickable {
                        exportContent = viewModel.exportLogs()
                        showExportDialog = true
                    }
                )

                ListItem(
                    headlineContent = { Text("Cancella tutti i log") },
                    supportingContent = { Text("Elimina permanentemente tutti gli eventi") },
                    leadingContent = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { /* Show confirmation */ }
                )
            }

            // About
            SettingsSection(title = "Informazioni") {
                ListItem(
                    headlineContent = { Text("Versione") },
                    supportingContent = { Text("1.0.0") }
                )

                ListItem(
                    headlineContent = { Text("Modello") },
                    supportingContent = { Text("YAMNet (TensorFlow Lite)") }
                )
            }
        }
    }

    // Export dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Esporta CSV") },
            text = {
                Column {
                    Text("Copia il contenuto CSV:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportContent,
                        onValueChange = { },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Chiudi")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card {
            Column {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThresholdSetting(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(value) }

    ListItem(
        headlineContent = { Text("Soglia di confidenza") },
        supportingContent = {
            Column {
                Text("Eventi con confidenza inferiore non verranno registrati")
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onValueChange(sliderValue) },
                    valueRange = 0.1f..1.0f,
                    steps = 8
                )
                Text(
                    text = "${(sliderValue * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassificationModeSetting(
    selectedMode: ClassificationMode,
    onModeChange: (ClassificationMode) -> Unit
) {
    ListItem(
        headlineContent = { Text("Modalità di classificazione") },
        supportingContent = {
            Column {
                Text(
                    when (selectedMode) {
                        ClassificationMode.BALANCED -> "Bilanciata: uso generale, stabile"
                        ClassificationMode.SENSITIVE -> "Sensibile: canto, tosse, burp, ecc."
                        ClassificationMode.RAW -> "Originale: come YAMNet puro"
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ClassificationMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = selectedMode == mode,
                            onClick = { onModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ClassificationMode.entries.size
                            )
                        ) {
                            Text(
                                text = when (mode) {
                                    ClassificationMode.BALANCED -> "Bilanciata"
                                    ClassificationMode.SENSITIVE -> "Sensibile"
                                    ClassificationMode.RAW -> "Raw"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    )
}
