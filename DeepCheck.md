# DeepCheck Feature — Implementation Guide

**Audience:** Local coding agent implementing this feature inside the SMSentry Android app.
**Goal:** Ship a working "Deep Check" flow that analyzes a suspicious SMS using a fast heuristic
pre-filter, an on-device LLM agentic loop (Gemma-4-E4B-it via LiteRT-LM), and a rule-based fallback,
streaming progress to the UI via `StateFlow`.

This document is the single source of truth for the implementation. Work through the sections in
order — each section is a self-contained unit of work with its own acceptance criteria. Do not skip
ahead to UI work before the data layer and core session logic compile and have passing unit tests.

---

## 0. Ground Rules for the Agent

- **Compile-first discipline.** After each section, run `./gradlew :app:compileDebugKotlin` (or the
  relevant module) before moving on. Do not accumulate multiple uncompiled sections.
- **No placeholder TODOs in committed code paths that are reachable from `DeepCheckSession.run()`.**
  If a tool can't be implemented yet (e.g., proxy not ready), implement the "unavailable" branch for
  real so the loop degrades gracefully — don't leave a `TODO()` that throws.
- **Everything must work fully offline.** WHOIS and site-compare are the *only* network-dependent
  tools. Every other code path (fast-path filter, allowlist, personal DB, offline reputation,
  brand mismatch, fallback heuristic) must function with airplane mode on.
- **Timeouts everywhere.** Any coroutine that touches the model or network must have an explicit
  timeout (`withTimeoutOrNull`). A hung tool call or hung inference must never freeze the UI.
- **Test as you go.** Each module below lists the unit tests expected before moving on. Use the
  `MockLlmInference` harness from Section 8 early — don't wait until the end to write it.
- **File locations.** Use the package root `com.smsentry.deepcheck` for all new code unless noted.
  Suggested package layout:
  ```
  com.smsentry.deepcheck/
    SMSentryAI.kt
    prefilter/FastPathFilter.kt
    session/DeepCheckSession.kt
    session/DeepCheckUpdate.kt
    session/PromptTemplates.kt
    tools/ToolDefinitions.kt
    tools/ToolExecutor.kt
    tools/BrandMismatchHeuristic.kt
    data/AllowlistEntry.kt
    data/HistoryEntry.kt
    data/DeepCheckDatabase.kt
    data/ReputationDb.kt
    data/OfficialSitesRepository.kt
    proxy/PrivacyProxyClient.kt
    ui/DeepCheckScreen.kt
    ui/VerdictScreen.kt
    ui/EvidenceCard.kt
  ```

---

## 1. Model Preparation (One-Time, Offline Step)

This step is performed once, on a dev machine, not on-device. The output artifacts get bundled
into the app's assets (or downloaded on first run — see 1.3).

### 1.1 Convert Gemma-4-E4B-it to TFLite int4

```bash
pip install ai-edge-torch
python -m ai_edge_torch.convert \
  --model_path google/gemma-4-E4B-it \
  --output gemma-4-e4b-it-int4.tflite \
  --quantization int4 \
  --tokenizer_output tokenizer.json
```

Expected output: `gemma-4-e4b-it-int4.tflite` (~2.5–3 GB depending on quantization scheme) and
`tokenizer.json`.

**Verify before proceeding:** Load the produced `.tflite` file with the LiteRT-LM CLI/test harness
(if available) and confirm it runs a single generation against a trivial prompt
(`"Say hello in one word."`). If this step fails, do not proceed — fix the conversion first. Every
downstream section assumes a working model file.

### 1.2 Asset Strategy Decision

A 2.5–3 GB asset bundled directly in the APK is likely too large for Play Store limits on many
configurations. Decide between:

- **Option A — Bundled asset** (simplest, larger APK): place under `app/src/main/assets/models/`.
- **Option B — On-demand download** (recommended): ship the app without the model, download it on
  first launch into `context.filesDir`, verify checksum, then proceed. Show a one-time "Setting up
  Deep Check (downloading ~2.7 GB)" screen with progress and a Wi-Fi-only toggle.

Implement **Option B** unless the agent has explicit instruction otherwise — it keeps the base APK
small and lets the model be updated independently. Store the model at
`context.filesDir.resolve("models/gemma-4-e4b-it-int4.tflite")`.

### 1.3 Add Dependencies

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-kotlin:2026.06.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
```

**Acceptance:** project syncs and builds with these dependencies added (even with no new code yet).

---

## 2. SMSentryAI — Model Lifecycle Manager

Create `SMSentryAI.kt`. Responsibilities: load/unload the LLM, expose loading state, hand out the
shared `LlmInference` instance to `DeepCheckSession`.

### 2.1 Public Surface

```kotlin
class SMSentryAI(private val context: Context) {
    enum class State { NOT_DOWNLOADED, DOWNLOADING, LOADING, READY, FAILED }

