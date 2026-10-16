package com.qtekfun.ultimatealarm.feature.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.data.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-off messages for the snackbar host. */
sealed interface AlarmEvent {
    /** RF-A3: shown after enabling or saving. `null`: it will not ring. */
    data class RingsIn(val duration: Duration?) : AlarmEvent

    /** RF-A4: deleted, with undo. */
    data class Deleted(val alarm: Alarm) : AlarmEvent

    /** RF-P6: skipped, with undo until the occurrence passes. */
    data class Skipped(val alarmId: Long, val date: LocalDate) : AlarmEvent
}

data class AlarmListState(val rows: List<AlarmRow> = emptyList(), val now: ZonedDateTime = ZonedDateTime.now(), val loaded: Boolean = false)

@HiltViewModel
class AlarmListViewModel @Inject constructor(private val repository: AlarmRepository) : ViewModel() {
    private val events = Channel<AlarmEvent>(Channel.BUFFERED)
    val messages: Flow<AlarmEvent> = events.receiveAsFlow()

    /** Re-evaluated every minute so "rings in" and the reasons stay current. */
    private val clock = flow {
        while (true) {
            emit(ZonedDateTime.now())
            delay(TICK_MS)
        }
    }

    val state: StateFlow<AlarmListState> = combine(repository.observeAlarms(), repository.observeRules(), clock) { alarms, rules, now ->
        AlarmListState(alarmRows(alarms, rules.copy(holidays = repository.holidayCalendar()), now), now, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AlarmListState())

    fun setEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(id, enabled)
        if (enabled) events.send(AlarmEvent.RingsIn(repository.ringsIn(id)))
    }

    fun delete(id: Long) = viewModelScope.launch {
        val alarm = repository.alarm(id) ?: return@launch
        repository.delete(id)
        events.send(AlarmEvent.Deleted(alarm))
    }

    fun restore(alarm: Alarm) = viewModelScope.launch { repository.restore(alarm) }

    fun duplicate(id: Long) = viewModelScope.launch { repository.duplicate(id) }

    fun skip(row: AlarmRow) = viewModelScope.launch {
        val date = row.skipTarget ?: return@launch
        repository.skip(row.alarm.id, date)
        events.send(AlarmEvent.Skipped(row.alarm.id, date))
    }

    fun unskip(alarmId: Long, date: LocalDate) = viewModelScope.launch { repository.unskip(alarmId, date) }

    private companion object {
        const val TICK_MS = 30_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
