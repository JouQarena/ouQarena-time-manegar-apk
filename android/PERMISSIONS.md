# Permissions & Setup Guide

Time Manager is local-only. No cloud, no account, no telemetry. Permissions are requested only to implement the features you enable.

## Required for full functionality

| Permission | System setting | Purpose | If denied |
|------------|---------------|---------|-----------|
| **Usage Access** (`PACKAGE_USAGE_STATS`) | Settings → Apps → Special access → Usage access → Time Manager → Allow | Know which app is foreground (so only foreground time counts, as on desktop). | App/website rules never count. Dashboard shows “inactive — grant Usage Access”. |
| **Post Notifications** | System prompt on Android 13+; Settings → Notifications | Warning thresholds + “limit reached” notices. | Enforcement still tries (overlay/home) but you won’t see warnings. Dashboard still shows state. |
| **Schedule Exact Alarm** | Settings → Apps → Special access → Alarms & reminders (Android 12+) | Fire schedule transitions and limit deadlines exactly (e.g. 08:00 window, 30-min session end). | Fallback to inexact 15-min WorkManager checks. Enforcement may be up to 15 min late — status screen says so. |
| **Draw over other apps** | Settings → Apps → Special access → Display over other apps | Show “Time’s up” overlay when a limit is hit. | Enforcement falls back to notification + navigating to Home. Documented as best-effort. |

## Recommended

| Permission | Setting | Purpose |
|------------|---------|---------|
| **Ignore Battery Optimisations** | Prompts → Settings → Battery → Unrestricted | WorkManager/AlarmManager otherwise throttled by Doze/OEM killers. Without it, tracking may pause while device dozes. |
| **AccessibilityService** | Settings → Accessibility → Time Manager | Faster foreground detection + website domain extraction (address bar). Without it, website rules are less reliable (package-only). |

## Optional — strong enforcement (not required)

Normal Android apps **cannot** force-close another app’s foreground window. The app honestly implements best-effort (overlay + Home). For hard blocks:

### Device Owner provisioning (one-time, requires ADB or QR)

```
# Factory-reset or secondary user, then:
adb shell dpm set-device-owner com.timemanager.android/.receiver.DeviceAdminReceiver
```

Once device-owner, `DevicePolicyManager.setPackagesSuspended()` is used to hard-suspend target packages (e.g. `com.google.android.youtube`). Remove via `dpm remove-active-admin`. This is documented and never done without user action.

## Privacy

- Extension analogy: the desktop extension sent domains only, never URLs/page content. Android does the same: **domain only** extracted from accessibility nodes, never page bodies.
- Package names (Discord, YouTube, …) and aggregated seconds per day are stored locally in Room. Nothing leaves the device.
- Backups are JSON validated on restore via `Rule.validate()` — malformed backups are rejected.

## Honest enforcement statement (shown in app)

> “On Android a normal app cannot reliably force-close every other app, prevent every bypass, or enforce desktop-level restrictions without privileges. Time Manager shows you exactly which restrictions are active, inactive or unsupported on this device, and logs every enforcement attempt.”
