# 01 — Requisitos

## RF-A · Alarmas

- **RF-A1** Crear, editar, duplicar, borrar y activar/desactivar alarmas. Campos: hora, etiqueta, programación, sonido, volumen máximo, rampa de volumen, vibración, posponer, respaldo, reto, "respetar festivos".
- **RF-A2** Lista ordenada por próxima ocurrencia; cada fila muestra "Suena en Xh Ym" o el motivo por el que no sonará (festivo, vacaciones, saltada, excepción).
- **RF-A3** Al activar o editar, toast/snackbar con el tiempo hasta la próxima ocurrencia.
- **RF-A4** Undo al borrar.

## RF-P · Programación

- **RF-P1 Días de la semana**: cualquier combinación de L–D, con atajos L–V, fines de semana, todos.
- **RF-P2 Patrones de turno**: ciclo de N días con máscara on/off y fecha ancla (ej. 4 on / 4 off; semanas alternas = ciclo 14).
- **RF-P3 Calendario de excepciones**: vista mensual por alarma; tocar un día alterna entre *según patrón* → *forzar sonar* → *forzar no sonar*.
- **RF-P4 Vacaciones**: rangos de fechas que pausan todas las alarmas o una selección. Lista de rangos, editables y borrables; los pasados se archivan solos.
- **RF-P5 Un solo uso**: fecha concreta (o "la próxima vez que sea HH:MM"). Se **borra al descartarla**. Si se pospone sigue viva hasta el descarte. Si se pierde (móvil apagado), se marca como perdida y se notifica, sin borrarla.
- **RF-P6 Saltar mañana**: botón por alarma en la lista que salta solo la próxima ocurrencia; se puede deshacer hasta que pase esa ocurrencia. No aplica a alarmas de un solo uso.
- **RF-P7** Precedencia definida en `02-diseno.md §3`, visible en la UI (el motivo se muestra).

## RF-F · Festivos

- **RF-F1** Setup inicial: país (por defecto ES) y subdivisión (comunidad autónoma, código ISO 3166-2). Cambiable en Ajustes.
- **RF-F2** Fuente automática: API Nager.Date v3, nacionales + los de la subdivisión elegida, solo tipo `Public`. Año en curso y siguiente.
- **RF-F3** Caché local; refresco mensual con WorkManager (solo con red) y forzable desde Ajustes. Snapshot embebido en el APK para el primer arranque.
- **RF-F4** Festivos manuales: añadir (fecha, nombre, recurrente anual sí/no), editar, borrar. Pensado para festivos locales, puentes o días propios.
- **RF-F5** Anular un festivo automático ("este año no es festivo para mí").
- **RF-F6** Vista de lista anual de festivos con origen (API / manual / anulado).
- **RF-F7 Víspera**: a la hora configurada (20:00 por defecto) del día anterior, si mañana es festivo y al menos una alarma con "respetar festivos" sonaría, notificación: *"Mañana es festivo (Nombre). Tienes N alarmas."* con acciones **Saltar todas** / **Mantener** / abrir para elegir por alarma. **Sin respuesta, suenan.**
- **RF-F8** Si la víspera la decisión se toma, la fila de la alarma lo refleja y se puede cambiar hasta la hora de la alarma.

## RF-R · Al sonar

- **RF-R1** Pantalla completa sobre la pantalla de bloqueo, encendiendo la pantalla. Hora grande, etiqueta, botones Posponer / Descartar (deslizar).
- **RF-R2** Volumen progresivo de silencio al máximo configurado en X segundos (30 s por defecto, configurable 0–300 s). Vibración con patrón configurable o desactivada.
- **RF-R3** Posponer: duración (1–30 min) y máximo de posposiciones (0–10 o ilimitado) por alarma. Notificación "Pospuesta hasta HH:MM" con acción Descartar.
- **RF-R4 Respaldo**: si no hay interacción en X minutos (5 por defecto) o se agotan las posposiciones, suena el tono de respaldo a volumen máximo, sin rampa, con vibración continua.
- **RF-R5 Retos** para descartar (por alarma): ninguno / matemáticas (dificultad 1–3, nº de operaciones) / escanear código QR o de barras registrado / acercar etiqueta NFC registrada. Si el hardware falta (sin NFC o cámara), el reto se degrada a matemáticas y se avisa al configurarlo.
- **RF-R6** Notificación previa opcional "Alarma en 1 h" con acción **Saltar esta**.
- **RF-R7** Botones de volumen: configurables (posponer / descartar / nada).
- **RF-R8** Silenciar automáticamente tras X minutos (por defecto nunca si hay respaldo).

