# Scheduling engine — case table

`:core:scheduling` (`SchedulingEngine`), design §3. Every row is covered by a unit test (class in the last column).
Dates: 2026-10-16 is a Friday; zone Europe/Madrid unless stated.

## Precedence (first rule that applies wins)

| # | Rule | Verdict | Test |
|---|---|---|---|
| 1 | Day override FORCE_ON | rings, even on an unscheduled day, during vacation, skipped or holiday-skipped | `EnginePrecedenceTest` |
| 1 | Day override FORCE_OFF | `EXCEPTION` | `EnginePrecedenceTest` |
| 2 | Vacation covering the day (all alarms or this one; bounds inclusive) | `VACATION` | `EnginePrecedenceTest` |
| 3 | Skip for this alarm and day | `SKIPPED` | `EnginePrecedenceTest` |
| 4 | Respects holidays + effective holiday + "skip" decision | `HOLIDAY` | `EngineHolidayTest` |
| 5 | Base pattern (weekly / cycle) | rings or `NOT_SCHEDULED` | `EngineBasicsTest` |
| – | Alarm disabled | `DISABLED`, no occurrence | `EngineBasicsTest` |

## Cases

| Case | Expected | Test |
|---|---|---|
| Weekly, time ahead today | today | `EngineBasicsTest` |
| Weekly, Friday after the time | Monday | `EngineBasicsTest` |
| Exactly at the alarm time | next day (strictly after now) | `EngineBasicsTest` |
| No days selected | never | `EngineBasicsTest` |
| Cycle 4 on / 4 off; alternate weeks (cycle 14); anchor in the future | repeats from the anchor in both directions | `EngineBasicsTest` |
| Nothing within 400 days | `null`; day 400 still counts | `EngineBasicsTest` |
| Holiday without decision | **rings** | `EngineHolidayTest` |
| Holiday, "skip" for all / "keep" | silenced / rings | `EngineHolidayTest` |
| Per-alarm decision vs. global decision | per-alarm wins | `EngineHolidayTest` |
| Alarm with "respect holidays" off | rings | `EngineHolidayTest` |
| Decision on a non-holiday | ignored | `EngineHolidayTest` |
| API holiday cancelled by the user | not a holiday | `EngineHolidayTest`, `EngineHelpersTest` |
| Manual, yearly, override-added holidays; manual name beats API name | holidays | `EngineHelpersTest` |
| Yearly holiday on 29 February | only in leap years | `EngineHelpersTest` |
| One-shot in the future / past | its instant / `null` | `EngineOneShotTest` |
| One-shot "next time it is HH:MM" | today or tomorrow | `EngineOneShotTest` |
| One-shot with overrides, vacation, skip, holiday decision | rings anyway (D-11) | `EngineOneShotTest` |
| One-shot skip target / suppression | none | `EngineOneShotTest` |
| Spring forward 2026-03-29, alarm 02:30 | 03:00 CEST | `EngineTimeTest` |
| Fall back 2026-10-25, alarm 02:30 | once, the first (CEST) 02:30 | `EngineTimeTest` |
| Fall back 2027 (31 October) | correct offsets before/after | `EngineTimeTest` |
| Zone change (Lisbon, Athens) | wall-clock time in the new zone | `EngineTimeTest` |
| Leap day 2028: weekly, cycle across it, one-shot on it | normal day | `EngineTimeTest` |
| New year crossing | next year | `EngineTimeTest` |
| Row suppression (holiday with name, skip, vacation, exception) | first silenced pattern day before the next occurrence | `EngineHelpersTest` |
| "Skip tomorrow" target | the next occurrence, even after a weekend; undo restores it | `EngineHelpersTest` |
| Skips whose time passed | dropped by `liveSkips` | `EngineHelpersTest` |
| Holiday eve | affected = recurring, enabled, respecting holidays, would ring ignoring answers; none → no notification | `EngineHelpersTest` |
