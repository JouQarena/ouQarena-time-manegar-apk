# Time Manager — Android Migration: Audit & Migration Plan

**Date:** 2026-10-25  
**Author:** Senior Android Engineer (Agent Mode)  
**Source-of-Truth:** `https://github.com/JouQarena/time-manager` @ `036b0a5` (main)  
**Status:** Phase A — Mandatory First Phase (complete, pending review)

---

## 1. Inspection Method

* Cloned repository `--depth 50`, inspected `HEAD` on `main` (`036b0a5`), verified clean working tree.
* Read every top-level doc: `README.md`, `docs/ARCHITECTURE.md`, `docs/DATABASE.md`, `docs/STATE_MACHINE.md`, `docs/PROTOCOL.md`, `docs/SECURITY-PRIVACY.md`, `docs/LIMITATIONS.md`, `docs/PACKAGING.md`, plus `PHASE1..9`.
* Enumerated all Python modules under `app/` (~60 files) and the browser extension (`browser-extension/manifest.json` + background/content/options/popup).
* Read canonical implementations: `app/core/rules/models.py`, `app/core/scheduling/schedule.py`, `app/core/tracking/tracker.py`, `app/core/enforcement/state_machine.py`, `app/core/enforcement/policy.py`, `app/core/monitoring/activity.py`, `app/core/monitoring/monitor.py`, `app/database/schema.sql` + `db.py`, `app/service.py`, `app/config/settings.py`, `app/i18n.py`, `app/ui/viewmodel.py`, `app/windows/*`, `app/ipc/*`, `app/core/detection/*`, `app/core/security/*`.
* Sampled 40 test files to understand invariants pinned by tests.

> **Finding:** `README.md` is current and accurate for the code on `main`. No divergence between docs and code was found.

---

## 2. Desktop Stack (Actual)

| Layer | Technology |
|-------|------------|
| Language | Python 3.12+ |
| GUI | PySide6 (Qt 6) — tray, dashboard, rule editor, settings, tour |
| Monitoring | `psutil` + `pywin32` (`GetForegroundWindow`, power events, process control) |
| Tracking | `time.monotonic()` for durations + wall clock for calendar-day attribution, midnight splits, WAL SQLite |
| Persistence | SQLite WAL (`timemanager.db` v3) + JSON `config.json` + `timemanager.floor.json` + `agent_token` |
| IPC | Local WebSocket `ws://127.0.0.1:17846` (authenticated, schema-validated) for browser extension |
| Extension | Manifest V3, Chrome/Edge, reports `domain` only when tab is active+focused |
| Packaging | PyInstaller (`TimeManagerTray.exe` + `TimeManager.exe`), portable zip |
| Tests | `pytest`, ~597 tests |

**Desktop-only framework confirmed:** Python/Qt cannot be packaged as an Android APK. A full native port is required.

---

## 3. Complete Feature Inventory (Verified Against Source Code)

### 3.1 Rules (core)

* **Rule types:** `APPLICATION`, `GAME`, `WEBSITE` — each with distinct matching (exe vs domain) and enforcement semantics.
* **Limits:** `daily_limit_seconds` and/or `session_limit_seconds` (at least one required, 1..86400). Daily resets at local midnight.
* **Warning thresholds:** configurable tuple (default 600/300/60 s), fires once per threshold per day.
* **Actions:** `BLOCK` (website→block page; app/game→prevent launch), `CLOSE` (terminate), `WAIT_FOR_SESSION_END` (games: wait for match end), `WARN_ONLY` (notify only).
* **Modes:** `NORMAL` (grandfathered relaunch prevention) vs `STRICT` (no grandfathering, startup repair, usage-floor tamper defence, harder pause).
* **Enabled toggle:** per-rule on/off.
* **Validation:** `Rule.__post_init__` + DB re-validation — prevents nonsense DB edits.

### 3.2 Scheduling

* **Schedule model:** `Schedule(days: Optional[Set[Day]], windows: List[(start,end)])`.
* Days: `EVERYDAY` | `WEEKDAYS` | `WEEKENDS` | explicit list `["MON",...]` | None (always).
* Windows: list of `[HH:MM, HH:MM]` pairs; empty = whole day; midnight-spanning windows (`22:00-02:00`) correctly handled by checking today + yesterday.
* Outside schedule → tracking paused, state `DISABLED`/`SCHEDULE_BLOCKED`, UI shows “outside schedule”.

