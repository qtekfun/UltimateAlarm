package com.qtekfun.ultimatealarm.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatealarm.core.data.HolidayEntry
import com.qtekfun.ultimatealarm.core.data.HolidayRepository
import com.qtekfun.ultimatealarm.core.data.HolidaySettings
import com.qtekfun.ultimatealarm.core.data.HolidaySourceType
import com.qtekfun.ultimatealarm.core.data.ManualHoliday
import com.qtekfun.ultimatealarm.feature.holidays.HolidayRefresher
import com.qtekfun.ultimatealarm.feature.holidays.LocalHolidays
import com.qtekfun.ultimatealarm.feature.ringing.Rescheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HolidayConfig(
    val automatic: Boolean,
    val country: String,
    val subdivision: String?,
    val icsUrl: String,
    val municipalityPath: String?,
    val municipalityName: String?,
    val eveEnabled: Boolean,
    val eveTime: LocalTime,
    val lastRefresh: Long,
    val lastError: String?,
    val refreshing: Boolean = false
)

/** Settings → Holidays: where the user lives, the extra public calendar, manual holidays, cancellations, the eve notice. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HolidaySettingsViewModel @Inject constructor(application: Application, private val repository: HolidayRepository) : AndroidViewModel(application) {
    private val settings = HolidaySettings(application)
    private val mutable = MutableStateFlow(read())
    val config: StateFlow<HolidayConfig> = mutable

    val year = MutableStateFlow(LocalDate.now().year)
    val entries: StateFlow<List<HolidayEntry>> = year.flatMapLatest { repository.observeYear(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private fun read() = HolidayConfig(
        settings.automatic,
        settings.country,
        settings.subdivision,
        settings.icsUrl.orEmpty(),
        settings.municipalityPath,
        settings.municipalityName,
        settings.eveEnabled,
        settings.eveTime,
        settings.lastRefresh,
        settings.lastError
    )

    fun setAutomatic(on: Boolean) = changeSource(HolidaySourceType.API) { settings.automatic = on }

    fun setCountry(country: String) = changeSource(HolidaySourceType.API) {
        settings.country = country
        settings.subdivision = null
        clearMunicipality()
    }

    fun setSubdivision(code: String?) = changeSource(HolidaySourceType.API) {
        settings.subdivision = code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        clearMunicipality()
    }

    /** D-16: provinces and municipalities of the chosen Spanish region, loaded from the network for the pickers. */
    val provinces = MutableStateFlow<List<LocalHolidays.Place>>(emptyList())
    val municipalities = MutableStateFlow<List<LocalHolidays.Place>>(emptyList())

    /** Why the last list failed to load; null when it loaded. */
    val placesError = MutableStateFlow<Throwable?>(null)

    fun loadProvinces() {
        val community = LocalHolidays.communitySlug[settings.subdivision] ?: return
        viewModelScope.launch {
            runCatching { refresher().provinces(community) }
                .onSuccess {
                    provinces.value = it
                    placesError.value = null
                }.onFailure { placesError.value = it }
        }
    }

    fun loadMunicipalities(province: String) {
        val community = LocalHolidays.communitySlug[settings.subdivision] ?: return
        municipalities.value = emptyList()
        viewModelScope.launch {
            runCatching { refresher().municipalities(community, province) }
                .onSuccess {
                    municipalities.value = it
                    placesError.value = null
                }.onFailure { placesError.value = it }
        }
    }

    fun setMunicipality(province: String, place: LocalHolidays.Place?) = changeSource(HolidaySourceType.LOCAL) {
        val community = LocalHolidays.communitySlug[settings.subdivision]
        if (place == null || community == null) {
            clearMunicipality()
        } else {
            settings.municipalityPath = "$community/$province/${place.slug}"
            settings.municipalityName = place.name
        }
    }

    private fun clearMunicipality() {
        settings.municipalityPath = null
        settings.municipalityName = null
        viewModelScope.launch { repository.clear(HolidaySourceType.LOCAL) }
    }

    private fun refresher() = HolidayRefresher(getApplication(), repository, settings)

    fun setIcsUrl(url: String) = changeSource(HolidaySourceType.ICS) { settings.icsUrl = url }

    fun setEve(enabled: Boolean, time: LocalTime = settings.eveTime) {
        settings.eveEnabled = enabled
        settings.eveTime = time
        mutable.value = read()
        viewModelScope.launch { Rescheduler.rescheduleAll(getApplication(), "holiday_settings") }
    }

    fun refresh() {
        mutable.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            HolidayRefresher(getApplication(), repository, settings).refresh()
            mutable.value = read()
        }
    }

    fun addManual(date: LocalDate, name: String, yearly: Boolean) = viewModelScope.launch {
        repository.saveManual(ManualHoliday(date = date, name = name.trim(), yearly = yearly))
    }

    fun deleteManual(id: Long) = viewModelScope.launch { repository.deleteManual(id) }

    fun setCancelled(date: LocalDate, cancelled: Boolean) = viewModelScope.launch { repository.setCancelled(date, cancelled) }

    /** A different region or calendar invalidates what was downloaded for the old one: drop it, then download again. */
    private fun changeSource(source: HolidaySourceType, change: () -> Unit) {
        change()
        mutable.value = read().copy(refreshing = true)
        viewModelScope.launch {
            repository.clear(source)
            val refresher = HolidayRefresher(getApplication(), repository, settings)
            refresher.seedFromSnapshot()
            refresher.refresh()
            mutable.value = read()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
