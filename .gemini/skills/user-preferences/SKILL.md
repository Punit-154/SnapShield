# User Preferences — Joel

## Metadata
- name: user-preferences-joel
- description: Joel's working style, design philosophy, and technical preferences. Reference this before making decisions.

## Design Philosophy
> "Minimalist and professional — this is a security app."
> "This is a feature that should be ready to ship, not a demo."

### Core Principles
1. **Ship-ready quality** — no placeholders, no TODOs, no half-implementations
2. **Minimalist UI** — clean, professional, no clutter. This is a security tool, not a social app
3. **Security first** — every feature must consider privacy and security implications
4. **Automate everything** — minimize manual steps, automate builds/deploys/tests

## Technical Preferences

### Code Style
- Kotlin (not Java) for all Android code
- Jetpack Compose (not XML layouts)
- Hilt for DI (not Koin or manual)
- Room for databases
- OkHttp for networking
- Coroutines + Flow (not RxJava)

### Build & Deploy
- Prefers single-command build+deploy workflows
- Wants to see filtered build output (errors only), not full build logs
- Expects builds to be verified before reporting success
- Commits should have descriptive multi-line messages with category prefixes

### Naming
- Commit format: `category: description` (e.g., `security:`, `features:`, `fix:`)
- Branch: `main` (not master)
- Package: `com.smssentry`

## Working Style

### What Joel Values
1. **Speed** — don't ask permission for obvious things, just do them
2. **Thoroughness** — scan for ALL issues, not just the first one found
3. **Parallelism** — use subagents to work on multiple things simultaneously
4. **Context compression** — when context gets long, compress and continue
5. **Autonomy** — "Continue" means keep going with the plan, don't ask what to do next

### What Joel Dislikes
1. **Asking obvious questions** — if the answer is clear from context, just proceed
2. **Half-measures** — fix ALL instances of a problem, not just one
3. **Unnecessary pauses** — don't stop to ask "should I continue?" unless genuinely ambiguous
4. **Demo quality** — everything should be production-ready
5. **Verbose output** — keep summaries concise, use tables

### Communication Preferences
- Provide progress tables showing what's done vs pending
- Summarize at the end of each batch of work
- Use checkmarks (✅) for completed items
- Don't re-explain things already discussed
- When hitting rate limits or errors, immediately try alternatives

## Device & Environment
- **OS**: Windows
- **Shell**: PowerShell
- **Android Device**: Physical device, serial `00056345M001042`
- **Emulator**: Available but Joel prefers physical device ("I need the phone" = switch to emulator)
- **IDE**: Android Studio (JBR at `C:\Program Files\Android\Android Studio\jbr`)
- **Cloud**: Cloudflare account (Joel, accessible via `npx wrangler`)
- **Git Remote**: `https://github.com/Punit-154/SMSentry`

## Decision History

### Security Decisions
- Chose SQLCipher over EncryptedSharedPreferences for DB encryption (more comprehensive)
- Chose Cloudflare Workers as privacy proxy (not direct API calls from app)
- Chose Android Keystore for key management (hardware-backed when available)
- Chose certificate pinning to intermediate CA, not leaf cert (rotation resilience)

### Feature Decisions
- Global search searches message BODIES, not just conversation names
- Pagination uses cursor-based (beforeTimestamp), not offset-based
- Search is debounced 300ms with 2-char minimum
- Swipe-to-delete shows confirmation dialog, doesn't delete immediately
- Personal learning stores body_preview (50 chars) + body_hash, never full body

### Rejected Approaches
- ❌ Emulator testing (prefers physical device unless phone is in use)
- ❌ TailwindCSS (for web — prefers vanilla CSS)
- ❌ Verbose logging in production
- ❌ Hardcoded strings in UI (must use strings.xml)