    val state: StateFlow<State>
    val downloadProgress: StateFlow<Float> // 0f..1f, only meaningful during DOWNLOADING

    suspend fun ensureReady(): Boolean
    fun getInference(): LlmInference?  // null unless state == READY
    fun unload() // release native memory, e.g. when app goes to background for a long time
}
```

### 2.2 Implementation Notes

- `ensureReady()` is idempotent: if already `READY`, returns `true` immediately; if `FAILED`, retries
  download+load once before giving up.
- Model loading (`LlmInference.create(...)`) is CPU/IO heavy — always on `Dispatchers.IO`, wrapped in
  `withContext`.
- Configuration:

  ```kotlin
  val options = LlmInference.Options(
      modelPath = context.filesDir.resolve("models/gemma-4-e4b-it-int4.tflite").absolutePath,
      maxOutputTokens = 384,
      temperature = 0.2,
      topK = 5,
      randomSeed = 42,
      useGpu = true,
      cacheStoragePath = context.cacheDir.absolutePath
  )
  ```

  - If `useGpu = true` throws or the GPU delegate is unavailable on the device, catch and retry with
    `useGpu = false`. Log which path was used (useful for the device-performance test in Section 9).
- Tokenizer: `Tokenizer.fromFile(context.assets, "tokenizer.json")` — tokenizer.json is small enough
  to bundle directly as an asset (Option A doesn't apply here, only the big `.tflite` is downloaded).
- Call `ensureReady()` from `Application.onCreate()` in a fire-and-forget coroutine so the model is
  warm by the time the user opens Deep Check. Also show a small persistent "Deep Check ready ✓" /
  "Deep Check unavailable" badge in the SMS list (wire this up in Section 7, but `state` must be
  observable from app launch).

**Unit tests (`SMSentryAITest`):**
- `getInference()` returns `null` before `ensureReady()`.
- On a device/emulator with the model file pre-placed at the expected path, `ensureReady()` →
  `READY` and `getInference()` returns non-null.
- Simulate a missing model file → `ensureReady()` → `FAILED`, `getInference()` stays null, no crash.

---

## 3. Fast-Path Pre-Filter

Create `prefilter/FastPathFilter.kt`. This is the highest-leverage piece of the whole feature — it
must run in well under 1ms and catch the majority of clear-cut cases without ever touching the LLM.

### 3.1 Data Class

```kotlin
data class PreFilterResult(
    val verdict: String?,   // "SAFE" | "SCAM" | null (= not decided)
    val confidence: Float,
    val reason: String?
)
```

### 3.2 Rule Order (apply in this exact order, first match wins)

1. **Allowlist match** — exact sender match OR any extracted domain matches an allowlisted domain →
   `SAFE`, confidence `0.99`.
2. **Suspicious TLDs** — any URL ending in `.tk .ml .ga .cf .xyz .top .club` (keep this list in a
   `val` so it can be extended later without touching logic) → `SCAM`, confidence `0.95`.
3. **Raw IP-address URLs** — any URL matching `https?://\d{1,3}(\.\d{1,3}){3}` → `SCAM`, confidence
   `0.95`.
4. **OTP without links** — SMS contains "OTP" (case-insensitive) AND `extractUrls()` returns empty →
   `SAFE`, confidence `0.90`.
5. **Personal history exact match** — hash of `(sender, first 10 chars of body)` found in
   `HistoryEntry` table with `verdict == "SCAM"` → `SCAM`, confidence `0.98`.
6. Otherwise → `PreFilterResult(null, 0.0f, null)` (fall through to the agentic loop).

### 3.3 Helper Functions to Implement

- `extractUrls(text: String): List<String>` — regex-based URL extraction. Must handle URLs without
  `http(s)://` prefix (e.g., `bit.ly/xyz123`, `secure-bank.com/login`) since scam SMS often omits the
  scheme. Normalize by prepending `https://` for downstream domain extraction if scheme is missing.
- `extractDomains(text: String): List<String>` — derive bare domains (eTLD+1, e.g. `hsbc.co.in`) from
  the URLs above. Use a small public-suffix-aware approach or a curated list of common
  multi-part TLDs (`.co.in`, `.com.au`, `.org.uk`, etc.) — don't naively take the last two labels.
- `hashSms(sender: String, prefix: String): String` — `SHA-256(sender + "|" + prefix)`, hex-encoded.

### 3.4 Caveat / Safety Note

Rule 4 (OTP without links → SAFE) is the riskiest heuristic — OTP-themed scams without links exist
(social engineering asking the user to read the OTP aloud). Keep confidence at `0.90`, not higher,
and document in code comments that this rule is a deliberate trade-off for speed and may be revisited
once we have user-reported false negatives data.

**Unit tests (`FastPathFilterTest`):** one test per rule above, plus:
- A message matching multiple rules resolves via the *first* matching rule in the priority order.
- A clean message with no URLs and no OTP keyword and no history → returns `null` verdict (falls
  through).
