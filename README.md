# TrustyListener Android

App Android nativa in Kotlin per rilevamento eventi audio con YAMNet.

## Requisiti

- Android Studio Hedgehog (2023.1.1) o superiore
- Android SDK 34
- JDK 17
- Dispositivo con Android 8.0+ (API 26)
- Consigliato: 6GB+ RAM per TensorFlow Lite

## Struttura del Progetto

```
TrustyListenerAndroid/
├── app/
│   ├── src/main/java/com/trustylistener/
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── audio/        # AudioRecorder
│   │   │   │   └── database/     # Room entities/dao
│   │   │   ├── ml/               # YAMNet TFLite
│   │   │   └── repository/       # Repository implementations
│   │   ├── domain/
│   │   │   ├── model/            # Domain models
│   │   │   ├── repository/       # Repository interfaces
│   │   │   └── usecase/          # Use cases
│   │   ├── presentation/
│   │   │   ├── components/       # Compose components
│   │   │   ├── screens/          # Compose screens
│   │   │   └── viewmodel/        # ViewModels
│   │   ├── service/              # ForegroundService
│   │   ├── web/                  # Ktor web server
│   │   ├── di/                   # Hilt modules
│   │   └── MainActivity.kt
│   └── src/main/assets/
│       ├── yamnet.tflite         # Modello ML (da scaricare)
│       └── yamnet_class_map.csv  # Class names
```

## Setup

1. **Scarica i modelli YAMNet:**

   ```bash
   mkdir -p app/src/main/assets
   curl -L https://storage.googleapis.com/download.tensorflow.org/models/tflite/yamnet/yamnet.tflite \
     -o app/src/main/assets/yamnet.tflite
   curl -L https://storage.googleapis.com/download.tensorflow.org/models/tflite/yamnet/yamnet_class_map.csv \
     -o app/src/main/assets/yamnet_class_map.csv
   ```

2. **Build del progetto:**

   ```bash
   ./gradlew assembleDebug
   ```

3. **Installa su dispositivo:**
   ```bash
   ./gradlew installDebug
   ```

## Funzionalità

- **Rilevamento Audio**: Cattura continua con AudioRecord API
- **Classificazione YAMNet**: 521 classi audio tramite TensorFlow Lite
- **Database Locale**: Room per logging eventi
- **Servizio Background**: ForegroundService per ascolto continuo
- **Web UI**: Server Ktor integrato su porta 8080
- **UI Nativa**: Jetpack Compose con Material You

## Architettura

MVVM + Clean Architecture con:

- **Hilt**: Dependency Injection
- **Room**: Database locale
- **TensorFlow Lite**: ML inference
- **Ktor**: Web server embedded
- **Coroutines/Flow**: Async operations

## Web UI

Accedi all'interfaccia web dal browser del dispositivo:

```
http://localhost:8080
```

Oppure dalla rete locale (trova l'IP del dispositivo):

```
http://<device-ip>:8080
```

## Permessi

L'app richiede:

- `RECORD_AUDIO`: Per catturare audio
- `FOREGROUND_SERVICE`: Per servizio in background
- `POST_NOTIFICATIONS`: Per notifica persistente
- `WAKE_LOCK`: Per continuare con schermo spento

## Ottimizzazioni

- GPU Delegate per inferenza accelerata su Snapdragon
- Audio buffer circolare per streaming efficiente
- TFLite con XNNPACK per CPU ottimizzata

## Note

- Il modello YAMNet richiede ~10MB
- L'app usa ~100MB RAM in totale
- GPU delegate automatico su dispositivi compatibili