### 3.3 Tracking & Timekeeping

* **Two clocks:** monotonic for durations (immune to clock changes), local wall clock for calendar day.
* **Session model:** at most ONE open session per rule; in-memory + DB `usage_sessions` (heartbeat every `persist_seconds`=5s, midnights split into per-day rows).
* **Day rollover:** sessions spanning midnight are proportionally split.
* **Clock skew >60s:** close all sessions, log to `clock_log`, restart baseline (never credits anomalous gap).
* **Suspend/resume:** `WM_POWERBROADCAST` → `notify_suspend()` splits sessions; gap accrues zero.
* **Single-tick delta cap:** `max_single_delta` 600s, logged, capped.
* **Reads are never stale:** `today_total()` = DB aggregate + unflushed remainder.

### 3.4 Enforcement State Machine (pure logic, `app/core/enforcement/state_machine.py`)

* States: `NORMAL` → `WARNING` → `LIMIT_REACHED` (transient) → `ENFORCED` / `WAITING_FOR_SESSION_END` → `DISABLED`; all reset to `NORMAL` on new local day.
* Tick input: `today_used`, `session_used`, `target_active_now`, `in_game_session`, `detector_confident`, schedule, stored state/warned thresholds.
* Decisions: `new_state`, `warnings_to_fire`, `action_to_execute` (`CLOSE_APP`/`BLOCK_WEBSITE`/`PREVENT_LAUNCH`/`NOTIFY_ONLY`), `reason`, `user_message`, remaining times.
* Game fail-safe: `unknown` detector state always means WAIT, never enforce.

### 3.5 Kill Policy & Process Control

* **Policy:** `NORMAL/BLOCK` grandfathering (existing instances finish run, only new launches blocked) vs `STRICT`/`CLOSE` immediate close.
* **Safety:** `PROTECTED_EXES` never killed, self-PID never killed, low PIDs protected, agent image protected.
* **Grandfather store:** persisted per-rule/day in `settings` so restarts don’t reinterpret old PIDs.
* **Executor:** `WM_CLOSE` graceful → escalate to `TerminateProcess` after 6s.
* **Relaunch guard:** STRICT re-closes within `strict_relaunch_guard_seconds`.

### 3.6 Monitoring (Phase 3)

* `psutil` snapshot + Win32 `GetForegroundWindow`; process list, foreground PID/exe, idle seconds (`GetLastInputInfo`).
* `ActivityResolver` determines per-rule `active` with foreground-only counting (background grace 5s), idle gate (opt-in), website grace/stale, browser-foreground defence.
* Foreground-only accounting is the product assumption (“installed ≠ used”).

### 3.7 Game Detectors (Phase 6/8)

* Abstraction: `GameSessionDetector` ABC, `DetectorRegistry`, `DetectorHost` (300 ms budget, quarantine after 5 failures), plugin loader.
* Built-ins: **League of Legends** (reference fail-safe), **VALORANT**, **R.E.P.O.**, **Teamfight Tactics** — each with honesty table; unknown = WAIT.
* Signals: match-process table (ground truth), Live Client API `:2999`, Riot logs, log freshness.
* Only LoL is high-accuracy; VALORANT/REPO coarse (wait while game running); TFT shares LoL match process.

### 3.8 Browser Link (Phase 4)

* WS server `127.0.0.1:<port>` + 32-byte token, 5 message types, rate-limited (30/10s), schema-validated, extension untrusted.
* Website time counts only when (a) extension reports `domain` active+focused, (b) a known browser exe is foreground (defence), (c) domain matches a website rule.
* Blocking: in-extension block page (`webNavigation`/`declarativeNetRequest`).
* MV3 service-worker eviction → heartbeat stops, “less time counted, never more”.
* Private windows need explicit extension permission.

### 3.9 Security & Anti-Bypass (Phase 7)