- Domain extraction correctly handles `.co.in`, `.com`, `.xyz`, bare-domain-no-scheme cases.

---

## 4. Local Data Layer (Room)

### 4.1 Entities

`data/AllowlistEntry.kt`:
```kotlin
@Entity(tableName = "allowlist")
data class AllowlistEntry(
    @PrimaryKey val id: String,   // sender string OR domain string
    val type: String,             // "sender" | "domain"
    val addedByUser: Boolean
)
```

`data/HistoryEntry.kt`:
```kotlin
@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey val hash: String,
    val verdict: String,          // "SAFE" | "SCAM" | "SUSPICIOUS"
    val confidence: Float,
    val timestamp: Long,
    val evidenceCount: Int
)
```

### 4.2 DAOs

`AllowlistDao`:
```kotlin
@Dao
interface AllowlistDao {
    @Query("SELECT EXISTS(SELECT 1 FROM allowlist WHERE id = :sender AND type = 'sender')")
    suspend fun containsSender(sender: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM allowlist WHERE id = :domain AND type = 'domain')")
    suspend fun containsDomain(domain: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AllowlistEntry)

    @Query("DELETE FROM allowlist WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM allowlist")
    suspend fun all(): List<AllowlistEntry>
}
```

`HistoryDao`:
```kotlin
@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE hash = :hash LIMIT 1")
    suspend fun get(hash: String): HistoryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry)

    @Query("DELETE FROM history WHERE timestamp < :cutoffEpochMillis")
    suspend fun pruneOlderThan(cutoffEpochMillis: Long)
}
```

Add a retention policy: call `pruneOlderThan(now - 90 days)` from a one-shot
`WorkManager` job scheduled on app start, so the history table doesn't grow unbounded.

### 4.3 Database Class

```kotlin
@Database(entities = [AllowlistEntry::class, HistoryEntry::class], version = 1)
abstract class DeepCheckDatabase : RoomDatabase() {
    abstract fun allowlistDao(): AllowlistDao
    abstract fun historyDao(): HistoryDao
}
```

### 4.4 Offline Reputation DB

Bundle a SQLite asset `phish_domains.db` with schema `domain TEXT PRIMARY KEY, type TEXT`. Build it
from a public phishing-domain feed snapshot (document the source and snapshot date in
`app/src/main/assets/phish_domains_README.md` so it can be refreshed). Implement
`data/ReputationDb.kt`:

```kotlin
class ReputationDb(context: Context) {
    private val db: SQLiteDatabase = /* open read-only from assets, copy to a writable
                                          location once on first access since Android can't
                                          open assets DBs directly with SQLiteDatabase */

    fun isScam(domain: String): Boolean
    fun threatType(domain: String): String?
}
```

Note the asset-DB caveat in the code comment: `SQLiteDatabase.openDatabase` requires a file path on
the filesystem, so on first run copy `phish_domains.db` from `assets/` to
`context.getDatabasePath("phish_domains.db")` before opening.

**Plan for weekly updates:** the DB ships read-only with the app. Document (in
`data/ReputationDb.kt` kdoc) that a future version should support downloading an updated
`phish_domains.db` via the same proxy used for WHOIS, with a version/ETag check — not required for
v1 but leave a `// FUTURE:` comment marking the extension point.

### 4.5 Official Sites Mapping

`app/src/main/assets/official_sites.json`:
```json
{
  "HSBC": "hsbc.co.in",
  "India Post": "indiapost.gov.in",
  "SBI": "onlinesbi.sbi",
  "Income Tax Department": "incometax.gov.in"
}
```

`data/OfficialSitesRepository.kt` — loads this JSON once at startup into an in-memory
`Map<String, String>` (case-insensitive key lookup — wrap with a helper that lowercases keys on
load and lowercases lookups). Start with the entries above plus 10–15 more common Indian
banks/government services (this is the user's likely SMS environment based on the
`india_post` / `hsbc.co.in` examples already in the plan) — list a few candidates (RBI, UIDAI/Aadhaar,
LIC, common courier services like BlueDart/DTDC, EPFO) and ask the user which brands matter most for
their userbase if uncertain, but ship with a reasonable default set rather than an empty map.

**Unit tests (`DeepCheckDatabaseTest`, in-memory Room DB):**
- Insert + lookup for allowlist (sender and domain types).
- History insert + get round-trip.
- Pruning removes only entries older than the cutoff.
- `ReputationDb.isScam()` returns true for a domain seeded into the test DB and false otherwise.
- `OfficialSitesRepository` lookup is case-insensitive.

---

## 5. Tool Definitions & Brand Mismatch Heuristic

### 5.1 `tools/ToolDefinitions.kt`

Define the six tools exactly as JSON-schema-shaped `Tool` objects (name, description, parameters)
matching the LiteRT-LM Kotlin `Tool` API:

