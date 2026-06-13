---
name: android-build-deploy
description: Build, install, and launch the SMSentry Android app on a connected device
---

# Android Build, Deploy & Launch

Build the debug APK, install it on a connected Android device, and launch the app.

## Prerequisites
- JAVA_HOME set to Android Studio JBR: `C:\Program Files\Android\Android Studio\jbr`
- Device connected via USB with USB debugging enabled
- ADB path: `C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe`

## Steps

### 1. Find connected devices
```powershell
C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe devices -l
```

### 2. Build debug APK
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
D:\SMSentry\gradlew.bat :app:assembleDebug
```

### 3. Install on device (replace DEVICE_ID)
```powershell
C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe -s <DEVICE_ID> install -r D:\SMSentry\app\build\outputs\apk\debug\app-debug.apk
```

### 4. Launch app
```powershell
C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe -s <DEVICE_ID> shell am start -n com.smssentry/.MainActivity
```

### 5. Verify app is running
```powershell
C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe -s <DEVICE_ID> shell pidof com.smssentry
```

## Current Device IDs
- Emulator: `emulator-5554`
- Phone: `00056345M001042` (Samsung A14)

## One-liner (build + install + launch)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; D:\SMSentry\gradlew.bat :app:assembleDebug 2>&1; C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe -s 00056345M001042 install -r D:\SMSentry\app\build\outputs\apk\debug\app-debug.apk; C:\Users\joel0\AppData\Local\Android\Sdk\platform-tools\adb.exe -s 00056345M001042 shell am start -n com.smssentry/.MainActivity
```