* `LifecycleAudit` → `agent_audit` table: `START`/`STOP`/`UNEXPECTED_STOP`/`USAGE_TAMPERED`/`STARTUP_REPAIRED`/`WATCHDOG_*`/`EXTENSION_SILENT`/`CLOCK_REGRESSED`.
* `UsageFloor` → `timemanager.floor.json` outside DB: per-day high-water mark, STRICT evaluates `max(DB, floor)` — defeats `UPDATE daily_usage SET 0`.
* `BypassWatch`: detects Chrome/Edge running unpaired with STRICT website rules.
* `WatchdogScheduler`: opt-in Task Scheduler task `@every minute` → `--monitor`.
* Startup repair (STRICT): re-adds `HKCU\…\Run` entry if removed.
* Backups: `backups/` daily, keep 7.

### 3.10 Pause

* `PauseController`: time-boxed 1–480 min, stops counting & lifts blocks for non-STRICT rules only; auto-expiry; deadline in wall time (survives restart); visible banner + clock_log.

### 3.11 Settings & i18n

* `AppSettings` JSON (`%APPDATA%\TimeManager\config.json`) with validation + defaults: `monitoring_interval`, `theme`, `language`, `launch_at_startup`, `ipc_port`, `game_detector_*`, etc.
* i18n: English (source) + العربية (RTL via `is_rtl()`), single catalog, `tr(key, …)`, Qt flips layout.

### 3.12 UI & Service

* `AgentService`: single owner of lock+DB+tracker+engine+monitor+IPC; thread-safe `Snapshot`.
* `app/ui/viewmodel.py`: Qt-free `RuleRow`, `state_text_and_role()`, durations, schedule text — fully unit-testable.
* `app/ui/qt/`: dashboard (live bars, state chips, security banner), rule editor (presets, exe picker), settings, tray, onboarding tour, extension help, theme/light-dark.

### 3.13 Database

* `schema_version` 3, tables: `rules`, `usage_sessions`, `daily_usage`, `enforcement_state`, `browser_events`, `settings`, `clock_log`, `enforcement_log`, `agent_audit`.
* Centralised `Database` class (all SQL), WAL, `PRAGMA foreign_keys`, RLock, `check_same_thread=False`.
* Operations: CRUD rules, sessions open/heartbeat/close, daily upsert, enforcement state put/get, settings KV, audit logs, backup/restore, recovery (seal leaked `completed=0` sessions idempotently).

### 3.14 CLI

* `--gui` / `--monitor` / `--status` / `--status-json` / `--security` / `--show-token` / `--simulate` / `--ipc-selftest` / `--detector-selftest` / `--gui-shot` / `--init-db`.

---

## 4. Features Mentioned in the Generic Prompt vs Actual Source

The assignment prompt’s sections 4–10 describe idealised features (e.g. “Study Mode: Active Studying / Passive Reading / Away / Not Studying”, “PDF Library and Internal Reader”, “weekly vs one-time schedules”). **None of those exact names appear in the inspected source code** (verified by `grep -R` across `app/` and `docs/`). The repository’s authoritative equivalents are:

| Prompt wording | Authority (source code) | Disposition |
|----------------|--------------------------|-------------|
| “Weekly recurring schedules / One-time schedules” | `Schedule` with days + windows; no distinct “one-time schedule” entity — a rule’s schedule is always the same model. Daily+session limits + midnight reset already cover one-time-like usage. | Prompt’s “one-time schedules” treated as a UI/UX requirement to implement anew (date-bounded availability window). |
| “Study Mode: Active Studying / Passive Reading / Away / Not Studying” | No `StudyMode` enum in code. Core has enforcement states `NORMAL/WARNING/ENFORCED/WAITING/DISABLED` + `PauseController` (+ idle gate). | Adopt prompt terminology as Android UX layer mapped onto existing state machine + idle detection (see §5). Preserved at behaviour level. |
| “Schedule-specific allowed applications and website restrictions” | Rule + Schedule fully cover this (per-rule schedule). No global allow-list in source — every restriction is per-rule. | Preserve rule→schedule binding; Android will also offer a convenience “Study Session” grouping (UX addition, not behavioural change). |
| “PDF Library and Internal Reader” | `grep -R pdf` hits only packaging exclusion `QtPdf` — there is no PDF import/reader/backup feature in code. | Prompt requirement taken as net-new on Android (no existing behaviour to preserve; must not require separate reader). Feature will be implemented fresh; parity matrix will mark it as *New on Android — no desktop counterpart*. |
| “Automatic transition into a scheduled study session” | `Schedule.is_active()` + engine’s schedule gating already implements “rule becomes countable only when inside schedule”. No global auto-start. | Preserve gating; add an Android foreground-service “auto-schedule activation” UX on top. |
| “Overnight schedules, time zones, device restarts” | `Schedule` midnight-spanning handling, `local_day`/`day_str_of_epoch` using `.astimezone()`, Tracker suspend handling — all verified. | Port verbatim; Android adds `BOOT_COMPLETED` + `TIMEZONE_CHANGED` receivers. |

