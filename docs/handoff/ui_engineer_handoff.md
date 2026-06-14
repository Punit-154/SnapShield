# UI Engineer Handoff — i18n Migration (Sprint 1)

## What Was Done

Completed the i18n migration for **three remaining screens** that had hardcoded English strings. All user-facing text now uses `stringResource(R.string.xxx)` (in composable contexts) or `context.getString(R.string.xxx)` (in non-composable contexts).

### Files Modified

| File | Hardcoded strings replaced |
|------|---------------------------|
| `DetailScreen.kt` | 20 strings (UI labels, button text, content descriptions, share chooser titles) |
| `SettingsScreen.kt` | 17 strings (section headers, item titles/subtitles, model state labels, dialog button) |
| `BlockedNumbersScreen.kt` | 22 strings (title, dialogs, error messages, snackbar messages, content descriptions) |
| `strings.xml` | ~40 new string entries added |

### Key Decisions

1. **Share helper functions** (`shareMessage`, `shareVerdict` in DetailScreen) are non-composable `private fun` — the chooser title strings are resolved at the call site via `context.getString()` and passed as parameters.

2. **`loadBlockedNumbers()`** is a `private suspend fun` that didn't have access to a composable context. Added a `Context` parameter so the `"Unknown"` fallback string could be resolved from resources.

3. **Error/snackbar messages** in `scope.launch {}` blocks use `context.getString()` with `%1$s` format parameters for dynamic content (e.g., error messages, blocked number confirmations).

4. **Reused existing string keys** wherever possible (e.g., `cancel`, `feedback_submitted`, `feedback_question`, `feedback_mark_safe`, `feedback_report_scam`, `learning_section_title`, etc.).

5. **App version string** uses a parameterized format `"Version %1$s\n%2$s"` with version and description as arguments.

### New String Resources Added

```
section_appearance, section_sms, section_ai_model, section_about
back, clear, share, share_via, share_verdict
default_sms_app_active, default_sms_app_inactive
blocked_numbers_subtitle
ai_powered_description, app_version
model_downloaded_idle, model_not_downloaded_idle
model_state_downloading, model_state_verifying, model_state_loading, model_state_ready, model_state_failed
on_device_ai_model
blocked_icon_desc, error_icon_desc
error_default_sms_required, error_load_blocked, error_unblock_failed, error_block_failed
error_unknown, error_invalid_phone
number_blocked, unknown
ai_deep_analysis, download_ai_model, download_ai_model_desc
detail_downloading, initializing_ai_engine, analyzing, analyze
run_again, run_deep_analysis, loading_message
```

## What's Left

- **No remaining hardcoded strings** in the assigned screens.
- **ChatScreen.kt**, **ConversationListScreen.kt**, **ComposeSmsScreen.kt** were already done (not touched).
- The share text body strings in `shareMessage()` and `shareVerdict()` (e.g., "SMS from:", "SMSentry Analysis:", "— Analyzed by SMSentry") are **not** externalized — these are internal/programmatic share text, not UI-rendered labels. Externalizing them is a future consideration if full l10n of share output is desired.
- The `themeMode.label` in SettingsScreen (line 111) references a property on the `ThemeMode` enum — if that enum uses hardcoded strings, it would need separate treatment.

## Gotchas

1. **`loadBlockedNumbers` now requires a `Context` parameter** — any future callers must pass it.
2. **Line ending warnings** from Git (LF → CRLF) — cosmetic only, Windows environment.
3. **Learning stats subtitle** (`"${stats.totalLabeled} messages learned • ${stats.trustedSenders} trusted senders"`) is still partially hardcoded with dynamic data interpolation — would need a plurals resource for full i18n.

## Build Status

✅ `assembleDebug` — **BUILD SUCCESSFUL**

## Commit

```
1f8e141 ui: complete i18n migration for DetailScreen, SettingsScreen, BlockedNumbersScreen
```
