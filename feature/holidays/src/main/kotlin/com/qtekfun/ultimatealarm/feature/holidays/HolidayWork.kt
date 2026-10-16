package com.qtekfun.ultimatealarm.feature.holidays

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
import com.qtekfun.ultimatealarm.core.data.HolidayRepository
import java.util.concurrent.TimeUnit

/** Where the refresher gets its repository (set by the app, which owns the database instance). */
object HolidayGraph {
    @Volatile
    var repository: ((Context) -> HolidayRepository)? = null
}

class HolidayRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = HolidayGraph.repository?.invoke(applicationContext) ?: return Result.failure()
        val error = HolidayRefresher(applicationContext, repository).refresh()
        return if (error == null) Result.success() else Result.retry()
    }
}

/** RF-F3: monthly with network (which also picks up next year in December), and on demand after a settings change. */
object HolidayWork {
    private const val PERIODIC = "holidays-monthly"
    private const val NOW = "holidays-now"
    private const val DAYS_PER_MONTH = 30L

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<HolidayRefreshWorker>(DAYS_PER_MONTH, TimeUnit.DAYS).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<HolidayRefreshWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }
}
