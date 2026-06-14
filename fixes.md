# SMSentry â€“ Senior Engineer Implementation Plan
> **Author:** Senior Engineering Review  
> **Date:** 2026-06-14  
> **Status:** Awaiting Execution  
> **Scope:** Full production-readiness audit + new Educational Verdict System feature

---

## Table of Contents
1. [Executive Summary](#1-executive-summary)
2. [Phase 1 â€“ Build & Dependency Fixes](#2-phase-1--build--dependency-fixes)
3. [Phase 2 â€“ LiteRT-LM Engine Correctness](#3-phase-2--litert-lm-engine-correctness)
4. [Phase 3 â€“ Educational Verdict System (New Feature)](#4-phase-3--educational-verdict-system-new-feature)
5. [Phase 4 â€“ Data Layer & Session Hardening](#5-phase-4--data-layer--session-hardening)
6. [Phase 5 â€“ UI / UX Improvements](#6-phase-5--ui--ux-improvements)
7. [Phase 6 â€“ Test Suite Repair & Expansion](#7-phase-6--test-suite-repair--expansion)
8. [Phase 7 â€“ Production Hardening](#8-phase-7--production-hardening)
9. [Verification Plan](#9-verification-plan)
10. [File Change Index](#10-file-change-index)

---

## 1. Executive Summary

SMSentry was scaffolded by a junior developer with **mock/placeholder implementations** in the most critical path (the on-device AI engine), inconsistent prompt formats, a JSON-based verdict parser that conflicts with the new educational output format, and a broken test suite that references non-existent class members.

This document is the **single source of truth** for all work required to ship a production-quality build. Changes are grouped into 7 phases ordered by dependency: complete earlier phases before later ones compile correctly.

---

## 2. Phase 1 â€“ Build & Dependency Fixes

### 2.1 `app/build.gradle.kts`

**Problems:**
- Kotlin metadata version mismatch between Kotlin 1.9.22 and LiteRT-LM (compiled against Kotlin 2.x) causes `kapt` to fail with `IllegalArgumentException: Unexpected metadata version`.
- `compileSdk = 34` is out of date; API 35 (Android 15) is now required for Play Store.
- `isMinifyEnabled = false` in release â€” unacceptable for production.
- Missing `kotlinx-coroutines-test` and `mockk` test dependencies.

**Fix:**
```kotlin
android {
    compileSdk = 35
    defaultConfig { targetSdk = 35 }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
}
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1") // was 0.8.0
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.10")
}
```

### 2.2 `proguard-rules.pro`

Add keep rules for LiteRT-LM, Room, Hilt, and Kotlin serialization:

```proguard
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * { @androidx.room.* <methods>; }
-keep class dagger.hilt.** { *; }
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.smssentry.**$$serializer { *; }
```

---

## 3. Phase 2 â€“ LiteRT-LM Engine Correctness

### 3.1 `ModelManager.kt` â€” Rewrite `LiteRtLmEngine`

**Problem:** `generate()` creates a **new `Conversation` object on every inference call**. This:
1. Loses all context between tool-call turns (model hallucinates).
2. Creates/destroys large JNI objects on every loop iteration, causing OOM on low-end devices.

Also, `SamplerConfig(1, 1.0, 0.6, 0)` uses positional args â€” silently wrong if SDK argument order changes.

**Fix â€” add stateful `LlmConversationSession` class:**

```kotlin
class LlmConversationSession(
    private val conversation: com.google.ai.edge.litertlm.Conversation
) {
    suspend fun sendTurn(userText: String): String = withContext(Dispatchers.IO) {
        val msg = Message.of(userText)
        val response = conversation.sendMessage(msg)
        response.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
    }
    fun close() = conversation.close()
}
```

**Rewrite `LiteRtLmEngine`:**

```kotlin
class LiteRtLmEngine(private val modelPath: String) : LlmInferenceEngine {
    private var engine: Engine? = null

    override suspend fun load() = withContext(Dispatchers.IO) {
        if (engine != null) return@withContext
        val modelFile = File(modelPath)
        if (!modelFile.exists() || modelFile.length() < ModelDownloadManager.MIN_FILE_SIZE_BYTES)
            throw IllegalStateException("Model file incomplete: ${modelFile.length()} bytes")
        try {
            engine = Engine(EngineConfig(modelPath = modelPath)).also { it.initialize() }
        } catch (e: UnsatisfiedLinkError) {
            throw IllegalStateException("LiteRT-LM native library missing", e)
        }
    }

    fun createSession(systemPrompt: String): LlmConversationSession {
        val eng = engine ?: throw IllegalStateException("Model not loaded")
        val samplerConfig = SamplerConfig(topK = 40, temperature = 0.7f, topP = 0.9f, randomSeed = 0)
        val conv = eng.createConversation(ConversationConfig(samplerConfig))
        conv.sendMessage(Message.of(systemPrompt)) // inject system prompt
        return LlmConversationSession(conv)
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val eng = engine ?: throw IllegalStateException("Model not loaded")
        val samplerConfig = SamplerConfig(topK = 40, temperature = 0.7f, topP = 0.9f, randomSeed = 0)
        val conv = eng.createConversation(ConversationConfig(samplerConfig))
        try {
            conv.sendMessage(Message.of(prompt))
                .contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        } finally { conv.close() }
    }

    override fun close() { engine?.close(); engine = null }
}
```

### 3.2 `LlmInference.kt` â€” Add `createSession()` to interface

```kotlin
interface LlmInferenceEngine {
    suspend fun load()
    suspend fun generate(prompt: String): String          // kept for legacy/tests
    fun createSession(systemPrompt: String): LlmConversationSession  // NEW
    fun close()
}
```

---

## 4. Phase 3 â€“ Educational Verdict System (New Feature)

### Overview

| File | Action |
|---|---|
| `PromptTemplates.kt` | Replace system prompt with educator-style prompt |
| `DeepCheckVerdict.kt` | Add `educationalExplanation` field |
| `LlmInference.kt` | Add `EducationalVerdictParser` + `ParsedEducationalVerdict` |
| `DeepCheckSession.kt` | Stateful loop, new tag detection, new tool format |
| `ToolExecutor.kt` | Add short tool name aliases |
| `FetchPageTool.kt` | **NEW** â€” implement `fetch_page` tool (was missing) |
| `DeepCheckTimeline.kt` | Add `EducationalExplanationCard` composable |

---

### 4.1 `PromptTemplates.kt` â€” Replace System Prompt

Replace the entire file contents with:

```kotlin
package com.smssentry.deepcheck.session

const val SYSTEM_PROMPT = """
You are an on-device SMS fraud investigator and cybersecurity educator running privately on this device.

When you need factual information, output ONE line in EXACTLY this format:
ACTION: tool_name|parameter

Available tools:
- whois|domain               â€“ Domain registration info (age, registrar, country)
- search_scam_db|query       â€“ Search the local phishing URL database
- fetch_page|url             â€“ Fetch the first 500 chars of a webpage
- official_site|company_name â€“ Verified official domain of a company
- brand_mismatch|sms_text    â€“ Check if sender pretends to be a known brand

After each ACTION, you will receive an OBSERVATION line. Continue step-by-step until confident.

When ready, output EXACTLY:

<<<VERDICT:VERDICT_LABEL,CONFIDENCE,SCAM_TYPE>>>
Your educational explanation paragraph here.

Rules:
- VERDICT_LABEL: exactly SCAM, SAFE, or SUSPICIOUS
- CONFIDENCE: decimal 0.0-1.0
- SCAM_TYPE: credential_theft | parcel_scam | fake_job | lottery | investment_fraud | safe | unknown
- Write the explanation for a non-technical person.
- Explain what the message tries to do, name specific red flags and WHY they are dangerous,
  teach the user to spot similar scams, and end with clear recommended actions.
- Do NOT output JSON.
- Always call at least one tool before giving your verdict.
"""

const val RETRY_VERDICT_PROMPT =
    "You have not yet produced a final verdict. Output the <<<VERDICT:...>>> tag followed by your educational explanation paragraph. Do not output JSON."
```

---

### 4.2 `DeepCheckVerdict.kt` â€” Add `educationalExplanation`

```kotlin
package com.smssentry.data.model

data class DeepCheckVerdict(
    val isScam: Boolean,
    val summary: String,
    val threatType: String?,
    val evidence: List<EvidenceItem>,
    val recommendedActions: List<String>,
    val educationalExplanation: String = ""  // NEW - defaults to "" for backward compat
)
```

---

### 4.3 `LlmInference.kt` â€” Add `EducationalVerdictParser`

Add after the existing `VerdictParser` object (do NOT delete the old one):

```kotlin
data class ParsedEducationalVerdict(
    val verdictLabel: String,
    val confidence: Float,
    val scamType: String,
    val explanation: String
)

object EducationalVerdictParser {
    private val TAG_PATTERN = Regex("""<<<VERDICT:(\w+),([\d.]+),([^>]+)>>>""")

    fun parse(rawOutput: String): ParsedEducationalVerdict? {
        val match = TAG_PATTERN.find(rawOutput) ?: return null
        val (verdictStr, confidenceStr, scamType) = match.destructured
        val explanation = rawOutput.substringAfter(match.value).trim()
        if (explanation.isBlank()) return null
        return ParsedEducationalVerdict(
            verdictLabel = when (verdictStr.uppercase()) { "SCAM" -> "SCAM"; "SAFE" -> "SAFE"; else -> "SUSPICIOUS" },
            confidence = confidenceStr.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f,
            scamType = scamType.trim(),
            explanation = explanation
        )
    }
}
```

---

### 4.4 `DeepCheckSession.kt` â€” Stateful Loop + New Parser

Key changes:
1. Replace `StringBuilder` + `engine.generate()` loop with `engine.createSession()` + `session.sendTurn()`.
2. Detect `<<<VERDICT:>>>` tag FIRST in every response, before JSON.
3. New `parseToolCall()` handles `ACTION: tool|param` format.
4. Remove inline `SYSTEM_PROMPT` and `RETRY_JSON_PROMPT` companion constants.
5. Rename `mapVerdict()` â†’ `mapLegacyVerdict()` and add `mapEducationalVerdict()`.

**New session loop skeleton:**

```kotlin
val session = engine.createSession(SYSTEM_PROMPT)
try {
    val toolExecutor = ToolExecutor(allowlistDao, historyDao, reputationDb, officialSites, proxyClient)
    val seenToolCalls = mutableSetOf<String>()
    var turn = 0; val maxTurns = 5
    var response = withTimeoutOrNull(8_000L) {
        session.sendTurn("Analyze this SMS from $smsSender:\n\"$smsText\"")
    }
    while (turn < maxTurns && !isCancelled) {
        if (response == null) { emitStep(context.getString(R.string.step_timeout)); runRuleBasedAnalysis(); return }

        // 1. Educational verdict tag
        val eduVerdict = EducationalVerdictParser.parse(response)
        if (eduVerdict != null) { emitVerdict(mapEducationalVerdict(eduVerdict)); return }

        // 2. Legacy JSON fallback
        val json = VerdictParser.extractJson(response)
        val verdict = json?.let { VerdictParser.parseVerdict(it) }
        if (verdict != null) { emitVerdict(mapLegacyVerdict(verdict)); return }

        // 3. Tool call
        val toolCall = parseToolCall(response)
        if (toolCall != null) {
            val callKey = "${toolCall.first}:${toolCall.second}"
            if (!seenToolCalls.add(callKey)) {
                response = withTimeoutOrNull(8_000L) { session.sendTurn("OBSERVATION: Already called. Use prior result or different tool.") }
                turn++; continue
            }
            emitStep(describeToolCall(toolCall.first, context))
            val toolResult = withTimeoutOrNull(5_000L) { toolExecutor.executeByName(toolCall.first, toolCall.second) } ?: "OBSERVATION: Tool timed out."
            if (toolResult.startsWith("evidence:")) { val ev = toolResult.removePrefix("evidence:").trim(); evidenceList.add(ev); emitEvidence(ev) }
            response = withTimeoutOrNull(8_000L) { session.sendTurn("OBSERVATION: ${toolResult.take(200)}") }
            turn++
        } else {
            response = withTimeoutOrNull(8_000L) { session.sendTurn(RETRY_VERDICT_PROMPT) }
            turn++
        }
    }
    runRuleBasedAnalysis()
} finally { session.close(); _isActive = false }
```

**New `parseToolCall()` supporting `ACTION: tool|param` format:**

```kotlin
private fun parseToolCall(response: String): Pair<String, String>? {
    val actionLine = response.lines().firstOrNull { it.trimStart().startsWith("ACTION:") }
    if (actionLine != null) {
        val content = actionLine.removePrefix("ACTION:").trim()
        val pipeIdx = content.indexOf('|')
        return if (pipeIdx >= 0) Pair(content.substring(0, pipeIdx).trim(), content.substring(pipeIdx + 1).trim())
        else Pair(content.trim(), "")
    }
    // Fallback: old JSON format
    val jsonMatch = Regex("""\{[^}]+\}""").find(response) ?: return null
    return try {
        val json = jsonMatch.value
        val name = Regex(""""tool_name"\s*:\s*"(\w+)"""").find(json)?.groupValues?.get(1)
        val args = Regex(""""arguments"\s*:\s*(\{[^}]+\})""").find(json)?.groupValues?.get(1)
        if (name != null && args != null) Pair(name, args) else null
    } catch (e: Exception) { null }
}
```

**New `mapEducationalVerdict()`:**

```kotlin
private fun mapEducationalVerdict(parsed: ParsedEducationalVerdict): DeepCheckVerdict {
    val isScam = parsed.verdictLabel == "SCAM"
    return DeepCheckVerdict(
        isScam = isScam || parsed.verdictLabel == "SUSPICIOUS",
        summary = parsed.explanation.take(150).let { if (parsed.explanation.length > 150) "$itâ€¦" else it },
        threatType = parsed.scamType.takeIf { it != "safe" },
        evidence = evidenceList.map { EvidenceItem("AI Analysis", it, if (isScam) "HIGH" else "MEDIUM") },
        recommendedActions = emptyList(),
        educationalExplanation = parsed.explanation
    )
}
```

**Updated companion object `describeToolCall()`:**

```kotlin
fun describeToolCall(toolName: String, context: Context): String = when (toolName) {
    "whois", "whois_lookup"                      -> context.getString(R.string.step_whois)
    "search_scam_db", "offline_reputation_check" -> context.getString(R.string.step_checking_reputation)
    "fetch_page"                                 -> "Fetching page contentâ€¦"
    "official_site", "compare_official_site"     -> context.getString(R.string.step_official_site)
    "brand_mismatch", "brand_mismatch_check"     -> context.getString(R.string.step_brand)
    "lookup_allowlist"                           -> context.getString(R.string.step_allowlist)
    "search_personal_db"                         -> context.getString(R.string.step_history)
    else -> context.getString(R.string.step_running_tool, toolName)
}
```

---

### 4.5 `ToolExecutor.kt` â€” Add Short Tool Name Aliases

```kotlin
"whois"          -> executeTool("whois_lookup", args)
"search_scam_db" -> executeTool("offline_reputation_check", args)
"official_site"  -> executeTool("compare_official_site", args)
"brand_mismatch" -> executeTool("brand_mismatch_check", args)
"fetch_page"     -> FetchPageTool(proxyClient).fetch(args)
```

---

### 4.6 [NEW FILE] `FetchPageTool.kt`

Path: `app/src/main/java/com/smssentry/deepcheck/tools/FetchPageTool.kt`

The `fetch_page` tool is advertised in the prompt but had no implementation. This is a real working replacement:

```kotlin
package com.smssentry.deepcheck.tools

import com.smssentry.deepcheck.proxy.PrivacyProxyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class FetchPageTool(private val proxyClient: PrivacyProxyClient?) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            return@withContext "Invalid URL scheme."
        return@withContext try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()?.take(500) ?: ""
            response.close()
            "Page content (first 500 chars): $body"
        } catch (e: Exception) {
            "Fetch failed: ${e.message}"
        }
    }
}
```

---

### 4.7 `DeepCheckTimeline.kt` â€” `EducationalExplanationCard`

Add this composable and wire it in after `VerdictCard(verdict = verdict)`:

```kotlin
@Composable
fun EducationalExplanationCard(explanation: String, modifier: Modifier = Modifier) {
    if (explanation.isBlank()) return
    var expanded by remember { mutableStateOf(false) }
    val preview = if (explanation.length > 220) explanation.take(220) + "â€¦" else explanation

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                Text("What this means for you", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            AnimatedContent(targetState = expanded, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "explanation") { isExpanded ->
                Text(if (isExpanded) explanation else preview, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            if (explanation.length > 220) {
                TextButton(onClick = { expanded = !expanded },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                    Text(if (expanded) "Show less" else "Read more")
                }
            }
        }
    }
}
```

Wire into `DeepCheckTimeline`:

```kotlin
state.verdict?.let { verdict ->
    VerdictCard(verdict = verdict)
    EducationalExplanationCard(explanation = verdict.educationalExplanation)  // ADD THIS LINE
    // ... rest unchanged
}
```

---

## 5. Phase 4 â€“ Data Layer & Session Hardening

### 5.1 Hash Collision Bug â€” `DeepCheckSession.kt`

```kotlin
// BEFORE (line 208):
val hash = HashUtil.hashSms(smsSender, smsText.take(10))

// AFTER:
val hash = HashUtil.hashSms(smsSender, smsText)
```

**Rationale:** Taking only 10 chars causes massive collisions for messages from the same sender (e.g., all "Your OTP is..." SMS history entries overwrite each other).

### 5.2 `ReputationDb.kt` â€” SQLiteException Handler

```kotlin
fun lookup(url: String): Boolean {
    return try {
        // ... existing lookup logic ...
    } catch (e: android.database.sqlite.SQLiteException) {
        false  // Corrupt DB should not crash the app
    }
}
```

### 5.3 Cloudflare Worker Verification

The proxy at `https://smsentry-proxy.joel010-alfred.workers.dev` (set in `BuildConfig.PROXY_URL`) must be live and returning valid WHOIS responses. If the Worker is not deployed, every `whois` tool call fails silently.

**Action:** Deploy and test the Cloudflare Worker. No source code change needed.

### 5.4 `OfficialSitesRepository.kt` â€” JSON Asset

Replace hardcoded brand map with a bundled `app/src/main/assets/official_sites.json` file. Load it via `context.assets.open("official_sites.json")` at init time. Keep hardcoded map as fallback.

---

## 6. Phase 5 â€“ UI / UX Improvements

### 6.1 Deep Check Button Icon â€” `DetailScreen.kt`

```kotlin
// BEFORE: Icons.Default.Warning implies danger before any analysis
Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))

// AFTER: Search icon is neutral and appropriate
Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
```

### 6.2 Model Status Chip â€” `DetailScreen.kt`

Add a chip that navigates to download screen when model is not ready:

```kotlin
if (!isModelReady && canStartDeepCheck) {
    AssistChip(
        onClick = onNavigateToDownload,
        label = { Text("Download AI Model for deeper analysis") },
        leadingIcon = {
            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    )
}
```

### 6.3 Evidence Severity Color Coding

Update `EvidenceCard` to color-code by severity:
- `HIGH` â†’ Red tinted container  
- `MEDIUM` â†’ Orange tinted container  
- `LOW` / unset â†’ Surface variant

### 6.4 Missing String Resources

Verify and add to `res/values/strings.xml` if absent:
- `R.string.step_running_tool` â€” requires `%s` format arg
- `R.string.step_official_compare` â€” requires `%s` format arg
- `R.string.model_unavailable`
- `R.string.summary_scam`
- `R.string.summary_suspicious`
- `R.string.reason_fast_path`

---

## 7. Phase 6 â€“ Test Suite Repair & Expansion

### 7.1 `MockLlmEngine.kt` â€” Script-Based API

```kotlin
class MockLlmEngine(private val script: List<String> = emptyList()) : LlmInferenceEngine {
    private var index = 0

    override suspend fun load() { }

    override suspend fun generate(prompt: String): String = nextResponse()

    override fun createSession(systemPrompt: String): LlmConversationSession =
        MockConversationSession(this)

    suspend fun nextResponse(): String {
        val response = script.getOrNull(index++) ?: "<<<VERDICT:SAFE,0.5,safe>>>\nNo issues found."
        return when (response) {
            "__TIMEOUT__" -> { kotlinx.coroutines.delay(60_000); "" }
            "__ERROR__"   -> throw java.io.IOException("Simulated engine error")
            else          -> response
        }
    }

    override fun close() { }
}
```

`LlmConversationSession` must be `open` so `MockConversationSession` can override `sendTurn()`.

### 7.2 `DeepCheckSessionTest.kt` â€” Updated Test Cases

| Test Case | Mock Script |
|---|---|
| Happy path: tool then verdict | `["ACTION: whois\|example.com", "<<<VERDICT:SCAM,0.95,credential_theft>>>\nThis SMS is a phishing attemptâ€¦"]` |
| Model timeout â†’ rule-based fallback | `["__TIMEOUT__"]` |
| Model error â†’ rule-based fallback | `["__ERROR__"]` |
| Max turns exceeded â†’ rule-based | Five consecutive `ACTION:` lines, never a verdict |
| Cancellation stops loop | `["__TIMEOUT__"]` with cancel called immediately |
| Safe message | `["ACTION: lookup_allowlist\|12345", "<<<VERDICT:SAFE,0.98,safe>>>\nThis message is from a legitimate sourceâ€¦"]` |

Remove ALL references to `LlmResponse.ToolCall` â€” this class does not exist in the codebase.

### 7.3 `OfflineEvaluationTest.kt` â€” Assert New Field

```kotlin
// Add after existing verdict assertions:
assertTrue(verdict.educationalExplanation.isNotBlank())
```

### 7.4 `ReputationDbTest.kt` â€” Error Handling Test

```kotlin
@Test
fun `corrupt db returns false not exception`() {
    // Point ReputationDb at a non-existent or corrupted file path
    val result = reputationDb.lookup("http://known-bad-domain.xyz/steal-your-data")
    assertFalse(result) // Must not throw SQLiteException
}
```

---

## 8. Phase 7 â€“ Production Hardening

### 8.1 Release Signing Config

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
        keyAlias = System.getenv("KEY_ALIAS") ?: ""
        keyPassword = System.getenv("KEY_PASSWORD") ?: ""
    }
}
```

**Never commit keystore credentials to version control.** Use CI environment variables.

### 8.2 Network Security Config

**NEW FILE:** `app/src/main/res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config>
        <domain includeSubdomains="true">smsentry-proxy.joel010-alfred.workers.dev</domain>
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </domain-config>
</network-security-config>
```

Reference in `AndroidManifest.xml`:
```xml
<application android:networkSecurityConfig="@xml/network_security_config" ...>
```

### 8.3 SHA-256 Model Checksum â€” `ModelDownloadManager.kt`

```kotlin
companion object {
    const val MODEL_SHA256 = "PASTE_OFFICIAL_SHA256_FROM_HUGGINGFACE_HERE"
}

private fun verifyChecksum(file: File): Boolean {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { stream ->
        val buffer = ByteArray(8192); var read: Int
        while (stream.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    return actual == MODEL_SHA256
}
```

Replace the size-only check with `verifyChecksum(modelFile)` after the download completes.

### 8.4 Permissions Audit

| Permission | Required For | Action |
|---|---|---|
| `RECEIVE_SMS` | SMS interception | Keep |
| `READ_SMS` | Inbox reading | Keep |
| `INTERNET` | Download + proxy | Keep |
| `ACCESS_NETWORK_STATE` | WiFi-only gate | Keep |
| `FOREGROUND_SERVICE` | Background download | Keep if using Service |
| `READ_CONTACTS` | Nothing | **REMOVE** |
| `WRITE_EXTERNAL_STORAGE` | Nothing | **REMOVE** |

---

## 9. Verification Plan

### Automated Tests
```bash
./gradlew.bat compileDebugKotlin   # Phases 1-4: must produce 0 errors
./gradlew.bat test                 # Phase 6: all unit tests must pass
./gradlew.bat assembleRelease      # Phase 7: release APK must build
```

### Manual On-Device Checklist

- [ ] **Model Download:** Fresh install â†’ download 3.66 GB model. Pause Wi-Fi mid-download, reconnect â†’ resumes from correct byte offset.
- [ ] **Tool Loop:** Send a suspicious SMS. Timeline shows at least 1 `ACTION:` step before the verdict badge appears.
- [ ] **Educational Explanation:** `EducationalExplanationCard` appears below the verdict badge with non-empty text for both SCAM and SAFE verdicts. "Read more" expands correctly.
- [ ] **Rule-Based Fallback:** With no model downloaded, trigger Deep Check â†’ falls through to rule-based analysis, no crash.
- [ ] **Cancellation:** Tap Cancel during investigation â†’ session stops immediately, no verdict emitted.
- [ ] **Cloudflare Worker:** Confirm `https://smsentry-proxy.joel010-alfred.workers.dev` is reachable and returns valid WHOIS data.
- [ ] **Release Build:** `assembleRelease` produces a signed APK that installs and runs without crashes.

---

## 10. File Change Index

| Phase | File | Action | Notes |
|---|---|---|---|
| 1 | `app/build.gradle.kts` | MODIFY | compileSdk 35, minify on, freeCompilerArgs, dep upgrades |
| 1 | `app/proguard-rules.pro` | MODIFY | LiteRT-LM, Room, Hilt, serialization keep rules |
| 2 | `deepcheck/ModelManager.kt` | MODIFY | Rewrite `LiteRtLmEngine`, add `LlmConversationSession`, fix `SamplerConfig` |
| 2 | `deepcheck/model/LlmInference.kt` | MODIFY | Add `createSession()` to interface |
| 3 | `deepcheck/session/PromptTemplates.kt` | MODIFY | Replace system prompt, add `RETRY_VERDICT_PROMPT` |
| 3 | `data/model/DeepCheckVerdict.kt` | MODIFY | Add `educationalExplanation` field (defaults to "") |
| 3 | `deepcheck/model/LlmInference.kt` | MODIFY | Add `EducationalVerdictParser` + `ParsedEducationalVerdict` |
| 3 | `deepcheck/session/DeepCheckSession.kt` | MODIFY | Stateful session loop, new tag detection, new tool format, new helpers |
| 3 | `deepcheck/tools/ToolExecutor.kt` | MODIFY | Add short tool name aliases |
| 3 | `deepcheck/tools/FetchPageTool.kt` | **NEW** | Implement `fetch_page` tool (replaces mock) |
| 3 | `deepcheck/ui/DeepCheckTimeline.kt` | MODIFY | Add `EducationalExplanationCard`, wire into verdict section |
| 4 | `deepcheck/session/DeepCheckSession.kt` | MODIFY | Fix hash collision (use full SMS text) |
| 4 | `deepcheck/data/ReputationDb.kt` | MODIFY | Wrap lookup in SQLiteException try-catch |
| 4 | `src/main/assets/official_sites.json` | **NEW** | Bundled JSON brand-to-domain map |
| 4 | `deepcheck/data/OfficialSitesRepository.kt` | MODIFY | Load from JSON asset |
| 5 | `ui/detail/DetailScreen.kt` | MODIFY | Fix button icon (Warningâ†’Search), add download chip |
| 5 | `deepcheck/ui/DeepCheckTimeline.kt` | MODIFY | Severity color coding on `EvidenceCard` |
| 5 | `res/values/strings.xml` | MODIFY | Add any missing string resources |
| 6 | `test/.../MockLlmEngine.kt` | MODIFY | Script API, `__TIMEOUT__`/`__ERROR__` sentinels, `createSession()` |
| 6 | `test/.../DeepCheckSessionTest.kt` | MODIFY | New verdict format, remove `LlmResponse.ToolCall` refs |
| 6 | `test/.../OfflineEvaluationTest.kt` | MODIFY | Assert `educationalExplanation` is non-blank |
| 6 | `test/.../ReputationDbTest.kt` | MODIFY | Add SQLite error-handling test case |
| 7 | `app/build.gradle.kts` | MODIFY | Add `signingConfigs` block |
| 7 | `res/xml/network_security_config.xml` | **NEW** | Cleartext off, proxy domain trust anchor |
| 7 | `AndroidManifest.xml` | MODIFY | Reference network security config, remove unused permissions |
| 7 | `deepcheck/ModelDownloadManager.kt` | MODIFY | SHA-256 checksum verification after download |
