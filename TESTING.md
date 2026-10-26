# Testing — Desktop parity + Android specifics

## Automated tests (JVM, run anywhere)

```bash
# From the repository root (project is at the root; there is no android/ folder):
./gradlew testDebugUnitTest
```

| Test file | What it pins |
|-----------|--------------|
| `ScheduleTest` | Weekly/weekends/custom days, time windows, overnight 22:00–02:00, overlapping, DST, parse round-trip, EVERYDAY/WEEKDAYS/WEEKENDS |
| `StateMachineTest` | NORMAL/WARNING/ENFORCED/DISABLED transitions, warnings fire once, daily vs session limits, outside-schedule DISABLED, `WAIT_FOR_SESSION_END` (in-session vs unknown vs not-in-session), day rollover reset, WARN_ONLY, BLOCK_WEBSITE |
| `TimeUtilsTest` | `formatDuration`, `classifySkew` (60s tolerance), `localMidnightsBetween` midnight splits |
| `RuleValidationTest` | Name/limit/domain/package validation, domain/package matching, schedule preserved |
| `TrackerTest` | Monotonic delta attribution, single-session counting, skew >60s closes sessions, `todayTotal` includes unflushed remainder, `flush()` |
| `PolicyTest` | NORMAL grandfathering, STRICT no grandfathering, CLOSE immediate, protected packages never blocked |
| `PauseControllerTest` | 1..480 min validation, active/expire, STRICT exempt list |

Expected: **~35 tests, all pass in <5 s**.

## Instrumented tests (need emulator/device)

```bash
./gradlew connectedAndroidTest
```

- `DatabaseMigrationTest.migrate1to3` — verifies Room migrations preserve tables.
- `DatabaseMigrationTest.backupRestoreValidation` — SAF round-trip (manual).
- `Backup` import validates `Rule.validate()` rejects malformed JSON.

## Manual on-device test plan (honest: requires real hardware)

1. **Foreground detection**: Install app + grant Usage Access. Open YouTube rule (30 s daily). Switch between YouTube and Chrome every 2 s → YouTube counts only while foreground.
2. **Overnight schedule**: Create rule with `22:00–02:00`. Test at 21:59 (inactive), 22:01 (active), 01:59 (active), 02:01 (inactive).
3. **Enforcement overlay**: Let daily limit hit. Verify overlay appears, Home button dismiss works, notification posted, log shows `EXECUTED`.
4. **Permission denied**: Revoke Usage Access while rule active → dashboard shows “inactive — grant Usage Access” and never silently counts.
5. **Protection**: Create rule targeting `com.timemanager.android` → verify `SKIPPED protected`.
6. **Reboot**: With rule enabled, `adb reboot` → on boot, `BOOT_COMPLETED` restarts monitoring (check logcat for `TimeManagerService` start).
7. **Timezone**: Change device timezone while rule has window `09:00–17:00` → schedule recalculates (check `isActive` after change).
8. **PDF**: Import PDF via SAF → open → flip pages → revoke permission → verify error handling + re-import works.
9. **Battery**: Enable battery optimisation → verify WorkManager still fires but delayed; exempt → faster.
10. **Arabic RTL**: Switch language to العربية → verify layout flips, no clipped text.

Items 6,8,9 are marked `tested-by-inspection` until run on hardware; all JVM logic is tested by code.