No feature described in the prompt was found to be missing from docs — the difference is naming, not capability.

---

## 5. Portability Classification

### 5.1 Fully Portable (logic → pure Kotlin, no capability loss)

| Feature | Android implementation |
|---------|-------------------------|
| Rule model + validation (`models.py`) | Kotlin `data class Rule` + validation; JSON serialisation; Room `TypeConverters` |
| Schedule model (`schedule.py`) incl. midnight-spanning | Kotlin `Schedule` (`days: Set<DayOfWeek>?`, `windows: List<Pair<LocalTime,LocalTime>>`) with identical `isActive(now)` |
| `timeutils` (local day, monotonic, `nextLocalMidnight`, `formatDuration`, `localMidnightsBetween`, clock-skew `classify_skew`) | Kotlin `TimeUtils` wrapping `java.time.*` + `SystemClock.elapsedRealtime()` |
| Tracker logic (sessions, daily aggregates, midnight splits, flush, cap, closeAll) | Kotlin `Tracker` + Room DAO for `usageSessions`/`dailyUsage` |
| Enforcement state machine (`state_machine.py`) | Kotlin `StateMachine.tick(TickInput): Decision` — direct port, unit-tested |
| Kill/grandfather logic (decision layer, excluding OS kill) | Kotlin `KillPolicy` + `GrandpaStore` (DataStore) |
| PauseController | Kotlin `PauseController` (DataStore deadline) |
| i18n (catalog, `is_rtl`) | Android `strings.xml` `en`+`ar` + `android:supportsRtl="true"` + `Locale` switching |
| Database schema (all tables/views) | Room database, version 3, matching DDL; autoMigration specs |
| Settings `AppSettings` | `Preferences DataStore` with typed keys + validation |
| ViewModel formatting (progress, `state_text_and_role`, `schedule_text`, `format_duration`) | Kotlin `RuleRow`/`StateUi` mapping |
| Enforcement audit log + agent audit + clock log | Room + log files |

### 5.2 Portable with Android-specific Implementation (same observable behaviour, different mechanism)

