#!/usr/bin/env bash
# Prints the version code of a version (default: appVersion from gradle.properties). Same formula as app/build.gradle.kts:
# (MAJOR*10000 + MINOR*100 + PATCH) * 100 + suffix, suffix = N for -rcN, 1 for -spike, 99 for a final version.
set -euo pipefail
cd "$(dirname "$0")/.."

version="${1:-$(grep '^appVersion=' gradle.properties | cut -d= -f2)}"
if [[ ! "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-rc\.?([0-9]+)|-(spike))?$ ]]; then
  echo "error: '$version' is not MAJOR.MINOR.PATCH, MAJOR.MINOR.PATCH-rcN or MAJOR.MINOR.PATCH-spike" >&2
  exit 1
fi
suffix=99
[ -n "${BASH_REMATCH[5]}" ] && suffix=${BASH_REMATCH[5]}
[ -n "${BASH_REMATCH[6]}" ] && suffix=1
echo $(( (BASH_REMATCH[1] * 10000 + BASH_REMATCH[2] * 100 + BASH_REMATCH[3]) * 100 + suffix ))
