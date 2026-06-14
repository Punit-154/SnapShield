# SMSentry Architecture & Codebase Map

## Metadata
- name: smssentry-architecture
- description: Complete architecture reference for navigating and modifying the SMSentry codebase

## Project Structure
```
D:\SMSentry\
├── app/src/main/java/com/smssentry/
│   ├── di/                          # Hilt dependency injection
│   │   └── AppModule.kt             # Provides all singletons
│   ├── data/
│   │   ├── model/                   # Data classes
│   │   │   ├── SmsMessage.kt        # Core message model
│   │   │   ├── Conversation.kt      # Thread model
│   │   │   └── ClassificationResult.kt
│   │   ├── repository/
│   │   │   └── SmsRepository.kt     # SMS CRUD, search, pagination
│   │   ├── util/
│   │   │   └── ContactResolver.kt   # Phone → contact name/photo
│   │   └── security/
│   │       └── DatabaseKeyManager.kt # Android Keystore passphrase
│   ├── deepcheck/                   # AI analysis engine
│   │   ├── session/
│   │   │   └── DeepCheckSession.kt  # Main analysis pipeline (CRITICAL)
│   │   ├── data/
│   │   │   └── DeepCheckDatabase.kt # Room DB (SQLCipher encrypted)
│   │   ├── proxy/
│   │   │   └── PrivacyProxyClient.kt # Network client (cert pinned)
│   │   ├── tools/                   # LLM tool implementations
│   │   │   ├── FetchPageTool.kt     # URL fetcher (SSRF protected)
│   │   │   ├── BrandMismatchTool.kt
│   │   │   └── ...
│   │   └── verdict/                 # Response parsers
│   │       ├── VerdictParser.kt
│   │       └── EducationalVerdictParser.kt
│   ├── learning/                    # Personal learning system
│   │   ├── PersonalLearningRepository.kt
│   │   └── data/
│   │       ├── PersonalLearningDao.kt
│   │       ├── SenderTrustEntity.kt
│   │       └── UserFeedbackEntity.kt
│   ├── sms/
│   │   ├── SmsReceiver.kt          # BroadcastReceiver for incoming SMS
│   │   └── NotificationHelper.kt
│   ├── ui/
│   │   ├── conversations/           # Main screen
│   │   │   ├── ConversationListScreen.kt
│   │   │   └── ConversationListViewModel.kt
│   │   ├── chat/                    # Thread view
│   │   │   ├── ChatScreen.kt
│   │   │   └── ChatViewModel.kt
│   │   ├── detail/                  # Message detail + Deep Check
│   │   │   ├── DetailScreen.kt
│   │   │   └── DetailViewModel.kt
│   │   ├── compose/                 # New message
│   │   │   └── ComposeSmsScreen.kt
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt
│   │   │   └── BlockedNumbersScreen.kt
│   │   ├── components/              # Shared UI components
│   │   │   └── PrivacyIndicator.kt
│   │   └── theme/                   # Material3 theme
│   ├── navigation/
│   │   └── AppNavigation.kt        # NavHost routes
│   └── util/
│       └── Diagnostics.kt          # Logging utility
├── cloudflare-worker/
│   └── src/index.js                 # Privacy proxy worker
└── app/src/main/res/
    └── values/strings.xml           # All UI strings (i18n ready)
```

## Key Architectural Patterns

### 1. Deep Check Analysis Pipeline
```
SMS Message
  → Pre-execute tools (brand check, scam DB, official site)
  → Build enriched prompt with XML-delimited SMS content
  → Inject personal learning context
  → Send to LLM (via Cloudflare Worker proxy)
  → Parse response: Educational verdict > JSON verdict > Tool call > Loop
  → Emit final verdict with confidence + evidence
```
**Critical file**: `DeepCheckSession.kt` — this is the brain of the app.

### 2. Network Security Stack
```
App (OkHttp)
  → API Key Interceptor (X-API-Key header)
  → Certificate Pinner (Cloudflare E1/R2 CAs)
  → HTTPS to Cloudflare Worker
  → Worker validates API key
  → Worker strips PII
  → Worker forwards to Gemini API
```

### 3. Data Encryption
```
Passphrase (32 random bytes)
  → Encrypted with AES-256-GCM (Android Keystore key)
  → Stored as ciphertext in SharedPreferences
  → Decrypted at runtime → fed to SQLCipher SupportOpenHelperFactory
  → Room database reads/writes encrypted
```

### 4. Pagination
- `SmsRepository.getThreadMessages(threadId, limit=50, beforeTimestamp=Long.MAX_VALUE)`
- `ChatViewModel.loadMoreMessages()` — prepends older messages
- `ChatScreen` detects scroll-to-top (reverseLayout, index near end)

### 5. Search
- Conversation search: filters by name/address/snippet (in-memory)
- Global message search: `SmsRepository.searchMessages()` — LIKE query on SMS body
- Debounced (300ms, min 2 chars) in ViewModel

## Database Schema (v3)
| Table | Key Columns | Purpose |
|-------|-------------|---------|
| `sender_trust` | address, trust_level, total_messages | Per-sender trust scores |
| `user_feedback` | source, body_preview(50), body_hash(SHA-256), user_label | Feedback without full PII |

## DI Graph (AppModule.kt)
- `SmsRepository` — singleton, needs ContentResolver + Context + ContactResolver
- `PersonalLearningRepository` — singleton, needs DeepCheckDatabase DAO
- `DeepCheckDatabase` — singleton (SQLCipher encrypted)
- `PrivacyProxyClient` — constructed with baseUrl + apiKey from BuildConfig
- `ContactResolver` — singleton

## Navigation Routes
```
conversations → chat/{threadId}/{address}
             → compose
             → settings → blockedNumbers
chat → detail/{messageId}/{threadId}/{address}
```

## Important Gotchas
1. **DeepCheckSession.kt is fragile** — multi-line string edits easily break the nested scope structure. Always verify the full section before/after edits.
2. **ConversationListScreen.kt is 886 lines** — the LazyColumn has deeply nested scopes (SwipeToDismiss, AlertDialog). Be very careful with brace matching.
3. **Git push on Windows/PowerShell** reports false exit code 1 due to stderr redirect. Check output for `main -> main` to confirm success.
4. **ContentObservers have debounce** — 200ms in ChatViewModel, 300ms in ConversationListViewModel. Don't add duplicate debounce.
