# 🎙️ TrustyListener Android

A high-performance, native Android application designed for **real-time audio event detection**. Leveraging Google's **YAMNet** model and a robust background architecture, TrustyListener turns any Android device into a powerful sound monitoring station.

---

## 🚀 Key Features

- **Real-time Audio Classification**: Detects **521 different sound classes** (speech, music, alarms, barking, etc.) with high precision.
- **Robust Background Monitoring**: Implemented as a persistent **Foreground Service** with WakeLock support, ensuring continuous operation even when the device is locked or the app is minimized.
- **Advanced ML Pipeline**: Multi-window ensemble classification with temporal smoothing and confidence-weighted fusion.
- **Embedded Web Dashboard**: A built-in **Ktor web server** provides a real-time monitoring interface accessible from any browser on the same network.
- **Offline First**: Local data persistence using **Room Database**; no cloud required.
- **Modern Android Stack**: Built with **Kotlin Coroutines**, **Jetpack Compose**, **Hilt DI**, and **Material 3**.

---

## 🏗️ Technical Architecture

TrustyListener follows **Clean Architecture** principles and **MVI/MVVM** patterns to ensure scalability and maintainability.

### Audio Pipeline & ML (The "Brain")

The core classification logic resides in `YAMNetClassifier.kt`, featuring:

- **Multi-Window Ensemble**: The app runs inference on multiple overlapping windows (e.g., 3 windows with 3900-sample offsets) to increase detection stability.
- **Temporal Smoothing**: Uses **Exponential Moving Average (EMA)** to filter out transient noise and provide stable class predictions.
- **Quality Metrics**: Each detection is rated based on **Entropy** and **Margin Score** to filter out low-confidence "background noise".
- **Dynamic Modes**:
  - `BALANCED`: Standard monitoring with 3-window ensemble.
  - `SENSITIVE`: Optimized for low-latency detection of specific sounds (2 windows).
  - `RAW`: Minimal processing for debugging (single window, no EMA).

### Background Service

The `ListeningService.kt` manages the lifecycle of audio capture and processing:

- **Foreground Service**: Notifies the OS that the app is performing critical background work.
- **Battery Optimization**: Uses a `WakeLock` to prevent the CPU from sleeping during active monitoring, while maintaining efficient power consumption via TFLite GPU delegates.

### Embedded Web Server

The built-in **Ktor server** (`WebServer.kt`) runs on port **8080** and features:

- **RESTful API**: `/api/logs` provides the last 100 detected events in JSON format.
- **Real-time HTML UI**: A responsive, self-hosted dashboard with automatic polling for live updates.

---

## 🛠️ Stack & Libraries

- **UI**: Jetpack Compose, Material 3, Navigation Compose.
- **DI**: Hilt (Dagger) for dependency injection.
- **Persistence**: Room for reliable local SQLite storage.
- **Concurrency**: Kotlin Coroutines & Flow for reactive data streams.
- **ML**: TensorFlow Lite with Support Library.
- **Networking**: Ktor (Netty) for the embedded server.

---

## 🚦 Getting Started

### 1. Requirements

- **Android Studio Hedgehog** (2023.1.1+)
- **JDK 17**
- **Android SDK 34**

### 2. Manual Assets Required

Due to licensing and size, you must manually place the following files in `app/src/main/assets/`:

- `yamnet.tflite`: The official [YAMNet TFLite model](https://storage.googleapis.com/tfhub-lite-models/google/lite-model/yamnet/classification/tflite/1.tflite).
- `yamnet_class_map.csv`: The [class mapping file](https://github.com/tensorflow/models/blob/master/research/audioset/yamnet/yamnet_class_map.csv).

### 3. Build & Install

```bash
./gradlew installDebug
```

---

## 📜 License & Acknowledgments

- Licensed under **MIT License**.
- Powered by Google's **YAMNet** Audio Research.
- This project is a native Android port/expansion of the desktop sound classification concepts.
