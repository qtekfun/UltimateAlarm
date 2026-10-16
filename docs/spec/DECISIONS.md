# Decisions and deviations from the spec

Changes to what `01`–`04` propose, and why. Newest last. The spec stays the source of truth for everything not listed here.

## D-1 · Signing secrets keep the spec names (`RELEASE_*`)

UltimateGallery and UltimatePhone sign with `UG_*` secrets, and an early version of this file switched to those names.
The secrets of this repository were created as `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`
and `RELEASE_KEY_PASSWORD` (spec `03-ci-release.md`), so the workflow reads those. Inside the build, `release.yml` hands them
to Gradle as the `UG_KEYSTORE_FILE`, `UG_KEYSTORE_PASSWORD`, `UG_KEY_ALIAS` and `UG_KEY_PASSWORD` environment variables, the
same ones the reference apps' `app/build.gradle.kts` reads; that also lets old tags be rebuilt unchanged.

## D-2 · Default branch is `master`

The repository's default branch is `master`, not `main`. Workflows trigger on pushes to `master` and every PR targets `master`.

## D-3 · The bootstrap PR ships a minimal `ci.yml`

No PR may merge without green checks, and the repository started empty. The first PR (`chore/bootstrap-spec`) therefore adds a
minimal `ci.yml` with one `check` job (spec files present, no signing material committed). Phase 0 replaces its body with the
Gradle build while keeping the job name, so the check that gates merges never disappears.

## D-4 · Module layout follows UltimatePhone, plus `:core:designsystem`

The modules are the ones in `02-diseno.md §1`. A `:core:designsystem` module holds the Compose theme and shared components,
as in UltimatePhone, so feature modules can share them without depending on `:app`. `:core:scheduling` is a plain Kotlin/JVM
module (no Android plugin) with Kover enforcing ≥ 90 % line coverage as part of `check`. DI is Hilt, as in both reference apps.

## D-5 · Version code formula

As in UltimateGallery/UltimatePhone: `(MAJOR*10000 + MINOR*100 + PATCH) * 100 + suffix`, where the suffix is `N` for `-rcN`,
`1` for `-spike` and `99` for a final version. `0.0.1-spike` is therefore `101`, `0.1.0` is `10099`, `1.0.0-rc1` is `1000001`.

## D-6 · One universal APK per release, no per-ABI splits

`03-ci-release.md` asks for an APK per ABI plus a universal one. Until now the app has no native code, so every per-ABI APK
would be identical to the universal one. Releases ship one universal `UltimateAlarm-X.Y.Z.apk` plus `SHA256SUMS.txt`, as
UltimateGallery and UltimatePhone do. CameraX (Phase 8, QR challenge) brings a small JNI library: ABI splits are added then.

## D-7 · F-Droid changelogs are committed in the phase's last PR, not written by `release.yml`

`release.yml` cannot push to `master` (no direct pushes). The `fastlane/.../changelogs/<versionCode>.txt` files are written
in the same PR that updates `CHANGELOG.md`; CI and `release.yml` run `scripts/release-notes.sh`, which fails if they are
missing. Same model as UltimateGallery.

## D-8 · Release labels

A version with a suffix (`-rcN`, `-spike`) is a GitHub pre-release; a final version (`0.1.0`…) is a normal release.
Without the four `RELEASE_*` secrets the build is debug-signed, the APK is named `…-UNSIGNED-DEBUG.apk`, the title says
`(UNSIGNED-DEBUG)` and the release is always a pre-release. Phase 0's acceptance tag is `v0.0.0-rc1`.

## D-9 · Instrumented tests are nightly and manual

`instrumented.yml` runs on AOSP images without Google apps (API 31 and 36), nightly and on demand, not on every PR (an
emulator boot takes several minutes). The screen-off firing test arrives with the spike (Phase 1).

## D-10 · Releases must be release-signed when the key exists, and can be rebuilt

`release.yml` verifies the APK certificate with `apksigner`: when the `RELEASE_*` secrets are set and the APK is still
signed with the debug key, the release fails instead of publishing. It can also be run by hand (`workflow_dispatch`, input
`tag`) to rebuild an already published tag and replace its assets, title and notes; this re-signed `v0.0.0-rc1` and
`v0.0.1-spike`, first published as UNSIGNED-DEBUG because the workflow was reading the wrong secret names.

## D-11 · One-shot alarms ignore exceptions, vacations, skips and holidays

§3 defines a one-shot occurrence as "its date and time if in the future". The engine applies none of the day rules to
one-shot alarms: the spec gives them no "skip tomorrow" (README assumption 3, "ahí se usa el interruptor") and their
"respect holidays" defaults to off. A one-shot with `oneShotDate = null` ("the next time it is HH:MM") is resolved to the
next occurrence after now; the UI stores the concrete date when the alarm is created. When both a decision for every alarm
and a decision for one alarm exist for a holiday, the per-alarm one wins.

## D-12 · The whole alarm database lives in device-protected storage

§4 asks for "a minimal copy" of what is needed to ring in device-protected (Direct Boot) storage. Keeping two copies in
sync is a source of bugs in the one path that must never fail, so the Room database (`alarms.db`: alarms, exceptions,
skips, vacations, holiday decisions) lives entirely in device-protected storage, like the event log. Its contents are not
sensitive; secrets (Nextcloud credentials, Phase 10) stay in credential-protected storage encrypted with the Android Keystore.
