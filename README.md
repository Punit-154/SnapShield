<div align="center">

<img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
<img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
<img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white"/>
<img src="https://img.shields.io/badge/AI-On--Device%20%7C%20Gemma-FF6F00?style=for-the-badge&logo=google&logoColor=white"/>
<img src="https://img.shields.io/badge/Tests-39%2F39%20Passing-2E7D32?style=for-the-badge&logo=checkmarx&logoColor=white"/>

# 🛡️ SMSentry — SnapShield

### *Your phone's AI-powered SMS bodyguard. No cloud. No compromise.*

Built in 24 hours at **ArcNight Hackathon** · Hosted by **Microsoft Innovations Club, VIT Chennai**

</div>

---

## 📌 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [How It Works](#how-it-works)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Running Tests](#running-tests)
- [Roadmap](#roadmap)
- [Team](#team)
- [License](#license)

---

## Overview

**SMSentry** (SnapShield) is a privacy-first Android application that detects SMS scams in real time — entirely on your device. No SMS content ever leaves your phone. No cloud AI. No data leaks.

It uses a two-tier AI pipeline:

- **Tier 1** classifies every incoming SMS in under 50 milliseconds and displays an instant colour-coded verdict directly in your inbox.
- **Tier 2** launches a full agentic investigation when you need it, streaming live evidence cards and ending with a final verdict and recommended actions.

> *"Not a single character of your SMS is sent to any server."*

---

## Features

### 🟢 Tier 1 — Instant Guard

| Feature | Detail |
|---|---|
| **Speed** | < 50ms per classification |
| **Labels** | `SAFE` · `SUSPICIOUS` · `SCAM` |
| **Risk Score** | 0–100 numeric score per message |
| **Trigger** | Automatic on every incoming SMS |
| **UI** | Colour-coded shield badge on each inbox card |

### 🔍 Tier 2 — Deep Check

| Feature | Detail |
|---|---|
| **Engine** | Gemma-4-E4B-it (int4 quantised) via LiteRT |
| **Duration** | 10–20 seconds end-to-end |
| **Evidence** | Streams live — one card at a time |
| **Verdict** | Threat type + severity + recommended actions |
| **Offline** | FastPathFilter handles 6 rule classes without the LLM |

### 🔒 Privacy Architecture

- All AI inference runs **locally** on-device
- WHOIS queries are anonymised through a **self-hosted Cloudflare Worker** privacy proxy
- No account, no login, no telemetry

### 🎨 UI / UX

- Material Design 3 · Jetpack Compose
- Dark & Light themes (follows system, manual override available)
- Micro-animations: badge pop-in, risk bar transition, evidence fade-in + slide-up, Deep Check pulse
- Explainable AI panel: *"Why was this flagged?"*
- Demo Mode for reliable offline demonstrations

---

## How It Works

```
Incoming SMS
     │
     ▼
┌─────────────────────┐
│   FastPathFilter     │  ← 6 priority rules, < 1s, fully offline
│  1. Allowlist match  │
│  2. IP-address link  │
│  3. Phishing DB hit  │
│  4. Brand mismatch   │
│  5. History match    │
│  6. No URL / clean   │
└──────────┬──────────┘
           │ Unknown / Ambiguous
           ▼
┌─────────────────────────────────┐
│     DeepCheckSession            │
│   Gemma-4-E4B-it Agentic Loop  │
│                                 │
│  ToolExecutor (6 tools):        │
│  • lookup_allowlist             │
│  • search_personal_db           │
│  • check_reputation_db          │
│  • check_brand_mismatch         │
│  • whois_lookup (via proxy)     │
│  • compare_official_site        │
│                                 │
│  → Streams DeepCheckUpdates     │
│  → Emits FinalVerdict           │
└──────────────────────────────────┘
           │
           ▼
  VerdictCard + Recommended Actions
  (Block Sender / Report Spam / Delete / Contact Official Support)
```

### Threat Types Detected

`credential_theft` · `parcel_scam` · `fake_job` · `fake_bank` · `investment_fraud`

### Evidence Severity Levels

`LOW` · `MEDIUM` · `HIGH` · `CRITICAL`

---

## Architecture

SMSentry follows a clean, layered architecture with strict separation between the AI engine and the UI layer. The UI knows nothing about DistilBERT, Gemma, WHOIS, or classification pipelines — it only talks to `SMSSentryAI`.

```
com.smssentry/
├── deepcheck/
│   ├── prefilter/        # FastPathFilter — 6-rule fast decision engine
│   ├── session/          # DeepCheckSession, DeepCheckUpdate, PromptTemplates
│   ├── tools/            # ToolDefinitions, ToolExecutor, BrandMismatchHeuristic
│   ├── data/             # Room DB — AllowlistEntry, HistoryEntry, ReputationDb
│   ├── model/            # ModelManager, LlmInferenceEngine interface, LiteRtLmEngine
│   ├── proxy/            # PrivacyProxyClient (Cloudflare Worker)
│   └── util/             # DomainMatchUtil, HashUtil
├── ui/
│   ├── inbox/            # InboxScreen, ModelStatusBadge
│   ├── detail/           # DetailScreen, DetailViewModel, DeepCheckTimeline
│   ├── components/       # ShieldBadge, RiskScoreBar, EvidenceCard, VerdictCard,
│   │                     #   ProgressIndicator, PrivacyIndicator, DemoModeBanner
│   ├── theme/            # Material 3 colour scheme, typography, dark/light
│   └── navigation/       # NavGraph
├── data/
│   ├── models/           # SMS data classes
│   ├── mock/             # MockSMSSentryAI for development & demo
│   └── repository/       # SMS repository
└── di/
    └── AppModule.kt      # Hilt dependency injection
```

### Key Design Decisions

**LlmInferenceEngine interface** — The real Gemma model is hidden behind an interface. This means the real model can be swapped in without touching the UI, and `MockLlmEngine` enables full UI development and demo mode without loading a 2.7 GB model.

**List-based update stream** — `DeepCheckSession` emits into a `MutableStateFlow<List<DeepCheckUpdate>>`. Evidence accumulates rather than replacing, giving users a live running timeline — not just the latest status.

**Compile-first discipline** — Every sprint was verified with `./gradlew :app:compileDebugKotlin` before proceeding. No placeholder `TODO()` in any reachable code path.

**Full offline fallback** — If the LLM times out or errors, a rule-based fallback heuristic always produces a verdict. The app never freezes or crashes on model failure.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose · Material Design 3 |
| **AI — Tier 1** | On-device lightweight classifier |
| **AI — Tier 2** | Gemma-4-E4B-it (int4) via LiteRT-LM |
| **Database** | Room (AllowlistEntry, HistoryEntry) |
| **Networking** | OkHttp · Cloudflare Worker (WHOIS proxy) |
| **DI** | Hilt |
| **Serialisation** | kotlinx.serialization |
| **Async** | Kotlin Coroutines · StateFlow |
| **Min SDK** | Android 8.0 (API 26) |
| **Target SDK** | Android 15 (API 35) |

---

## Getting Started

### Prerequisites

- Android Studio Meerkat (2024.3.1) or newer
- JDK 17+
- Android SDK (API 35)
- A physical device or emulator (API 26+)

> **Note on the AI model:** Deep Check uses Gemma-4-E4B-it (~2.7 GB). The app downloads it on first launch over Wi-Fi. To run the app without the model, use **Demo Mode** (see below).

### Clone & Build

```bash
git clone https://github.com/Punit-154/SMSentry.git
cd SMSentry
git checkout Final
```

Open the project in Android Studio and let Gradle sync. Then run:

```bash
./gradlew :app:assembleDebug
```

Install on a connected device:

```bash
./gradlew :app:installDebug
```

### Demo Mode (No Model Required)

SMSentry includes a built-in Demo Mode that uses `MockLlmEngine` to simulate the full Deep Check investigation flow without downloading the Gemma model. This is ideal for hackathon presentations and development.

Enable it from the app Settings → **Enable Demo Mode**.

A banner is displayed: *"🔬 DEMO MODE – Mock data / Instant investigation"*

### Cloudflare Worker (Optional)

The WHOIS lookup and site comparison tools route through a self-hosted Cloudflare Worker for privacy. To set this up:

```bash
cd cloudflare-worker/
npm install -g wrangler
wrangler deploy
```

Then add the deployed URL to `local.properties`:

```properties
PROXY_BASE_URL=https://your-worker.workers.dev
```

If the proxy is not configured, WHOIS and site-compare tools degrade gracefully and the remaining 4 offline tools still function.

---

## Running Tests

```bash
./gradlew :app:testDebugUnitTest
```

### Test Results

| Suite | Tests | Status |
|---|---|---|
| `FastPathFilterTest` | 8 | ✅ All passing |
| `DeepCheckSessionTest` | 6 | ✅ All passing |
| `VerdictParserTest` | 8 | ✅ All passing |
| `BrandMismatchHeuristicTest` | 5 | ✅ All passing |
| `DomainMatchUtilTest` | 9 | ✅ All passing |
| `HashUtilTest` | 3 | ✅ All passing |
| **Total** | **39** | **0 failures** |

---

## Roadmap

### ✅ Phase 1 — Hackathon MVP (Complete)
- Tier 1 instant SMS classification with risk scores
- Tier 2 Deep Check agentic investigation with live evidence streaming
- FastPathFilter (6 rules, fully offline)
- Privacy proxy for WHOIS and site comparison
- Full Material 3 UI with dark/light theme
- Demo Mode for reliable demonstrations
- 39 unit tests passing

### 🔲 Phase 2 — Post-Hackathon
- Explainable AI panel in production build
- Progress percentage during Deep Check
- Threat analytics dashboard
- Evidence severity visualisation improvements
- Real-device performance profiling (Snapdragon 7 Gen 3 target)

### 🔲 Phase 3 — Future
- SMS Inbox integration (system-level SMS access)
- Community threat sharing (opt-in, privacy-preserving)
- Weekly scam trend intelligence
- Real-time background protection service

---

## Team

Built in 24 hours at **ArcNight Hackathon** — *Microsoft Innovations Club, VIT Chennai*

| Name | GitHub |
|---|---|
| Punit | [@Punit-154](https://github.com/Punit-154) |
| *(Teammate 2)* | — |
| *(Teammate 3)* | — |

---

## License

```
MIT License

Copyright (c) 2026 SMSentry Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

---

<div align="center">

*🛡️ All analysis runs on your device — no SMS leaves your phone.*

**#timetomicrocraftyourself** · Built with ☕ and zero sleep at ArcNight 2026

</div>
