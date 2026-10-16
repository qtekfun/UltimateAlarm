# Phase 1 spike — reliability report

APK: `UltimateAlarm-0.0.1-spike*.apk` from the [v0.0.1-spike release](https://github.com/qtekfun/UltimateAlarm/releases/tag/v0.0.1-spike).
Status: **waiting for device validation** (Pixel 8, OPPO Find X8 Pro, OPPO Find X9 Ultra).

## What the spike does

| Piece | Implementation |
|---|---|
| Scheduling | `AlarmManager.setAlarmClock` with a show intent; one entry per alarm. `setWindow` only if exact alarms are denied (logged). |
| Re-scheduling | `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`. |
| Direct Boot | Receivers, service and alarm screen are `directBootAware`; the test alarm and the event log live in device-protected storage. |
| Firing | Receiver → foreground service → full-screen notification → alarm screen (`showWhenLocked`, `turnScreenOn`). |
| FGS type | `systemExempted` on Android 14+ when exact alarms are allowed, else `mediaPlayback`; falls back to `mediaPlayback` if refused. A switch in Diagnostics forces `mediaPlayback`. |
| Sound | System alarm tone on `USAGE_ALARM`, looping; if it fails or does not start in 3 s, a generated beep. |
| Diagnostics | `CapabilityProbe` list with "Fix" buttons, the autotest (alarm in 1 min) and the event log (export to a file). |

## How to run a scenario

1. Install the APK. Open the app → **Alarms** tab (spike screen). In **Diagnostics**, press **Fix** on everything red (notifications, exact alarms, full screen). Note whether "Not restricted by battery optimisation" is green.
2. Set the **test alarm** (daily) for the scenario time, or use **Start autotest (1 min)** for the quick ones.
3. Run the scenario (table below). When the alarm rings, note: did it sound, did the screen turn on, did the alarm screen show over the lock screen, delay.
4. Dismiss it, then **Export event log** (Diagnostics → Event log → Export, pick a folder). One file per scenario and phone; the name carries the date and time.

### Event log

Each line: `time · alarm id · event · details`. Events: `SCHEDULED`, `RESCHEDULE` (reason), `FIRED` (delay and state: unlocked, interactive, doze, power_save, ignoring_battery_opt, dnd, alarm_volume), `FGS_STARTED` (type, full_screen), `SOUND` (source: system tone or beep and why), `SCREEN_SHOWN` (time from firing to the alarm screen), `DISMISSED`, `SNOOZED`, `AUTOTEST_*`, `ERROR`, `TIMEOUT`.
The export button writes a `.txt` through the system file picker (Downloads, Drive-less local folder, etc.). Over USB: the file lands where you saved it; `adb pull` works for Downloads.

## Scenarios

Mark each cell ✅ (rang on time, screen on, alarm screen over lock), ⚠️ (rang but late / no screen / beep instead of tone — say which) or ❌ (did not ring). Add the delay from `FIRED` and attach the exported log.

| # | Scenario | How | Pixel 8 | OPPO Find X8 Pro | OPPO Find X9 Ultra |
|---|---|---|---|---|---|
| 1 | Screen off 8 h | Test alarm ~8 h ahead, screen off, phone still overnight (Doze). | | | |
| 2 | After reboot, not unlocked | Test alarm in ~10 min, reboot, **do not unlock**. Expect `RESCHEDULE reason=locked_boot` and `unlocked=false` in `FIRED`. | | | |
| 3 | App removed from recents | Test alarm in ~10 min (or autotest), swipe the app away from recents, screen off. | | | |
| 4 | Battery saver on | Turn battery saver on, autotest or test alarm in ~10 min, screen off. | | | |
| 5 | Manual time zone change | Test alarm at HH:MM, change zone manually (e.g. Lisbon), check "Next" shows HH:MM in the new zone, wait. Expect `RESCHEDULE reason=timezone`. | | | |
| 6 | Do Not Disturb | DND on (default: alarms allowed), autotest, screen off. Note DND mode in `FIRED`. | | | |
| 7 | FGS type comparison (optional) | Diagnostics → force `mediaPlayback`, repeat scenario 3. | | | |

Per phone, also note: Android version / ROM, whether "Not restricted by battery optimisation" was green, and any autostart / background setting changed by hand (ColorOS: *Battery → App battery management*, *Auto launch*).

## Results

_To be filled in after device testing._

**Chosen FGS type:** _pending — `systemExempted` is the candidate on Android 14+; decided from the matrix._
