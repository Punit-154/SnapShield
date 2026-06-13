# SMSSentryAI Interface Specification

## Overview

SMSSentryAI is an on-device SMS scam detection system consisting of:

* Tier 1: Instant SMS Classification
* Tier 2: Deep Investigation Engine

The frontend communicates only with local service classes.

No HTTP APIs, backend servers, or cloud dependencies are exposed to the UI layer.

---

# High-Level Architecture

## Core Components

### SMSSentryAI

Singleton service that wraps all AI functionality.

Responsibilities:

* Model initialization
* Tier 1 classification
* Tier 2 investigations
* Session management

### ClassificationResult

Represents an instant Tier 1 classification result.

### DeepCheckSession

Represents an active investigation that streams progress updates.

---

# Data Models

## ClassificationResult

```kotlin
data class ClassificationResult(
    val label: String,
    val confidence: Float,
    val reasoning: String,
    val isScam: Boolean
)
```

### Fields

| Field      | Description                |
| ---------- | -------------------------- |
| label      | SAFE, SCAM, or SUSPICIOUS  |
| confidence | Confidence score (0.0–1.0) |
| reasoning  | Human-readable explanation |
| isScam     | Convenience flag           |

---

## DeepCheckVerdict

```kotlin
data class DeepCheckVerdict(
    val isScam: Boolean,
    val summary: String,
    val threatType: String?,
    val evidence: List<EvidenceItem>,
    val recommendedActions: List<String>
)
```

### Example Threat Types

* credential_theft
* parcel_scam
* fake_job
* fake_bank
* investment_fraud

---

## EvidenceItem

```kotlin
data class EvidenceItem(
    val source: String,
    val detail: String
)
```

### Example

```json
{
  "source": "WHOIS",
  "detail": "Domain registered 2 days ago in Russia"
}
```

---

## DeepCheckUpdate

```kotlin
sealed class DeepCheckUpdate {

    data class Step(
        val message: String
    ) : DeepCheckUpdate()

    data class FoundEvidence(
        val item: EvidenceItem
    ) : DeepCheckUpdate()

    data class FinalVerdict(
        val verdict: DeepCheckVerdict
    ) : DeepCheckUpdate()

    data class Error(
        val reason: String
    ) : DeepCheckUpdate()
}
```

---

# Public API

## Initialization

```kotlin
SMSSentryAI.initialize(
    context: Context,
    callback: (Boolean) -> Unit
)
```

### Purpose

Loads all required models and resources.

### Callback

```kotlin
true
```

Models loaded successfully.

```kotlin
false
```

Initialization failed.

---

# Tier 1 – Instant Classification

```kotlin
SMSSentryAI.classifySMS(
    smsText: String,
    callback: (ClassificationResult) -> Unit
)
```

### Behaviour

* Runs automatically for every incoming SMS
* Returns almost instantly
* No loading UI required

### Expected Latency

< 50 ms

---

# Tier 2 – Deep Check

```kotlin
SMSSentryAI.startDeepCheck(
    smsText: String,
    listener: DeepCheckListener
): DeepCheckSession
```

### Purpose

Performs a full investigation of suspicious SMS messages.

### Behaviour

* Runs asynchronously
* Streams updates to the UI
* Produces evidence incrementally
* Ends with verdict or error

### Expected Duration

10–20 seconds

---

## DeepCheckListener

```kotlin
interface DeepCheckListener {
    fun onUpdate(update: DeepCheckUpdate)
}
```

---

## Session Cancellation

```kotlin
session.cancel()
```

Stops the investigation immediately.

No additional updates should be emitted after cancellation.

---

# UI Expectations

## Tier 1

### Trigger

* New SMS arrives
* User opens SMS

### UI

* Green Shield → SAFE
* Red Shield → SCAM
* Orange Shield → SUSPICIOUS

Tap badge to reveal reasoning.

---

## Tier 2

### Trigger

User taps:

```text
Deep Check
```

### UI Flow

1. Show detective animation
2. Stream step messages
3. Display evidence cards
4. Present final verdict
5. Offer recommended actions

### Example Timeline

```text
Extracting sender details...
Checking domain registration...
Comparing against official domain...
Searching scam databases...
Forming conclusion...
```

---

# Mock Data

## Tier 1 Response

```json
{
  "label": "SCAM",
  "confidence": 0.97,
  "reasoning": "Bank fraud pattern detected; link domain is suspicious",
  "isScam": true
}
```

---

## Tier 2 Stream

```text
Step:
Extracting link and sender details...

Step:
Performing WHOIS lookup...

FoundEvidence:
Domain registered 2 days ago in Russia

Step:
Comparing with official HSBC domain...

FoundEvidence:
Real HSBC domain is hsbc.com

Step:
Searching scam databases...

FoundEvidence:
URL found in SmishTank

Step:
Forming conclusion...

FinalVerdict:
Credential-theft scam detected
```

---

# Frontend Integration Checklist

## Required

* Implement DeepCheckListener
* Render live update stream
* Add SMS classification badge
* Add Deep Check button
* Handle session cancellation
* Handle error states

## Optional Enhancements

* Progress bar
* Risk score
* Threat severity indicators
* Animated evidence cards
* Share report functionality

---

# Design Principle

The UI layer should know nothing about:

* DistilBERT
* Gemma
* WHOIS
* Scam databases
* Classification pipelines

The frontend communicates only through:

```text
SMSSentryAI
```

This guarantees clean separation of concerns and allows AI implementation changes without affecting the UI.
