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

## D-6 · Universal APK plus one per ABI (corrected)

An earlier version of this decision shipped only a universal APK claiming the app had no native code. That was wrong:
Compose already brought a small native library, and CameraX (Phase 8) adds two more. Releases now follow the spec: one
APK per ABI (arm64-v8a, armeabi-v7a, x86, x86_64) plus the universal one, all in `SHA256SUMS.txt`. ABI splits are only
enabled for release builds, so debug builds and CI keep a single APK. F-Droid's recipe keeps pointing at the universal APK.

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

## D-13 · Ringing rules: snooze exhaustion, backup, auto-silence, ring state

- "If the snoozes run out, the backup tone sounds" (RF-R4) is read as: the ring that follows the last allowed snooze starts
  directly with the backup tone (full volume, no ramp, continuous vibration). Only for alarms with a backup configured.
- The backup timer counts from the start of each ring; snoozing or dismissing stops it. A safety exact alarm at the same
  instant re-starts the ring in backup mode if the service was killed.
- RF-R8: with a backup, a ring never silences itself by default; without one, after 30 min. Either way a ring stops after
  60 min without interaction (a phone left at home must not ring for days); the global setting can shorten it.
- `RingState` (§2) is kept in device-protected preferences, not Room: it is one row, written synchronously at the start of
  every ring and read before the first unlock and after a process death.

## D-14 · Holidays first, in Settings, from public calendars (product owner, 2026-10-16)

The product owner moved holidays ahead of sounds and asked for **no Holidays screen of its own**:
- Settings → Holidays holds everything: where the user lives (country + region, Nager.Date), an optional **public ICS
  calendar** address for extra holidays (a town's local holidays, a company calendar), manual extra holidays and the
  holiday list where an automatic one can be cancelled (RF-F4–F6). The bottom navigation drops the Holidays tab
  (Alarms · Timer · Stopwatch).
- The priority feature is the holiday eve notice (RF-F7): "Tomorrow is a holiday (Name). You have N alarms." with Skip
  them / Keep them; without an answer, the alarms ring.
- Release order: holidays ship as **0.3.0** and sounds as **0.4.0** (the table in `03-ci-release.md` is updated).
- The holiday tables live in the same device-protected database (schema v2, auto-migration).

## D-15 · The sound pack is 100 % original, no CC0 files

§7 allowed original synthesised tones and curated CC0 files. The pack (13 tones, RF-S1 asks ≥ 12) is entirely generated
by `tools/sounds/generate.py` (numpy + ffmpeg/libopus, deterministic, bit-exact output), so every file is the project's
own work under GPL-3.0-or-later and nothing depends on a third-party licence or a download. Notification tones are short
(3.6–4 s) by nature; wake-up and backup tones are 20–32 s loops. CI checks `LICENSES.md`; it does not regenerate the
audio (libopus versions differ between machines), the committed files are the reference.
The fallback chain when ringing is: chosen sound → pack default (`dawn_marimba`, or `backup_pulse` for the backup) →
system alarm tone → generated beep. Bundled assets are readable before the first unlock.

## D-16 · Local (municipal) holidays from Calendarios Nacionales (product owner, 2026-10-16)

The product owner asked for local holidays too. There is no official national API for municipal holidays; each region
publishes them in its bulletin, and the Social Security keeps a national list. The public JSON API of
[Calendarios Nacionales](https://calendariosnacionales.com/es/api/) compiles them per municipality from those official
sources (BOE, Social Security, regional open data) for all 8,000+ Spanish municipalities, free and without a key.
- Settings → Holidays (Spain): region → province → municipality (searchable). The municipality's calendar (local,
  provincial and the full official calendar, which also carries regional moves Nager.Date may miss) is stored as source
  `LOCAL` and merged with the other sources.
- Terms: visible attribution with a link (shown under the picker), cached responses, no abusive syncing: the app refreshes
  monthly like Nager.Date, current and next year. A year not published yet (empty) is not an error.
- Network only for holidays (rule 2); failures keep the cache; nothing here can delay an alarm.

## D-17 · Official open-data calendars of towns take precedence (product owner, 2026-10-16) — withdrawn by D-18

Where a town publishes its labour calendar as open data, the app reads it on every holiday refresh and its days, with the
official names, take precedence over the compiled municipal calendar (D-16); days only in the compiled calendar are kept.
First source: the **Ayuntamiento de Madrid** dataset `300082-0-calendario_laboral` on datos.madrid.es (CC BY 4.0,
attribution shown in Settings → Holidays). Each year is a new CKAN resource with a new address, so the app looks the
package up through the CKAN API (`package_show`) instead of storing a URL. For 2026 its dates match the compiled calendar
exactly; it adds the official names (San Isidro Labrador, Nuestra Señora de la Almudena). More towns are added to
`OfficialCalendars.sources`. Any failure falls back silently to the compiled calendar.

## D-18 · Madrid's official calendar removed; the compiled municipal calendar is the only local source (product owner, 2026-10-16)

The product owner reported that the Madrid download did not work on the phone and asked for an alternative or its removal.
It was removed in 1.0.0-rc3: the compiled calendar of D-16 already gives Madrid the same dates (checked for 2026), the
official feed only added names, and it was the most fragile source (a new address every year, three overlapping ICS
files exported from a personal calendar, a mislabelled day). One fewer network endpoint (rule 2). D-17 no longer applies;
towns' open data can come back only with a source that is stable from year to year.

## D-19 · "Amanecer" look, light/dark and a choice of colour (product owner, 2026-10-16)

Of two UI concepts the product owner chose "Amanecer": warm ground, Bricolage Grotesque for times and headings, DM Sans
for text (both SIL OFL 1.1, bundled in `core/designsystem`, no font download), large radii, the next alarm as a hero card,
a sun-shaped ringing screen. Settings → Appearance: System / Light / Dark and the accent (orange by default; raspberry,
plum, indigo, teal, mustard) or "from your wallpaper" (Material You, the old default). The palette is a Material colour
scheme built in `AmanecerPalette`, so every screen follows it; `PaletteTest` keeps text at WCAG 4.5:1 for every accent in
both modes. The choice lives in device-protected preferences so the ringing screen can use it before the first unlock.
Rolled out screen by screen (theme and settings, alarm list, editor, ringing). Secondary and tertiary colours are shades
of the chosen accent (not a fixed indigo/teal), and the ringing screen is tinted by it; the sections are swipeable pages
behind a floating pill navigation (1.0.0-rc6). Timer and stopwatch follow in 1.0.0-rc7: a keypad that fills hh mm ss from the right instead of three
text fields, timers as cards with a ring, the stopwatch inside a ring that turns once a minute.


## D-20 · Louder tones and a ramp that is audible from the start (product owner, 2026-10-17)

The product owner found the tones too soft. The bundled pack moves from the −16 / −12 LUFS of 02-diseno §7 to **−10 LUFS**
(wake-up, notification) and **−8 LUFS** (backup), reached with a peak limiter instead of `loudnorm`, whose dynamic mode
fell short on percussive tones; the two plucked tones stop at +20 dB of gain (about −13 LUFS) so they do not turn into a
buzz. The volume ramp (RF-R2) starts at a tenth of the maximum (−20 dB) and rises linearly, instead of quadratically from
2 %: the event log showed a snooze 12 s into a 30 s ramp, when the old curve was still at 18 %. Each ring now logs the
alarm stream volume it set (`VOLUME`).