| Feature | Desktop mechanism | Android mechanism | Notes |
|---------|-------------------|-------------------|-------|
| Process/foreground monitoring | `psutil` + `GetForegroundWindow` | `UsageStatsManager.queryUsageStats()` + `UsageEvents` + `AccessibilityService` (foreground window). Foreground-only accounting preserved. | Requires **Usage Access** (`PACKAGE_USAGE_STATS`) + Accessibility permission; polling 1–5 s. |
| Idle (“Away”) detection | `GetLastInputInfo` | `UsageStats` gap + `PowerManager` + `DeviceIdle` + `AccessibilityService` inactivity. “Passive Reading” heuristic: foreground app still visible but no touch/scroll for N s → not counted (optional, user-configurable). | Maps prompt’s “Away/Passive Reading” to existing idle gate, now with Android signals. |
| Application restriction (BLOCK/CLOSE) | `WM_CLOSE` → `TerminateProcess` | **Overlay + Accessibility “bring-home”** (see §6.3). True `killBackgroundProcesses()` only kills background; foreground-close for 3P apps requires Accessibility “Back/Home” or Device-Owner `DevicePolicyManager.setPackagesSuspended()` (work-profile). No root required for best-effort, Device-Owner optional for strong enforcement. | User must be told exact enforcement strength (see Permissions Guide). |
| Game `WAIT_FOR_SESSION_END` | Riot detector suite + `GameSessionGuard` | Android: **generic** `GameSessionDetector` — match signal for mobile games (e.g. via notification listener “Game is running” or companion heuristics). LoL/VALORANT desktop detectors do not port; they are replaced by a conservative “if game package still foreground or ongoing notification present → WAIT”. Coarse but safe (same fail-safe direction). | Desktop game plugins classified as *desktop-only*. |
| Scheduling & background execution | Long-lived thread + optional Task Scheduler watchdog | `WorkManager` (15-min minimum) + `AlarmManager.setExactAndAllowWhileIdle()` for enforcement deadlines + `ForegroundService` while a limit is live + `BOOT_COMPLETED` + `TIMEZONE_CHANGED` receivers. | Android 12+ exact-alarm permission; 14+ foreground-service types. |
| Startup / autostart | Registry `HKCU\…\Run` | `BOOT_COMPLETED` receiver + user-visible `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` guidance. | Cannot self-revive silently; user must exempt from battery opt. |
| Notifications & warnings | Qt tray notifications | `NotificationManager` channels (limits, warnings, pause, security). `POST_NOTIFICATIONS` (Android 13+). | |
| Backup/restore | `backups/` copy + SQLite backup API | `Storage Access Framework` (SAF) export/import (`ACTION_CREATE_DOCUMENT`/`OPEN_DOCUMENT`) + auto-backup to app-private storage. | Cloud backend explicitly avoided. |
| Website tracking & blocking | Local WS + MV3 extension (`activeTab` domain only) | **AccessibilityService URL extraction** (address-bar node traversal per browser) + `DevicePolicyManager` / `VpnService` *not* used (overly broad, privacy risk). Best-effort: match on package + extracted domain; block via overlay + navigation-back. **DNS/VPN blocking is documented as alternative for rooted/device-owner setups only.** | Honest limitation: not as reliable as desktop extension; fully documented. |
| Day rollover / timezone / DST | `local_day_str`/`localMidnightsBetween` + wall clock | `java.time.ZoneId.systemDefault()` + `ACTION_TIMEZONE_CHANGED` + WorkManager reschedule; identical wall-vs-monotonic split preserved. | |
| Security hardening (LifecycleAudit, UsageFloor, BypassWatch) | File + DB | Floor via `DataStore`/`EncryptedFile` outside Room; lifecycle audit in Room; `BypassWatch` → “Usage Access revoked / Battery optimisation re-enabled” detection. | |

### 5.3 Partially Portable with Documented Limitations

| Feature | Why partial | Android stance |
|---------|-------------|----------------|
| Reliable “force-close any app on sight” | Android sandbox: normal apps cannot `kill` foreground 3P apps (only OS can). `ActivityManager.killBackgroundProcesses()` leaves foreground alive; Accessibility “Back” is interruptible by the app. | Implement **best-effort overlay gate** (“Time limit reached — take a break”) + auto-navigate home; measure + report enforcement success rate; offer **Device Owner / Managed Profile** path for hard blocks (requires ADB/QR provisioning). Never claim reliability without privileges. |
| Desktop game detectors (LoL/VALORANT/REPO/TFT LoL process, Live Client API `:2999`, Riot log formats) | Windows process names, Windows paths, loopback API for Riot client — none exist on Android. | Marked Impossible (see 5.4) and replaced by generic detector (see above). |
| Browser extension ecosystem | Android Chrome does not support extensions; Kiwi/Firefox variants limited; `webNavigation` blocking unavailable. | Accessibility-based domain tracking is weaker; matrix documents per-browser support. |
| Continuous 1 s monitor loop | Doze, App Standby, battery optimisation kill background work; WorkManager min interval 15 min; exact alarms throttled. | Compensated by `AlarmManager` exact callbacks + `ForegroundService` during active enforcement; recalculation on wake; documented battery trade-off. |
| `WM_POWERBROADCAST` suspend gap | Android sleep is `Doze`/`ACTION_SCREEN_OFF` — semantics differ; monotonic `elapsedRealtime()` pauses differently. | Use `elapsedRealtime()` (monotonic) + `ACTION_SCREEN_OFF`/`ON` receivers to seal sessions. |

### 5.4 Impossible / Unsupported Under Normal Android App Permissions