| Tool | Required params | Notes |
|---|---|---|
| `lookup_allowlist` | none | `sender`, `domain` optional |
| `search_personal_db` | `sender`, `sms_prefix` | |
| `offline_reputation_check` | `urls` (array) | |
| `brand_mismatch_check` | `sms_text`, `urls` | |
| `whois_lookup` | `domain` | network |
| `compare_official_site` | `claimed_entity`, `linked_domain` | |

Keep this as a `val toolList: List<Tool> = listOf(...)` exposed at top level so
`DeepCheckSession` can pass it directly to `generateResponseAsync`.

### 5.2 `tools/BrandMismatchHeuristic.kt`

```kotlin
fun brandMismatchHeuristic(smsText: String, urls: List<String>): String?
```

Algorithm:
1. Scan `smsText` (case-insensitive) for any brand name present as a key in
   `OfficialSitesRepository`'s map.
2. If a brand is found and `urls` is non-empty, compute the eTLD+1 domain for each URL (reuse
   `extractDomains` from Section 3).
3. If **none** of those domains match (or end with, as a suffix on a label boundary) the official
   domain for that brand, return a string describing the mismatch, e.g.
   `"SMS claims to be from 'HSBC' but link points to 'hsbc-secure-login.xyz'"`.
4. If a brand is found but no URL is present, return `null` (nothing to compare).
5. If no known brand is mentioned, return `null`.

**Edge case to handle:** subdomain spoofing like `hsbc.co.in.verify-account.xyz` — the eTLD+1 of
this is `verify-account.xyz`, not `hsbc.co.in`, so it correctly triggers a mismatch. Make sure
`extractDomains` computes eTLD+1 from the right end of the string, not by simple substring
containment (a naive `domain.contains("hsbc.co.in")` check would be fooled by this exact example —
write a unit test specifically for this case).

**Unit tests (`BrandMismatchHeuristicTest`):**
- Brand mentioned + matching official domain → `null`.
- Brand mentioned + mismatched domain → non-null with both names in the message.
- Brand mentioned + lookalike subdomain trick (`hsbc.co.in.evil.xyz`) → flagged as mismatch.
- No brand mentioned → `null`.
- Brand mentioned, no URL → `null`.

---

## 6. The Agentic Loop — `DeepCheckSession`

This is the core of the feature. Build it in this order: (a) update/event types, (b) prompt
templates, (c) the loop skeleton with mocked tool execution, (d) wire in real `ToolExecutor`, (e)
fallback heuristic.

### 6.1 `session/DeepCheckUpdate.kt`

```kotlin
sealed class DeepCheckUpdate {
    data class Step(val description: String) : DeepCheckUpdate()
    data class FoundEvidence(val item: EvidenceItem) : DeepCheckUpdate()
    data class FinalVerdict(val verdict: DeepCheckVerdict) : DeepCheckUpdate()
    data class Error(val message: String) : DeepCheckUpdate()
}

data class EvidenceItem(val text: String)

data class DeepCheckVerdict(
    val verdict: String,        // "SAFE" | "SCAM" | "SUSPICIOUS"
    val confidence: Float,
    val reasoning: String,
    val evidence: List<String>
)
```

### 6.2 `session/PromptTemplates.kt`

Store the system prompt as a top-level `const val SYSTEM_PROMPT = """..."""` using the text from
the original plan (Section 4.2 of the source blueprint), verbatim is fine since it's our own
content. Keep it in its own file so prompt tuning (Sprint 5) doesn't require touching session
logic.

Additionally define:
```kotlin
const val RETRY_JSON_PROMPT =
    "Please provide the final verdict in valid JSON, matching exactly the schema described above. " +
    "Do not include any text outside the JSON object."
```

### 6.3 `session/DeepCheckSession.kt` — Skeleton

