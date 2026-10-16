# 03 — CI, firma y releases

Modelo idéntico a UltimateGallery / UltimatePhone: GitHub Actions + GitHub Releases + metadatos F-Droid desde el día 1. Sin release-please.

## Workflows

- **`ci.yml`** (push y PR): ktlint/detekt, lint de Android, tests unitarios (con cobertura del módulo `:core:scheduling` ≥ 90 %), build debug, comprobación de `assets/sounds/LICENSES.md`, validación de strings es/en (no faltan claves).
- **`instrumented.yml`** (manual y nocturno): tests instrumentados en emulador AOSP **sin GApps** (API 31 y la última), incluido un test de disparo de alarma con pantalla apagada.
- **`release.yml`** (push de tag `v*`): build release firmado, APK por ABI + universal, checksums SHA-256, release en GitHub con notas del CHANGELOG, actualización de `fastlane/metadata/android/{es-ES,en-US}/changelogs/{versionCode}.txt`.
- **`holidays-snapshot.yml`** (mensual + antes de cada release): regenera `assets/holidays/` y abre PR si cambia.

## Secrets (los crea Z con `KEYS.md`)

| Secret | Contenido |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `ultimatealarm-release.jks` en base64 |
| `RELEASE_KEYSTORE_PASSWORD` | contraseña del store |
| `RELEASE_KEY_ALIAS` | `ultimatealarm` |
| `RELEASE_KEY_PASSWORD` | contraseña de la clave |

Si no existen, `release.yml` firma con debug y marca la release como **pre-release "UNSIGNED-DEBUG"**, sin fallar.

## Versionado por fase

| Tras fase | Versión |
|---|---|
| 1 (spike) | 0.0.1-spike (pre-release) |
| 3 | 0.1.0 |
| 4 | 0.2.0 |
| 6 (festivos, adelantada, D-14) | 0.3.0 |
| 5 (sonidos) | 0.4.0 |
| 7 | 0.5.0 |
| 8 | 0.6.0 |
| 9 | 0.7.0 |
| 10 | 0.8.0 |
| 11 | 0.9.0 |
| 12 | 1.0.0-rc1 |

`versionCode` monotónico derivado de la versión. CHANGELOG en formato Keep a Changelog, en inglés; changelogs de F-Droid en es e en.

## F-Droid

Metadatos fastlane completos desde la Fase 0 (título, descripciones cortas/largas es/en). Builds reproducibles: sin timestamps, dependencias fijadas, `dependenciesInfo { includeInApk = false }`. Alta en F-Droid tras la 1.0.
