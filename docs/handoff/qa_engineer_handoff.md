# QA Engineer Handoff — Sprint 1 Code Quality Review

**Date:** 2026-06-14  
**Reviewer:** QA Engineer (Automated Review)  
**Scope:** Full codebase audit — edge cases, memory leaks, unused code, error handling, Compose recomposition  

---

## Summary

| Severity | Found | Fixed | Remaining |
|----------|-------|-------|-----------|
| P0       | 0     | 0     | 0         |
| P1       | 6     | 6     | 0         |
| P2       | 5     | 0     | 5         |
| P3       | 4     | 0     | 4         |

---

## P1 Bugs — FIXED

### BUG-1: `formatDateHeader` mutates shared Calendar instance
- **File:** `app/src/main/java/com/smssentry/ui/chat/ChatScreen.kt`, line 96
- **Severity:** P1
- **What's wrong:** `todayCal.apply { add(Calendar.DAY_OF_YEAR, -1) }` mutates `todayCal` in place. After the "Yesterday" check runs, `todayCal` permanently points to yesterday. If `formatDateHeader` is called multiple times within a single `buildDisplayItems` call, the "Today" check would silently fail for subsequent messages, causing today's messages to be labeled "Yesterday".
- **Fix:** Created a separate `yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }`.
- **Steps to reproduce:** Open a chat with messages from today — some would display "Yesterday" header instead of "Today" if the list had mixed timestamps.

### BUG-2: `debounceJob` not cancelled in `ChatViewModel.onCleared()`
- **File:** `app/src/main/java/com/smssentry/ui/chat/ChatViewModel.kt`, line 182
- **Severity:** P1
- **What's wrong:** ContentObserver is unregistered, but if `onChange()` fired just before `onCleared()`, the `debounceJob` coroutine (which has a 200ms delay) could still be pending. While `viewModelScope` cancellation will eventually stop it, the observer's `onChange()` callback runs on the main looper and could enqueue a new coroutine *after* `onCleared()` starts but before `unregisterContentObserver` completes.
- **Fix:** Added `debounceJob?.cancel()` before unregistering the observer.

### BUG-3: Race condition in `ChatViewModel.loadMoreMessages()`
- **File:** `app/src/main/java/com/smssentry/ui/chat/ChatViewModel.kt`, lines 94-117
- **Severity:** P1
- **What's wrong:** Two issues: (1) `isLoadingMore` was a plain `Boolean` — concurrent calls from `LaunchedEffect` and the debounce observer could race. (2) `currentMessages` was captured outside the coroutine but used inside it to set `_messages.value`, potentially discarding messages added between capture and write.
- **Fix:** Changed `isLoadingMore` to `AtomicBoolean` with `compareAndSet()`. Changed `_messages.value = older + _messages.value` to read fresh state inside the coroutine.

### BUG-4: `debounceJob` and `pendingDeleteJob` not cancelled in `ConversationListViewModel.onCleared()`
- **File:** `app/src/main/java/com/smssentry/ui/conversations/ConversationListViewModel.kt`, line 351
- **Severity:** P1
- **What's wrong:** `debounceJob` from the SMS observer and `pendingDeleteJob` (5-second delayed delete) were not cancelled in `onCleared()`. The `pendingDeleteJob` is especially important: if the user navigates away, the deferred deletion could fire after the VM is cleared, attempting to use a disposed context.
- **Fix:** Added `debounceJob?.cancel()` and `pendingDeleteJob?.cancel()` in `onCleared()`.

### BUG-5: Unused `import android.util.Log` and `TAG` in `NavGraph.kt`
- **File:** `app/src/main/java/com/smssentry/ui/navigation/NavGraph.kt`, lines 3, 39
- **Severity:** P1 (code hygiene — explicitly requested removal)
- **What's wrong:** `Log` import and `TAG` constant are not referenced anywhere in the file.
- **Fix:** Removed both.

### BUG-6: Unused `scope` variable in `ComposeSmsScreen.kt`
- **File:** `app/src/main/java/com/smssentry/ui/compose/ComposeSmsScreen.kt`, line 47
- **Severity:** P1 (code hygiene)
- **What's wrong:** `val scope = rememberCoroutineScope()` was declared but never used. The `kotlinx.coroutines.launch` import was also unused.
- **Fix:** Removed both.