```kotlin
class DeepCheckSession(
    private val ai: SMSentryAI,
    private val allowlistDao: AllowlistDao,
    private val historyDao: HistoryDao,
    private val reputationDb: ReputationDb,
    private val officialSites: OfficialSitesRepository,
    private val proxyClient: PrivacyProxyClient?, // null if proxy disabled/unconfigured
) {
    private val _updates = MutableStateFlow<DeepCheckUpdate>(DeepCheckUpdate.Step("Idle"))
    val updates: StateFlow<DeepCheckUpdate> = _updates.asStateFlow()

    private val evidenceList = mutableListOf<String>()
    @Volatile private var isCancelled = false

    fun cancel() { isCancelled = true }

    suspend fun run(sms: SmsMessage) {
        evidenceList.clear()
        isCancelled = false

        // --- Fast path ---
        val pre = fastPathFilter(sms.body, sms.sender, allowlistDao, historyDao)
        if (pre.verdict != null) {
            emit(DeepCheckUpdate.FinalVerdict(
                DeepCheckVerdict(pre.verdict, pre.confidence, pre.reason ?: "", listOf())
            ))
            return
        }

        // --- Agentic loop ---
        val inference = ai.getInference()
        if (inference == null) {
            emit(DeepCheckUpdate.Step("Model unavailable — using rule-based analysis."))
            emitFinalHeuristicVerdict()
            return
        }

        val messages = mutableListOf(
            ChatMessage(role = "system", content = SYSTEM_PROMPT),
            ChatMessage(role = "user", content = "SMS from ${sms.sender}: \"${sms.body}\"")
        )

        var turn = 0
        val maxTurns = 4
        val seenToolCalls = mutableSetOf<String>() // for "don't repeat same tool+args" rule

        while (turn < maxTurns && !isCancelled) {
            val response = withTimeoutOrNull(8_000) {
                inference.generateResponseAsync(messages, tools = toolList)
            }
            if (response == null) {
                emit(DeepCheckUpdate.Step("Model timed out — using rule-based analysis."))
                emitFinalHeuristicVerdict()
                return
            }
            when (response) {
                is LlmResponse.Text -> {
                    val json = extractJson(response.text)
                    if (json != null) {
                        val verdict = parseVerdict(json)
                        if (verdict != null) {
                            emit(DeepCheckUpdate.FinalVerdict(verdict))
                            return
                        }
                    }
                    messages.add(ChatMessage(role = "assistant", content = response.text))
                    messages.add(ChatMessage(role = "user", content = RETRY_JSON_PROMPT))
                    turn++
                }
                is LlmResponse.ToolCall -> {
                    val callKey = "${response.toolCall.name}:${response.toolCall.arguments}"
                    if (!seenToolCalls.add(callKey)) {
                        // Model repeated a call — nudge it instead of executing again
                        messages.add(ChatMessage(role = "assistant", toolCall = response.toolCall))
                        messages.add(ChatMessage(
                            role = "tool",
                            content = "You already called this tool with these arguments. " +
                                "Use the prior result or call a different tool."
                        ))
                        turn++
                        continue
                    }
                    emit(DeepCheckUpdate.Step(describeToolCall(response.toolCall)))
                    val toolResult = withTimeoutOrNull(5_000) {
                        executeTool(response.toolCall)
                    } ?: "Tool timed out."
                    val truncated = toolResult.take(200)
                    if (toolResult.startsWith("evidence:")) {
                        val evidence = toolResult.removePrefix("evidence:").trim()
                        evidenceList.add(evidence)
                        emit(DeepCheckUpdate.FoundEvidence(EvidenceItem(evidence)))
                    }
                    messages.add(ChatMessage(role = "assistant", toolCall = response.toolCall))
                    messages.add(ChatMessage(role = "tool", content = truncated))
                    turn++
                }
                is LlmResponse.Error -> {
                    emit(DeepCheckUpdate.Step("Model error: ${response.error}"))
                    emitFinalHeuristicVerdict()
                    return
                }
            }
        }
        emitFinalHeuristicVerdict()
    }

    private suspend fun emit(update: DeepCheckUpdate) {
        _updates.value = update
    }

    private suspend fun emitFinalHeuristicVerdict() {
        val verdict = if (evidenceList.size >= 2) "SCAM" else "SUSPICIOUS"
        emit(DeepCheckUpdate.FinalVerdict(
            DeepCheckVerdict(
                verdict = verdict,
                confidence = 0.7f,
                reasoning = "Limited analysis due to model constraints.",
                evidence = evidenceList.toList()
            )
        ))
    }
}
```

### 6.4 Helper Functions to Implement Alongside

- `extractJson(text: String): String?` — find the first valid top-level JSON object in `text` (the
  model may wrap it in markdown fences or prose). Strategy: search for the first `{` and the
  matching closing `}` via brace counting (respecting string literals so braces inside strings don't
  throw off the count), then attempt `JSON.parse`; return `null` if parsing fails.
- `parseVerdict(json: String): DeepCheckVerdict?` — deserialize via kotlinx.serialization into the
  expected schema (`verdict`, `confidence`, `reasoning`, `evidence`). Validate that `verdict` is one
  of `"SAFE" | "SCAM" | "SUSPICIOUS"` and `confidence` is in `[0.0, 1.0]`; if either is violated,
  return `null` so the loop treats it as "not a final answer yet" rather than emitting garbage to
  the UI.
- `describeToolCall(toolCall: ToolCall): String` — human-readable progress strings for the `Step`
  updates, e.g. `"Checking if this domain is a known scam site…"` rather than raw tool/arg names.
  Map each of the six tool names to a friendly phrase.

### 6.5 Cancellation

`cancel()` sets `isCancelled = true`. The `while` loop checks it each iteration, but a long-running
`generateResponseAsync` or tool call inside `withTimeoutOrNull` won't observe it mid-call — that's
acceptable (bounded by the 8s/5s timeouts). When the screen calls `cancel()`, also call
`viewModelScope` job cancellation so the coroutine itself is cancelled — don't rely solely on the
flag for instant UI responsiveness; the flag is a belt-and-suspenders backstop for in-flight loop
iterations.

