# SMSSentry UI Design Specification (V2)

## Overview

This document defines the complete UI architecture for the SMSSentry Android application.

The UI is designed to work independently from the AI implementation using a mock SMSSentryAI service.

Goals:

* Immediate scam visibility
* Simple user experience
* Streaming investigation experience
* Easy swap between mock and real AI
* Strong hackathon demo impact

---

# Design Philosophy

## Clarity First

Users should immediately understand whether an SMS is safe or dangerous.

## Non-Intrusive

Tier 1 classification appears automatically.

Tier 2 investigation is user-triggered.

## Detective Mode

Deep Check should feel like a real investigation rather than a loading screen.

## Privacy First

Clearly communicate that SMS processing happens on-device.

## Material You

Use Material Design 3 with Jetpack Compose.

---

# Application Structure

## Screen 1: SMS Inbox

Purpose:

* Display all SMS messages
* Show instant scam classification
* Display risk score

Entry Point:

* App launch

---

## Screen 2: SMS Detail + Deep Check

Purpose:

* Full SMS view
* Classification reasoning
* Deep Check investigation
* Evidence timeline
* Final verdict

Entry Point:

* Tap SMS from Inbox

---

## Optional Screen 3: Security Dashboard

Purpose:

* Analytics
* Threat history
* Protection statistics

---

# SMS List Design

Each row contains:

* Sender
* Timestamp
* SMS preview
* Classification badge
* Risk score

Examples:

🟢 SAFE (12/100)

🟠 SUSPICIOUS (64/100)

🔴 SCAM (97/100)

Tap row to open Detail screen.

---

# Detail Screen Layout

## Message Card

Displays:

* Sender
* Timestamp
* Full SMS text

---

## Classification Card

Displays:

* Label
* Confidence
* Risk score
* Reasoning

Example:

Risk Score: 97/100

Reason:

Bank fraud pattern detected.

---

## Privacy Card

Displays:

🛡 Running On Device

No SMS content leaves your phone.

---

## Deep Check Button

Primary CTA:

Deep Check

Icon:

Magnifying glass or detective icon.

---

# Investigation Area

Displays streamed updates.

Components:

* Progress bar
* Current step
* Evidence timeline
* Final verdict

---

# Progress Section

Example:

80%

Checking scam databases...

Visual:

[████████░░]

---

# Evidence Timeline

Evidence accumulates.

Example:

10:21:03
Domain extracted

10:21:05
WHOIS lookup completed

10:21:07
Domain registered 2 days ago

10:21:09
Found in scam database

---

# Evidence Card

Fields:

* Source
* Detail
* Severity

Severity:

* LOW
* MEDIUM
* HIGH
* CRITICAL

Example:

HIGH

Domain registered 2 days ago

---

# Explainable AI Section

Expandable card.

Example:

Why was this flagged?

✓ Urgent language detected

✓ Suspicious URL detected

✓ Banking scam pattern matched

---

# Final Verdict Card

Displays:

* Large status icon
* Summary
* Threat type
* Threat level
* Actions

Example:

Credential Theft Scam

Threat Level:

CRITICAL

---

# Recommended Actions

Examples:

* Block Sender
* Report Spam
* Delete SMS
* Open Official Website
* Contact Official Support

---

# Dashboard Screen (Optional)

Metrics:

* Messages Scanned
* Scams Detected
* Suspicious Messages
* Threat Categories
* Weekly Trends

Example:

Messages Scanned: 145

Scams Detected: 23

Protection Rate: 96%

---

# UI State Model

Recommended:

```kotlin
data class InvestigationUiState(
    val progress: Int = 0,
    val currentStep: String? = null,
    val evidence: List<EvidenceItem> = emptyList(),
    val verdict: DeepCheckVerdict? = null,
    val error: String? = null
)
```

Evidence should accumulate instead of replacing previous state.

---

# Mock Development Strategy

1. Implement MockSMSSentryAI
2. Build SMS Inbox
3. Build Detail Screen
4. Build Investigation Timeline
5. Add Animations
6. Add Error Handling
7. Swap to Real AI

No UI code should depend directly on model internals.

---

# Hackathon Deliverables

Required:

* SMS List
* Classification Badges
* Deep Check
* Evidence Timeline
* Final Verdict

Recommended:

* Risk Scores
* Progress Bar
* Explainable AI
* Privacy Indicator

Stretch Goals:

* Analytics Dashboard
* Threat History
* Demo Mode

---

# Demo Flow

1. Open SMS Inbox
2. Show instant scam detection
3. Open suspicious SMS
4. Launch Deep Check
5. Stream evidence live
6. Display final verdict
7. Trigger safety actions

This flow demonstrates both AI speed and investigation depth within 30–60 seconds, making it ideal for hackathon judging.
