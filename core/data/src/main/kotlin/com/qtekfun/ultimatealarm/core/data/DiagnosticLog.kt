package com.qtekfun.ultimatealarm.core.data

import android.content.Context
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Process-wide [EventLog] in device-protected storage (readable and writable before the first unlock, Direct Boot).
 * [log] never blocks the caller: writes go to a single background thread, in order, so logging can never delay an alarm.
 */
object DiagnosticLog {
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "event-log").apply { isDaemon = true }
    }

    @Volatile
    private var instance: EventLog? = null

    fun get(context: Context): EventLog = instance ?: synchronized(this) {
        instance ?: EventLog(DeviceStorage.context(context).filesDir.resolve("diagnostics")).also { instance = it }
    }

    fun log(context: Context, type: String, detail: String = "", alarmId: Long? = null) {
        val log = get(context)
        val event = Event(Instant.now(), alarmId, type, detail)
        writer.execute { runCatching { log.append(event) } }
    }
}

/** Device-protected storage: what the alarm needs to ring before the user unlocks the phone after a reboot. */
object DeviceStorage {
    fun context(context: Context): Context = context.applicationContext.createDeviceProtectedStorageContext()
}
