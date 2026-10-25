# Known Limitations & Required Device Configuration

## Android platform ceilings (transparent)

1. **Normal app cannot kill foreground apps**: `ActivityManager.killBackgroundProcesses()` leaves foreground alive; Accessibility “Back” is interruptible. The app uses overlay + Home navigation as best-effort and offers Device Owner hard-suspend as an explicit opt-in. See `PERMISSIONS.md`.

2. **WorkManager minimum repeat is 15 min** (desktop Task Scheduler was ~60 s). Enforcement deadlines therefore rely on `AlarmManager` exact alarms; if the user denies `SCHEDULE_EXACT_ALARM`, enforcement can be up to 15 min late.

3. **Doze/App Standby** throttle background work despite exemption; exempting battery optimisation greatly improves but does not eliminate throttling on aggressive OEM skins (Samsung/MIUI/OnePlus). User must set “Unrestricted” battery for the app if enforcement is critical.

4. **Exact alarms require user grant on Android 12+**: `canScheduleExactAlarms()` gating; the app degrades gracefully to inexact alarms and shows status.

5. **UsageStats accuracy**: `UsageEvents` granularity is ~1–2 s; rapid app switching may be slightly delayed vs desktop’s 1 s `GetForegroundWindow`. Not a fidelity loss for daily limits.

6. **Website blocking**: requires AccessibilityService + per-browser DOM class names (Chrome, Samsung Internet, Firefox). Update may break domain extraction for one browser — tracked per-release.

7. **PDF `PdfRenderer` limitations**: no annotation/editing; zoom is basic (scale bitmap). For advanced features, a third-party library could be added, but was avoided per “avoid unnecessary dependencies”.

8. **Game detectors**: LoL/VALORANT desktop signals do not exist on Android. Generic detector is coarse (game package foreground = wait).

## Required setup

- Grant **Usage Access**, **Notifications**, **Overlay**, **Exact Alarm** (Android 12+) on first launch (guided in Permissions screen).
- Optionally grant **Accessibility** + **Battery Unrestricted**.
- For hard app suspend, provision as **Device Owner** (needs factory reset or secondary user + ADB: see `PERMISSIONS.md`).

## Verified vs inspected

- Schedule math, state machine, tracker midnight splits, overnight windows: **verified by unit tests** on JVM.
- DB migrations, SAF roundtrip: **instrumentedTest** (requires emulator/device).
- Foreground detection, overlay, reboot: **manual on-device spot check** required — see `TESTING.md` manual plan. The repository’s CI cannot run a full on-device matrix; those items are labelled `tested-by-inspection` until executed on hardware.
