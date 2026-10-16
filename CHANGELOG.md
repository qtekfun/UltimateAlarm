# Changelog

All notable changes to UltimateAlarm. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
versions follow [SemVer](https://semver.org/); the same text is kept under `fastlane/metadata/android/*/changelogs`
for F-Droid.

## [0.4.0]

Phase 5: sounds.

### Added
- Our own sound pack: 13 tones made for UltimateAlarm (gentle wake-up, energetic wake-up, backup, notification), free software like the app, normalised in loudness and looping without clicks.
- Sound picker per alarm with preview using the alarm's volume ramp; system alarm tones and your own audio files are also allowed.
- Backup tone: a general one in Settings, which each alarm can override.
- If a sound cannot be played (a deleted file, for example), the alarm falls back to the pack's tone, then the system tone, then a beep: something always rings.

## [0.3.0]

Holidays (moved ahead of sounds at the product owner's request).

### Added
- The evening before a holiday (20:00 by default), a notification asks: "Tomorrow is a holiday (Name). You have N alarms." with Skip them / Keep them. Without an answer, the alarms ring.
- Settings → Holidays: country and region (official holidays from Nager.Date, Spain's regions listed), an optional public calendar (ICS address) for town or extra holidays, holidays added by hand (once or every year), and the yearly list where any holiday can be marked as "not a holiday for me".
- Holidays work offline from day one (included in the app) and refresh monthly.
- Alarms that respect holidays show the holiday in their row once you decide to skip it.

### Changed
- The Holidays tab is gone: everything about holidays lives in Settings.

### Fixed
- A dismiss or snooze meant for another alarm (for example from an old notification) no longer silences the alarm ringing now.

## [0.2.0]

Phase 4: the complete ringing experience.

### Added
- Gentle volume ramp up to each alarm's maximum volume; the alarm stream is raised while ringing and restored afterwards.
- Snooze with each alarm's duration and limit, and a "Snoozed until HH:MM" notification that can dismiss the alarm.
- Backup alarm: if nobody reacts, or after the last allowed snooze, it rings at full volume with continuous vibration, with a safety alarm in case the app was killed.
- A ring interrupted by a crash or a reboot resumes.
- Alarm screen: huge time, label, snooze and swipe to dismiss (also available to TalkBack).
- "Alarm in 1 h" notification with "Skip this" for alarms that ask for it.
- Settings: what the volume buttons do while ringing and when an unanswered alarm goes quiet.

## [0.1.0]

Phases 2 and 3: the scheduling engine, alarms stored on the device, the alarm list and the editor.

### Added
- Scheduling engine: weekly days, shift cycles, per-day exceptions, vacations, skips and holiday decisions with a fixed precedence; Spanish DST and time zone changes handled; 77 unit tests, 100 % coverage.
- Alarms stored in a local database readable before the first unlock.
- Alarm list sorted by next alarm, with "rings in", the reason a day is silenced, "skip" with undo, duplicate and delete with undo.
- Editor: time, label, repeat days with shortcuts, one-time alarms (date or next time), respect holidays, maximum volume, volume ramp, vibration, snooze, backup alarm and 1 h notice settings.
- One-time alarms are deleted when dismissed (with a 10 s undo) and flagged when the phone was off at their time.
- Alarms are re-registered on boot, time and zone changes, updates and every change; daily watchdog.
- Banner when a permission needed to ring is missing; diagnostics behind the top bar.

### Changed
- The spike's test alarm is gone; the autotest stays in Diagnostics.

## [0.0.1-spike]

Phase 1: reliability spike (pre-release, for device testing only).

### Added
- Daily test alarm scheduled with `setAlarmClock`, re-scheduled on boot (also before the first unlock), time and zone changes and app updates.
- Firing path: foreground service (type chosen at runtime: `systemExempted` or `mediaPlayback`), full-screen alarm screen over the lock screen, alarm-stream sound with a generated beep fallback, vibration, snooze and dismiss.
- Direct Boot: firing components and their data live in device-protected storage.
- Diagnostics: runtime capability probe with shortcuts to the system settings, one-minute autotest, exportable event log.

## [0.0.0-rc1]

Phase 0: project skeleton (test release).

### Added
- Gradle project with the modules of the design, Material 3 Expressive theme with dynamic colour, English and Spanish.
- CI: ktlint, detekt, Android lint, unit tests, 90 % coverage gate on the scheduling engine, string and sound licence checks, no-Google check.
- Release workflow (signed APK, SHA-256 checksums), nightly instrumented tests on AOSP emulators, monthly holiday snapshot.
- F-Droid metadata in English and Spanish.
