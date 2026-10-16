package com.qtekfun.ultimatealarm.core.data

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One diagnostic record (02-diseno §2, `EventLog`). Kept on the device only (RNF-7). */
data class Event(val at: Instant, val alarmId: Long?, val type: String, val detail: String)

/**
 * Append-only diagnostic log in [dir], one tab-separated line per event, rotated to a single `.1` file once it passes
 * [maxBytes]. Synchronous and Android-free so it can be tested on the JVM; [DiagnosticLog] runs it off the main thread
 * and in device-protected storage so it works before the first unlock.
 */
class EventLog(private val dir: File, private val maxBytes: Long = DEFAULT_MAX_BYTES) {
    private val current = File(dir, FILE_NAME)
    private val previous = File(dir, "$FILE_NAME.1")

    @Synchronized
    fun append(event: Event) {
        dir.mkdirs()
        if (current.length() >= maxBytes) {
            previous.delete()
            current.renameTo(previous)
        }
        current.appendText(encode(event) + "\n")
    }

    /** All events, oldest first. Lines that cannot be parsed are skipped. */
    @Synchronized
    fun read(): List<Event> = listOf(previous, current).filter { it.exists() }.flatMap { file -> file.readLines().mapNotNull(::decode) }

    /** Human-readable export: a header line and one event per line, times in [zone]. */
    fun export(zone: ZoneId): String = buildString {
        append("# UltimateAlarm event log\n")
        read().forEach { event ->
            append(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(event.at.atZone(zone)))
            append('\t').append(event.alarmId?.toString() ?: "-")
            append('\t').append(event.type)
            append('\t').append(event.detail)
            append('\n')
        }
    }

    @Synchronized
    fun clear() {
        current.delete()
        previous.delete()
    }

    companion object {
        const val FILE_NAME = "event-log.tsv"
        const val DEFAULT_MAX_BYTES = 256L * 1024

        internal fun encode(event: Event): String = listOf(
            event.at.toEpochMilli().toString(),
            event.alarmId?.toString() ?: "",
            escape(event.type),
            escape(event.detail)
        ).joinToString("\t")

        internal fun decode(line: String): Event? {
            val parts = line.split('\t')
            if (parts.size != FIELDS) return null
            val millis = parts[0].toLongOrNull() ?: return null
            return Event(Instant.ofEpochMilli(millis), parts[1].toLongOrNull(), unescape(parts[2]), unescape(parts[3]))
        }

        private const val FIELDS = 4

        private fun escape(value: String) = value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

        private fun unescape(value: String): String {
            val out = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    out.append(
                        when (value[i + 1]) {
                            't' -> '\t'
                            'n' -> '\n'
                            else -> value[i + 1]
                        }
                    )
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}