**Unit tests deferred to Section 8** (require the `MockLlmInference` harness).

---

## 7. Tool Executor

`tools/ToolExecutor.kt` — a top-level `suspend fun executeTool(toolCall: ToolCall): String`, or a
class taking the same dependencies as `DeepCheckSession` (prefer a class —
`ToolExecutor(allowlistDao, historyDao, reputationDb, officialSites, proxyClient)` — and have
`DeepCheckSession` hold an instance, since both need the same repositories; this avoids duplicate
constructor params).

Implement each branch faithfully to the original plan (Section 4.4 of the source blueprint), with
these clarifications:

- **`lookup_allowlist`**: query both `containsSender` and `containsDomain` (if a `domain` arg was
  given); return `"Allowlist match found. SAFE."` or `"Not in allowlist."`.
- **`search_personal_db`**: compute the hash via the same `hashSms()` used in `FastPathFilter`
  (move `hashSms` to a shared `util/HashUtil.kt` so both call sites use one implementation — do not
  duplicate this function).
- **`offline_reputation_check`**: for each URL, extract its domain (reuse `extractDomains`), check
  `reputationDb.isScam(domain)`. If any are scam domains, return
  `"evidence: URLs found in scam database: $bad"`. Otherwise `"No known bad URLs."`.
- **`brand_mismatch_check`**: delegate to `brandMismatchHeuristic` from Section 5.2. Prefix with
  `"evidence: "` only if non-null.
- **`whois_lookup`**: if `proxyClient == null` or `!proxyClient.isAvailable()`, return
  `"WHOIS unavailable (offline)."` immediately — do not attempt a network call. Otherwise call
  `proxyClient.whois(domain)` inside a try/catch; on success, if `creationDate` is within the last 30
  days, return `"evidence: Domain registered very recently (${creationDate})."`, else
  `"Domain age: older than 30 days."`. On exception, return `"WHOIS failed: ${e.message}"` (not
  prefixed with `evidence:` — a failed lookup is not evidence of anything).
- **`compare_official_site`**: look up `claimed_entity` in `officialSites` (case-insensitive). If
  found and `linked_domain` doesn't match (use the same eTLD+1-aware comparison as
  `brandMismatchHeuristic` — extract this comparison into a shared
  `util/DomainMatchUtil.kt::domainMatchesOfficial(linkedDomain, officialDomain): Boolean` so both
  tools use identical logic), return
  `"evidence: Link domain $linked_domain does not match official site $official for $claimed_entity."`.
  Otherwise `"Domain seems to match official site."`. If `claimed_entity` isn't in the map, return
  `"Unknown entity: $claimed_entity — cannot verify."`.

**Unit tests (`ToolExecutorTest`):** one test per tool branch, covering both the
"evidence found" and "nothing found" paths, plus the offline/error paths for `whois_lookup`.

---

## 8. Privacy Proxy Client

`proxy/PrivacyProxyClient.kt`:

```kotlin
class PrivacyProxyClient(baseUrl: String?) {
    fun isAvailable(): Boolean // true only if baseUrl is non-null/non-blank AND last
                                // health check succeeded within the last 5 minutes
    suspend fun whois(domain: String): WhoisResult
    suspend fun fetchPage(url: String): String
}

data class WhoisResult(val creationDate: LocalDate?, val registrar: String?)
```

- Use OkHttp with `connectTimeout = 3s`, `readTimeout = 3s`.
- `isAvailable()` should not block — cache the result of a lightweight `/health` GET, refreshed
  opportunistically (e.g., before the first WHOIS call of a session, with its own short timeout, and
  cached for 5 minutes). Never let a proxy outage add more than ~3s to a Deep Check run.
- If `baseUrl` is null (proxy not configured), `isAvailable()` always returns `false` and `whois`/
  `fetchPage` throw `IllegalStateException` immediately (callers in `ToolExecutor` must check
  `isAvailable()` first, as specified in Section 7).

**Cloudflare Worker (separate small project, not part of the Android app):**

Build a worker exposing:
- `GET /whois?domain=...` → JSON `{creationDate, registrar}`, cached 1 hour (Cloudflare Cache API).
- `GET /fetch-page?url=...` → returns page text content (strip scripts/styles), cached 1 hour.
- `GET /health` → `200 OK`.
- Strip all incoming headers except what's needed to make the upstream WHOIS/fetch call — do not
  forward the client's IP, User-Agent, etc. to upstream providers.

Document the deployed worker URL in `local.properties` (not committed) as
`deepcheck.proxy.baseUrl=...`, read into `BuildConfig` via Gradle so it's not hardcoded.

