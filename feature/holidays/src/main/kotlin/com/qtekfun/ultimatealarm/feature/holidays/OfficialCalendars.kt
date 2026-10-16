package com.qtekfun.ultimatealarm.feature.holidays

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Municipalities that publish their own labour calendar as open data (D-17). When the user's municipality is one of them,
 * its official ICS files are read on every refresh and their days (with the official names) take precedence over the
 * compiled municipal calendar. The CKAN package is looked up each time because every year is a new resource with a new
 * address.
 */
object OfficialCalendars {
    data class Source(
        /** `community/province/municipality` slugs, as in the settings. */
        val path: String,
        /** CKAN `package_show` endpoint of the dataset. */
        val packageUrl: String,
        /** Shown under the municipality picker (CC BY requires attribution). */
        val attribution: String,
        val attributionUrl: String
    )

    val sources = listOf(
        Source(
            path = "mad/madrid/madrid",
            packageUrl = "https://datos.madrid.es/api/3/action/package_show?id=300082-0-calendario_laboral",
            attribution = "Ayuntamiento de Madrid · datos.madrid.es (CC BY 4.0)",
            attributionUrl = "https://datos.madrid.es/dataset/300082-0-calendario_laboral"
        )
    )

    fun forPath(path: String?): Source? = sources.firstOrNull { it.path == path }

    private val json = Json { ignoreUnknownKeys = true }

    /** ICS download addresses of a CKAN `package_show` answer; https only. */
    fun icsUrls(packageShow: String): List<String> = json.decodeFromString<PackageShow>(packageShow).result.resources
        .filter { it.format.equals("ICS", ignoreCase = true) && it.url.startsWith("https://") }
        .map { it.url }

    @Serializable
    private data class PackageShow(val result: Result = Result())

    @Serializable
    private data class Result(val resources: List<Resource> = emptyList())

    @Serializable
    private data class Resource(val format: String = "", val url: String = "")
}

/** Official days first: same date, the official name; days only in the compiled calendar are kept. */
fun mergeLocal(official: List<com.qtekfun.ultimatealarm.core.scheduling.Holiday>, compiled: List<com.qtekfun.ultimatealarm.core.scheduling.Holiday>) =
    (official + compiled).distinctBy { it.date }.sortedBy { it.date }
