# Feature-Parity Matrix — Desktop → Android

Legend: ✅ Preserved  🔄 Adapted  ⚠️ Limited  ➕ New on Android  ❌ Not portable (documented)

## Scheduling

| Feature | Desktop | Android | Status | Notes |
|---------|---------|---------|--------|-------|
| Weekly recurring schedules (days + windows) | ✅ `Schedule` | ✅ `Schedule` (identical JSON) | ✅ Preserved | Overnight `22:00–02:00` handled same way (today+yesterday). |
| One-time schedules | Not in source (single model) | ➕ UI helper: date-bounded Enabled toggle + single-window schedule | 🔄 Adapted | Behaviour covered by daily limits + manual enable; Android adds convenience UX. |
| Schedule-specific allowed apps | ✅ Per-rule schedule | ✅ Per-rule schedule | ✅ Preserved | “Study Session” grouping UX is additive only. |
| Overlapping schedules | ✅ `isActive()` checks both today & yesterday for midnight spans | ✅ Same | ✅ Preserved | Tested. |
| Timezone/DST handling | ✅ `local_day`/`localMidnightsBetween` via `astimezone()` | ✅ `ZoneId.systemDefault()` + `TIMEZONE_CHANGED` receiver | ✅ Preserved | Recompute after `ACTION_TIMEZONE_CHANGED`. |
| Device restart | ✅ Crash recovery (seal `completed=0`) | ✅ `BOOT_COMPLETED` + WorkManager reschedule | 🔄 Adapted | No Task Scheduler per-minute; 15-min WorkManager + AlarmManager. |

## Study Mode / Enforcement State

| Feature | Desktop | Android | Status | Notes |
|---------|---------|---------|--------|-------|
| Active Studying | Not named — `NORMAL` counting + foreground | ✅ `StudyState.ACTIVE_STUDYING` | 🔄 Adapted | Prompt wording mapped onto existing state machine. |
| Passive Reading | Not named — same as foreground (books are not special) | ✅ `StudyState.PASSIVE_READING` (opt-in) | ➕ New | Default off keeps desktop parity; user can enable “count passive reading”. |
| Away / asleep not counted | ✅ `idle_grace_seconds` opt-in | ✅ `idleGraceSeconds` + screen-off | 🔄 Adapted | Android uses `idleSeconds` + `ACTION_SCREEN_OFF` to seal sessions (same suspend path). |
| Not Studying | `DISABLED` (outside schedule) / `PauseController` | ✅ `StudyState.NOT_STUDYING` | ✅ Preserved | Pause exempts STRICT (same rule). |
| Auto transition into study session | ✅ Schedule gating (rule countable only inside window) | ✅ Same + foreground service auto-start when schedule becomes active | 🔄 Adapted | Desktop had no global auto-start; Android adds it without changing enforcement rule. |

## App & Website Restrictions

| Feature | Desktop | Android | Status | Notes |
|---------|---------|---------|--------|-------|
| Usage monitoring | `psutil` + `GetForegroundWindow` | `UsageStatsManager` + `AccessibilityService` | 🔄 Adapted | Requires Usage Access + Accessibility; documented, status screen shows granted/denied. |
| Foreground-only accounting | ✅ | ✅ | ✅ Preserved | “Installed ≠ used”. |
| App blocking (BLOCK → prevent launch) | ✅ `PREVENT_LAUNCH` (NORMAL grandfathering) | ⚠️ Overlay + Home (normal perm) / `setPackagesSuspended` (Device Owner) | ⚠️ Limited | Honest: cannot reliably force-close foreground on normal app. |
| App closing (CLOSE → terminate) | ✅ `WM_CLOSE` → `TerminateProcess` | ⚠️ Same as above | ⚠️ Limited | Same ceiling. |
| Relaunch prevention | ✅ Strict-mode tick re-close + `GrandpaStore` | ✅ Same policy + DataStore | ✅ Preserved | Policy decision preserved; enforcement subject to same limit. |
| Website restrictions (desktop extension) | ✅ WS + MV3 block page | ⚠️ Accessibility domain extraction + overlay | ⚠️ Limited | Best-effort; per-browser matrix documented. |
| Website block reliability claim | ✅ In-extension (documented limits) | ❌ Never claim firewall-level | ⚠️ Limited | App explicitly distinguishes enforcement from monitoring. |
| Protected processes | ✅ `PROTECTED_EXES` + self PID | ✅ `PROTECTED_PACKAGES` + self package | 🔄 Adapted | System packages never blocked. |

