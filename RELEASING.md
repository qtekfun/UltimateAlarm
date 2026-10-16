# Releasing

One release per phase, versions from `docs/spec/03-ci-release.md`. Releases are tagged by hand from `master`.

1. Before the last PR of a phase: run the *Holidays snapshot* workflow (Actions → Holidays snapshot → Run workflow)
   and merge its PR if it opened one.
2. In the last PR of the phase:
   - set `appVersion` in `gradle.properties` (for example `0.1.0`, `0.0.1-spike` or `1.0.0-rc1`);
   - add a `## [x.y.z]` section to `CHANGELOG.md`;
   - save a short version of the notes (500 bytes max) as `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
     and `es-ES/...`. `scripts/version-code.sh` prints the version code:
     `(MAJOR*10000 + MINOR*100 + PATCH) * 100 + suffix` (suffix `N` for `-rcN`, `1` for `-spike`, `99` for a final version).
   CI runs `scripts/release-notes.sh`, which fails if any of this is missing.
3. Once that PR is merged and CI on `master` is green: `git tag vX.Y.Z && git push origin vX.Y.Z`.
   The *Release* workflow checks the tag against `appVersion`, builds, verifies and publishes the GitHub Release with
   `UltimateAlarm-X.Y.Z.apk` and `SHA256SUMS.txt`. Versions with a suffix are pre-releases.

## Signing

Repository secrets for a release-signed APK:
`RELEASE_KEYSTORE_BASE64` (the keystore, base64), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
How they are created: `docs/spec/KEYS.md`. Without all four, the workflow signs with the debug key and publishes a
pre-release marked **UNSIGNED-DEBUG**. With them, the workflow checks the APK certificate and fails if it is still the
debug key. Never commit a keystore.

To rebuild an already published tag (for example after fixing signing): Actions → Release → Run workflow, `tag` = `vX.Y.Z`.
The APK, `SHA256SUMS.txt`, title and notes of that release are replaced.

## F-Droid

F-Droid builds the same source and compares its APK with the one published here (reproducible build: no timestamps, pinned
dependencies, no dependency metadata in the APK). Metadata lives in `fastlane/metadata/android/<locale>/` and the draft
recipe in `fdroid/`. Submission happens after 1.0.