**Unit tests:** mock OkHttp responses (e.g. with `MockWebServer`) for success, timeout, and
malformed-JSON cases for both `whois` and `fetchPage`. A separate integration test (not run in CI)
can hit the real deployed worker.

---

## 9. UI Integration

### 9.1 ViewModel

`DeepCheckViewModel` wraps `DeepCheckSession`, exposes `updates: StateFlow<DeepCheckUpdate>` to
Compose, and owns the coroutine `run()` is launched in (so `viewModelScope.cancel()` /
`session.cancel()` both fire on screen exit).

### 9.2 `ui/DeepCheckScreen.kt`

```kotlin
@Composable
fun DeepCheckScreen(viewModel: DeepCheckViewModel) {
    val update by viewModel.session.updates.collectAsState()
    Column {
        when (val u = update) {
            is DeepCheckUpdate.Step -> StepRow(u.description)
            is DeepCheckUpdate.FoundEvidence -> EvidenceCard(u.item)
            is DeepCheckUpdate.FinalVerdict -> VerdictScreen(u.verdict)
            is DeepCheckUpdate.Error -> ErrorMessage(u.message)
        }
    }
}
```

**Important UX correction vs. the raw plan:** a `StateFlow<DeepCheckUpdate>` with a single current
value means each new `Step`/`FoundEvidence` *replaces* the previous one in the UI — the screen would
show only the latest event, not a running log. Decide explicitly:

- If the desired UX is a running timeline of steps/evidence with a verdict at the end (recommended,
  matches "Calling X… / Evidence found: Y…" narrative), change `_updates` to accumulate into a
  `MutableStateFlow<List<DeepCheckUpdate>>` (append on each emission) and have the Composable render
  a `LazyColumn` over the list, with the final item (if `FinalVerdict`) rendered via
  `VerdictScreen`.
- If only the latest status matters and history isn't needed, the single-value `StateFlow` is fine
  but say so explicitly in code comments so future readers aren't confused.

Implement the **list-based version** — it better matches the original "feels like a superpower"
intent and is what users expect from an agentic progress UI.

### 9.3 `VerdictScreen` / `EvidenceCard`

- `VerdictScreen(verdict: DeepCheckVerdict)`: large color-coded badge (green/red/amber for
  SAFE/SCAM/SUSPICIOUS), confidence as a percentage, reasoning text, and a bulleted evidence list.
  Include a "Was this helpful?" thumbs up/down that writes a flag onto the corresponding
  `HistoryEntry` (for future tuning — not required to do anything with it yet beyond storing).
- `EvidenceCard(item: EvidenceItem)`: small card with a warning icon and the evidence text.
- `StepRow(description: String)`: small row with a spinner (while the session is still running) or a
  checkmark (once superseded by a later step) plus the description text.

### 9.4 Warm-up Badge

In the SMS list screen, observe `SMSentryAI.state` and show:
- `READY` → small green "Deep Check ready" badge.
- `LOADING` / `DOWNLOADING` → progress indicator with `downloadProgress` if downloading.
- `FAILED` → "Deep Check unavailable — tap to retry" that calls `ensureReady()` again.

---

## 10. Testing Plan

### 10.1 `MockLlmInference` Harness

