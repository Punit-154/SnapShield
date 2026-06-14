# SMSentry — Agent Skills Index

## Metadata
- name: smssentry-skills-index
- description: Index of all available skills for agents working on the SMSentry project. Read the relevant SKILL.md before starting work.

## Available Skills

| Skill | Path | When to Read |
|-------|------|--------------|
| **Build & Deploy** | `.gemini/skills/smssentry-build/SKILL.md` | Before any build/deploy task |
| **Architecture** | `.gemini/skills/smssentry-architecture/SKILL.md` | Before navigating or modifying the codebase |
| **Security Hardening** | `.gemini/skills/android-security-hardening/SKILL.md` | Before any security-related changes |
| **User Preferences** | `.gemini/skills/user-preferences/SKILL.md` | **ALWAYS read first** — contains Joel's working style and preferences |
| **Cloudflare Worker** | `.gemini/skills/cloudflare-worker-deploy/SKILL.md` | Before touching the proxy worker |
| **Compose UI** | `.gemini/skills/compose-ui-patterns/SKILL.md` | Before any UI changes |
| **Decision Log** | `.gemini/skills/agent-decision-log/SKILL.md` | Before starting any complex task — learn from past mistakes |

## Recommended Reading Order for New Agents
1. **User Preferences** — understand Joel's expectations
2. **Architecture** — understand the codebase
3. **Build & Deploy** — know how to build and test
4. **Decision Log** — avoid repeating past mistakes
5. Then read the specific skill for your task

## Quick Reference

### Build
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat assembleDebug --no-daemon
```

### Deploy
```powershell
& "C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "D:\SMSentry\app\build\outputs\apk\debug\app-debug.apk"
```

### Key Files
- **Brain**: `DeepCheckSession.kt` — AI analysis pipeline
- **Data**: `SmsRepository.kt` — all SMS operations
- **Security**: `DatabaseKeyManager.kt` + `PrivacyProxyClient.kt`
- **UI Entry**: `ConversationListScreen.kt` (886 lines, edit carefully)
- **DI**: `AppModule.kt` — all dependency wiring