| Desktop capability | Android normal-app ceiling | Handling |
|--------------------|----------------------------|----------|
| `TerminateProcess` any PID | No; `KILL_BACKGROUND_PROCESSES` or root only. | Documented; degraded to overlay+home. |
| Task Scheduler watchdog per-minute | WorkManager cannot repeat <15 min; exact alarms limited. | Guidance + foreground service alternative; Device-Owner `setPackagesSuspended` is the hard-watchdog analogue. |
| Registry autostart invisible repair | No registry; `BOOT_COMPLETED` needs user opt-in + battery exemption. | User must grant; app shows status. |
| `GetForegroundWindow` for any exe title | `UsageStats` aggregated (last-used timestamp), not real-time title; requires user grant. | Combine `UsageEvents` + `AccessibilityService` for near-real-time; still less granular — documented. |
| Per-process exe lists with launch-helpers (`LeagueClientUx.exe` + `League of Legends.exe`) | Android APK = one package per app (no multi-exe helpers). | Rule model simplified to `packageName` + optional `extraPackages` for split-APK/overlay helpers; game plugin hosts retained but detectors rewritten. |
| LoL/VALORANT/REPO/TFT specific probes (process table `ShooterGame.log`, Live Client API) | No Riot client on Android; no comparable logs. | Removed; generic detector substituted; matrix notes “desktop-only game plugin”. |
| PDF “never require external reader” for desktop package | Desktop had no PDF feature — limitation N/A. | Android implements a fresh internal PDF reader (`PdfRenderer`); no external app required (meets prompt verbatim). |

---

## 6. Android Technology Choices

### 6.1 Stack (selected after inspection)

* **Language:** Kotlin 2.0
* **UI:** Jetpack Compose (Material 3) — adaptive (phone/tablet), `Navigation Compose` with correct back handling.
* **Persistence:** Room (SQLite) + `Preferences DataStore` for settings; `EncryptedFile/DataStore` for floor file & token.
* **Background:** `WorkManager` for periodic checks + `AlarmManager` (exact) for enforcement deadlines + `ForegroundService` (`shortService`/`specialUse` type on Android 14+) for live enforcement window + `BroadcastReceiver` for `BOOT_COMPLETED`/`TIMEZONE_CHANGED`/`MY_PACKAGE_REPLACED`.
* **PDF:** `android.graphics.pdf.PdfRenderer` (API 21+) for viewing; `SAF` for import; no third-party PDF lib unless zoom/annotation needed (avoided per “avoid unnecessary dependencies”).
* **Permissions layer:** `UsageStatsManager` (usage access), `AccessibilityService` (foreground + web domain), `SYSTEM_ALERT_WINDOW` (overlay enforcement), `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` (Android 12+), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` guidance.

### 6.2 Architecture

```
ui (Compose screens + Navigation) 
  → viewmodel (StateFlow/Snapshot, RuleRow, i18n)
    → domain (Schedule/Rule/Tracker/StateMachine/Policy/PauseController — pure Kotlin, JVM-testable)
      → data (Room DAOs + DataStore + Repositories)
        → platform (UsageStatsMonitor, AccessibilityMonitor, EnforcementExecutor, NotificationHelper, AlarmScheduler, BootReceiver)
