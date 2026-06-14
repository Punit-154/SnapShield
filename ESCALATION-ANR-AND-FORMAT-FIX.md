# SMSentry — ANR Fix & Paragraph Format Fix
## Escalation Brief for Higher Model

---

## Context

SMSentry is an Android SMS fraud detection app using on-device AI (LiteRT-LM with ~2.7GB Gemma model). Running on a Samsung A14 (budget phone). Two critical issues:

1. **Constant "App isn't responding" (ANR) dialogs** — main thread is blocked
2. **Deep Check responses not in paragraph format** — LLM output is bullet-pointed/structured instead of flowing paragraphs

---

## Issue 1: ANR Root Causes (Priority Order)

### CRITICAL — Must Fix

#### A. SmsRepository — Synchronous ContentResolver on Main Thread

**File**: `D:\SMSentry\app\src\main\java\com\smssentry\data\repository\SmsRepository.kt`

- `getInboxMessages(limit: Int)` (line ~20) — plain non-suspend function, calls `contentResolver.query()` synchronously
- `getMessageById(id: String)` (line ~70) — same pattern

**Called on main thread from**:
- `InboxViewModel.kt` line 157: `val realMessages = smsRepository.getInboxMessages()` inside `viewModelScope.launch` (defaults to `Dispatchers.Main`)
- `DetailViewModel.kt` line 59: `val found = smsRepository.getMessageById(smsId)` inside `viewModelScope.launch`

**Impact**: 100-500ms blocking on Samsung A14 for 50 SMS messages

#### B. ReputationDb — Synchronous SQLite Copy in init block

**File**: `D:\SMSentry\app\src\main\java\com\smssentry\deepcheck\data\ReputationDb.kt`

- `init` block (lines 12-33): copies `phish_domains.db` from assets to internal storage, then opens SQLite — ALL synchronous
- Hilt `@Singleton`, injected into `RealSMSSentryAI` and `DetailViewModel`
- First construction triggers on main thread during Activity/ViewModel creation

**Impact**: 200-1000ms+ blocking on first launch

#### C. OfficialSitesRepository — Synchronous Asset Read in Constructor

**File**: `D:\SMSentry\app\src\main\java\com\smssentry\deepcheck\data\OfficialSitesRepository.kt`

- Constructor (lines 12-17): `context.assets.open("official_sites.json")` + `reader.readText()` + JSON parse — ALL synchronous
- Hilt `@Singleton`, injected into `RealSMSSentryAI`, `DetailViewModel`, `ToolExecutor`

**Impact**: 50-200ms blocking

#### D. LiteRtLmEngine.createSession() — Synchronous Native LLM Call

**File**: `D:\SMSentry\app\src\main\java\com\smssentry\deepcheck\ModelManager.kt` lines 103-111

- `createSession()` is non-suspend, calls `eng.createConversation()` + `conv.sendMessage(Message.of(systemPrompt))` synchronously
- Called from `DeepCheckSession.run()` line 100, which is invoked from `DetailViewModel.startDeepCheck()` inside `viewModelScope.launch` (main thread)

**Impact**: 500ms-2s+ blocking

### HIGH — Should Fix

#### E. InboxViewModel.init — Immediate SMS Load

**File**: `D:\SMSentry\app\src\main\java\com\smssentry\ui\inbox\InboxViewModel.kt`

- `init` block (lines 83-87): calls `loadMessages()` which triggers `refreshMessages()` -> `getInboxMessages()` on main thread

#### F. SmsContentObserver — Main Thread Handler

**File**: `D:\SMSentry\app\src\main\java\com\smssentry\sms\SmsContentObserver.kt`

- Line 10: `ContentObserver(Handler(Looper.getMainLooper()))` — observer runs on main looper
- Every SMS arrival triggers synchronous ContentProvider query on main thread

#### G. DetailViewModel.loadMessage() — Synchronous on Main

**File**: `D:\SMSentry\app\src\main\java\com\smssentry\ui\detail\DetailViewModel.kt`

- Line 59: `smsRepository.getMessageById(smsId)` inside `viewModelScope.launch` with no dispatcher switch

### MODERATE — Nice to Fix