## RF-S · Sonidos

- **RF-S1** Pack incluido: ≥12 tonos suaves y agradables, categorías *Despertar suave*, *Despertar enérgico*, *Respaldo*, *Notificación* (víspera, temporizador). Todos con licencia que permita redistribución en F-Droid.
- **RF-S2** Tonos del sistema (RingtoneManager, tipo alarma).
- **RF-S3** Ficheros propios (SAF, persistiendo permiso). Si el fichero desaparece, fallback al tono por defecto.
- **RF-S4** Previsualización con la rampa aplicada.
- **RF-S5** Tono de respaldo independiente, global con override por alarma.

## RF-E · Extras

- **RF-E1 Recordatorio de acostarse**: X h antes de la primera alarma del día siguiente (8 h por defecto), notificación suave. Desactivable; no sale si mañana no suena nada.
- **RF-E2 Temporizador**: varios simultáneos, presets, notificación en curso con tiempo restante, sonido de la categoría Notificación, pantalla completa al terminar.
- **RF-E3 Cronómetro**: vueltas, notificación en curso, sobrevive a cerrar la app.

## RF-B · Copia de seguridad

- **RF-B1** Exportar/importar a fichero (SAF): alarmas, programaciones, excepciones, vacaciones, festivos manuales y anulaciones, ajustes. Opcionalmente incluir los audios propios (zip).
- **RF-B2** Credenciales de Nextcloud en el fichero, cifradas con contraseña elegida al exportar (como UltimatePhone).
- **RF-B3** Backup automático en Nextcloud por WebDAV: tras cada cambio (debounce 1 min) y diario; carpeta configurable; conserva las últimas N copias (10 por defecto).
- **RF-B4** Restaurar desde lista de copias en Nextcloud, con vista previa (nº alarmas, fecha).
- **RF-B5** Importar = reemplazar o fusionar, a elegir. Tras importar se reprograman todas las alarmas.

## RF-O · Onboarding y fiabilidad

- **RF-O1** Asistente de permisos: notificaciones, alarmas exactas, pantalla completa, optimización de batería, y pasos específicos detectados para ColorOS, OriginOS/Funtouch, HyperOS/MIUI, MagicOS, One UI (autoarranque, bloqueo en recientes), con deep-link cuando exista y capturas/instrucciones si no.
- **RF-O2 Autotest**: programa una alarma real en 1 min y pide apagar la pantalla; informa del resultado.
- **RF-O3** Banner persistente en la lista si falta algún permiso crítico.

## RNF

- **RNF-1** Fiabilidad: la alarma debe sonar con pantalla apagada tras ≥8 h de Doze, tras reinicio (incluso antes del primer desbloqueo) y tras cambio de hora/zona.
- **RNF-2** Sin GMS. Probado en emulador AOSP sin GApps.
- **RNF-3** Dispositivos de validación: Pixel 8, OPPO Find X8 Pro (ColorOS), OPPO Find X9 Ultra (global).
- **RNF-4** Accesibilidad: TalkBack en todas las pantallas, incluida la de alarma; objetivos táctiles ≥48 dp.
- **RNF-5** Arranque en frío <1 s en Pixel 8; sin trabajo en el hilo principal en el disparo.
- **RNF-6** i18n es/en; formato de hora según el sistema (12/24 h).
- **RNF-7** Privacidad: sin analítica ni telemetría. Logs solo locales.
