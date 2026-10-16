#!/usr/bin/env bash
# Fails if a text named in the manufacturer guides (assets/oem_guides.json) is missing from the built APK. The texts are
# looked up by name at runtime, so the resource shrinker cannot see them; 1.0.0-rc1 shipped without them and the
# assistant crashed. Usage: scripts/check-oem-strings.sh <apk>
set -euo pipefail

cd "$(dirname "$0")/.."
apk="${1:?usage: scripts/check-oem-strings.sh <apk>}"
guides=feature/onboarding/src/main/assets/oem_guides.json
aapt2=$(ls "$ANDROID_HOME"/build-tools/*/aapt2 | sort -V | tail -1)

present=$("$aapt2" dump resources "$apk" | sed -n 's#.* string/\(oem_[A-Za-z0-9_]*\).*#\1#p' | sort -u)
missing=0
for key in $(grep -o '"textKey" *: *"[^"]*"' "$guides" | sed 's/.*"\([^"]*\)"$/\1/' | sort -u); do
  if ! grep -qx "$key" <<<"$present"; then
    echo "Missing from the APK: string/$key" >&2
    missing=1
  fi
done
[ "$missing" = 0 ] && echo "Every manufacturer guide text is in the APK."
exit "$missing"
