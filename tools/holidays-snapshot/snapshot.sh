#!/usr/bin/env bash
# Downloads the public holidays of the current and the next year from Nager.Date v3 for every supported country and
# stores the API response as is in feature/holidays/src/main/assets/holidays/{country}-{year}.json. The app filters by
# type and subdivision itself (docs/spec/02-diseno.md §6) and prefers its own cache to this snapshot.
# Usage: tools/holidays-snapshot/snapshot.sh [year]   (countries: tools/holidays-snapshot/countries.txt)
set -euo pipefail
cd "$(dirname "$0")/../.."

year="${1:-$(date -u +%Y)}"
out=feature/holidays/src/main/assets/holidays
mkdir -p "$out"

while read -r country; do
  [ -z "$country" ] && continue
  for y in "$year" $((year + 1)); do
    tmp=$(mktemp)
    curl -fsS --retry 3 --max-time 30 "https://date.nager.at/api/v3/PublicHolidays/$y/$country" -o "$tmp"
    # Refuse anything that is not a non-empty JSON array of holidays: a broken response must never replace good data.
    jq -e 'type == "array" and length > 0 and all(.[]; has("date") and has("types"))' "$tmp" > /dev/null
    jq --sort-keys . "$tmp" > "$out/$country-$y.json"
    rm -f "$tmp"
    echo "$out/$country-$y.json"
  done
done < <(grep -v '^#' tools/holidays-snapshot/countries.txt)