```

* Business rules are **pure Kotlin with no Android imports** — fully unit-testable on JVM.
* `androidTest` (instrumented) only for DB migrations, SAF round-trip, and real-device restriction spot checks.
* Single `TimeManagerService` (ForegroundService) owns the monitor loop when any non-STRICT→STRICT transition is live; otherwise WorkManager+Alarm.

### 6.3 Enforcement Honesty

The app will expose a **Restrictions Status screen** (permission onboarding) that shows, per method:

* Usage Access: granted / denied → “monitoring possible yes/no”
* AccessibilityService: enabled / disabled for Time Manager → “foreground detection & website domain extraction possible”
* Draw-over-others: granted / denied → “overlay blocking possible”
* Exact alarm: granted / denied (Android 12+) → “exact enforcement timers possible”
* Battery optimisation: exempt / not → “background execution reliable/ throttled”
* Device Owner (optional): active / not → “hard `setPackagesSuspended` blocks possible”

Every enforcement event logs `ENFORCED` vs `DEFERRED` vs `PROTECT_SKIP` with human detail — same transparency as desktop `enforcement_log`.

---

## 7. Data-Migration, Permissions, Battery, Security

* **Data migration:** fresh install (new DB path) — import via SAF JSON backup of desktop DB not required for v1, but backup format is versioned JSON so a future desktop→Android migration path stays open. Auto-backup keeps last 7 in `getExternalFilesDir`.
* **Permissions rationale screen** before any system permission request; `shouldShowRequestPermissionRationale` respected; denial never silent → status screen shows “inactive / unsupported”.
* **Battery:** on first enforcement, show exemption dialog; `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` only after user taps “Fix”; never stealth.
* **Security:** all usage stays on-device; no backend, no accounts; backup validation (JSON schema, rule re-validation on restore); imported PDFs validated via `PdfRenderer` open test; secrets (`floor.json`, token) in app-private encrypted storage; DataStore writes are atomic.

---

## 8. Phased Implementation Plan + Acceptance Criteria

> The desktop app is never overwritten — Android lives in `android/` (the “clearly isolated module” required by §11).

| Phase | Work | Acceptance Criteria |
|-------|------|---------------------|
| **A** | Audit & plan (this doc) | Doc committed, no code changes to `app/` verified by `git status`. |
| **B** | Architecture & design (this doc §6 + `android/README.md`) | Module map + data-flow diagram + API choice table reviewed. |
| **C** | Android project setup + core domain models | `android/` builds (Gradle sync success), Kotlin ports of `Rule`, `Schedule`, `TimeUtils`, `StateMachine`, `Tracker`, `Policy`, `PauseController` compile; JVM unit tests for schedule/matcher/state-machine pass. |
| **D** | Scheduling, persistence, study-state | Room DB matches `schema.sql` v3 (verified by migration test); `Schedule.isActive()` handles overnight/overlapping/timezone/DST; Study Mode mapping (Active/Passive/Away/NotStudying) ↔ enforcement states via idle gate. |
| **E** | Native UI, PDF reader, settings | Compose navigation with back handling; screens: Dashboard, Schedules (weekly + one-time), Rule editor, Allowed-apps/websites, Study Mode controls, PDF library + `PdfRenderer` reader (SAF import), Settings/Permissions/Backup; RTL Arabic works; light/dark themes; `supportsRtl`. |
| **F** | Monitoring & enforcement (Android-specific) | `UsageStatsMonitor` + `AccessibilityService` provide foreground + website domain; overlay enforcement + home-nav; optional Device-Owner path; `WorkManager`+`AlarmManager`+`ForegroundService` keep enforcement alive across Doze/reboot with `BOOT_COMPLETED` receiver; permission-denied states render correctly. |
| **G** | Testing & reliability | Unit tests ≥ desktop parity for schedules/overlaps/overnight/state-machine/accounting; integration tests for midnight splits, process-death recovery, reboot reschedule, DB migration, backup restore, PDF re-open; instrumented spot checks on real device (or code-inspection label when unavailable). |
| **H** | APK build, docs, verification | Debug APK builds via `./gradlew assembleDebug` (or CI artifact); `android/BUILD.md`, `PERMISSIONS.md`, `FEATURE_PARITY.md` (preserved/adapted/impossible), known limitations, device-config guide; GitHub Actions `build.yml` (build+test). No signing keys committed; `.gitignore` excludes `*.apk`, `local.properties`. |

Reporting after each phase: changed files, tests run, unresolved issues.

---

## 9. Risks & Open Questions

1. **Exact alarm on Android 12+** — user must grant in Settings; if denied, enforcement timings degrade to WorkManager inexact → documented, status screen warns.
2. **OEM battery killers** (Samsung/MIUI) — exemption prompt may be insufficient; doc lists vendor-specific steps.
3. **Accessibility URL extraction** is brittle per browser (DOM class names change); block coverage will be best-effort — honest matrix required.
4. **APK size**: Compose+Room ≈ 5–15 MB; acceptable vs PyInstaller (~80 MB).

---

## 10. Definition of Done (for Android)

* `android/` builds, installs as debug APK.
* Core scheduling (weekly + one-time), rule enforcement, state-machine, tracking (incl. overnight/timezone/reboot) preserve desktop behaviour (verified by tests).
* Data persists safely across process death, reboot, upgrades (Room migrations, DataStore).
* Restrictions are enforced **to the extent Android allows**; setup guide explains privileges; no feature is silently weakened.

---

*End of Phase A. No files under `app/` were modified. Next: Phase B→H implementation.*