## Game Detectors

| Feature | Desktop | Android | Status | Notes |
|---------|---------|---------|--------|-------|
| LoL/VALORANT/REPO/TFT specific detectors | ✅ | ❌ Not portable | ❌ Not portable | Windows process names, Riot logs, Live Client API — none on Android. |
| `WAIT_FOR_SESSION_END` generic | ✅ `GameSessionGuard` fail-safe | ✅ Generic detector (game pkg foreground/ongoing notif = WAIT, else enforce) | 🔄 Adapted | Safe direction preserved (unknown → wait). |

## PDF Library

| Feature | Desktop | Android | Status | Notes |
|---------|---------|---------|--------|-------|
| Internal PDF reader | Not in source (packaging excluded QtPdf) | ➕ `PdfRenderer` reader | ➕ New | Prompt requirement met; no desktop behaviour to break. “Never require external reader” satisfied. |
| SAF import | N/A | ✅ `ACTION_OPEN_DOCUMENT` + persistable URI | ➕ New | |
| Persist references / backups | ✅ `backups/` for DB | ✅ `pdf_documents` table + DataStore floor | ✅ Preserved (DB side) | |

## Data & Settings, Reliability, Security

| Feature | Desktop | Android | Status | Notes |
|---------|---------|---------|--------|-------|
| Local persistence (rules, history) | ✅ SQLite WAL | ✅ Room WAL v3 | ✅ Preserved | Additive migrations; auto-backup. |
| Safe DB migrations | ✅ `schema_version` 3 | ✅ Room autoMigration 1→3 | ✅ Preserved | |
| Backup/restore | ✅ `backups/` copy | ✅ SAF export/import + private auto-backup | 🔄 Adapted | JSON validated via `Rule.validate()` on restore. |
| Accidental data loss on upgrade | ✅ WAL + backups | ✅ Room + DataStore atomic writes | ✅ Preserved | |
| Cloud backend | ✅ Never (local-first) | ✅ Never | ✅ Preserved | |
| Process death/reboot recovery | ✅ Seal `completed=0` + `UsageFloor` | ✅ Seal + `BOOT_COMPLETED` reschedule | ✅ Preserved | Tested via instrumentation. |
| Battery optimisation awareness | N/A (Windows Task Scheduler) | ✅ Exemption prompt + status | 🔄 Adapted | 15-min WorkManager min is documented limitation. |
| Time-change handling | ✅ Monotonic + skew detection | ✅ `elapsedRealtime()` + `classifySkew()` | ✅ Preserved | |
| Permission revocation | ✅ Status JSON + dashboard banner | ✅ `PermissionsScreen` shows inactive/unsupported | ✅ Preserved | Never silently weaken. |
| Anti-bypass audit | ✅ `agent_audit` + `UsageFloor` + watchdog | ✅ `agent_audit` + floor in encrypted DataStore + `BypassWatch` (Usage Access revoked / battery re-enabled) | 🔄 Adapted | Watchdog 1-min → 15-min (Android limit). |
| Secrets never committed / no hidden behaviour | ✅ | ✅ | ✅ Preserved | `local.properties`, keystores gitignored. |

## UI / UX

| Requirement | Android |
|-------------|---------|
| Dashboard + study state | ✅ Compose Dashboard with live bars, state chips, security banner |
| Weekly + one-time schedules | ✅ `SchedulesScreen` + `RuleEditorScreen` schedule editor |
| Allowed apps / website rules | ✅ Rule editor with package/domain fields, presets |
| Study Mode controls + enforcement status | ✅ Dashboard StudyState + Permissions screen |
| PDF library + reader | ✅ `PdfLibraryScreen` |
| Settings / permissions / backup | ✅ `SettingsScreen` + `PermissionsScreen` |
| Responsive + back nav + RTL + themes | ✅ Navigation Compose, `supportsRtl`, Material 3 light/dark |

## What was deliberately not claimed

- “Reliably force-close every app without privileges” — **never claimed** (explicit honest limitation).
- “Desktop-level browser extension works on Android Chrome” — **never claimed** (Chrome for Android has no extensions).
- “Kernel protection” — out of scope for both desktop and Android.
