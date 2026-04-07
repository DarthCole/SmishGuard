# SmishGuard

A native Android application that runs as a background service to detect and prevent SMS fraud (smishing). SmishGuard uses a fine-tuned DistilBERT model combined with rule-based pattern matching and contact-aware scoring to classify incoming messages as **Safe**, **Spam**, or **Fraud** — entirely on-device, with no data leaving the user's phone.

## Features

- **Hybrid Detection Engine** – On-device DistilBERT TFLite model + regex rule matching + contact/whitelist scoring
- **Three-Category Classification** – Messages are classified as Safe, Spam, or Fraud with confidence percentages
- **Threat Explanations** – Plain-language explanations of why a message was flagged
- **Real-Time Monitoring** – Foreground service scans incoming SMS in the background
- **Push Notifications** – Instant alerts when a threat is detected
- **Contact-Aware Scoring** – Known contacts receive a trust boost; unknown senders are scrutinised more closely
- **Casual Message Filtering** – Informal greetings (e.g. "yo what's up", "charle how far") are not flagged as threats
- **Privacy-First** – All processing happens on-device; AES-256 encrypted local storage; no network calls
- **Settings Screen** – View privacy information and clear stored analysis history

## Project Structure

```
SmishGuard/
├── android_app/          # Android Studio project (Kotlin)
│   └── app/src/main/
│       ├── java/com/smishguard/app/
│       │   ├── ml/           # SmishDetector, BertTokenizer
│       │   ├── ui/           # Activities, Fragments, ViewModels
│       │   ├── data/         # Room database, entities, DAOs
│       │   └── service/      # SmsMonitorService, SmsReceiver
│       ├── assets/           # TFLite model goes here (see below)
│       └── res/              # Layouts, drawables, menus, values
├── model/
│   ├── notebooks/            # Google Colab fine-tuning notebook
│   │   └── smishguard_fine_tuning.ipynb
│   └── data/                 # Training datasets
│       ├── Ghana_sms_text.csv
│       ├── safe_chats.csv
│       └── survey_responses.csv
├── documentation/            # Capstone documentation (Chapters 1-6)
└── README.md
```

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34 (min SDK 27 / Android 8.1)
- A physical Android device (recommended) or emulator

### Obtaining the TFLite Model

The fine-tuned DistilBERT model (`smishguard_model.tflite`, ~127 MB) is **not included in this repository** because it exceeds GitHub's file size limit.

To generate the model:

1. Open [`model/notebooks/smishguard_fine_tuning.ipynb`](model/notebooks/smishguard_fine_tuning.ipynb) in **Google Colab** (GPU runtime recommended).
2. Run all cells — the notebook fine-tunes DistilBERT on the datasets in `model/data/` and exports a TFLite file.
3. Download the resulting `smishguard_model.tflite`.
4. Place it in `android_app/app/src/main/assets/smishguard_model.tflite`.

### Build & Run

1. Clone the repository:
   ```bash
   git clone https://github.com/DarthCole/SmishGuard.git
   ```
2. Follow the **Obtaining the TFLite Model** steps above.
3. Open `android_app/` in Android Studio.
4. Sync Gradle, then **Run** on a connected device or emulator.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 1.9.22 |
| Build | Gradle 8.5 / AGP 8.2.2 |
| ML | TensorFlow Lite (DistilBERT, float16) |
| Database | Room (SQLite) |
| Security | EncryptedSharedPreferences (AES-256) |
| Architecture | MVVM + Clean Architecture |
