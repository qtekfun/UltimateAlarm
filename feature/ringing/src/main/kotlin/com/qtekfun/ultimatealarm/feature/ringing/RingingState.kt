package com.qtekfun.ultimatealarm.feature.ringing

import com.qtekfun.ultimatealarm.core.data.RingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What is ringing right now, shared by the service and the alarm screen (same process). */
data class Ringing(val alarmId: Long, val firedAt: Long, val title: String = "", val canSnooze: Boolean = true, val mode: RingMode = RingMode.NORMAL)

object RingingState {
    private val state = MutableStateFlow<Ringing?>(null)
    val current: StateFlow<Ringing?> = state

    internal fun set(ringing: Ringing?) {
        state.value = ringing
    }
}
