# Changelog

All notable changes to UltimateAlarm. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
versions follow [SemVer](https://semver.org/); the same text is kept under `fastlane/metadata/android/*/changelogs`
for F-Droid.

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
