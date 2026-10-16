# 02 — Diseño

## 1. Módulos

```
:app                 UI Compose, navegación, DI
:core:scheduling     Kotlin puro: modelo + motor (sin Android) — tests exhaustivos
:core:data           Room, DataStore, repositorios
:feature:alarms      lista, editor, calendario de excepciones, vacaciones
:feature:ringing     servicio de disparo, pantalla de alarma, retos, respaldo
:feature:holidays    proveedor Nager.Date, snapshot, manuales, notificación de víspera
:feature:sounds      catálogo, selector, reproductor con rampa
:feature:clock       temporizador, cronómetro, recordatorio de acostarse
:feature:backup      export/import, WebDAV Nextcloud
:feature:onboarding  asistente, CapabilityProbe, autotest
```

Adapta nombres a la estructura de UltimateGallery si difiere.

## 2. Modelo de datos (Room)

```kotlin
Alarm(id, time: LocalTime, label, enabled, kind: RECURRING|ONE_SHOT,
      oneShotDate: LocalDate?, respectHolidays: Boolean,
      soundRef, backupSoundRef?, maxVolume, rampSeconds, vibrationPattern?,
      snoozeMinutes, maxSnoozes, backupAfterMinutes?, challenge: ChallengeConfig?,
      preNotifyMinutes?)          // sin grupos en el MVP

Schedule (1:1 con Alarm, sealed):
  Weekly(days: Set<DayOfWeek>)
  Cycle(anchor: LocalDate, mask: List<Boolean>)   // mask.size = N días

DayOverride(alarmId, date, mode: FORCE_ON|FORCE_OFF)
SkipNext(alarmId, occurrenceDate)                  // consumido al pasar
Vacation(id, start, end, appliesTo: ALL|Set<alarmId>)
Holiday(date, name, source: API|MANUAL, recurringYearly, region)
HolidayOverride(date, isHoliday: Boolean)          // anula o añade
HolidayDecision(date, alarmId?, skip: Boolean)     // respuesta de la víspera
RingState(alarmId, firedAt, snoozeCount, backupFired)  // para recuperarse tras muerte del proceso
EventLog(ts, alarmId?, type, detail)               // diagnóstico local, rotado
```

## 3. Motor de programación (`:core:scheduling`)

Función pura:

```kotlin
fun nextOccurrence(alarm, rules: RuleSet, now: ZonedDateTime, zone): Occurrence?
data class Occurrence(at: ZonedDateTime, date: LocalDate)
fun explain(alarm, date, rules): DayVerdict   // RINGS | OFF(reason)
```

Para cada fecha candidata `d` (desde hoy, máx. 400 días) se decide en este orden; **la primera regla que aplica gana**:

1. `DayOverride(FORCE_ON)` → suena. `DayOverride(FORCE_OFF)` → no (`reason=EXCEPTION`).
2. Vacaciones que cubren `d` y aplican a la alarma → no (`VACATION`).
3. `SkipNext` con `occurrenceDate == d` → no (`SKIPPED`).
4. Si `respectHolidays` y `d` es festivo efectivo y existe `HolidayDecision(skip=true)` para `d` → no (`HOLIDAY`). **Sin decisión, suena.**
5. Patrón base (`Weekly` / `Cycle`) → suena o no (`NOT_SCHEDULED`).

Festivo efectivo = (API ∪ manuales) con `HolidayOverride` aplicado.

Hora local en `d` con la zona actual. Casos de cambio de hora: si la hora no existe (salto de primavera) → primer instante válido posterior; si se repite (otoño) → la primera. Todo cubierto por tests.

Un solo uso: `nextOccurrence` = esa fecha/hora si es futura; si no, `null`.

## 4. Programación en el sistema

