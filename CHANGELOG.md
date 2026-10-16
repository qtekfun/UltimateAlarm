# Changelog

All notable changes to UltimateAlarm. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
versions follow [SemVer](https://semver.org/); the same text is kept under `fastlane/metadata/android/*/changelogs`
for F-Droid.

## [1.0.0-rc5]

New look ("Amanecer").

### Added
- Settings → Appearance: system, light or dark mode, and the colour (orange, raspberry, plum, indigo, teal, mustard, or your wallpaper's).
- The next alarm leads the list as a large card; days an alarm will not ring (holiday, vacation, skipped) get their own notice.
- The time is set with scroll wheels, or typed with the keyboard button.

### Changed
- New typefaces (Bricolage Grotesque, DM Sans), warm colours, rounded cards, day pills and a sun-rise ringing screen.

## [1.0.0-rc4]

Fourth release candidate.

### Fixed
- When holidays cannot be downloaded because the app has no internet (for example, its network permission is off, or a VPN or private DNS blocks it), Settings → Holidays now says so and opens the app's system settings, instead of showing a technical error.

## [1.0.0-rc3]

Third release candidate.

### Removed
- Madrid's official open-data calendar: its download did not work. Madrid's local holidays keep coming from the compiled municipal calendar (Calendarios Nacionales), with the same dates.

## [1.0.0-rc2]

Second release candidate.

### Fixed
- The first-run assistant closed the app on the "Your phone" step: the release build had dropped the manufacturer guide texts.

## [1.0.0-rc1]

Phase 12: ready for F-Droid (release candidate).

### Added
- New adaptive and monochrome icon.
- Store screenshots in English and Spanish, taken automatically on an emulator.
- Licence review and dependency audit (no Google Play services, Firebase or ML Kit).

### Fixed
- Screens without their own bar (such as the alarm editor) showed a dark background in the light theme.

## [0.9.0]

Phase 11: onboarding and polish.

### Added
- First-run reliability assistant: permissions with their system screens, the steps of your phone's maker (ColorOS, One UI, HyperOS/MIUI, OriginOS/Funtouch, MagicOS, plain Android or generic: battery, auto-start, recent apps, full screen), opening the right screen when the phone has it, and a real one-minute autotest. Also in Settings.

### Fixed
- An alarm that started ringing right after another one was dismissed could be silenced by the end of the first one.

## [0.8.1]

Official town calendars.

### Added
- If your town publishes its labour calendar as open data, the app uses it on top of the compiled municipal calendar, with the official names. First one: Madrid city (Ayuntamiento de Madrid, datos.madrid.es, CC BY 4.0), shown with its attribution in Settings → Holidays.

## [0.8.0]

Phase 10: backup.

### Added
- Export to a file and import from it: alarms, schedules, exceptions, vacations, your holidays and cancellations, and settings; optionally your own audio files. Import replaces what you have or adds to it, then every alarm is re-scheduled.
- The Nextcloud account can travel in the file, encrypted with a password you choose (AES-GCM, Argon2id).
- Automatic backups to your own Nextcloud (WebDAV, https only): one minute after a change and daily, keeping the last N copies (10 by default); restore from the list of copies with a preview. The app password is stored encrypted with the Android Keystore.

## [0.7.0]

Phase 9: clock.

### Added
- Timers: several at once, quick presets (1–30 min) and custom durations with a label; a notification counts down; when one ends it rings with its own tone and a full screen with Stop and +1 min.
- Stopwatch with laps; it keeps running with the app closed and shows its time in a notification.
- Bedtime reminder some hours (8 by default, 6–10) before the first alarm of the next day; not shown if nothing rings; can be turned off in Settings.

## [0.6.0]

Phase 8: challenges to dismiss.

### Added
- Per-alarm challenge to dismiss (never to snooze): maths (difficulty 1–3, 1–5 problems), scanning a registered QR or barcode, or holding a registered NFC tag. Codes and tags are registered from the editor, with an optional name.
- Phones without camera or NFC get maths instead, and the editor says so.
- With a challenge, the notification, the volume buttons and the snoozed notice cannot dismiss: only the alarm screen can.
- Releases include one APK per processor type (ABI) besides the universal one.

## [0.5.0]

Phase 7: advanced scheduling.

### Added
- Shift patterns in the editor: presets 4×4, 5×2, 6×3, 2×2 and alternate weeks, or any cycle of 2–28 days; choose day 1 of the cycle and see the next days it rings.
- Exceptions calendar per alarm: a month view showing which days ring; tap a day to force it to ring or not to ring.
- Vacations: date ranges that pause every alarm or the ones you pick; editable, deletable, past ones kept apart.

## [0.4.1]

Local holidays.

### Added
- Settings → Holidays (Spain): choose your province and municipality and the app adds its local holidays (and the full official calendar of your town), refreshed monthly. Data: Calendarios Nacionales, compiled from the BOE, the Social Security and regional open data.

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
