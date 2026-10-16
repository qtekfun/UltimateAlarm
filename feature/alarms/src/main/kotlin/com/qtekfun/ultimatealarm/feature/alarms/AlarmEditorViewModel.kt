package com.qtekfun.ultimatealarm.feature.alarms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.data.AlarmRepository
import com.qtekfun.ultimatealarm.core.scheduling.AlarmKind
import com.qtekfun.ultimatealarm.core.scheduling.Schedule
import com.qtekfun.ultimatealarm.core.scheduling.nextDailyOccurrence
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(val draft: Alarm = Alarm(), val loaded: Boolean = false, val isNew: Boolean = true)

/** Draft of the alarm being edited (RF-A1). Nothing is written until [save]. */
@HiltViewModel
class AlarmEditorViewModel @Inject constructor(savedState: SavedStateHandle, private val repository: AlarmRepository) : ViewModel() {
    private val alarmId: Long = savedState.get<Long>(ARG_ALARM_ID) ?: 0L
    private val mutable = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = mutable

    init {
        viewModelScope.launch {
            val existing = if (alarmId != 0L) repository.alarm(alarmId) else null
            mutable.value = EditorState(draft = existing ?: Alarm(), loaded = true, isNew = existing == null)
        }
    }

    fun edit(change: (Alarm) -> Alarm) = mutable.update { it.copy(draft = change(it.draft)) }

    fun setTime(time: LocalTime) = edit { it.copy(time = time) }

    fun toggleDay(day: DayOfWeek) = edit { alarm ->
        val days = (alarm.schedule as? Schedule.Weekly)?.days.orEmpty()
        alarm.copy(schedule = Schedule.Weekly(if (day in days) days - day else days + day))
    }

    fun setDays(days: Set<DayOfWeek>) = edit { it.copy(schedule = Schedule.Weekly(days)) }

    /** Switching kind also sets the default of "respect holidays" for that kind (README assumption 1). */
    fun setKind(kind: AlarmKind) = edit { alarm ->
        if (alarm.kind == kind) alarm else alarm.copy(kind = kind, respectHolidays = kind == AlarmKind.RECURRING)
    }

    fun setOneShotDate(date: LocalDate?) = edit { it.copy(oneShotDate = date) }

    /** Saves and returns the time until it rings (RF-A3), or `null` if it will not ring. */
    suspend fun save(): Duration? {
        val draft = mutable.value.draft
        // "Next time it is HH:MM" is stored as a concrete date so the alarm is deleted after that one time (D-11).
        val resolved = if (draft.kind == AlarmKind.ONE_SHOT && draft.oneShotDate == null) {
            draft.copy(oneShotDate = nextDailyOccurrence(draft.time, ZonedDateTime.now()).toLocalDate())
        } else {
            draft
        }
        val id = repository.save(resolved.copy(enabled = true))
        return repository.ringsIn(id)
    }

    suspend fun delete(): Alarm? {
        val alarm = repository.alarm(alarmId) ?: return null
        repository.delete(alarmId)
        return alarm
    }

    companion object {
        const val ARG_ALARM_ID = "alarmId"
    }
}
