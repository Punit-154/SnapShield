# Security Engineer Sprint 3 Handoff — Crash Prevention Audit

**Date:** 2026-06-15  
**Commit:** `fix: crash prevention audit - null safety and coroutine guards`  
**Build status:** ✅ BUILD SUCCESSFUL

---

## Summary

Continued and completed the crash prevention audit started by a previous engineer. Audited all cursor column access, coroutine exception handling, unsafe collection access, LazyColumn key uniqueness, and string resource completeness.

---

## What Was Already Done (Prior Engineer)

| Item | Status |
|------|--------|
| `SmsRepository.kt` — safe `getColumnIndex` pattern | ✅ Already converted |
| `ConversationFilter` enum — `@StringRes` annotation | ✅ Already added |
| `ConversationListScreen.kt` — `formatTimestamp` i18n | ✅ Already done |
| `filter_all`, `filter_unread`, `filter_flagged` strings | ✅ Already in `strings.xml` |
| `yesterday` string | ✅ Already in `strings.xml` |

---

## What I Fixed

### 1. `getColumnIndexOrThrow` → Safe `getColumnIndex` (CWE-754: Improper Check for Unusual Conditions)

Replaced all remaining `getColumnIndexOrThrow` calls with `getColumnIndex` + guard pattern. This prevents `IllegalArgumentException` crashes on devices where the SMS/contacts provider schema may differ.

| File | Lines | Columns Protected |
|------|-------|-------------------|
| `PersonalLearningRepository.kt` | 233-235 | `ADDRESS`, `BODY`, `DATE` — returns 0 if missing |
| `PersonalLearningRepository.kt` | 347-348 | `NUMBER`, `DISPLAY_NAME` — skips block if missing |
| `ConversationListViewModel.kt` | 256-258 | `thread_id`, `msg_count`, `snippet` — returns empty list if missing |
| `BlockedNumbersScreen.kt` | 384-385 | `COLUMN_ID`, `COLUMN_ORIGINAL_NUMBER` — skips block if missing |

**Zero remaining `getColumnIndexOrThrow` in `app/src/main/`.**

### 2. Uncaught Coroutine Exceptions (CWE-248: Uncaught Exception)

Added `try-catch` guards to all unprotected `viewModelScope.launch` blocks:

**ChatViewModel.kt** (7 launch blocks fixed):
- `resolveContact()` — contact lookup
- `loadMessages()` — thread message loading
- `loadMoreMessages()` — pagination (had try/finally, added catch)
- `markAsRead()` — mark thread read
- `deleteMessage()` — single message deletion
- `deleteConversation()` — thread deletion
- SMS observer debounce callback

**DetailViewModel.kt** (1 launch block fixed):
- `loadMessage()` — message-by-ID lookup

**ConversationListViewModel.kt** (2 launch blocks fixed):
- `onSearchQueryChanged()` — debounced search
- SMS observer debounce callback

**Already protected (no changes needed):**
- `ConversationListViewModel.loadConversations()` — had try-catch ✅
- `ConversationListViewModel.markAllAsRead()` — had try-catch ✅
- `ConversationListViewModel.deleteConversation()` — had try-catch ✅
- `DetailViewModel.startDeepCheck()` — had CoroutineExceptionHandler ✅
- `DetailViewModel.submitFeedback()` — had try-catch ✅
- `ChatViewModel.sendMessage()` — had try-catch ✅

### 3. Unsafe Collection Access — No Issues Found

Searched for `.first()`, `.last()`, and `[0]` patterns:
- **`SmsRepository.kt:386`** `sentIntents[0]` — safe; inside `else` branch of `parts.size > 1`, guarantees `parts.size == 1`
- **`ToolExecutor.kt:114,202`** `parts[0]` — safe; guarded by `parts.size == 2` check
- No unguarded `.first()` or `.last()` calls found

### 4. LazyColumn Keys — All Unique

| File | Key Pattern | Unique? |
|------|-------------|---------|
| `ChatScreen.kt` | `msg_${id}`, `date_${index}_${label}`, `gap_${index}_${timestamp}` | ✅ Type-prefixed + index |
| `ConversationListScreen.kt` | `convo.threadId` | ✅ Deduplicated by `distinctBy` upstream |
| `ConversationListScreen.kt` | `msg_${it.id}` | ✅ SMS ID is unique |
| `BlockedNumbersScreen.kt` | `it.id` | ✅ DB primary key |

### 5. String Resources — All Present

All required strings exist in `res/values/strings.xml`:
- `filter_all` (line 31) ✅
- `filter_unread` (line 277) ✅  
- `filter_flagged` (line 278) ✅
- `yesterday` (line 304) ✅

---

## Files Modified

1. `app/src/main/java/com/smssentry/learning/PersonalLearningRepository.kt`
2. `app/src/main/java/com/smssentry/ui/conversations/ConversationListViewModel.kt`
3. `app/src/main/java/com/smssentry/ui/settings/BlockedNumbersScreen.kt`
4. `app/src/main/java/com/smssentry/ui/chat/ChatViewModel.kt`
5. `app/src/main/java/com/smssentry/ui/detail/DetailViewModel.kt`

---

## Gotchas / Things to Watch

1. **`PersonalLearningRepository.kt` line 235** — if columns are missing, `importExistingMessages()` returns 0. The caller should handle this gracefully (it does — the UI shows "0 messages imported").

2. **`ConversationListViewModel.kt` line 259** — if thread cursor columns are missing, the entire conversation list returns empty. This is the safest behavior since partial data would be misleading, but consider adding a user-visible error state in future.

3. **Error logging** — all catch blocks log via `Log.e()`. None log PII (no message bodies, no phone numbers). Only tags and generic error messages are logged.

4. **KT-73255 warnings** — several `@ApplicationContext` / `@ApplicationScope` annotations emit Kotlin 2.x deprecation warnings about annotation targets. Non-blocking, purely cosmetic — will need `@param:` target prefix eventually.

---

## Remaining Work (Out of Scope)

- [ ] Consider adding `CoroutineExceptionHandler` at the ViewModel base class level for global crash prevention
- [ ] Add ProGuard keep rules audit for cursor column name strings (obfuscation could theoretically affect string-based queries, though unlikely)
- [ ] Consider adding `SupervisorJob` to observer coroutine scopes to isolate failures
