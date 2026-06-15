# Sprint 6 Handoff — Smart Scam-Aware Notifications

**Author:** Backend/Data Engineer  
**Date:** 2026-06-15  
**Commit:** `feat: smart scam-aware notifications`

---

## What Was Done

### Task 1: Notification Handling Audit
- ✅ `SmsReceiver.kt` exists and receives SMS via `SMS_DELIVER_ACTION` / `SMS_RECEIVED_ACTION`
- ✅ Incoming SMS is classified via `FastPathFilter.filter()` (checks allowlists, suspicious TLDs, IP URLs, history, personal learning)
- ✅ Scam warnings were shown, but only for SCAM — SUSPICIOUS was silently treated as safe

### Task 2: Smart Notification Text (Implemented)

| Verdict | Notification Title | Notification Body |
|---|---|---|
| **SCAM** | `⚠️ Possible scam from [displayName]` | Message preview (100 chars) + reason in expanded view |
| **SUSPICIOUS** | `⚡ Suspicious message from [displayName]` | Message preview (100 chars) |
| **SAFE / null** | `[displayName]` | Full message body (unchanged) |

**Files changed:**
- `app/src/main/java/com/smssentry/sms/NotificationHelper.kt` — Updated `showScamWarning()` text, added `showSuspiciousNotification()`
- `app/src/main/java/com/smssentry/sms/SmsReceiver.kt` — Refactored to tri-state dispatch via `when(filterResult?.verdict)`

### Task 3: Notification Channel Setup (Verified + Extended)
- ✅ `CHANNEL_NEW_SMS` ("New Messages") — `IMPORTANCE_HIGH`, vibration
- ✅ `CHANNEL_SCAM_WARNING` ("Scam Warnings") — `IMPORTANCE_HIGH`, red lights, strong vibration
- ✅ **NEW** `CHANNEL_SUSPICIOUS` ("Suspicious Messages") — `IMPORTANCE_HIGH`, orange lights, moderate vibration
- All channels created in `SMSSentryApp.onCreate()` via `NotificationHelper.createChannels()`

---

## Architecture Notes

### SmsReceiver Flow
```
SMS received → goAsync() → coroutine (Dispatchers.IO)
  → writeToSmsProvider()
  → classifySms() → FastPathFilter.filter() → PreFilterResult
  → when(verdict) {
      SCAM        → showScamWarning()           [scam_warning channel]
      SUSPICIOUS  → showSuspiciousNotification() [suspicious_warning channel]
      else        → showNewMessageNotification() [new_sms channel]
    }
  → broadcast ACTION_SMS_RECEIVED
```

### Key Design Decisions
1. **`classifySms()` is a pure function** — returns `PreFilterResult?` with no side effects. All notification dispatch happens in the caller's `when` block.
2. **No PII in logs** — error logs use `sender.hashCode()` as `threadHash`.
3. **Message preview truncated to 100 chars** for SCAM/SUSPICIOUS to avoid overly long notification text.
4. **Distinct notification IDs** — SCAM uses `"scam_$sender".hashCode()`, SUSPICIOUS uses `"suspicious_$sender".hashCode()`, normal uses `sender.hashCode()` to prevent collisions.

---

## What's Left / Gotchas

1. **FastPathFilter currently never returns `"SUSPICIOUS"`** — it returns `"SCAM"`, `"SAFE"`, or `null`. The SUSPICIOUS verdict comes from deeper analysis (`RealSMSSentryAI`, `DeepCheckSession`, `LlmInference`). If you want `SmsReceiver` to also fire suspicious notifications from the inline fast path, add SUSPICIOUS logic to `FastPathFilter.filter()` (e.g., for URLs with uncommon but not flagged TLDs, or low-confidence scam signals).

2. **DeepCheck async results** — When `DeepCheckSession` finishes a background investigation and returns SUSPICIOUS, its own UI updates the verdict card. If you want a *delayed* notification for background SUSPICIOUS findings, that would need to be wired from `DeepCheckSession` → `NotificationHelper.showSuspiciousNotification()`.

3. **Notification cancellation** — `cancelNotification()` only cancels by `sender.hashCode()` (normal notifs). SCAM and SUSPICIOUS notifications use different ID schemes and won't be cancelled by the existing `cancelNotification()`. If mark-as-read should also dismiss scam/suspicious notifications, update `cancelNotification()` to cancel all three ID variants.

4. **Android 13+ POST_NOTIFICATIONS permission** — Already declared in manifest and requested at runtime in `MainActivity`. No changes needed.

5. **Test coverage** — No unit tests were added. Consider testing:
   - `NotificationHelper` channel creation
   - `SmsReceiver.classifySms()` verdict routing (mock FastPathFilter)
   - Notification ID collision avoidance