- `AlarmManager.setAlarmClock(info, operation)` **siempre**, con `showIntent` a la app. Una única entrada por alarma: su próxima ocurrencia.
- Permiso `USE_EXACT_ALARM` (app de despertador), con `SCHEDULE_EXACT_ALARM` como respaldo comprobado con `canScheduleExactAlarms()`.
- `Rescheduler.rescheduleAll()` se llama en: `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, cambios en BD, cambio de festivos, importación de backup.
- Datos necesarios para disparar en almacenamiento **device-protected** (Direct Boot): copia mínima (alarma, sonido por defecto) para sonar antes del primer desbloqueo.
- Watchdog diario (WorkManager, sin restricciones) que verifica que lo programado coincide con lo esperado y lo registra en `EventLog`.
- Tareas auxiliares (víspera, pre-aviso, recordatorio de acostarse) también con `setExactAndAllowWhileIdle`; si no se permiten exactas, `setWindow` de 15 min. Nunca bloquean la alarma.

## 5. Disparo

1. Receiver → arranca `RingingService` en primer plano (tipo de FGS a validar en el spike: `systemExempted` si la app tiene alarma exacta, si no `mediaPlayback`).
2. Notificación con `fullScreenIntent` → `RingingActivity` (`showWhenLocked`, `turnScreenOn`). Comprobar `canUseFullScreenIntent()`; si no, notificación heads-up de máxima prioridad + aviso en onboarding.
3. Audio con `AudioAttributes.USAGE_ALARM`, `MediaPlayer`/ExoPlayer en bucle, rampa por software. Si el sonido falla → tono por defecto del pack → tono de sistema → beep generado. **Siempre suena algo.**
4. Persistir `RingState`; si el proceso muere, al relanzarse retoma el sonido.
5. Respaldo: temporizador interno + alarma exacta de seguridad a `firedAt + backupAfter` por si el servicio muere.
6. Al descartar: un solo uso → borrar (y en un `Undo` de 10 s); recurrente → consumir `SkipNext`/decisiones pasadas y reprogramar.

## 6. Festivos

- `GET https://date.nager.at/api/v3/PublicHolidays/{year}/{country}`. Filtrar `types` contiene `Public` y (`global == true` o `counties` contiene la subdivisión, p. ej. `ES-MD`).
- El campo `counties` puede renombrarse a `subdivisionCodes` en el futuro: parsear ambos.
- Nager.Date cubre país + primera subdivisión, no municipios → los locales van por manuales (RF-F4). Indicarlo en el setup.
- Snapshot: script de CI `tools/holidays-snapshot` descarga el año actual y el siguiente de los países soportados en la UI (empezar por ES) a `assets/holidays/{country}-{year}.json`. La app usa caché > snapshot.
- Refresco: WorkManager mensual con red + al cambiar de región + en diciembre para el año siguiente.
- Error de red o JSON inválido: se mantiene la caché, se registra, sin notificar al usuario salvo que la caché sea del año anterior.

## 7. Sonidos

- Fuentes del pack:
  1. **Originales generados** en el repo (`tools/sounds/`): síntesis con scripts reproducibles (marimba/campanas suaves, pads, carillón) renderizados a OGG Opus. Licencia del propio proyecto.
  2. **CC0** curados (p. ej. Freesound filtrando por CC0). Nada con licencias que no permitan redistribución (evitar "Pixabay License" y similares por F-Droid).
- `assets/sounds/LICENSES.md` con origen, autor y licencia de cada fichero; el script de CI falla si un audio no tiene entrada.
- Normalización de loudness (−16 LUFS para despertar, −12 para respaldo), loops sin clic, duración 20–60 s.
- `SoundRef` sellado: `Bundled(id)`, `System(uri)`, `File(uri)`.

## 8. Fiabilidad por fabricante

- `CapabilityProbe`: exact alarm, full screen intent, ignore battery optimizations, NFC, cámara, notificaciones.
- `OemGuide`: detecta fabricante/ROM por `Build` + paquetes de sistema y ofrece intents conocidos (autoarranque, ahorro de batería). Si un intent no existe, instrucciones de texto. Nunca asume que existe.
- Autotest (RF-O2) registra en `EventLog` el retraso real entre hora programada y disparo.

## 9. Backup

- Formato: JSON versionado (`schemaVersion`), dentro de un zip si incluye audios.
- Credenciales Nextcloud: en el móvil, cifradas con Android Keystore; en el export, AES-GCM con clave derivada (Argon2id o PBKDF2) de la contraseña del usuario.
- WebDAV: misma librería/cliente que UltimatePhone. Ficheros `ultimatealarm-YYYYMMDD-HHMMSS.zip` en la carpeta elegida; rotación a N copias.
- Restaurar → validar esquema → reemplazar/fusionar en transacción → `rescheduleAll()`.

## 10. UI

- Material 3 Expressive, color dinámico, tema claro/oscuro. Navegación inferior: **Alarmas · Temporizador · Cronómetro · Festivos**. Ajustes desde la barra superior.
- Fila de alarma: hora grande, días/patrón resumido, chip de estado (festivo, saltada, vacaciones), botón "Saltar mañana", interruptor.
- Editor en hoja a pantalla completa con secciones plegables.
- Pantalla de alarma oscura, hora enorme, animaciones suaves; legible a 1 m.