- `InboxViewModel.checkDefaultSmsApp()` — `isRoleHeld()` binder IPC on main (10-50ms)
- `ModelRepository.isModelDownloaded()` — `File.mkdirs()` + `exists()` on main (5-50ms)
- `DeepCheckDatabase.getInstance()` — Room schema validation on first call (50-200ms)

---

## Issue 2: Deep Check Paragraph Format

### Root Causes

#### A. Weak Prompt Engineering

**File**: `D:\SMSentry\app\src\main\java\com\smssentry\deepcheck\session\PromptTemplates.kt` (lines 3-32)

The system prompt says "Your educational explanation paragraph here" but also says "name specific red flags" and "end with clear recommended actions." Small on-device LLMs produce bullet points when asked to "name" things. The prompt does NOT explicitly say:
- "Write ONLY in paragraph form"
- "Do NOT use bullet points, numbered lists, or any list format"
- "Combine all points into flowing sentences"

#### B. Zero Post-Processing

**File**: `D:\SMSentry\app\src\main\java\com\smssentry\deepcheck\session\LlmInference.kt` line 117

```kotlin
var explanation = rawOutput.substringAfter(match.value).trim()
```

No cleanup of bullet markers (`-`, `*`, `1.`), no Markdown stripping, no newline collapsing.

#### C. Summary Shows First 150 Chars of Bullet Text

**File**: `D:\SMSentry\app\src\main\java\com\smssentry\deepcheck\session\DeepCheckSession.kt` line 377

```kotlin
summary = parsed.explanation.take(150)...
```

If LLM produces bullets, the first 150 chars of bullet text becomes the prominent VerdictCard summary.

### Recommended Fixes

1. **Prompt fix** (`PromptTemplates.kt`): Add explicit instruction: "Write the explanation as a single flowing paragraph. Do NOT use bullet points, numbered lists, dashes, or any list structure. Combine all observations into connected sentences."

2. **Post-processing** (`LlmInference.kt` or new utility): Strip list markers, collapse newlines into spaces, remove Markdown formatting

3. **Summary fix** (`DeepCheckSession.kt`): Take summary from first sentence or first clause, not raw first 150 chars

---

## File List (All Files That Need Changes)

### ANR Fixes
1. `D:\SMSentry\app\src\main\java\com\smssentry\data\repository\SmsRepository.kt` — make suspend or wrap callers
2. `D:\SMSentry\app\src\main\java\com\smssentry\deepcheck\data\ReputationDb.kt` — lazy init on IO
3. `D:\SMSentry\app\src\main\java\com\smssentry\deepcheck\data\OfficialSitesRepository.kt` — lazy init on IO
4. `D:\SMSentry\app\src\main\java\com\smssentry\deepcheck\ModelManager.kt` — suspend createSession()
5. `D:\SMSentry\app\src\main\java\com\smssentry\ui\inbox\InboxViewModel.kt` — IO dispatcher for SMS load
6. `D:\SMSentry\app\src\main\java\com\smssentry\ui\detail\DetailViewModel.kt` — IO dispatcher for SMS query
7. `D:\SMSentry\app\src\main\java\com\smssentry\sms\SmsContentObserver.kt` — background handler

### Paragraph Format Fixes
8. `D:\SMSentry\app\src\main\java\com\smssentry\deepcheck\session\PromptTemplates.kt` — explicit paragraph instruction
9. `D:\SMSentry\app\src\main\java\com\smssentry\deepcheck\session\LlmInference.kt` — post-processing
10. `D:\SMSentry\app\src\main\java\com\smssentry\deepcheck\session\DeepCheckSession.kt` — summary extraction

---

## Build Environment

- AGP: 9.2.1, Gradle: 9.5, Kotlin: 2.2.10
- LiteRT-LM SDK: `com.google.ai.edge.litertlm:litertlm-android:0.13.1`
- Device: Samsung A14 (00056345M001042)
- Working directory: `D:\SMSentry`
- Build command: `.\gradlew.bat assembleDebug`
- Test command: `.\gradlew.bat test`

---

## Verification Plan

1. After ANR fixes: `.\gradlew.bat test` — all tests pass
2. Install on device: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
3. Clear logcat: `adb logcat -c`
4. Launch app and navigate through inbox → detail → deep check
5. Check for "Skipped N frames" warnings in logcat
6. Verify no ANR dialogs appear
7. For paragraph format: trigger Deep Check on a message, verify explanation is flowing paragraph text without bullet points
