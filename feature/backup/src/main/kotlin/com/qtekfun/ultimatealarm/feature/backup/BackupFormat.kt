package com.qtekfun.ultimatealarm.feature.backup

import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.data.BackupContent
import com.qtekfun.ultimatealarm.core.data.ManualHoliday
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.DayOverride
import com.qtekfun.ultimatealarm.core.scheduling.HolidayDecision
import com.qtekfun.ultimatealarm.core.scheduling.HolidayOverride
import com.qtekfun.ultimatealarm.core.scheduling.OverrideMode
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import com.qtekfun.ultimatealarm.core.scheduling.SkipNext
import com.qtekfun.ultimatealarm.core.scheduling.Vacation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The backup file (02-diseno §9): a zip with `backup.json` (versioned) and, optionally, the user's own audio files under
 * `audio/`. Nextcloud credentials travel only encrypted with a password chosen at export (RF-B2).
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val createdAt: Long,
    val appVersion: String,
    val alarms: List<AlarmDto> = emptyList(),
    val overrides: List<OverrideDto> = emptyList(),
    val skips: List<SkipDto> = emptyList(),
    val vacations: List<VacationDto> = emptyList(),
    val decisions: List<DecisionDto> = emptyList(),
    val manualHolidays: List<ManualHolidayDto> = emptyList(),
    val holidayOverrides: List<HolidayOverrideDto> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    /** Own audio files: the alarm's original sound reference → its name under `audio/` in the zip. */
    val audioRefs: Map<String, String> = emptyMap(),
    /** [BackupCrypto] container (Base64) with the Nextcloud settings JSON, or `null`. */
    val encryptedCredentials: String? = null
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class AlarmDto(
    val id: Long,
    val time: String,
    val label: String = "",
    val enabled: Boolean = true,
    val kind: String = AlarmKind.RECURRING.name,
    val weekDays: List<Int>? = null,
    val cycleAnchor: String? = null,
    val cycleMask: String? = null,
    val oneShotDate: String? = null,
    val respectHolidays: Boolean = true,
    val sound: String? = null,
    val backupSound: String? = null,
    val maxVolume: Int = 100,
    val rampSeconds: Int = 30,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    val maxSnoozes: Int = 3,
    val backupAfterMinutes: Int? = 5,
    val challenge: String? = null,
    val preNotifyMinutes: Int? = null
)

@Serializable data class OverrideDto(val alarmId: Long, val date: String, val mode: String)

@Serializable data class SkipDto(val alarmId: Long, val date: String)

@Serializable data class VacationDto(val start: String, val end: String, val alarmIds: List<Long>? = null)

@Serializable data class DecisionDto(val date: String, val alarmId: Long? = null, val skip: Boolean)

@Serializable data class ManualHolidayDto(val date: String, val name: String, val yearly: Boolean)

@Serializable data class HolidayOverrideDto(val date: String, val isHoliday: Boolean, val name: String = "")

/** A backup ready to restore, and what the preview shows (RF-B4). */
data class ParsedBackup(val file: BackupFile, val content: BackupContent, val audio: Map<String, ByteArray>) {
    val alarmCount: Int get() = file.alarms.size
}

object BackupFormat {
    const val ENTRY_JSON = "backup.json"
    const val AUDIO_PREFIX = "audio/"
    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun toFile(
        content: BackupContent,
        createdAt: Long,
        appVersion: String,
        settings: Map<String, String>,
        encryptedCredentials: ByteArray?,
        audioRefs: Map<String, String> = emptyMap()
    ): BackupFile = BackupFile(
        createdAt = createdAt,
        appVersion = appVersion,
        alarms = content.alarms.map { it.toDto() },
        overrides = content.overrides.map { OverrideDto(it.alarmId, it.date.toString(), it.mode.name) },
        skips = content.skips.map { SkipDto(it.alarmId, it.occurrenceDate.toString()) },
        vacations = content.vacations.map { VacationDto(it.start.toString(), it.end.toString(), it.alarmIds?.sorted()) },
        decisions = content.decisions.map { DecisionDto(it.date.toString(), it.alarmId, it.skip) },
        manualHolidays = content.manualHolidays.map { ManualHolidayDto(it.date.toString(), it.name, it.yearly) },
        holidayOverrides = content.holidayOverrides.map { HolidayOverrideDto(it.date.toString(), it.isHoliday, it.name) },
        settings = settings,
        audioRefs = audioRefs,
        encryptedCredentials = encryptedCredentials?.let { Base64.getEncoder().encodeToString(it) }
    )