---

## P2 Bugs — Reported (not fixed)

### BUG-7: `SimpleDateFormat` allocation on every recomposition in `TimeGapRow`
- **File:** `app/src/main/java/com/smssentry/ui/chat/ChatScreen.kt`, line 595
- **Severity:** P2
- **What's wrong:** `val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())` is created every time `TimeGapRow` recomposes. This allocates garbage on every frame during scroll.
- **Recommended fix:** Wrap in `remember { SimpleDateFormat(...) }` or move to a companion/top-level constant.

### BUG-8: `getThreadInfo` cursor queries with no try-catch protection
- **File:** `app/src/main/java/com/smssentry/ui/conversations/ConversationListViewModel.kt`, line 265-331
- **Severity:** P2
- **What's wrong:** If `getColumnIndexOrThrow()` throws inside the `cursor?.use {}` block (e.g. due to a custom ROM returning a non-standard schema), the exception propagates up to `readConversationsFromProvider()` which *does* have a catch, but the individual conversation fails silently while partially constructed.
- **Recommended fix:** Wrap in try-catch and return null on failure.

### BUG-9: `ContactResolver.LruCache` is not thread-safe
- **File:** `app/src/main/java/com/smssentry/data/util/ContactResolver.kt`, line 27
- **Severity:** P2
- **What's wrong:** `LruCache` is thread-safe for individual `get`/`put` operations, but the `resolve()` method has a check-then-act pattern (get → check null → lookupContact → put) that is not atomic. Two concurrent calls with the same address could both miss the cache and perform duplicate lookups.
- **Recommended fix:** Use a `@Synchronized` annotation or a `Mutex` around the `resolve()` method, or accept the minor duplicate work.

### BUG-10: `SmsRepository.searchMessages` has SQL LIKE injection potential with special characters
- **File:** `app/src/main/java/com/smssentry/data/repository/SmsRepository.kt`, line 412
- **Severity:** P2
- **What's wrong:** If the user searches for `%` or `_` (SQL LIKE wildcards), the search will match unintended patterns. The parameterized query properly escapes most SQL injection, but LIKE wildcards are still meaningful inside the `%query%` pattern.
- **Recommended fix:** Escape `%` and `_` in the query string before wrapping with `%..%`, using something like `query.replace("%", "\\%").replace("_", "\\_")`.

### BUG-11: `getConversations()` loads ALL SMS messages into memory
- **File:** `app/src/main/java/com/smssentry/data/repository/SmsRepository.kt`, lines 38-119
- **Severity:** P2
- **What's wrong:** The query has no `LIMIT` clause and loads every SMS message in the phone to build the thread map in memory. On devices with 100K+ messages, this could cause OOM or ANR. The `limit` parameter only limits the *output*, not the *query*.
- **Recommended fix:** Use `content://sms/conversations` for thread listing (which `ConversationListViewModel.readConversationsFromProvider()` already does) or add a practical LIMIT to the raw query.

---

## P3 Bugs — Reported (not fixed)

### BUG-12: `getThreadMessages` skips messages with blank bodies
- **File:** `app/src/main/java/com/smssentry/data/repository/SmsRepository.kt`, line 161
- **Severity:** P3
- **What's wrong:** `body?.takeIf { b -> b.isNotBlank() } ?: continue` silently skips messages with empty or whitespace-only bodies. Some SMS (e.g. carrier push, WAP push) may have empty bodies and still be valid thread members.
- **Recommended fix:** If intentional, document it. If not, show empty messages with a placeholder.

### BUG-13: `formatTimestamp` "Yesterday" logic is imprecise at year boundaries
- **File:** `app/src/main/java/com/smssentry/ui/conversations/ConversationListScreen.kt`, line 849-850
- **Severity:** P3
- **What's wrong:** The "Yesterday" check uses `calendar.get(Calendar.DAY_OF_YEAR) - msgCalendar.get(Calendar.DAY_OF_YEAR) == 1`, which fails at year boundaries (Jan 1 → Dec 31 would give a negative diff, not 1). The `diff < 2 * 24 * 60 * 60 * 1000L` guard catches some cases but not all.
- **Recommended fix:** Use a proper day-difference calculation or `java.time.LocalDate` API.

