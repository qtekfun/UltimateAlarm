#!/usr/bin/env bash
# Fails if a bundled audio file has no entry in assets/sounds/LICENSES.md (origin, author, licence), so nothing
# without a redistributable licence can ship (F-Droid). Usage: scripts/check-sound-licenses.sh
set -euo pipefail
cd "$(dirname "$0")/.."

status=0
while IFS= read -r audio; do
  dir=$(dirname "$audio")
  while [ "$(basename "$dir")" != "sounds" ] && [ "$dir" != "." ]; do dir=$(dirname "$dir"); done
  licenses="$dir/LICENSES.md"
  name=$(basename "$audio")
  if [ ! -f "$licenses" ] || ! grep -qF "$name" "$licenses"; then
    echo "error: $audio has no entry in $licenses" >&2
    status=1
  fi
done < <(find . -path '*/build' -prune -o -path ./.git -prune -o -path '*/assets/sounds/*' -type f \
  \( -name '*.ogg' -o -name '*.opus' -o -name '*.mp3' -o -name '*.wav' -o -name '*.flac' -o -name '*.m4a' \) -print)

[ "$status" -eq 0 ] && echo "Sounds: every bundled audio file has a licence entry."
exit "$status"
