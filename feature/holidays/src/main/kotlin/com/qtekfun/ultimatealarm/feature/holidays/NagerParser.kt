package com.qtekfun.ultimatealarm.feature.holidays

import com.qtekfun.ultimatealarm.core.scheduling.Holiday
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One entry of `GET /api/v3/PublicHolidays/{year}/{country}` (Nager.Date). */
@Serializable
internal data class NagerHoliday(
    val date: String,
    val localName: String? = null,
    val name: String? = null,
    val global: Boolean = true,
    val counties: List<String>? = null,
    /** Announced replacement of `counties` (02-diseno §6): both are read. */
    val subdivisionCodes: List<String>? = null,
    val types: List<String> = emptyList()
)

/**
 * Nager.Date → holidays of one region (RF-F2): type `Public` and either national (`global`) or listing [subdivision]
 * (e.g. `ES-MD`). With no subdivision, only national ones. Throws on malformed JSON so the caller keeps its cache.
 */
object NagerParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, subdivision: String?): List<Holiday> = json.decodeFromString<List<NagerHoliday>>(body)
        .filter { PUBLIC in it.types }
        .filter { it.global || (subdivision != null && subdivision in (it.counties.orEmpty() + it.subdivisionCodes.orEmpty())) }
        .map { Holiday(LocalDate.parse(it.date), it.localName ?: it.name ?: "") }
        .distinctBy { it.date }

    private const val PUBLIC = "Public"
}
