# Start-up and firing performance (RNF-5)

- `Application.onCreate` does nothing on the main thread beyond Hilt's graph: re-scheduling, the holiday snapshot seed,
  WorkManager jobs and the backup watcher all run on a background coroutine.
- The database, holiday and backup repositories are created lazily (`AlarmGraph`).
- Firing path: the receiver starts the foreground service and returns; database reads, logging and re-scheduling happen
  off the main thread (`doAsync`, single-thread event log writer). Foreground notification and sound start before any I/O.
- Lists are computed in ViewModels with flows; the alarm list re-evaluates once per 30 s, not per frame.
- Measuring cold start on a Pixel 8 (target < 1 s) needs a device: `adb shell am start -W com.qtekfun.ultimatealarm/.MainActivity`
  (no devices are available for this project, see docs/spike-report.md).
