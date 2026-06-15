# SMSentry

**AI-Powered SMS Scam Detection for Android**

SMSentry is an Android app that protects users from SMS scams using a multi-layered analysis pipeline — from fast rule-based heuristics to on-device AI deep analysis powered by Google's LiteRT (TFLite) LLM runtime.

## Features

- **Real-Time SMS Scanning** — Incoming messages are automatically classified as Safe, Suspicious, or Scam
- **Rule-Based Fast Path** — Instant detection using pattern matching, allowlists, URL reputation, and known-scam databases
- **On-Device AI Deep Check** — A locally-running LLM performs multi-step forensic analysis including:
  - URL reputation and WHOIS lookups
  - Brand impersonation detection
  - Official website comparison
  - Personal scam history matching
- **Privacy-First Architecture** — All analysis happens on-device; a Cloudflare Worker proxy handles external lookups without exposing user data
- **Material 3 UI** — Modern design with dark/light theme support, search, and filter chips
- **Default SMS App** — Can replace the system SMS app for full inbox access

## Architecture

```
┌─────────────────────────────────────────────────┐
│                   UI Layer                       │
│  InboxScreen ─── DetailScreen ─── ModelDownload  │
│        (Jetpack Compose + Material 3)            │
├─────────────────────────────────────────────────┤
│               ViewModel Layer                    │
│     InboxViewModel    DetailViewModel            │
├─────────────────────────────────────────────────┤
│              Domain / Use Cases                  │
│   SMSSentryAI  ──  DeepCheckSession              │
│   (Rule-Based)     (LLM + Tools)                 │
├─────────────────────────────────────────────────┤
│                Data Layer                        │
│  SmsRepository  ModelRepository  Room DB         │
│  DataStore      PrivacyProxyClient               │
├─────────────────────────────────────────────────┤
│             Platform / Infra                     │
│  Hilt DI  ─  LiteRT-LM  ─  OkHttp              │
└─────────────────────────────────────────────────┘
```

**Key Libraries:**
- **Jetpack Compose** — Declarative UI
- **Hilt** — Dependency injection
- **Room** — Local SMS classification database
- **DataStore** — Preferences (theme, allowlist)
- **LiteRT-LM** — On-device LLM inference
- **OkHttp** — Network requests via privacy proxy
- **Kotlinx Serialization** — JSON parsing

## Requirements

- **Android Studio** Ladybug or newer
- **JDK 17** (bundled with Android Studio)
- **Android SDK 35** (compileSdk / targetSdk)
- **Min SDK 26** (Android 8.0 Oreo)
- **~2.7 GB free storage** on device for the AI model (downloaded on first Deep Check)

## Build Instructions

### Debug Build

```bash
# Windows
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

### Release Build

1. Create a `keystore.properties` file in the project root:
   ```properties
   storeFile=path/to/your/keystore.jks
   storePassword=your_store_password
   keyAlias=your_key_alias
   keyPassword=your_key_password
   ```
2. Uncomment the `signingConfigs` block in `app/build.gradle.kts`
3. Run:
   ```bash
   .\gradlew.bat assembleRelease
   ```

### Running Tests

```bash
.\gradlew.bat test
```

## Project Structure

```
SMSentry/
├── app/
│   ├── src/main/
│   │   ├── java/com/smssentry/
│   │   │   ├── data/           # Repositories, Room DB, DataStore
│   │   │   ├── deepcheck/      # LLM-based deep analysis pipeline
│   │   │   │   ├── model/      # LiteRT-LM engine wrapper
│   │   │   │   ├── prefilter/  # Fast-path heuristic filters
│   │   │   │   ├── session/    # Multi-step investigation session
│   │   │   │   ├── tools/      # WHOIS, reputation, brand checks
│   │   │   │   └── ui/         # Deep Check timeline & download UI
│   │   │   ├── di/             # Hilt modules
│   │   │   ├── sms/            # SMS receiver, MMS receiver
│   │   │   └── ui/             # Inbox & Detail screens
│   │   └── res/                # Resources, strings, themes
│   └── build.gradle.kts
├── cloudflare-worker/          # Privacy proxy worker source
├── gradle.properties
├── settings.gradle.kts
└── build.gradle.kts
```

## Security Features

SMSentry is designed with a privacy-first, defense-in-depth approach:

- **SQLCipher Encrypted Database** — All classification results and scam history are stored in an AES-256 encrypted Room database backed by Android Keystore
- **Certificate Pinning** — OkHttp certificate pinning for all Cloudflare Worker proxy communications, preventing MITM attacks
- **API Key Authentication** — Rotatable API keys stored in `local.properties` (gitignored), loaded at build time via `BuildConfig`
- **Privacy Proxy Architecture** — SMS content never leaves the device; only metadata queries (WHOIS, URL reputation) go through the Cloudflare Worker
- **SSRF Protection** — Dual-layer IP/URL validation prevents malicious URLs in SMS from targeting internal services
- **Prompt Injection Defense** — XML delimiter isolation protects the on-device LLM from adversarial instructions embedded in scam messages
- **No PII Logging** — Phone numbers and message bodies are never logged; only thread IDs and hashes appear in logcat
- **Backup Disabled** — `android:allowBackup=false` prevents SMS data extraction via ADB backup

For full details, see [docs/SECURITY.md](docs/SECURITY.md).

## Internationalization (i18n)

All user-facing strings are externalized to `res/values/strings.xml` for localization support. The app is fully prepared for translation into additional languages.

## License

All rights reserved.
