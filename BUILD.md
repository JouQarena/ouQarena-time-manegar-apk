# Build & Installation Guide

## Prerequisites

1. **JDK 17** (Temurin or Oracle): `java --version` must show 17.
2. **Android SDK**: install via Android Studio → SDK Manager, or cmdline:
   ```bash
   export ANDROID_SDK_ROOT=$HOME/Android/Sdk
   export ANDROID_HOME=$ANDROID_SDK_ROOT
   export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
   yes | sdkmanager --licenses
   sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   ```
3. **Gradle Wrapper** already ships (`gradlew`). No global Gradle needed.

## Debug APK (unsigned, installable)

```bash
# Project is at the repository root (no android/ folder) — run from the repo root:
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk  (~8–14 MB)
```

Install on device/emulator:

```bash
adb devices                  # verify device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.timemanager.android.debug/.ui.MainActivity
```

## Release APK (signed)

Create `keystore.properties` at the repository root (never commit — it is gitignored):

```
storeFile=/path/to/release.keystore
storePassword=...
keyAlias=timemanager
keyPassword=...
```

Then `./gradlew assembleRelease`. CI builds only debug (see `.github/workflows/android.yml`).

## Troubleshooting

- **`canScheduleExactAlarms()` false on Android 12+**: grant in Settings → Alarms & reminders.
- **`PACKAGE_USAGE_STATS` not granted**: the app shows a banner; enforcement never silently weakens.
- **Gradle sync fails offline**: first sync requires network (Compose BOM, Room, etc.).
- **Tmpfs full in CI sandbox**: SDK needs ~800 MB; ensure `/tmp` or `ANDROID_SDK_ROOT` has space.
