# Dependency audit (Phase 12)

Release runtime classpath of `:app` (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`), audited
2026-10-16. **No Google Play services, Firebase or ML Kit** (CLAUDE.md rule 3); CI enforces it with
`scripts/check-no-google.sh` on every build and release, and Licensee only lets Apache-2.0, MIT, BSD-3-Clause and
Bouncy Castle's licence into the APK.

| Group | What | Licence | Why |
|---|---|---|---|
| `androidx.*` (activity, compose, core, lifecycle, navigation, room3, sqlite, work, camera, media3…) | AndroidX / Jetpack, AOSP | Apache-2.0 | UI, database, background work, CameraX for the QR challenge |
| `org.jetbrains.kotlin*`, `org.jetbrains.kotlinx` (coroutines, serialization) | Kotlin | Apache-2.0 | Language runtime |
| `com.google.dagger` | Dagger / Hilt | Apache-2.0 | Dependency injection, same as UltimateGallery/UltimatePhone |
| `com.google.zxing:core` | ZXing | Apache-2.0 | QR / barcode decoding, fully on device |
| `com.google.guava`, `com.google.j2objc`, `com.google.code.findbugs`, `com.google.auto.value`, `org.checkerframework`, `org.jspecify` | Libraries and annotations pulled by CameraX / Dagger | Apache-2.0 / MIT | Transitive; not Play services |
| `com.squareup.okhttp3`, `com.squareup.okio` | OkHttp | Apache-2.0 | WebDAV to the user's Nextcloud |
| `org.bouncycastle:bcprov-jdk18on` | Bouncy Castle (Argon2id only) | Bouncy Castle (MIT-style) | Backup encryption key derivation |
| `javax.inject`, `jakarta.inject` | Annotations | Apache-2.0 / EPL-2.0 | DI annotations |

Network endpoints used by the app (CLAUDE.md rule 2): Nager.Date (holidays), Calendarios Nacionales (Spanish municipal
holidays), the public ICS address the user types, and the user's Nextcloud.
Nothing else; no analytics or crash reporting.
