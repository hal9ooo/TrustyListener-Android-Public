# TrustyListener Android

A native Android application built with Kotlin and Jetpack Compose for real-time audio event detection using Google's YAMNet model.

## 🚀 Features

- **Real-time Audio Classification**: Detects 521 different sounds using the YAMNet model.
- **Continuous Listening**: Runs as a Foreground Service for background monitoring.
- **Embedded Web UI**: Monitor events and logs from any browser via a built-in Ktor server.
- **Local Storage**: All detected events are saved locally using Room Database.
- **Modern UI**: Built entirely with Jetpack Compose and Material 3.

## 🛠️ Setup

1. **Clone the repository**:

   ```bash
   git clone https://github.com/yourusername/TrustyListener-Android.git
   ```

2. **Download YAMNet Models**:
   Place the following files in `app/src/main/assets/`:
   - [yamnet.tflite](https://storage.googleapis.com/tfhub-lite-models/google/lite-model/yamnet/classification/tflite/1.tflite)
   - [yamnet_class_map.csv](https://github.com/tensorflow/models/blob/master/research/audioset/yamnet/yamnet_class_map.csv)

3. **Build and Run**:
   Open the project in Android Studio (Hedgehog or newer) or use the terminal:
   ```bash
   ./gradlew installDebug
   ```

## 📜 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

## 🔧 Technical Stack

- **Kotlin** & **Jetpack Compose**
- **TensorFlow Lite** (Inference)
- **Ktor** (Local Web Server)
- **Hilt** (Dependency Injection)
- **Room** (Local Persistence)
