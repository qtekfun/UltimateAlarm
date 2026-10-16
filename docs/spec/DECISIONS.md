# Decisions and deviations from the spec

Changes to what `01`–`04` propose, and why. Newest last. The spec stays the source of truth for everything not listed here.

## D-1 · Signing secrets use the family names (`UG_*`)

The spec named the secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.
UltimateGallery and UltimatePhone both sign with `UG_KEYSTORE_BASE64`, `UG_KEYSTORE_PASSWORD`, `UG_KEY_ALIAS` and `UG_KEY_PASSWORD`,
so `KEYS.md` asks to reuse those names. `03-ci-release.md` and `KEYS.md` are updated accordingly. The keystore file and the alias
(`ultimatealarm-release.jks`, `ultimatealarm`) do not change.

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