Build this **before** writing `DeepCheckSession` unit tests. It should implement the same interface
as the real `LlmInference` (extract an interface `LlmInferenceLike` if the real LiteRT-LM class
isn't easily mockable/interfaceable — wrap it) and return a scripted sequence of `LlmResponse`
values regardless of input, configured per-test:

```kotlin
class MockLlmInference(private val script: List<LlmResponse>) : LlmInferenceLike {
    private var index = 0
    override suspend fun generateResponseAsync(
        messages: List<ChatMessage>, tools: List<Tool>
    ): LlmResponse = script.getOrElse(index++) { script.last() }
}
```

### 10.2 `DeepCheckSessionTest` Cases

- **Fast-path short-circuit**: SMS matching an allowlist entry never invokes the mock LLM at all.
- **Happy path**: script = `[ToolCall(lookup_allowlist), ToolCall(search_personal_db),
  Text("{...SAFE verdict JSON...}")]` → session emits the steps in order, ends with a `SAFE`
  `FinalVerdict`, and no fallback is triggered.
- **Repeated tool call**: script repeats the same `ToolCall` twice → second occurrence does not
  re-execute the tool (verify via a spy on `ToolExecutor`) and instead nudges the model.
- **Malformed JSON then valid JSON**: script = `[Text("not json"), Text("{...valid...}")]` → session
  retries once with `RETRY_JSON_PROMPT` and then succeeds.
- **Max turns exceeded**: script of 5 `ToolCall`s with no final `Text` → after 4 turns, fallback
  heuristic verdict is emitted.
- **Model timeout**: mock that delays beyond 8s → fallback heuristic emitted, `Step` update logged
  with a timeout message.
- **Model error**: script = `[LlmResponse.Error(...)]` → fallback heuristic emitted immediately.
- **Cancellation**: call `cancel()` mid-loop → loop exits without emitting `FinalVerdict` (or emits a
  cancellation-specific state — decide and assert consistently).

### 10.3 The Five Offline SMS Evaluation Tasks

Curate 5 representative SMS samples covering: (1) clean OTP message, (2) allowlisted bank SMS with a
legit link, (3) classic phishing with lookalike domain + brand mismatch, (4) IP-address-link scam
(should be caught by fast-path), (5) ambiguous message requiring the full agentic loop with WHOIS
mocked to return a recently-registered domain. Run each through the full session with
`proxyClient` returning canned WHOIS data, and assert: correct final verdict category, turn count
within `maxTurns`, and total wall-clock time under the targets in Section 10.4. Score against a
written "expected verdict + key evidence" rubric stored alongside the test fixtures.

### 10.4 Real-Device Performance

On a Snapdragon 7 Gen 3 / 6GB RAM reference device (or closest available):
- Fast-path verdicts: **< 1s** end-to-end (model not invoked).
- Full agentic-loop verdicts: **< 20s**, hard ceiling 25s including proxy round trips.
- Run 5 consecutive deep checks back-to-back; monitor via `adb shell dumpsys meminfo` for memory
  growth and confirm no OOM kill (check `adb logcat` for `lowmemorykiller` / ANR traces).
- Record whether `useGpu = true` succeeded or fell back to CPU (from the log added in Section 2.2)
  and note the timing difference between the two paths if both are observed across test devices.

---

## 11. Sprint Plan / Task Checklist

Use this as the working checklist; check items off as completed and verified (build passes + tests
pass), not just "code written".

- [ ] **Sprint 1 — Foundation**
  - [ ] Model converted & verified (Section 1.1)
  - [ ] Asset/download strategy implemented (Section 1.2)
  - [ ] Dependencies added, project builds (Section 1.3)
  - [ ] `SMSentryAI` implemented with state machine + tests (Section 2)
- [ ] **Sprint 2 — Fast-path + data layer**
  - [ ] Room entities/DAOs + database (Section 4.1–4.3)
  - [ ] `ReputationDb` + `phish_domains.db` asset (Section 4.4)
  - [ ] `OfficialSitesRepository` + `official_sites.json` (Section 4.5)
  - [ ] `FastPathFilter` + helpers + tests (Section 3)
- [ ] **Sprint 3 — Agentic loop (offline tools only)**
  - [ ] `ToolDefinitions` (Section 5.1)
  - [ ] `BrandMismatchHeuristic` + tests (Section 5.2)
  - [ ] `DeepCheckUpdate` / `PromptTemplates` (Section 6.1–6.2)
  - [ ] `DeepCheckSession` skeleton compiles against `MockLlmInference`
  - [ ] `ToolExecutor` offline branches + tests (Section 7, minus whois/proxy)
  - [ ] `MockLlmInference` harness + `DeepCheckSessionTest` suite (Section 10.1–10.2)
- [ ] **Sprint 4 — Proxy + web tools**
  - [ ] Cloudflare Worker deployed (Section 8)
  - [ ] `PrivacyProxyClient` + tests (Section 8)
  - [ ] `whois_lookup` / `compare_official_site` proxy-dependent branches wired in
- [ ] **Sprint 5 — UI**
  - [ ] `DeepCheckViewModel`, `DeepCheckScreen`, list-based update rendering (Section 9)
  - [ ] `VerdictScreen`, `EvidenceCard`, `StepRow`
  - [ ] Warm-up badge in SMS list (Section 9.4)
- [ ] **Sprint 6 — Evaluation & release**
  - [ ] 5 offline SMS evaluation tasks pass rubric (Section 10.3)
  - [ ] Real-device performance targets met (Section 10.4)
  - [ ] Memory profiling clean across 5 consecutive runs
  - [ ] Prompt tuning pass based on eval results (adjust `SYSTEM_PROMPT` only, in
    `PromptTemplates.kt`, with before/after eval scores recorded)

---

## 12. Open Decisions to Flag Back to the Human

These are judgment calls the local agent should *not* silently decide — surface them in a status
update before proceeding past the relevant section:

1. Bundled vs. on-demand model download (Section 1.2) — confirmed default is Option B, but app size
   budget should be confirmed.
2. Final list of brands in `official_sites.json` (Section 4.5) — defaults given, but the human may
   want a different/longer regional list.
3. Cloudflare Worker hosting/billing ownership (Section 8) — needs a deployed URL and credentials
   the agent doesn't have.
4. UX choice in Section 9.2 (running timeline vs. latest-only) — implemented as running timeline by
   default; flag if the human had a different mental model.
5. Source/licensing of the `phish_domains.db` snapshot (Section 4.4) — must use a feed whose terms
   permit redistribution inside the app; confirm before bundling.
