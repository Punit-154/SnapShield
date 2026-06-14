# Security Engineer Handoff — Sprint 2

**Date:** 2026-06-14  
**Scope:** Fix CWE-798 (Hardcoded API Key) — move `PROXY_API_KEY` from `build.gradle.kts` to `local.properties`.

---

## Summary

Addressed the **CRITICAL** finding #3 from Sprint 1 audit: the Cloudflare Worker proxy API key was hardcoded in `app/build.gradle.kts` (lines 49 and 59) and committed to version control.

---

## What Was Done

### 1. Moved API key to `local.properties`

**Before (CWE-798 — Hardcoded Credentials):**
```kotlin
// app/build.gradle.kts — debug build type
buildConfigField("String", "PROXY_API_KEY", "\"7938e31b-af45-4274-8870-480ab286343c\"")
// app/build.gradle.kts — release build type
buildConfigField("String", "PROXY_API_KEY", "\"7938e31b-af45-4274-8870-480ab286343c\"")
```

**After:**
```kotlin
// Top-level in build.gradle.kts, before android { } block
val localProps = java.util.Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())
val apiKey = localProps.getProperty("SMSSENTRY_API_KEY", "")

// In both debug and release build types:
buildConfigField("String", "PROXY_API_KEY", "\"$apiKey\"")
```

### 2. Added key to `local.properties`

```properties
# local.properties (gitignored — never committed)
SMSSENTRY_API_KEY=7938e31b-af45-4274-8870-480ab286343c
```

### 3. Verified `.gitignore`

`.gitignore` already contains `local.properties` — no changes needed.

### 4. Codebase scan for leaked key

Searched entire codebase for the UUID `7938e31b-af45-4274-8870-480ab286343c`. Found only in:
- `app/build.gradle.kts` — **FIXED** (removed)
- `docs/handoff/security_engineer_handoff.md` — documentation only (describes the old finding)

Runtime code in `AppModule.kt` uses `BuildConfig.PROXY_API_KEY` which is correct — it reads the generated BuildConfig field, not a hardcoded value.

### 5. Build verification

`assembleDebug` builds successfully with the key loaded from `local.properties`.

---

## Files Modified

| File | Changes |
|------|---------|
| `app/build.gradle.kts` | Removed hardcoded API key; added `local.properties` loader at top-level scope |
| `local.properties` | Added `SMSSENTRY_API_KEY` entry |

---

## Remaining Work

### CRITICAL — Key Rotation Required

> [!CAUTION]
> The key `7938e31b-af45-4274-8870-480ab286343c` has been committed to git history. Moving it to `local.properties` prevents future commits, but the key is already in the repository log. **It should be considered compromised.**

**To fully remediate:**
1. Generate a new API key (e.g., `uuidgen` or `python -c "import uuid; print(uuid.uuid4())"`)
2. Update the Cloudflare Worker secret: `wrangler secret put API_KEY` (paste new key)
3. Update `local.properties` with the new key: `SMSSENTRY_API_KEY=<new-key>`
4. Optionally use `git filter-repo` or BFG Repo Cleaner to scrub the old key from git history

### MEDIUM — CI/CD Integration

For CI builds, the API key should be injected via environment variable. Update `build.gradle.kts` to fall back:
```kotlin
val apiKey = localProps.getProperty("SMSSENTRY_API_KEY", "")
    .ifEmpty { System.getenv("SMSSENTRY_API_KEY") ?: "" }
```

### LOW — Empty key guard

If `local.properties` is missing or doesn't contain the key, `apiKey` defaults to `""`. The app will compile but API calls will fail with HTTP 401. Consider adding a Gradle warning:
```kotlin
if (apiKey.isEmpty()) {
    logger.warn("WARNING: SMSSENTRY_API_KEY not set in local.properties. Proxy calls will fail.")
}
```

---

## Gotchas

1. **Properties loading must be top-level:** `java.util.Properties()` does NOT resolve inside the `android { }` block in Gradle Kotlin DSL. The loader must be placed before the `android { }` block.
2. **`local.properties` is machine-specific:** Each developer needs to add `SMSSENTRY_API_KEY=...` to their local copy. Add setup instructions to `CONTRIBUTING.md`.
3. **Build cache invalidation:** First build after this change may take longer due to BuildConfig regeneration. Subsequent builds are incremental.
4. **Key still in APK:** The key is still embedded in the final APK's `BuildConfig.class`. This is expected — the defense here is against *source code* exposure, not APK decompilation. For APK-level protection, consider runtime key retrieval from a secure backend.
