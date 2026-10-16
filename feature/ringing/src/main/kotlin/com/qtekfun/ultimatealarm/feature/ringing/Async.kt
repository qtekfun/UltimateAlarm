package com.qtekfun.ultimatealarm.feature.ringing

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Background scope of the firing path: database and AlarmManager work never runs on the main thread (RNF-5). */
internal val ringingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
internal fun BroadcastReceiver.doAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    ringingScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
