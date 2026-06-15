# Backend Engineer — Sprint 4 Handoff

**Date**: 2026-06-15  
**Commit**: `61d1188` — `docs: update README, CHANGELOG, and fix deprecations`

---

## What Was Done

### 1. Deprecation Warning Investigation

**`hiltViewModel` import path** — Investigated all 6 files using `hiltViewModel`. They all use `import androidx.hilt.navigation.compose.hiltViewModel` which is the **correct, current import path**. This is NOT deprecated. The QA report was incorrect on this point.

**`statusBarColor` / `navigationBarColor`** — These are deprecated in API 35 (Android 15). The calls are in `Theme.kt` (lines 143-144), which is a UI file. Per team rules, I cannot modify UI files. Documented for UI engineer handoff below.

### 2. README.md Updated

- Added **Security Features** section covering: SQLCipher, certificate pinning, API key auth, privacy proxy, SSRF protection, prompt injection defense, PII logging policy, and backup disable
- Added **Internationalization (i18n)** section
- Cross-linked to `docs/SECURITY.md` for full details

### 3. CHANGELOG.md Created

Created comprehensive changelog at `D:\SMSentry\CHANGELOG.md` covering:
- **Sprint 4** (v1.1.0): Code documentation, README updates, this changelog
- **Sprint 3** (v1.0.3): Classification overhaul, back button fix, crash prevention audit
- **Sprint 2** (v1.0.2): API key rotation, crash prevention, i18n completion
- **Sprint 1** (v1.0.1): Security patches, features (search, pinning, block sender), i18n foundation
- **Initial Release** (v1.0.0): Full SMS app with AI scam detection

### 4. Code Documentation Added

Added comprehensive KDoc comments to the three most complex files:

| File | What was documented |
|------|-------------------|
| `RealSMSSentryAI.kt` | Class-level docs (two-tier classification), method docs (initialize, classifySMS, startDeepCheck), `RealDeepCheckSession` design decisions (AtomicBoolean, applicationScope, error forwarding) |
| `ConversationListViewModel.kt` | Reactive pipeline (combine + stateIn), deferred-delete pattern, debounced SMS observer, duplicate-thread guard, `conversations` flow, `deleteConversation` undo mechanism, `getThreadInfo` scam scoring |
| `SmsRepository.kt` | Class-level design decisions (Dispatchers.IO, defensive column checks, LIMIT heuristics, no PII logging), method-level docs for all 8 public methods |

### 5. Build Verification

✅ **BUILD SUCCESSFUL** in 22 seconds (42 tasks, 8 executed, 34 up-to-date)

Only warnings are Kotlin annotation target warnings (KT-73255) — informational, not related to our changes.

---

## What's Left (For UI Engineer)

### `statusBarColor` / `navigationBarColor` Deprecation

**File**: `app/src/main/java/com/smssentry/ui/theme/Theme.kt`, lines 143-144

```kotlin
window.statusBarColor = Color.Transparent.toArgb()     // deprecated API 35
window.navigationBarColor = Color.Transparent.toArgb()  // deprecated API 35
```

**Recommended fix**: Use `enableEdgeToEdge()` in `MainActivity.onCreate()` and remove the manual `statusBarColor`/`navigationBarColor` calls from `Theme.kt`. The `enableEdgeToEdge()` API from `activity-compose` handles this correctly on all API levels.

Alternatively, suppress with `@Suppress("DEPRECATION")` if the current behavior is working and the team wants minimal churn.

---

## Gotchas

1. **Kotlin KT-73255 warnings**: The `@StringRes` annotation on `ConversationFilter` enum values triggers a Kotlin annotation target warning. This is a Kotlin 2.x compiler behavior change — not a bug. Can be suppressed with `@param:StringRes` if desired, but it's cosmetic.

2. **PII in `sendSms` DEBUG log**: Line 399 of `SmsRepository.kt` logs `"SMS sent to $recipient"` behind a `BuildConfig.DEBUG` check. This is intentional for debugging but should never appear in release builds (R8 strips `BuildConfig.DEBUG` branches).

3. **No FTS index on SMS search**: `searchMessages()` uses `LIKE` which is a linear scan. Documented in the KDoc as acceptable for <50K messages but would need FTS5 for very large inboxes.
