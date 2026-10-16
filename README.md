# UltimateAlarm

An Android alarm clock that **knows public holidays**, asks you the evening before and lets you decide; with flexible
schedules (days of the week, shift patterns, per-day exceptions, vacations), self-deleting one-time alarms, "skip tomorrow",
a backup alarm, challenges to dismiss, timer, stopwatch and local + Nextcloud backup.

- Android 12+ (API 31), any manufacturer, with or without Google services. No Play Services, Firebase or ML Kit.
- Everything local: alarms are scheduled on the device. The network is only used to download holidays and for your Nextcloud backup.
- Kotlin, Jetpack Compose, Material 3 Expressive. App in English and Spanish.
- GPL-3.0-or-later.

**Status:** 0.2.0 — alarms, list, editor and the complete ringing experience work; sounds, holidays, challenges, clock and backup arrive phase by phase. The specification lives in [`docs/spec/`](docs/spec/README.md) (in Spanish),
deviations from it in [`docs/spec/DECISIONS.md`](docs/spec/DECISIONS.md).

Download: [releases](https://github.com/qtekfun/UltimateAlarm/releases). How releases are made: [RELEASING.md](RELEASING.md).

## Build

```sh
./gradlew check assembleDebug
```

JDK 21 and the Android SDK (compileSdk 37) are needed.
