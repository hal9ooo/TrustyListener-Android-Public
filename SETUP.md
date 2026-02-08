# Setup - TrustyListener Android

## Prerequisiti

1. **Android Studio Hedgehog** (2023.1.1) o superiore
2. **JDK 17** installato
3. **Android SDK 34**
4. **Git** (opzionale)

## Istruzioni Setup

### 1. Scarica i Modelli YAMNet

I modelli TensorFlow non possono essere scaricati automaticamente. Devi farlo manualmente:

**Opzione A: Da TensorFlow Hub (Consigliato)**

1. Vai su https://www.tensorflow.org/hub/tutorials/yamnet
2. Scarica il modello TFLite: [yamnet.tflite](https://storage.googleapis.com/tfhub-lite-models/google/lite-model/yamnet/classification/tflite/1.tflite)
3. Rinomina in `yamnet.tflite`

**Opzione B: Da GitHub TensorFlow**

```bash
# Clona il repo dei modelli TensorFlow
git clone https://github.com/tensorflow/models.git
cp models/research/audioset/yamnet/yamnet.tflite TrustyListenerAndroid/app/src/main/assets/
```

**Posiziona i file:**

```
TrustyListenerAndroid/app/src/main/assets/
├── yamnet.tflite          # ~4MB
└── yamnet_class_map.csv   # ~15KB (scarica da stessa fonte)
```

**Modelli Avanzati (Consigliato per performance Desktop-like):**

Se il dispositivo è moderno, usa la versione **Float32** (non quantizzata) per una precisione superiore:

1. Scarica il modello "float" ufficiale da TensorFlow Hub.
2. Sostituisci il file in `assets/` mantenendo il nome `yamnet.tflite` (~15MB invece di ~4MB).

### 2. Crea le icone Launcher

Puoi usare l'Asset Studio di Android Studio:

1. Click destro su `res/` → **New → Image Asset**
2. Scegli un'icona o carica un'immagine
3. Lascia i nomi default (`ic_launcher`)

**Oppure copia icone placeholder:**

```bash
# Le icone sono già in res/mipmap-xxx/
# Se vuoi personalizzarle, sostituisci i file
```

### 3. Build del Progetto

**Da Android Studio:**

1. Apri la cartella `TrustyListenerAndroid` in Android Studio
2. Attendi il sync Gradle (può richiedere diversi minuti la prima volta)
3. Connetti un dispositivo Android o avvia un emulatore
4. Click su **Run** (▶️) o premi `Shift+F10`

**Da terminale:**

```bash
cd TrustyListenerAndroid

# Build debug APK
./gradlew assembleDebug

# Installa su dispositivo connesso
./gradlew installDebug

# O con adb
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Primo Avvio

1. **Permessi**: All'avvio l'app chiederà il permesso microfono → **Consenti**
2. **Notifica**: Abilita le notifiche per il servizio in background
3. **Avvio**: Tappa il pulsante **▶️ Start** nel centro dello schermo
4. **Web UI**: Apri il browser e vai su `http://localhost:8080`

## Troubleshooting

### "Gradle sync failed"

```bash
# Cancella cache
./gradlew clean
rm -rf ~/.gradle/caches/
# Riavvia Android Studio
```

### "Model not found"

- Verifica che `yamnet.tflite` sia in `app/src/main/assets/`
- Controlla che il file sia ~4MB (non 298 bytes)

### "App crashes on start"

- Verifica JDK 17: `java -version`
- Assicurati di avere Android SDK 34
- Controlla i log in Logcat

### "No microphone input"

- Verifica permessi in Impostazioni → App → TrustyListener → Permessi
- Testa con altre app di registrazione

### "Web UI not accessible"

- Verifica che il servizio sia attivo (notifica presente)
- Controlla firewall/dispositivo non blocchi porta 8080
- Prova `http://127.0.0.1:8080` invece di `localhost`

## Struttura File Importante

```
TrustyListenerAndroid/
├── app/src/main/
│   ├── assets/
│   │   ├── yamnet.tflite          ⬅️ TUO FILE
│   │   └── yamnet_class_map.csv   ⬅️ TUO FILE
│   ├── java/com/trustylistener/
│   │   ├── MainActivity.kt
│   │   ├── service/ListeningService.kt
│   │   └── ...
│   └── AndroidManifest.xml
└── build.gradle.kts
```

## Prossimi Passi Suggeriti

1. **Personalizza l'icona**: Usa Image Asset Studio
2. **Modifica i colori**: `ui/theme/Color.kt`
3. **Aggiungi categorie filtro**: Modifica `YAMNetClassifier.kt`
4. **Esporta APK release**: Build → Generate Signed Bundle/APK
