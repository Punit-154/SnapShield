# SMSentry

**Real-time SMS scam detection for Android — powered by on-device AI.**

SMSentry scans every incoming SMS in milliseconds using a quantized BERT model running entirely on-device. No data leaves the phone. When a scam is detected, you get an instant push notification; tap through to a Deep Check screen that shows a step-by-step evidence breakdown and a final threat verdict.

---

## Table of Contents

- [Features](#features)
- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Tech Stack](#tech-stack)
- [How the AI Works](#how-the-ai-works)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Clone & Build](#clone--build)
  - [Running on a Device or Emulator](#running-on-a-device-or-emulator)
- [Permissions](#permissions)
- [Demo Mode](#demo-mode)
- [Reproducing the ML Model](#reproducing-the-ml-model)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Features

| Feature | Details |
|---|---|
| **Instant classification** | Every incoming SMS is classified as `SAFE`, `SUSPICIOUS`, or `SCAM` within milliseconds of arrival |
| **On-device inference** | Quantized BERT model (ONNX Runtime) — no cloud API, no data sent off-device |
| **Deep Check investigation** | 7-step forensic analysis: URL extraction, TLD reputation, urgency-language detection, financial-bait detection, AI classification, legitimate-pattern matching, and final verdict |
| **Real-time push notifications** | Color-coded alerts (🛑 red / ⚠️ orange / ✅ green) with risk score and message preview |
| **Risk score bar** | Per-message risk score 0–100 displayed visually in the inbox and detail screens |
| **Inbox view with filters** | Searchable inbox filterable by `All`, `Scam`, `Suspicious`, `Safe` |
| **Demo mode** | Fully functional offline demo using curated sample messages — no SIM card required |
| **Foreground scanner service** | Persistent `START_STICKY` service ensures scanning survives app close |
| **Dark theme UI** | Material 3 dark theme throughout; Jetpack Compose UI |

---

## Architecture Overview

SMSentry follows a clean **MVVM + Repository** pattern with Hilt for dependency injection.

```
┌─────────────────────────────────────────────────────────────────┐
│                         Android System                          │
│   SMS_RECEIVED broadcast ──► SmsReceiver / SmsScannerService    │
└──────────────────────────┬──────────────────────────────────────┘
                           │ classifies SMS
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      ML Layer  (ml/)                            │
│   BertTokenizer  ──►  SmsClassifierModel (ONNX Runtime)        │
│                        ├── Tier 1: quantized BERT inference     │
│                        └── Tier 2: rule-based fallback          │
└──────────────────────────┬──────────────────────────────────────┘
                           │ ClassificationResult
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Repository / Domain  (data/ + domain/)         │
│   SmsRepository        ──  reads device inbox via ContentResolver│
│   RealDeepCheckEngine  ──  7-step coroutine-based investigation  │
│   SMSSentryAI interface──  injectable AI contract               │
└──────────────────────────┬──────────────────────────────────────┘
                           │ StateFlow<T>
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    UI Layer  (ui/)                               │
│   InboxViewModel / InboxScreen   ──  searchable message list    │
│   DetailViewModel / DetailScreen ──  deep-check investigation   │
└─────────────────────────────────────────────────────────────────┘
```

### Classification Pipeline

Tier 1 runs on every message automatically:

```
Raw SMS
  └─► BertTokenizer (WordPiece, 128 tokens, lowercase)
        └─► ONNX Session (sms_classifier_quantized.onnx)
              └─► softmax(logits) → spam_probability
                    └─► weighted blend: 60% BERT + 40% rule-based
                          └─► SAFE / SUSPICIOUS / SCAM + riskScore 0–100
```

Tier 2 (Deep Check) is triggered manually per message and runs a 7-step coroutine pipeline emitting `DeepCheckUpdate` sealed class events to the UI in real time.

---

## Project Structure

```
SMSentry/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── sms_classifier_quantized.onnx   # ~4.3 MB quantized BERT
│       │   └── vocab.txt                        # 30 522-token BERT vocabulary
│       └── java/com/smssentry/
│           ├── MainActivity.kt                  # Entry point, permission flow, service start
│           ├── SMSSentryApp.kt                  # @HiltAndroidApp application class
│           ├── data/
│           │   ├── mock/
│           │   │   ├── MockData.kt              # 10 curated demo SMS messages
│           │   │   └── MockSMSSentryAI.kt       # Pattern-based mock AI + demo deep-check
│           │   ├── model/
│           │   │   ├── ClassificationResult.kt  # label, confidence, riskScore, reasoning
│           │   │   ├── DeepCheckUpdate.kt       # Sealed: Step | FoundEvidence | FinalVerdict | Error
│           │   │   ├── DeepCheckVerdict.kt      # Final verdict with evidence list
│           │   │   ├── EvidenceItem.kt          # source, detail, severity (LOW/MEDIUM/HIGH/CRITICAL)
│           │   │   ├── InvestigationUiState.kt  # UI state for deep-check progress
│           │   │   └── SmsMessage.kt            # id, sender, text, timestamp, classification?
│           │   └── repository/
│           │       ├── RealDeepCheckEngine.kt   # 7-step live investigation coroutine
│           │       ├── RealSMSSentryAI.kt       # Scaffold for future model upgrades
│           │       └── SmsRepository.kt         # ContentResolver inbox + by-ID queries
│           ├── di/
│           │   └── AppModule.kt                 # Hilt: Context, SmsClassifierModel, SmsRepository, SMSSentryAI
│           ├── domain/
│           │   └── service/
│           │       ├── SMSSentryAI.kt           # Interface: initialize, classifySMS, startDeepCheck
│           │       └── SmsScannerService.kt     # Foreground service + internal BroadcastReceiver
│           ├── ml/
│           │   ├── BertTokenizer.kt             # Custom WordPiece tokenizer (no external library)
│           │   └── SmsClassifierModel.kt        # ONNX session management + hybrid classify()
│           ├── receiver/
│           │   └── SmsReceiver.kt               # BroadcastReceiver for SMS_RECEIVED
│           └── ui/
│               ├── components/                  # Reusable Compose components
│               │   ├── DemoModeBanner.kt
│               │   ├── EvidenceCard.kt
│               │   ├── PrivacyIndicator.kt
│               │   ├── ProgressIndicator.kt
│               │   ├── RiskScoreBar.kt
│               │   ├── ShieldBadge.kt
│               │   └── VerdictCard.kt
│               ├── detail/
│               │   ├── DetailScreen.kt          # Deep-check UI with live evidence feed
│               │   └── DetailViewModel.kt       # Owns RealDeepCheckEngine session lifecycle
│               ├── inbox/
│               │   ├── InboxScreen.kt           # Searchable, filterable message list
│               │   └── InboxViewModel.kt        # Loads + classifies messages; mock fallback
│               ├── navigation/
│               │   └── NavGraph.kt              # Inbox ↔ Detail with smsId argument
│               └── theme/
│                   ├── Color.kt
│                   ├── Theme.kt
│                   └── Type.kt
├── convert.py                                   # Export mrm8488/bert-tiny → sms_classifier.onnx
├── convert2.py                                  # Quantize → sms_classifier_quantized.onnx (QInt8)
├── build.gradle.kts                             # Root plugins (AGP 8.2.2, Kotlin 1.9.22, Hilt 2.51)
├── settings.gradle.kts
└── gradle.properties
```

---

## Tech Stack

| Layer | Library / Version |
|---|---|
| Language | Kotlin 1.9.22 |
| UI | Jetpack Compose (BOM 2024.10.00), Material 3 |
| Navigation | Navigation Compose 2.7.6 |
| DI | Hilt 2.51 + Hilt Navigation Compose 1.1.0 |
| ML Inference | ONNX Runtime Android 1.17.0 |
| ML Model | `mrm8488/bert-tiny-finetuned-sms-spam-detection` → ONNX → QInt8 |
| Async | Kotlin Coroutines 1.7.3 |
| ViewModel | Lifecycle ViewModel Compose 2.7.0 |
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 34 (Android 14) |
| Build System | Gradle 8.5, AGP 8.2.2, Java 17 |

---

## How the AI Works

### Model

The base model is [`mrm8488/bert-tiny-finetuned-sms-spam-detection`](https://huggingface.co/mrm8488/bert-tiny-finetuned-sms-spam-detection) — a BERT-tiny variant fine-tuned on an SMS spam dataset. It is exported to ONNX (`convert.py`) and then quantized to QInt8 (`convert2.py`), reducing the model from ~17 MB to ~4.3 MB.

### Tokenizer

A custom `BertTokenizer` is implemented in Kotlin (no third-party NLP dependency) performing:
- Lowercase normalization
- Punctuation splitting
- WordPiece subword segmentation against `vocab.txt`
- Padding / truncation to 128 tokens with `[CLS]` and `[SEP]`

### Hybrid Scoring

The final risk score blends two signals:

```
combined_score = (bert_spam_prob × 0.6) + (rule_score / 100 × 0.4)
```

The rule-based engine uses weighted regex patterns for scam signals (suspicious TLDs, urgency phrases, prize language) and reduces the score for legitimate signals (OTP keywords, delivery notices). This hybrid approach handles both novel scam phrasing (BERT) and known pattern variants (rules).

### Thresholds

| combined_score | Label |
|---|---|
| > 0.75 | `SCAM` |
| 0.45 – 0.75 | `SUSPICIOUS` |
| < 0.45 | `SAFE` |

### Deep Check (Tier 2)

`RealDeepCheckEngine` runs a 7-step coroutine pipeline on demand:

1. **Sender analysis** — parses the originating address
2. **URL scanning** — regex extraction; flags suspicious TLDs (`.xyz`, `.buzz`, `.win`, `.tk`, `.ml`, `.ga`, `.cf`, `.top`)
3. **Urgency detection** — matches `urgent`, `immediately`, `suspended`, `expire`, etc.
4. **Financial bait** — regex for prize/lottery language adjacent to currency symbols
5. **AI classification** — re-runs the on-device model for a per-check score
6. **Legitimate pattern check** — OTP markers, transaction IDs reduce false-positive risk
7. **Verdict synthesis** — CRITICAL/HIGH evidence counts determine final `isScam` decision

Each step emits a `DeepCheckUpdate` sealed class event consumed by `DetailViewModel` via a `DeepCheckListener` callback, updating `InvestigationUiState` in real time.

---

## Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK 17** (bundled with Android Studio)
- **Android device or emulator** running API 26+

No additional tooling is required. The ONNX model and vocabulary are already bundled under `app/src/main/assets/`.

### Clone & Build

```bash
git clone https://github.com/your-org/SMSentry.git
cd SMSentry
./gradlew assembleDebug
```

Or open the project in Android Studio and let Gradle sync automatically.

### Running on a Device or Emulator

```bash
# Install debug APK on a connected device
./gradlew installDebug

# Or run directly from Android Studio: Shift+F10
```

On first launch the app will request `READ_SMS` and (on Android 13+) `POST_NOTIFICATIONS`. The foreground scanner service starts automatically after permissions are granted.

> **Emulator note:** The emulator has no SMS inbox by default. SMSentry automatically falls back to demo mode with 10 curated sample messages when the real inbox is empty or permission is denied.

---

## Permissions

| Permission | Why it's needed |
|---|---|
| `READ_SMS` | Read the device inbox to classify existing messages |
| `RECEIVE_SMS` | Intercept incoming SMS in real time via `SmsReceiver` |
| `POST_NOTIFICATIONS` | Show threat alerts on incoming scam messages |
| `FOREGROUND_SERVICE` | Keep `SmsScannerService` alive in the background |
| `FOREGROUND_SERVICE_DATA_SYNC` | Required foreground service type for Android 14+ |
| `INTERNET` | Reserved for future WHOIS / scam-database lookups (not used in current release) |

All SMS data is processed entirely on-device. Nothing is transmitted to external servers.

---

## Demo Mode

When `READ_SMS` permission is denied **or** the real inbox is empty, the app automatically switches to demo mode. A banner is displayed in the inbox to indicate this.

Demo mode uses 10 hand-crafted `SmsMessage` objects in `MockData.kt` covering a range of real-world scenarios:
- Classic bank phishing (HSBC impersonation)
- Legitimate Amazon order dispatch
- Microsoft lottery prize scam
- Legitimate DHL delivery notice
- OTP from a bank
- Suspicious PayPal payment alert
- Netflix renewal scam with `.buzz` TLD
- Personal message from a contact
- Suspicious Royal Mail redelivery link
- Nigerian inheritance advance-fee fraud

The Deep Check investigation in demo mode shows a detailed pre-scripted walkthrough including a WHOIS result ("Domain registered 2 days ago in Russia"), SmishTank database hit, and urgency-language NLP evidence.

---

## Reproducing the ML Model

The ONNX model bundled in `assets/` was generated using the two Python scripts in the repo root. To regenerate:

```bash
pip install transformers torch onnx onnxruntime
python convert.py       # Downloads bert-tiny, exports → sms_classifier.onnx (~17 MB)
python convert2.py      # Quantizes → sms_classifier_quantized.onnx (~4.3 MB, QInt8)
```

Then copy `sms_classifier_quantized.onnx` and `sms_spam_model/vocab.txt` to:

```
app/src/main/assets/sms_classifier_quantized.onnx
app/src/main/assets/vocab.txt
```

---

## Roadmap

The codebase includes scaffolding (`RealSMSSentryAI`) and `TODO` markers for the following planned improvements:

- **Live WHOIS lookups** — real domain-age and registrar checks replacing the current simulation
- **Scam URL database** — integration with SmishTank or similar phishing feed
- **Gemma / on-device LLM** — Tier 2 deep-check narrative generation using an on-device generative model
- **Proguard / R8 minification** — currently disabled; enable for production release builds
- **Unit & instrumentation tests** — add tests for `SmsClassifierModel`, `BertTokenizer`, and `RealDeepCheckEngine`
- **Accessibility** — content descriptions and font scaling support
- **Localization** — i18n for non-English UI strings

---

## Contributing

1. Fork the repository and create a feature branch (`git checkout -b feature/my-feature`)
2. Follow the existing package structure: UI in `ui/`, data in `data/`, ML in `ml/`
3. Keep new dependencies to a minimum — on-device privacy is a core constraint
4. Open a pull request with a clear description of the change and any model-side implications

---

## License

This project is released under the [MIT License](LICENSE).

---

> **Privacy notice:** SMSentry processes all SMS content on-device using local ML inference. No message content, sender information, or personal data is transmitted to any external server.
