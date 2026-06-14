# Contributing

> Guide for developers contributing to SMSentry.

---

## Table of Contents

- [Development Setup](#development-setup)
- [Code Style](#code-style)
- [Commit Messages](#commit-messages)
- [Testing](#testing)
- [Cloudflare Worker Deployment](#cloudflare-worker-deployment)
- [Important Guidelines](#important-guidelines)

---

## Development Setup

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| **Android Studio** | Ladybug (2024.2) or later | Includes bundled JDK 17 |
| **Android SDK** | API 35 | Install via SDK Manager |
| **ADB** | Latest | Part of platform-tools |
| **Node.js** | 18+ | Only for Cloudflare Worker development |
| **Wrangler CLI** | Latest | `npm install -g wrangler` |

### Steps

1. **Clone the repository:**
   ```bash
   git clone <repo-url> SMSentry
   cd SMSentry
   ```

2. **Open in Android Studio:**
   - File → Open → select the `SMSentry` directory
   - Wait for Gradle sync to complete

3. **Configure the JDK** (if building from command line):
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   ```

4. **Build the debug APK:**
   ```powershell
   .\gradlew.bat assembleDebug
   ```

5. **Deploy to a device:**
   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

6. **Set as default SMS app:**
   - On the device, go to Settings → Apps → Default apps → SMS app → SMSentry
   - Or accept the prompt on first launch

### IDE Configuration

- Enable **KSP** (Kotlin Symbol Processing) in Android Studio for Hilt/Room code generation
- Install the **Compose Multiplatform** plugin for live Compose previews
- The project uses `compileOptions` with JVM 17 — ensure your IDE JDK matches

---

## Code Style

### Kotlin

- Follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use **4-space indentation** (no tabs)
- Prefer `val` over `var` wherever possible
- Use **data classes** for models, **sealed classes/interfaces** for state hierarchies
- Keep functions short — extract complex logic into well-named private functions

### Jetpack Compose

| Convention | Example |
|---|---|
| Screen composables are in their own package | `ui/chat/ChatScreen.kt` |
| Screens take lambda callbacks, not NavController | `onBackClick: () -> Unit` |
| Use `@Preview` for all reusable components | — |
| State hoisting: screens read state from ViewModel | `val state by viewModel.uiState.collectAsState()` |
| Use string resources for all user-visible text | `stringResource(R.string.label)` |

### Naming

| Element | Convention | Example |
|---|---|---|
| Composable screens | `PascalCase` + `Screen` suffix | `ChatScreen`, `DetailScreen` |
| ViewModels | `PascalCase` + `ViewModel` suffix | `ChatViewModel` |
| DAOs | `PascalCase` + `Dao` suffix | `AllowlistDao` |
| Room entities | `PascalCase` + `Entity` suffix | `SenderTrustEntity` |
| Use cases / repositories | Descriptive `PascalCase` | `PersonalLearningRepository` |

### Dependencies

- Add dependencies to `app/build.gradle.kts`
- Pin specific versions (no `+` or `latest` ranges)
- Prefer AndroidX / Google official libraries where possible
- New dependencies require justification in the PR description

---

## Commit Messages

Use the following format:

```
category: concise description of change
```

### Categories

| Category | Use For |
|---|---|
| `feat` | New features or capabilities |
| `fix` | Bug fixes |
| `security` | Security improvements |
| `refactor` | Code restructuring without behavior change |
| `ui` | UI/UX changes |
| `docs` | Documentation updates |
| `test` | Adding or modifying tests |
| `build` | Build system, dependencies, CI changes |
| `perf` | Performance improvements |
| `chore` | Maintenance tasks, cleanup |

### Examples

```
feat: add Bayesian trust scoring to personal learning
fix: handle null sender in SmsReceiver gracefully
security: replace full body storage with preview+hash
ui: add slide animation to Model Download screen
docs: add ARCHITECTURE.md with data flow diagrams
build: bump Room to 2.8.4
```

### Guidelines

- Keep the first line under 72 characters
- Use imperative mood ("add", "fix", "remove" — not "added", "fixed", "removed")
- If needed, add a blank line followed by a more detailed description
- Reference issue numbers where applicable: `fix: handle crash on empty thread (#42)`

---

## Testing

### Framework

| Layer | Framework | Configuration |
|---|---|---|
| **Unit tests** | JUnit 4 + MockK | `testImplementation` |
| **Android tests** | Robolectric | `testOptions { unitTests.isIncludeAndroidResources = true }` |
| **Coroutine tests** | kotlinx-coroutines-test | For testing suspend functions |

### Running Tests

```powershell
# All unit tests
.\gradlew.bat test

# Specific test class
.\gradlew.bat test --tests "com.smssentry.deepcheck.session.DeepCheckSessionTest"
```

### Test Structure

```
app/src/test/java/com/smssentry/
├── deepcheck/
│   ├── session/          # DeepCheckSession tests
│   ├── tools/            # Tool executor tests
│   └── prefilter/        # FastPathFilter tests
├── learning/             # PersonalLearningRepository tests
└── data/
    └── security/         # DatabaseKeyManager tests
```

### Writing Tests

- **Mock external dependencies** using MockK: network clients, DAOs, engines
- **Test the pipeline** at each fallback boundary (fast-path, LLM, rule-based)
- **Verify security invariants**: SSRF blocking, prompt delimiter integrity, PII not logged
- Use `runTest` from `kotlinx-coroutines-test` for coroutine-based code

```kotlin
@Test
fun `FetchPageTool blocks private IP addresses`() = runTest {
    val tool = FetchPageTool(mockProxyClient)
    val result = tool.fetch("http://192.168.1.1/admin")
    assertThat(result).contains("Blocked")
}
```

---

## Cloudflare Worker Deployment

The privacy proxy worker lives in `cloudflare-worker/`.

### First-Time Setup

1. **Install Wrangler:**
   ```bash
   npm install -g wrangler
   ```

2. **Authenticate:**
   ```bash
   wrangler login
   ```

3. **Configure the API key secret:**
   ```bash
   cd cloudflare-worker
   wrangler secret put API_KEY
   # Paste the API key when prompted — must match PROXY_API_KEY in build.gradle.kts
   ```

### Deploy

```bash
cd cloudflare-worker
npx wrangler deploy
```

### Worker Endpoints

| Endpoint | Description |
|---|---|
| `GET /health` | Health check — returns `{ "status": "ok" }` |
| `GET /whois?domain=example.com` | RDAP/WHOIS lookup via Verisign |
| `GET /fetch-page?url=https://...` | Fetch and extract text from a URL |

### Environment Variables

| Variable | Type | Description |
|---|---|---|
| `API_KEY` | Secret | Shared API key for client authentication |

### Testing Locally

```bash
cd cloudflare-worker
npx wrangler dev
# Worker runs at http://localhost:8787
```

```bash
# Health check
curl http://localhost:8787/health

# WHOIS lookup
curl "http://localhost:8787/whois?domain=example.com"

# Page fetch
curl "http://localhost:8787/fetch-page?url=https://example.com"
```

---

## Important Guidelines

> [!CAUTION]
> **Never log PII.** Full SMS bodies, phone numbers, and contact names must never appear in logcat output or crash reports. Use `TextSanitizer` and truncation helpers.

> [!IMPORTANT]
> **Always use string resources** for user-visible text. Never hardcode English strings in Composables. This enables future localization and keeps the UI layer clean.

### Security Checklist for PRs

Before submitting a pull request, verify:

- [ ] No full SMS bodies stored in the database (use `body_preview` + `body_hash`)
- [ ] No PII in log statements (`Log.d`, `Diagnostics.d`, etc.)
- [ ] New network calls go through `PrivacyProxyClient`, not directly
- [ ] URL-fetching code includes SSRF protection
- [ ] User-controlled text in LLM prompts is wrapped in delimiter tags
- [ ] New broadcast receivers are `exported=false` unless system-required
- [ ] String resources used for all UI text
- [ ] Dependencies pinned to specific versions

### Architecture Decisions

When making significant architecture changes, consider:

1. **Privacy first** — SMS content stays on device; only metadata queries go external
2. **Graceful degradation** — every component has a fallback (LLM → rule-based, proxy → offline)
3. **Separation of concerns** — UI knows nothing about LLM internals; `domain` interfaces mediate
4. **Testability** — constructor injection via Hilt; `DispatcherProvider` for coroutine testing
