#!/usr/bin/env bash
# Fails if a translatable string exists in English (values/) but not in Spanish (values-es/), or the other way round,
# in any module. The app ships in both languages and nothing may fall back silently. Usage: scripts/check-strings.sh
set -euo pipefail
cd "$(dirname "$0")/.."

# Prints "name" for every <string>/<plurals>/<string-array> in the given files, skipping translatable="false".
keys() {
  [ "$#" -eq 0 ] && return 0
  grep -hoE '<(string|plurals|string-array) [^>]*name="[^"]+"[^>]*>' "$@" \
    | grep -v 'translatable="false"' \
    | sed -E 's/.*name="([^"]+)".*/\1/' | sort -u
}

status=0
while IFS= read -r res; do
  en=$(keys $(find "$res/values" -maxdepth 1 -name '*.xml' 2>/dev/null))
  es=$(keys $(find "$res/values-es" -maxdepth 1 -name '*.xml' 2>/dev/null))
  missing_es=$(comm -23 <(printf '%s\n' "$en") <(printf '%s\n' "$es") | sed '/^$/d')
  missing_en=$(comm -13 <(printf '%s\n' "$en") <(printf '%s\n' "$es") | sed '/^$/d')
  if [ -n "$missing_es" ]; then echo "$res: missing in values-es: $missing_es" >&2; status=1; fi
  if [ -n "$missing_en" ]; then echo "$res: missing in values: $missing_en" >&2; status=1; fi
done < <(find . -path ./build -prune -o -type d -path '*/src/main/res' -print | grep -v '/build/')

[ "$status" -eq 0 ] && echo "Strings: English and Spanish have the same keys."
exit "$status"
