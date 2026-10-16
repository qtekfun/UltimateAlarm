package com.qtekfun.ultimatealarm.feature.ringing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qtekfun.ultimatealarm.core.data.Alarm
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import java.util.concurrent.ConcurrentHashMap

/** "Undo" on the notification shown after a dismissed one-shot alarm was deleted. */
class UndoDeleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ALARM_ID, 0)
        val alarm = recentlyDeleted.remove(id)
        Notifications.cancelDeleted(context, id)
        if (alarm == null) {
            DiagnosticLog.log(context, "UNDO_DELETE", "too late: the deleted alarm is no longer in memory", id)
            return
        }
        doAsync {
            AlarmGraph.repository(context).restore(alarm.copy(lastFiredDate = null))
            DiagnosticLog.log(context, "UNDO_DELETE", "restored", id)
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"

        /** Kept in memory only for the 10 s undo window (§5.6). */
        internal val recentlyDeleted = ConcurrentHashMap<Long, Alarm>()
    }
}
