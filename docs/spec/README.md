# UltimateAlarm — paquete SDD

`com.qtekfun.ultimatealarm` · familia Ultimate · Android 12+ (minSdk 31), con y sin GMS.

Despertador que sabe qué días son festivo, te avisa la víspera y te deja decidir; con programación flexible (días de la semana, turnos, excepciones por día, vacaciones), alarmas de un solo uso que se borran solas, "saltar mañana" por alarma, alarma de respaldo, retos para apagar, temporizador, cronómetro y copia de seguridad local + Nextcloud.

## Contenido

| Fichero | Para qué |
|---|---|
| `KEYS.md` | **Paso 0, lo haces tú**: generar el JKS en `~/keys` y subir los secrets a GitHub |
| `KICKOFF.md` | Prompt para arrancar Claude Code |
| `CLAUDE.md` | Reglas permanentes del repo (va a la raíz) |
| `01-requisitos.md` | Requisitos funcionales y no funcionales, numerados |
| `02-diseno.md` | Arquitectura, modelo de datos, motor de programación, festivos, fiabilidad, sonidos, backup |
| `03-ci-release.md` | CI, firma, releases por fase y F-Droid |
| `04-fases.md` | Plan de implementación en 13 fases con criterios de aceptación |

## Decisiones de la entrevista (resumen)

- Festivos: API Nager.Date (nacionales + autonómicos) con caché local y snapshot embebido, más festivos manuales en la app. Locales de municipio: manuales.
- Víspera de festivo: notificación preguntando; **si no respondes, la alarma suena**.
- Programación: días de la semana, patrones de turno, calendario de excepciones por día y rangos de vacaciones.
- Un solo uso: se borra al **descartarla** (posponer la mantiene viva).
- "Saltar mañana": botón por alarma en la lista.
- Al sonar: volumen progresivo + vibración, posponer configurable, alarma de respaldo, retos (matemáticas, QR, NFC).
- Sonidos: pack propio incluido (original/CC0), tonos del sistema y ficheros propios. Tono específico para la alarma de respaldo.
- Backup: export/import a fichero + copia automática a Nextcloud por WebDAV.
- Fiabilidad: todo local (`setAlarmClock`), asistente de permisos por fabricante y autotest.
- Extras MVP: recordatorio de acostarse, temporizador y cronómetro.
- UI Material 3 Expressive con color dinámico; español + inglés.
- CI/releases como UltimateGallery/UltimatePhone.

## Supuestos que he tomado (corrígeme si alguno no encaja)

1. Cada alarma tiene un interruptor **"Respetar festivos"** (activo por defecto en las recurrentes, desactivado en las de un solo uso).
2. La notificación de víspera solo sale si mañana es festivo **y** hay al menos una alarma que respeta festivos y sonaría. Hora por defecto: 20:00, configurable.
3. "Saltar mañana" salta la **próxima ocurrencia** (si la próxima es pasado mañana, salta esa). En alarmas de un solo uso no aparece: ahí se usa el interruptor.
4. La alarma de respaldo salta si en X minutos (5 por defecto) no has interactuado, o al agotar el máximo de posposiciones.
5. Los retos se exigen solo para descartar, nunca para posponer.
6. Snapshot de festivos embebido en el APK (generado por CI) para que funcione el primer día sin red.
7. No hay release-please: versionado por fase, como UltimateGallery.
