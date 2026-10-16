package com.qtekfun.ultimatealarm.feature.holidays

import android.content.Context
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import com.qtekfun.ultimatealarm.core.data.HolidayRepository
import com.qtekfun.ultimatealarm.core.data.HolidaySettings
import com.qtekfun.ultimatealarm.core.data.HolidaySourceType
import com.qtekfun.ultimatealarm.core.scheduling.Holiday
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads holidays (02-diseno §6): Nager.Date for the user's country and region, current and next year, and the
 * optional public ICS calendar. Never makes things worse: a network or parsing error keeps the cache, and a year with no
 * cache falls back to the snapshot embedded in the APK. Nothing here can stop an alarm from ringing.
 */
class HolidayRefresher(
    private val context: Context,
    private val repository: HolidayRepository,
    private val settings: HolidaySettings = HolidaySettings(context)
) {
    /** Returns `null` when everything worked, otherwise the first error (also stored for Settings). */
    suspend fun refresh(today: LocalDate = LocalDate.now()): String? = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val years = listOf(today.year, today.year + 1)
        if (settings.automatic) {
            years.forEach { year -> refreshNager(year)?.let(errors::add) }
        }
        settings.municipalityPath?.takeIf { settings.country == SPAIN }?.let { path ->
            years.forEach { year -> refreshLocal(year, path)?.let(errors::add) }
        }
        settings.icsUrl?.let { url -> refreshIcs(url, LocalDate.of(years.first(), 1, 1), LocalDate.of(years.last(), DECEMBER, LAST_DAY))?.let(errors::add) }
        settings.lastRefresh = System.currentTimeMillis()
        settings.lastError = errors.firstOrNull()
        DiagnosticLog.log(context, "HOLIDAYS", if (errors.isEmpty()) "refreshed ${settings.country}/${settings.subdivision}" else "errors: $errors")
        errors.firstOrNull()
    }

    /** First start or region change without network: use the embedded snapshot for the years not cached yet. */
    suspend fun seedFromSnapshot(today: LocalDate = LocalDate.now()) = withContext(Dispatchers.IO) {
        if (!settings.automatic) return@withContext
        listOf(today.year, today.year + 1).forEach { year ->
            if (!repository.hasYear(HolidaySourceType.API, year)) snapshot(year)?.let { store(HolidaySourceType.API, year, it) }
        }
    }

    private suspend fun refreshNager(year: Int): String? {
        val country = settings.country
        val result = runCatching { NagerParser.parse(download("$NAGER/$year/$country"), settings.subdivision) }
        result.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
            store(HolidaySourceType.API, year, it)
            return null
        }
        if (!repository.hasYear(HolidaySourceType.API, year)) snapshot(year)?.let { store(HolidaySourceType.API, year, it) }
        return "Nager.Date $country $year: ${result.exceptionOrNull()?.let { it.javaClass.simpleName + ": " + it.message } ?: "empty"}"
    }

    /**
     * D-16: municipal calendar. The next year is often not published yet (an empty list): that is not an error, the
     * monthly refresh picks it up later.
     */
    private suspend fun refreshLocal(year: Int, path: String): String? {
        val result = runCatching { LocalHolidays.parseHolidays(download(LocalHolidays.holidaysUrl(year, path))) }
        result.getOrNull()?.let { holidays ->
            store(HolidaySourceType.LOCAL, year, holidays)
            return null
        }
        return "Municipal $path $year: ${result.exceptionOrNull()?.let { it.javaClass.simpleName + ": " + it.message }}"
    }

    private suspend fun refreshIcs(url: String, from: LocalDate, to: LocalDate): String? {
        val result = runCatching { IcsParser.parse(download(url), from, to) }
        val holidays = result.getOrNull()
        if (holidays.isNullOrEmpty()) return "ICS: ${result.exceptionOrNull()?.let { it.javaClass.simpleName + ": " + it.message } ?: "no events"}"
        repository.replace(HolidaySourceType.ICS, from, to, holidays)
        return null
    }

    private suspend fun store(source: HolidaySourceType, year: Int, holidays: List<Holiday>) =
        repository.replace(source, LocalDate.of(year, 1, 1), LocalDate.of(year, DECEMBER, LAST_DAY), holidays)

    private fun snapshot(year: Int): List<Holiday>? = runCatching {
        context.assets.open("holidays/${settings.country}-$year.json").bufferedReader().use { NagerParser.parse(it.readText(), settings.subdivision) }
    }.getOrNull()

    /** Lists for the province / municipality pickers (network, not cached). */
    suspend fun provinces(community: String, year: Int = LocalDate.now().year): List<LocalHolidays.Place> =
        withContext(Dispatchers.IO) { LocalHolidays.parseProvinces(download(LocalHolidays.provincesUrl(year, community))) }

    suspend fun municipalities(community: String, province: String, year: Int = LocalDate.now().year): List<LocalHolidays.Place> =
        withContext(Dispatchers.IO) { LocalHolidays.parseMunicipalities(download(LocalHolidays.municipalitiesUrl(year, community, province))) }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "UltimateAlarm")
            if (connection.responseCode !in SUCCESS) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BYTES) throw IOException("response larger than $MAX_BYTES bytes")
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val NAGER = "https://date.nager.at/api/v3/PublicHolidays"
        const val SPAIN = "ES"
        const val TIMEOUT_MS = 15_000
        const val MAX_BYTES = 2 * 1024 * 1024
        const val BUFFER = 8 * 1024
        const val DECEMBER = 12
        const val LAST_DAY = 31
        val SUCCESS = 200..299
    }
}
