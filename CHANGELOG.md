# Changelog

All notable changes to SMSentry are documented in this file.

Format is based on [Keep a Changelog](https://keepachangelog.com/).

---

## [1.1.0] — Sprint 4 (2026-06-15)

### Added
- Comprehensive code documentation for `RealSMSSentryAI.kt`, `ConversationListViewModel.kt`, and `SmsRepository.kt`
- Security features section in README.md
- This CHANGELOG.md

### Fixed
- Documented deprecation warnings (`statusBarColor`/`navigationBarColor`) for future UI engineer resolution

---

## [1.0.3] — Sprint 3

### Changed
- **Classification overhaul**: Replaced flat keyword matching with weighted scoring system
  - Three-tier indicator weights (high=3, medium=2, low=1) replace binary keyword detection
  - Safe-sender detection: alphanumeric sender IDs (e.g., `VM-HDFCBK`) treated as business senders
  - Safe-content patterns: OTP, transaction, delivery messages auto-classified as safe
  - Higher threshold (score ≥ 4) required before flagging as SCAM — dramatically reduces false positives

### Fixed
- Back button navigation in chat/detail screens
- Crash prevention audit across all ViewModels:
  - Null-safe cursor column access in `SmsRepository`
  - Guarded coroutine launches with try/catch in ContentObserver
  - Duplicate thread ID guard (`.distinctBy`) to prevent LazyColumn key crash
- `LazyColumn` performance: added `contentType` for better view recycling

---

## [1.0.2] — Sprint 2

### Changed
- API key rotation support: keys loaded from `local.properties` via `BuildConfig` at build time
- Completed i18n migration for `DetailScreen`, `SettingsScreen`, `BlockedNumbersScreen`, and `ComposeSmsScreen`

### Fixed
- Crash prevention: pre-Q SMS app support, force unwrap elimination
- Duplicate string resource conflict resolved
- Kotlin plugin configuration restored after build break

### Security
- API key auth wired end-to-end (app → Cloudflare Worker)
- Hardened recent changes per security audit

---

## [1.0.1] — Sprint 1

### Added
- Block sender option in chat action sheet
- Global message search with SQL injection prevention (LIKE wildcard escaping)
- Certificate pinning for Cloudflare Worker proxy
- Delivery reports support
- i18n string resources foundation (`strings.xml` externalization)
- Personal learning database for always-learning AI
- Notification reply and mark-as-read actions
- Conversation pinning with swipe-right gesture
- Long-press actions, delete conversation, undo delete
- Contact photo resolution in conversation list

### Security
- SQLCipher encrypted Room database (AES-256 + Android Keystore)
- 6 HIGH severity fixes: SSRF protection, prompt injection defense, PII log sanitization
- Pagination + injection defense + accessibility hardening
- `allowBackup=false` to prevent data extraction

### Fixed
- Notification deep-linking ANR
- Dynamic progress bar and UI/UX redesign
- Auto-refresh on new message arrival via ContentObserver

---

## [1.0.0] — Initial Release

### Added
- Full SMS app with conversations, chat, settings, and notifications
- AI-powered scam detection (rule-based + on-device LLM via LiteRT-LM)
- Material 3 UI with dark/light theme support
- Default SMS app role support
- Cloudflare Worker privacy proxy for external lookups
