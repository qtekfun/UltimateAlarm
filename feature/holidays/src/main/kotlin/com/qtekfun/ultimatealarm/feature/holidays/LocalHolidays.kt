package com.qtekfun.ultimatealarm.feature.holidays

import com.qtekfun.ultimatealarm.core.scheduling.Holiday
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Spanish municipal holidays (local and provincial, plus the full official calendar of the municipality) from the public
 * API of Calendarios Nacionales (https://calendariosnacionales.com/es/api/), which compiles them from the BOE, the
 * Social Security list of local holidays and the regions' open data. Terms: visible attribution with a link (shown in
 * Settings → Holidays), cached responses, no abusive syncing (monthly refresh). D-16.
 */
object LocalHolidays {
    const val BASE = "https://calendariosnacionales.com/es/v1"
    const val ATTRIBUTION_URL = "https://calendariosnacionales.com/"

    /** ISO 3166-2 region → the API's community slug. */
    val communitySlug = mapOf(
        "ES-AN" to "and", "ES-AR" to "ara", "ES-AS" to "ast", "ES-CN" to "can", "ES-CB" to "cnt", "ES-CL" to "cyl",
        "ES-CM" to "clm", "ES-CT" to "cat", "ES-CE" to "ceu", "ES-MD" to "mad", "ES-NC" to "nav", "ES-VC" to "val",
        "ES-EX" to "ext", "ES-GA" to "gal", "ES-IB" to "bal", "ES-RI" to "rio", "ES-ML" to "mel", "ES-PV" to "eus",
        "ES-MC" to "mur"
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun provincesUrl(year: Int, community: String) = "$BASE/$year/regiones/$community/provincias.json"

    fun municipalitiesUrl(year: Int, community: String, province: String) = "$BASE/$year/regiones/$community/provincias/$province/localidades.json"

    /** [path] = `community/province/municipality` slugs, as stored in the settings. */
    fun holidaysUrl(year: Int, path: String) = "$BASE/$year/localidades/$path.json"

    fun parseProvinces(body: String): List<Place> = json.decodeFromString<ProvincesResponse>(body).provinces.map { Place(it.slug, it.name) }

    fun parseMunicipalities(body: String): List<Place> = json.decodeFromString<MunicipalitiesResponse>(body).municipalities
        .map { Place(it.slug, it.name) }
        .sortedBy { it.sortKey }

    /**
     * Every holiday of the municipality: the official `calendar` when present, otherwise the union of all scopes. Local
     * days published as "Fiesta local" keep that name.
     */
    fun parseHolidays(body: String): List<Holiday> {
        val scopes = json.decodeFromString<MunicipalityResponse>(body).holidays
        val days = scopes.calendar.ifEmpty { scopes.national + scopes.regional + scopes.provincial + scopes.local }
        val local = (scopes.local + scopes.provincial).associateBy { it.date }
        return days.plus(local.values).map { day -> Holiday(LocalDate.parse(day.date), local[day.date]?.name ?: day.name) }
            .distinctBy { it.date }
            .sortedBy { it.date }
    }

    @Serializable
    data class Place(val slug: String, val name: String) {
        /** "Acebeda, La" sorts under A, like the official lists. */
        val sortKey: String get() = name.lowercase()
    }

    @Serializable
    private data class ProvincesResponse(val provinces: List<Place> = emptyList())

    @Serializable
    private data class MunicipalitiesResponse(val municipalities: List<Place> = emptyList())

    @Serializable
    private data class MunicipalityResponse(val holidays: Scopes = Scopes())

    @Serializable
    private data class Scopes(
        val national: List<Day> = emptyList(),
        val regional: List<Day> = emptyList(),
        val provincial: List<Day> = emptyList(),
        val local: List<Day> = emptyList(),
        val calendar: List<Day> = emptyList()
    )

    @Serializable
    private data class Day(val date: String, val name: String = "")
}
