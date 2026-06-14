# SMSentry Android Build & Deploy

## Metadata
- name: smssentry-build
- description: Build, deploy, and test the SMSentry Android app

## Environment
- **JAVA_HOME**: `C:\Program Files\Android\Android Studio\jbr`
- **ADB**: `C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- **Device Serial**: `00056345M001042`
- **Project Root**: `D:\SMSentry`

## Build Command
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat assembleDebug --no-daemon
```

### If configuration cache fails:
```powershell
.\gradlew.bat assembleDebug --no-daemon --no-configuration-cache
```

### Clean build (after dependency changes):
```powershell
.\gradlew.bat clean assembleDebug --no-daemon --no-configuration-cache
```

## Deploy Command
```powershell
& "C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "D:\SMSentry\app\build\outputs\apk\debug\app-debug.apk"
```

## Build Error Filtering
Use this pattern to filter build output for errors only:
```powershell
... 2>&1 | Select-String -Pattern '^e:|BUILD|error:|Unresolved|FAILED' | Select-Object -First 20
```

## Common Build Issues

### 1. Configuration Cache Error
**Symptom**: `Configuration cache state could not be cached`
**Fix**: Add `--no-configuration-cache` flag

### 2. Dependency Not Found
**Symptom**: `Could not find net.zetetic:android-database-sqlcipher`
**Fix**: The library was renamed. Use `net.zetetic:sqlcipher-android:4.6.1` (NOT `android-database-sqlcipher`)

### 3. Unresolved Reference after library change
**Symptom**: `Unresolved reference 'sqlcipher'` with new sqlcipher-android
**Fix**: Package moved from `net.sqlcipher.database.SupportFactory` to `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`

### 4. CRLF Warnings on Git
**Symptom**: `warning: LF will be replaced by CRLF`
**Fix**: Harmless on Windows. Ignore. Git push still succeeds despite exit code 1.

### 5. Git Push "Fails" with exit code 1
**Symptom**: PowerShell reports `NativeCommandError` on git push but output shows `main -> main`
**Fix**: This is a PowerShell stderr redirect issue. The push actually succeeded. Check output for `main -> main`.

## Commit Message Format
```
category: brief description

Detailed bullet points of changes
```
Categories: `security:`, `features:`, `improvements:`, `fix:`, `docs:`

## Testing Checklist
1. Build succeeds (`BUILD SUCCESSFUL`)
2. APK installs (`Performing Streamed Install / Success`)
3. App launches without crash
4. Key flows work: conversation list → chat → send message → deep check
