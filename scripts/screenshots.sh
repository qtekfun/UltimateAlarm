#!/usr/bin/env bash
# Takes the store screenshots on a running emulator with the debug build installed, in English and Spanish.
# Usage: scripts/screenshots.sh <output dir>. Run by .github/workflows/screenshots.yml. Elements are found by their text
# (scripts/ui.sh, as in UltimatePhone), so layout changes do not break it; a step that cannot find its element is skipped.
# No personal data: the emulator is empty and every alarm is created here.
set -uo pipefail
. "$(dirname "$0")/ui.sh"

PKG=com.qtekfun.ultimatealarm.debug
OUT=${1:-screenshots}
APK=app/build/outputs/apk/debug/app-debug.apk

shot() { adb exec-out screencap -p > "$OUT/$LOCALE/$1.png"; echo "shot $LOCALE/$1"; }
back() { adb shell input keyevent KEYCODE_BACK; sleep 2; }

adb install -r "$APK"
adb shell settings put system time_12_24 24
for LOCALE in en-US es-ES; do
  mkdir -p "$OUT/$LOCALE"
  adb shell am force-stop "$PKG"
  adb shell pm clear "$PKG"
  adb shell cmd locale set-app-locales "$PKG" --locales "$LOCALE" || true
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
  adb shell appops set "$PKG" SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS allow || true

  adb shell am start -n "$PKG/com.qtekfun.ultimatealarm.MainActivity" > /dev/null
  sleep 6
  shot 01_assistant
  tap_text '^(Skip|Omitir)$' 3
  # Two alarms through the editor: the default (07:00, Mon–Fri) and a labelled one-off.
  tap_text '^(Alarm|Alarma)$' 3
  shot 03_editor
  tap_text '^(Save|Guardar)$' 3
  tap_text '^(Alarm|Alarma)$' 3
  tap_text '^(Once|Una vez)$' 2
  tap_text '^(Save|Guardar)$' 3
  shot 02_alarms
  tap_text '^(Timer|Temporizador)$' 3
  tap_text '^(5 min)$' 2
  tap_text '^(10 min)$' 2
  shot 04_timer
  tap_text '^(Stopwatch|Cronómetro)$' 3
  tap_text '^(Start|Iniciar)$' 2
  sleep 3
  tap_text '^(Lap|Vuelta)$' 2
  shot 05_stopwatch
  tap_text '^(Alarms|Alarmas)$' 2
  tap_text '^(Settings|Ajustes)$' 3
  tap_text '^(Holidays|Festivos)$' 6
  shot 06_holidays
  back
  back
  # The ringing screen: a real autotest alarm, one minute from now.
  tap_text '^(Diagnostics|Diagnóstico)$' 3
  tap_text '^(Start autotest|Iniciar autotest)' 2
  adb shell input keyevent KEYCODE_HOME
  sleep 70
  shot 07_ringing
  adb shell input keyevent KEYCODE_BACK
  adb shell am force-stop "$PKG"
done
ls -R "$OUT"
