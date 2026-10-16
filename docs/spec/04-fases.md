# 04 — Plan por fases

Cada fase cierra con tests verdes, CI verde, CHANGELOG y release según `03-ci-release.md`.

## Fase 0 — Esqueleto y CI
Repo, módulos vacíos, tema M3 Expressive, strings es/en, workflows de `03-ci-release.md`, metadatos fastlane. Comprobar que los secrets existen (solo nombres, `gh secret list`).
**Acepta:** build release firmado sale de un tag de prueba.

## Fase 1 — Spike de fiabilidad ⏸ (pausa obligatoria)
APK mínimo con 1 alarma de prueba: `setAlarmClock`, receiver, FGS de disparo, full screen sobre bloqueo, sonido `USAGE_ALARM`, Direct Boot, `EventLog` exportable, autotest. Pantalla de diagnóstico con `CapabilityProbe`.
Escenarios a validar por Z en **Pixel 8, OPPO Find X8 Pro y OPPO Find X9 Ultra**: pantalla apagada 8 h; tras reinicio sin desbloquear; app quitada de recientes; ahorro de batería activo; cambio manual de zona horaria; modo No molestar.
**Entrega:** informe `docs/spike-report.md` con la matriz de resultados y el tipo de FGS elegido. **Esperar validación.**

## Fase 2 — Motor de programación
`:core:scheduling` completo (§3 del diseño) con tests: semanal, ciclos, excepciones, vacaciones, saltos, festivos con y sin decisión, un solo uso, cambios de hora (ES: último domingo de marzo/octubre), años bisiestos.
**Acepta:** cobertura ≥ 90 %, tabla de casos documentada.

## Fase 3 — Lista y editor → 0.1.0
Room + repositorios, lista con estado y motivo, editor completo (sin retos), un solo uso con borrado al descartar, "Saltar mañana", `Rescheduler` con todos los broadcasts.

## Fase 4 — Disparo completo → 0.2.0
Pantalla de alarma definitiva, rampa, vibración, posponer, botones de volumen, respaldo (con alarma de seguridad), recuperación tras muerte del proceso, pre-aviso "Alarma en 1 h · Saltar esta".

## Fase 5 — Sonidos → 0.3.0
Scripts de generación en `tools/sounds/`, curación CC0, `LICENSES.md`, normalización, selector con previsualización (pack / sistema / fichero), tono de respaldo global + por alarma, fallbacks.

## Fase 6 — Festivos → 0.4.0
Setup país + comunidad, proveedor Nager.Date, snapshot de CI, caché y refresco, festivos manuales y anulaciones, pantalla Festivos, notificación de víspera con decisiones, integración con el motor.

## Fase 7 — Programación avanzada → 0.5.0
UI de patrones de turno (presets 4×4, 5×2, semanas alternas, personalizado), calendario de excepciones por alarma, gestión de vacaciones.

## Fase 8 — Retos → 0.6.0
Matemáticas, QR/código de barras (CameraX + ZXing), NFC (registro de etiquetas con alias). Degradación si falta hardware.

## Fase 9 — Reloj → 0.7.0
Temporizador múltiple, cronómetro con vueltas, recordatorio de acostarse.

## Fase 10 — Backup → 0.8.0
Export/import a fichero (con audios opcionales, credenciales cifradas), Nextcloud WebDAV automático con rotación, restaurar desde lista, reemplazar/fusionar.

## Fase 11 — Onboarding y pulido → 0.9.0
Asistente por fabricante, banner de permisos, accesibilidad TalkBack, revisión de textos es/en, rendimiento de arranque.

## Fase 12 — Tienda → 1.0.0-rc1
Icono adaptativo y monocromo, capturas es/en, descripciones F-Droid, revisión de licencias, auditoría final de dependencias (cero Google).
