# Security Engineer Handoff — Sprint 1 Audit

**Date:** 2026-06-14  
**Scope:** Security review of recent 10 commits, focused on BlockedNumbersScreen, Cloudflare Worker, DeepCheckSession, and ProGuard rules.

---

## Summary

Performed a focused security audit of recently shipped features. Found **9 findings** across 4 severity levels. All actionable findings have been fixed and the build passes.

---

## Findings & Fixes

### CRITICAL

| # | File | Line | CWE | Description | Status |
|---|------|------|-----|-------------|--------|
| 1 | `cloudflare-worker/src/index.js` | 37 | [CWE-208](https://cwe.mitre.org/data/definitions/208.html) | **Timing-unsafe API key comparison.** `apiKey === env.API_KEY` allows timing side-channel attacks to brute-force the key byte-by-byte. | **FIXED** — Replaced with constant-time XOR comparison loop. |
| 2 | `cloudflare-worker/src/index.js` | 36 | [CWE-287](https://cwe.mitre.org/data/definitions/287.html) | **Auth bypass when API_KEY not configured.** `if (!env.API_KEY) return true` skips authentication entirely if the Worker secret hasn't been set, exposing all endpoints unauthenticated. | **FIXED** — Now returns `{ valid: false, misconfigured: true }` → HTTP 500, refusing to serve requests without a configured secret. |
| 3 | `app/build.gradle.kts` | 49, 59 | [CWE-798](https://cwe.mitre.org/data/definitions/798.html) | **Hardcoded API key in source code.** `PROXY_API_KEY` is a plaintext UUID committed to the repo in both debug and release build types. Anyone with repo access (or who decompiles the APK) gets the proxy key. | **NOT FIXED** — Requires infrastructure change. See "Remaining Work" below. |

### HIGH

| # | File | Line | CWE | Description | Status |
|---|------|------|-----|-------------|--------|
| 4 | `DeepCheckSession.kt` | 176 | [CWE-74](https://cwe.mitre.org/data/definitions/74.html) | **Prompt injection via delimiter breakout.** SMS text containing `</sms_content>` literal could break out of the XML-delimited sandbox and inject instructions into the LLM prompt. | **FIXED** — Strip `<sms_content>` and `</sms_content>` from `smsSender` and `smsText` before embedding in the prompt. |
| 5 | `DeepCheckSession.kt` | 445 | [CWE-74](https://cwe.mitre.org/data/definitions/74.html) | **JSON injection in `runRuleBasedAnalysis`.** Raw `smsText` was interpolated into a JSON string literal (`"""{"sms_text":"$smsText",...}"""`). Quotes, backslashes, or newlines in SMS text would break the JSON or inject keys. | **FIXED** — Added proper JSON escaping for `\`, `"`, `\n`, `\r`, `\t` before interpolation. |
| 6 | `cloudflare-worker/src/index.js` | 55–70 | [CWE-918](https://cwe.mitre.org/data/definitions/918.html) | **SSRF bypass via alternative IP representations.** The `isUrlSafe` function blocked dotted-decimal private IPs but not: decimal integer IPs (e.g. `2130706433` = `127.0.0.1`), octal IPs (e.g. `0177.0.0.1`), or IPv6-embedded IPv4 (e.g. `[::ffff:127.0.0.1]`). | **FIXED** — Added blocks for decimal-only hostnames, octal-prefix octets, and any hostname containing `:` or `[`. |

### MEDIUM

| # | File | Line | CWE | Description | Status |
|---|------|------|-----|-------------|--------|
| 7 | `proguard-rules.pro` | EOF | [CWE-693](https://cwe.mitre.org/data/definitions/693.html) | **Missing SQLCipher ProGuard keep rules.** SQLCipher uses JNI native methods that R8 could strip in release builds, causing `UnsatisfiedLinkError` at runtime. | **FIXED** — Added `-keep class net.zetetic.database.** { *; }` and native methods keep. |
| 8 | `proguard-rules.pro` | EOF | [CWE-200](https://cwe.mitre.org/data/definitions/200.html) | **Source file and line number info retained in release.** Without `-renamesourcefileattribute` and stripping `SourceFile`/`LineNumberTable`, stack traces in release builds reveal internal code structure. | **FIXED** — Added `-renamesourcefileattribute ""` and `-keepattributes !SourceFile,!LineNumberTable`. |

### LOW

| # | File | Line | CWE | Description | Status |
|---|------|------|-----|-------------|--------|
| 9 | `BlockedNumbersScreen.kt` | 223, 293 | [CWE-209](https://cwe.mitre.org/data/definitions/209.html) | **Exception message leaked to user.** `e.message` was displayed in snackbar messages for block/unblock failures, potentially revealing internal implementation details. | **FIXED** — Replaced with generic error messages. Updated corresponding string resources. |

---

## What Was Already Good

These items were audited and found to be properly implemented:

- **BlockedNumbersScreen SQL injection (CWE-89):** Uses `ContentValues.put()` with parameterized `COLUMN_ID = ?` queries — no SQL injection risk.
- **BlockedNumbersScreen input validation (CWE-20):** Already has `PhoneNumberUtils.normalizeNumber()` + regex (`^\\+?[0-9]+$`) + length check (3–20 chars). Added by i18n commit.
- **BlockedNumbersScreen permission model:** `BlockedNumberContract` enforces default SMS app requirement at the OS level — `SecurityException` is caught.
- **CORS headers:** Properly set to `'null'` origin, blocking all browser-origin requests.
- **Worker error handling:** Generic error messages returned to clients; no stack traces or internal details leaked.
- **Network security config:** `cleartextTrafficPermitted="false"`, domain-scoped to the proxy endpoint, system CAs only.
- **AndroidManifest exported components:** All exported receivers/services have proper permission guards (`BROADCAST_SMS`, `BROADCAST_WAP_PUSH`, `SEND_RESPOND_VIA_MESSAGE`). `NotificationActionReceiver` is correctly `exported="false"`.
- **DeepCheckSession prompt structure:** Uses `<sms_content>` XML delimiters with explicit instruction to treat content as data. System prompt is separate from user content.

---

## Remaining Work

### CRITICAL — Hardcoded API Key (CWE-798)

The `PROXY_API_KEY` is hardcoded in `app/build.gradle.kts` lines 49 and 59:
```kotlin
buildConfigField("String", "PROXY_API_KEY", "\"7938e31b-af45-4274-8870-480ab286343c\"")
```

**Recommended fix:**
1. Move the API key to a `local.properties` or `secrets.properties` file (gitignored).
2. Read it at build time: `buildConfigField("String", "PROXY_API_KEY", "\"${localProps["PROXY_API_KEY"]}\"")`.
3. For CI, inject via environment variable.
4. **Rotate the current key immediately** — it's been committed to git history.

### MEDIUM — ProGuard source stripping conflicts

The added `-keepattributes !SourceFile,!LineNumberTable` may conflict with Crashlytics/Firebase crash reporting if those are added later. If crash reporting is integrated, switch to:
```
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
```
and use the R8 mapping file for de-obfuscation.

### LOW — DeepCheckSession personal context sanitization

The `buildPersonalContext` output includes user-supplied `displayName` from contacts (line 188 in PersonalLearningRepository). While the contact name is from the system contacts provider (trusted source), it could theoretically contain adversarial content if a malicious contact card was imported. Consider sanitizing `displayName` before including in the LLM prompt.

### LOW — Domain validation regex bypass (Cloudflare Worker)

The `/whois` endpoint's domain regex (`/^[a-zA-Z0-9][a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/`) is loose enough to accept domains like `localhost.localdomain` or `127.0.0.1.nip.io`. This is mitigated because it only queries the Verisign RDAP service, but tightening the regex would be defense-in-depth.

---

## Files Modified

| File | Changes |
|------|---------|
| `cloudflare-worker/src/index.js` | Timing-safe auth, auth-bypass fix, SSRF hardening |
| `app/.../DeepCheckSession.kt` | Prompt injection delimiter sanitization, JSON escaping |
| `app/proguard-rules.pro` | SQLCipher keep rules, source info stripping |
| `app/.../BlockedNumbersScreen.kt` | Error message info leak fix |
| `app/.../res/values/strings.xml` | Removed format args from error strings |

---

## Gotchas

1. **Build cache:** The first build after editing may fail with stale Kotlin compilation cache errors. A second `assembleDebug` run resolves this.
2. **Cloudflare Worker deploy required:** The Worker changes need to be deployed via `wrangler deploy` to take effect in production.
3. **API key rotation:** The hardcoded key `7938e31b-af45-4274-8870-480ab286343c` should be considered compromised. Rotate on the Worker side (`wrangler secret put API_KEY`) and update `local.properties`.
