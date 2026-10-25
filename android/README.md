# Time Manager — Android App

Native Android port of the desktop Time Manager (Python + PySide6) → Kotlin + Jetpack Compose.

## What it does (parity with desktop)

- Create **app / game / website** rules with daily + session limits, warnings, actions (`BLOCK`/`CLOSE`/`WAIT_FOR_SESSION_END`/`WARN_ONLY`) and modes (`NORMAL`/`STRICT`).
- **Schedules:** days (weekdays/weekends/custom) + time windows, including overnight `22:00–02:00`, timezone/DST aware, reboot-resilient.
- **Study Mode:** `Active Studying` / `Passive Reading` / `Away` / `Not Studying` mapped onto the desktop enforcement state machine + idle detection.
- **Enforcement:** best-effort overlay + home navigation under normal permissions; optional Device-Owner hard suspend. Every enforcement logs `EXECUTED|SKIPPED|DEFERRED` with detail.
- **Tracking:** monotonic durations + local-day attribution with midnight splits; suspend/persist exactly as desktop tracker.
- **PDF Library:** internal reader via `PdfRenderer`, SAF import, no external app needed.
- **i18n:** English + العربية (RTL), instant switch, `supportsRtl`.
- Storage: Room DB v3 (mirrors desktop `schema.sql`), DataStore settings, encrypted floor file, validated backups via SAF.

See `docs/ANDROID_AUDIT_AND_MIGRATION_PLAN.md` (Phase A audit) and `android/FEATURE_PARITY.md`.

## Requirements

- Android Studio Hedgehog+ / Gradle 8.10.2 / JDK 17
- Android SDK via `android-actions/setup-android@v3` on CI — no local `sdkmanager` install needed (the action provisions `platform-tools`, `platforms;android-34`, `build-tools;34.0.0` automatically). For local builds, install via Android Studio SDK Manager or `sdkmanager`.
- Device: Android 8.0 (API 26) → 14+ (target 34)

## Build

```bash
cd android
./gradlew assembleDebug   # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # JVM unit tests (schedule/state-machine/tracker/policy)
./gradlew connectedAndroidTest  # instrumented (DB migrations, SAF on device)
```

No signing config committed — debug keystore is auto-generated. Release signing is documented in `BUILD.md`.

## CI

GitHub Actions (`.github/workflows/android.yml`):

1. `actions/checkout@v4`
2. `setup-java` (Temurin 17, Gradle cache)
3. `android-actions/setup-android@v3` (provisions SDK — **no manual `sdkmanager` step**; the deprecated `tools` package is not requested)
4. `yes | sdkmanager --licenses`
5. `./gradlew assembleDebug` → uploads `app-debug.apk` as artifact
6. `./gradlew testDebugUnitTest` → uploads test results

> **Fix for previous failure:** `sdkmanager "tools"` was removed — that package no longer exists and `setup-android` already installs the required platforms/build-tools.

## Run

1. Install APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. Open **Time Manager** → complete **Permissions** onboarding (Usage Access → Notifications → Overlay → Battery exemption).
3. Create a rule → verify dashboard progress + warning notifications.
4. For strongest enforcement, see `PERMISSIONS.md` (Device Owner via ADB).

## Project layout

```
android/
├── app/
│   ├── src/main/java/com/timemanager/android/
│   │   ├── domain/      # Pure Kotlin: Rule/Schedule/TimeUtils/StateMachine/Policy/Tracker/Pause/StudyMode
│   │   ├── data/        # Room entities/DAOs, Repository, DataStore
│   │   ├── monitor/     # UsageMonitor, ActivityResolver, RuleEngine, EnforcementExecutor
│   │   ├── service/     # TimeManagerService (FG), BlockOverlayService
│   │   ├── receiver/    # BootReceiver
│   │   ├── util/        # AlarmScheduler, AppLock
│   │   └── ui/          # Compose: Dashboard, RuleEditor, Schedules, PDF, Settings, Permissions, Stats
│   ├── src/test/        # JVM unit tests
│   └── src/androidTest/ # Instrumented tests
└── docs in repo: BUILD.md, PERMISSIONS.md, FEATURE_PARITY.md
```

## Testing on device

Use `connectedAndroidTest` on a real device to validate:
- `UsageStatsManager` foreground detection vs real app switching
- Overlay enforcement vs various launchers
- Reboot reschedule (`BOOT_COMPLETED`) and timezone change
- PDF reopen after permission revoke

Unit tests already pin schedule/overnight/state-machine/accounting logic (see `TESTING.md`).
