---
description: Capture crash logs from the SMSentry app on a connected device
---

# Logcat Crash Capture

Clear logcat, launch the app, wait for crash, and capture the stack trace.

## Usage
Run this when the app crashes on launch or when tapping a feature.

## Steps

### 1. Clear logcat
```powershell
C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe -s <DEVICE_ID> logcat -c
```

### 2. Launch app
```powershell
C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe -s <DEVICE_ID> shell am start -n com.smssentry/.MainActivity
```

### 3. Wait and capture crash (run after user triggers crash)
```powershell
Start-Sleep -Seconds 5
C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe -s <DEVICE_ID> logcat -d 2>&1 | Select-String -Pattern "FATAL|AndroidRuntime|Caused by|signal|Abort" -CaseSensitive:$false | Select-Object -Last 40
```

### 4. For native crashes (SIGABRT), get tombstone
```powershell
C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe -s <DEVICE_ID> logcat -d -t "06-14 00:00:00.000" 2>&1 | Select-String -Pattern "F DEBUG|signal|backtrace|libc" -CaseSensitive:$false | Select-Object -Last 30
```

## Common Crash Patterns
- **FATAL EXCEPTION: main** → Java crash, check stack trace
- **signal 6 (SIGABRT)** → Native crash (usually liblitertlm_jni.so)
- **ClassNotFoundException** → Missing receiver or class
- **INSTALL_FAILED_INSUFFICIENT_STORAGE** → Device storage full
