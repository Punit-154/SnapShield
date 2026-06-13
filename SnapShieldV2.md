# Recommended Improvements (V2)

These improvements are not required for the MVP but are strongly recommended for future iterations.

---

## 1. Add Risk Score

Current output:

```kotlin
label = "SCAM"
```

Recommended:

```kotlin
data class ClassificationResult(
    val label: String,
    val confidence: Float,
    val riskScore: Int, // 0-100
    val reasoning: String,
    val isScam: Boolean
)
```

Example:

```text
SCAM
Risk Score: 97/100
```

### Why

* Easier for users to understand.
* Makes the product appear more professional.
* Allows future filtering and prioritization.

---

## 2. Model Status Monitoring

Current initialization:

```kotlin
initialize(callback: (Boolean) -> Unit)
```

Recommended:

```kotlin
sealed class ModelStatus {
    object Loading : ModelStatus()
    object Ready : ModelStatus()
    data class Error(val reason: String) : ModelStatus()
}
```

### Why

* Easier debugging.
* Better user experience during startup.
* Prevents silent failures.

---

## 3. Investigation Progress Percentage

Current:

```kotlin
Step("Checking domain age...")
```

Recommended:

```kotlin
data class Progress(
    val percent: Int,
    val message: String
)
```

Example:

```text
25% - Extracting URLs
50% - Checking domain age
75% - Comparing with official site
100% - Verdict generated
```

### Why

Users feel the app is actively working rather than frozen.

---

## 4. Evidence Severity Levels

Current:

```kotlin
data class EvidenceItem(
    val source: String,
    val detail: String
)
```

Recommended:

```kotlin
data class EvidenceItem(
    val source: String,
    val detail: String,
    val severity: String
)
```

Severity values:

```text
LOW
MEDIUM
HIGH
CRITICAL
```

### Why

Allows evidence to be prioritized visually.

Example:

```text
HIGH
Domain registered 1 day ago
```

---

## 5. Estimated Investigation Time

Add:

```kotlin
estimatedDurationSeconds: Int
```

Example:

```text
Estimated time remaining: 12 seconds
```

### Why

Improves user confidence and reduces abandonment.

---

## 6. Offline/Online Capability Indicator

Add:

```kotlin
enum class InvestigationMode {
    OFFLINE,
    ONLINE
}
```

### Why

The project's key selling point is privacy.

Users should know:

```text
Running completely on device
```

or

```text
Using secure online verification
```

---

## 7. SMS History Dashboard

Future feature:

```kotlin
data class ScanHistory(
    val smsId: String,
    val timestamp: Long,
    val verdict: String
)
```

### Why

Users can review previously detected scams.

Potential metrics:

* Total SMS scanned
* Scams blocked
* Threat categories detected
* Weekly threat trends

---

## 8. Explainable AI Panel

Add:

```kotlin
val aiExplanation: List<String>
```

Example:

```text
Reason 1:
Urgent language detected

Reason 2:
Suspicious URL found

Reason 3:
Matches known banking scam pattern
```

### Why

Builds trust and helps judges understand how the model reached a decision.

---

## 9. One-Tap Safety Actions

Recommended actions:

* Block Sender
* Report Spam
* Delete Message
* Open Official Website
* Call Official Customer Support

### Why

Transforms detection into immediate protection.

---

## 10. Demo Mode (Hackathon Feature)

Add:

```kotlin
SMSSentryAI.enableDemoMode()
```

### Purpose

Allows the team to demonstrate realistic scam detections without requiring internet connectivity or live attacks.

### Why

Hackathon demos frequently fail because of poor network conditions.

A demo mode guarantees a reliable presentation.

---

# Future Roadmap

## Phase 1 (Hackathon MVP)

* Tier 1 Classification
* Tier 2 Deep Check
* Evidence Streaming
* Final Verdict

## Phase 2

* Risk Scores
* Progress Tracking
* Explainable AI
* Threat Analytics

## Phase 3

* SMS Inbox Integration
* Scam Trend Intelligence
* Community Threat Sharing
* Real-Time Protection

---

# Judge-Focused Value

These additions improve scoring in:

* Technical Complexity
* User Experience
* Explainability
* Scalability
* Product Readiness
* Real-World Impact

While not required for the MVP, implementing even 2–3 of these features would significantly strengthen the final hackathon submission.
