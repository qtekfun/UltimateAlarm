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