    /** Zip with `backup.json` and the given audio files (name → bytes). Entry times are fixed: same content, same bytes. */
    fun pack(file: BackupFile, audio: Map<String, ByteArray> = emptyMap()): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_JSON).apply { time = 0 })
            zip.write(json.encodeToString(file).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            audio.toSortedMap().forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(AUDIO_PREFIX + safeName(name)).apply { time = 0 })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Validates the schema (§9): unknown newer versions and malformed files are refused before anything is touched. */
    fun unpack(bytes: ByteArray): ParsedBackup {
        var file: BackupFile? = null
        val audio = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val data = readBounded(zip)
                when {
                    entry.name == ENTRY_JSON -> file = json.decodeFromString<BackupFile>(String(data, Charsets.UTF_8))
                    entry.name.startsWith(AUDIO_PREFIX) && !entry.isDirectory -> audio[safeName(entry.name.removePrefix(AUDIO_PREFIX))] = data
                }
            }
        }
        val parsed = file ?: throw IllegalArgumentException("Not an UltimateAlarm backup: no $ENTRY_JSON")
        require(parsed.schemaVersion in 1..BackupFile.SCHEMA_VERSION) { "Backup schema ${parsed.schemaVersion} is newer than this app" }
        return ParsedBackup(parsed, parsed.toContent(), audio)
    }

    fun credentials(file: BackupFile): ByteArray? = file.encryptedCredentials?.let { Base64.getDecoder().decode(it) }

    private fun readBounded(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_ENTRY_BYTES) { "Backup entry too large" }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** No paths from the archive reach the file system (zip slip). */
    fun safeName(name: String): String = name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "audio" }

    private fun Alarm.toDto(): AlarmDto {
        val weekly = schedule as? Schedule.Weekly
        val cycle = schedule as? Schedule.Cycle
        return AlarmDto(
            id = id,
            time = time.toString(),
            label = label,
            enabled = enabled,
            kind = kind.name,
            weekDays = weekly?.days?.map { it.value }?.sorted(),
            cycleAnchor = cycle?.anchor?.toString(),
            cycleMask = cycle?.mask?.joinToString("") { if (it) "1" else "0" },
            oneShotDate = oneShotDate?.toString(),
            respectHolidays = respectHolidays,
            sound = sound,
            backupSound = backupSound,
            maxVolume = maxVolume,
            rampSeconds = rampSeconds,
            vibrate = vibrate,
            snoozeMinutes = snoozeMinutes,
            maxSnoozes = maxSnoozes,
            backupAfterMinutes = backupAfterMinutes,
            challenge = challenge,
            preNotifyMinutes = preNotifyMinutes
        )
    }

    private fun AlarmDto.toAlarm(): Alarm {
        val mask = cycleMask
        val anchor = cycleAnchor
        val schedule = if (mask != null && anchor != null && mask.isNotEmpty()) {
            Schedule.Cycle(LocalDate.parse(anchor), mask.map { it == '1' })
        } else {
            Schedule.Weekly(weekDays.orEmpty().map { DayOfWeek.of(it) }.toSet())
        }
        return Alarm(
            id = id,
            time = LocalTime.parse(time),
            label = label,
            enabled = enabled,
            kind = AlarmKind.entries.firstOrNull { it.name == kind } ?: AlarmKind.RECURRING,
            schedule = schedule,
            oneShotDate = oneShotDate?.let(LocalDate::parse),
            respectHolidays = respectHolidays,
            sound = sound,
            backupSound = backupSound,
            maxVolume = maxVolume.coerceIn(1, 100),
            rampSeconds = rampSeconds.coerceIn(0, 300),
            vibrate = vibrate,
            snoozeMinutes = snoozeMinutes.coerceIn(1, 30),
            maxSnoozes = maxSnoozes,
            backupAfterMinutes = backupAfterMinutes,
            challenge = challenge,
            preNotifyMinutes = preNotifyMinutes
        )
    }

    private fun BackupFile.toContent() = BackupContent(
        alarms = alarms.map { it.toAlarm() },
        overrides = overrides.mapNotNull { o ->
            OverrideMode.entries.firstOrNull { it.name == o.mode }?.let { DayOverride(o.alarmId, LocalDate.parse(o.date), it) }
        },
        skips = skips.map { SkipNext(it.alarmId, LocalDate.parse(it.date)) },
        vacations = vacations.map { Vacation(0, LocalDate.parse(it.start), LocalDate.parse(it.end), it.alarmIds?.toSet()) },
        decisions = decisions.map { HolidayDecision(LocalDate.parse(it.date), it.alarmId, it.skip) },
        manualHolidays = manualHolidays.map { ManualHoliday(date = LocalDate.parse(it.date), name = it.name, yearly = it.yearly) },
        holidayOverrides = holidayOverrides.map { HolidayOverride(LocalDate.parse(it.date), it.isHoliday, it.name) }
    )

    private const val BUFFER = 8 * 1024
}