### BUG-14: Pinned conversation IDs stored as `StringSet` in SharedPreferences
- **File:** `app/src/main/java/com/smssentry/ui/conversations/ConversationListViewModel.kt`, line 137
- **Severity:** P3
- **What's wrong:** `SharedPreferences.getStringSet()` returns a set that may be backed by the preferences file — mutating the returned set is undefined behavior. While the code `.mapNotNull { ... }.toSet()` creates a new set, the `savePinnedIds` method is fine. Just a note for future maintainers.
- **Recommended fix:** Document or defensively copy.

### BUG-15: `sendSms` uses `FLAG_UPDATE_CURRENT` for all PendingIntents with request code 0
- **File:** `app/src/main/java/com/smssentry/data/repository/SmsRepository.kt`, lines 341-354
- **Severity:** P3
- **What's wrong:** All multi-part SMS pending intents share the same request code (0) and use `FLAG_UPDATE_CURRENT`. This means for a multi-part SMS, only the last PendingIntent will be active — earlier parts won't get proper sent/delivery callbacks.
- **Recommended fix:** Use unique request codes per part (e.g., `i` or `System.nanoTime().toInt() + i`).

---

## Areas Reviewed — No Issues Found

### SmsRepository.kt — Cursor Safety
- ✅ All cursor operations use `cursor?.use {}` — cursors are always closed
- ✅ `contentResolver.query()` returning null is handled via `cursor?.use`
- ✅ Null bodies handled with `?: ""` or `?: continue`
- ✅ Null addresses handled with `?: "Unknown"`
- ✅ No explicit cursor close without try-finally — `use {}` handles it

### ContactResolver.kt — Error Handling  
- ✅ `lookupContact()` wraps the entire query in try-catch returning null
- ✅ Missing contacts permission causes `SecurityException` caught by the try-catch
- ✅ Blank address handled with early return
- ✅ `PhoneNumberUtils.normalizeNumber` null return handled with `?: number`

### ComposeSmsScreen.kt — Contact Picker
- ✅ Contact picker handles null URI (line 56)
- ✅ Malformed URI would throw in `contentResolver.query()` — caught by the outer try-catch (line 87)
- ✅ Missing phone number handled with `isNullOrBlank` check (line 80)
- ✅ Contact without phone number handled (cursor.moveToFirst() returns false)

### Compose Recomposition Issues
- ✅ `mutableStateOf` always wrapped in `remember {}` or `rememberSaveable {}`
- ✅ No unstable lambda parameters causing excessive recomposition — lambdas are passed to top-level composables with proper keys
- ✅ `remember(key)` used correctly for expensive computations (e.g., `buildDisplayItems`, avatar colors, photo bitmaps)

---

## What's Left to Test

1. **Manual testing:** Verify the "Today"/"Yesterday" date headers display correctly on device
2. **Pagination stress test:** Scroll rapidly up/down in a large thread to verify race condition fix
3. **Multi-part SMS send:** Verify that multi-part SMS delivery callbacks work correctly (P3 pending intent issue)
4. **Year boundary:** Test with messages from Dec 31 → Jan 1 to verify "Yesterday" logic
5. **Search with special characters:** Test searching for `%`, `_`, `'`, `"` to verify no SQL issues
6. **Large inbox:** Test with 100K+ messages to verify OOM issue in `getConversations()`
7. **Unit tests:** Write unit tests for `formatDateHeader`, `buildDisplayItems`, `loadMoreMessages`

---

## Files Modified

| File | Change |
|------|--------|
| `ui/chat/ChatScreen.kt` | Fixed `formatDateHeader` Calendar mutation bug |
| `ui/chat/ChatViewModel.kt` | Fixed race condition in pagination, cancel debounceJob in onCleared |
| `ui/conversations/ConversationListViewModel.kt` | Cancel debounceJob and pendingDeleteJob in onCleared |
| `ui/navigation/NavGraph.kt` | Removed unused `Log` import and `TAG` constant |
| `ui/compose/ComposeSmsScreen.kt` | Removed unused `scope` variable and `launch` import |

---

## Build Status

**Build:** ✅ Successful (assembleDebug)  
**Commit:** `test: code quality fixes from QA review`
