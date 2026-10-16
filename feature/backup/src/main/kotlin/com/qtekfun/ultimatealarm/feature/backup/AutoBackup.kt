package com.qtekfun.ultimatealarm.feature.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qtekfun.ultimatealarm.core.data.DiagnosticLog
import java.util.concurrent.TimeUnit

/** Where the worker gets its service (set by the app, which owns the database). */
object BackupGraph {
    @Volatile
    var service: ((Context) -> BackupService)? = null
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = NextcloudSettings(applicationContext)
        if (!settings.auto || !settings.account.configured) return Result.success()
        val service = BackupGraph.service?.invoke(applicationContext) ?: return Result.failure()
        return runCatching { service.backupToNextcloud() }
            .fold(
                onSuccess = {
                    DiagnosticLog.log(applicationContext, "BACKUP", "uploaded $it")
                    Result.success()
                },
                onFailure = {
                    settings.lastError = it.message ?: it.javaClass.simpleName
                    DiagnosticLog.log(applicationContext, "BACKUP", "failed: ${it.javaClass.simpleName}: ${it.message}")
                    if (it is DavException && it.error == DavError.AUTH) Result.failure() else Result.retry()
                }
            )
    }
}

/** RF-B3: one minute after the last change (debounce) and daily, only with network. */
object AutoBackup {
    private const val SOON = "backup-after-change"
    private const val DAILY = "backup-daily"
    private const val DEBOUNCE_MINUTES = 1L

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun afterChange(context: Context) {
        if (!NextcloudSettings(context).auto) return
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>().setInitialDelay(DEBOUNCE_MINUTES, TimeUnit.MINUTES).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
    }

    fun ensureDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
