# CLAUDE.md — UltimateAlarm

## Qué es
Despertador Android de la familia Ultimate (`com.qtekfun.ultimatealarm`). Specs en `docs/spec/` (01-requisitos, 02-diseno, 03-ci-release, 04-fases). Ante una duda, la spec manda; si la spec es ambigua, pregunta antes de inventar.

## Reglas no negociables
1. **Una alarma que no suena es el peor bug posible.** Ningún cambio puede poner en riesgo el disparo. Red, festivos, Nextcloud o sonidos que fallan nunca impiden que suene: si algo falla, suena con el tono por defecto.
2. **Todo local.** Ni servidor propio, ni push, ni FCM. La red solo se usa para descargar festivos y para el backup en Nextcloud.
3. **Cero dependencias de Google**: nada de Play Services, Firebase ni ML Kit. QR con ZXing + CameraX. Debe funcionar en AOSP sin GApps.
4. Código, identificadores, commits y comentarios en **inglés**. App en **español e inglés** (todas las strings en recursos, nada hardcodeado).
5. El motor de programación (`scheduling` module) es Kotlin puro sin Android y tiene **tests unitarios exhaustivos** (festivos, cambio de hora, turnos, excepciones, saltos, un solo uso). Sin tests no se mergea.
6. Nunca generes, leas ni muevas claves de firma. Solo usa los nombres de secrets definidos en `03-ci-release.md`.
7. Capacidades detectadas en runtime (`CapabilityProbe`), nunca suposiciones por fabricante.
8. Máximo 2 agentes en paralelo.

## Stack
Kotlin, Jetpack Compose, Material 3 Expressive con color dinámico, Room, DataStore, WorkManager. Misma DI, estructura de módulos y convenciones de Gradle que UltimateGallery (clónalo como referencia, no copies código sin entenderlo). minSdk 31, targetSdk = la última estable.

## Flujo
Rama por defecto: `master`. Un PR por unidad de trabajo, Conventional Commits en inglés, squash-merge solo con CI en verde. Desviaciones de la spec en `docs/spec/DECISIONS.md`. Repos de referencia en `../reference/` (UltimateGallery, UltimatePhone).

Fase a fase según `04-fases.md`. Al cerrar cada fase: tests verdes, CI verde, CHANGELOG actualizado, tag y release según `03-ci-release.md`. La única pausa obligatoria es tras la Fase 1 (spike): espera a que Z valide en sus móviles.
