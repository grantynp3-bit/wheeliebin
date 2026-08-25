package com.wheeliebin.newport

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Runs periodically in the background. Fetches the household's bin collection dates from
 * Newport City Council and, if any bin is due tomorrow and we haven't already notified for
 * that date, shows a local notification.
 */
class BinCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val uprn = prefs.uprn
        if (uprn.isBlank()) {
            return Result.success()
        }

        return try {
            val collections = NewportBinApi.fetchBinDates(uprn)
            val tomorrow = LocalDate.now().plusDays(1)
            val dueTomorrow = collections.filter { it.date == tomorrow }

            if (dueTomorrow.isNotEmpty()) {
                val tomorrowKey = tomorrow.toString()
                if (prefs.lastNotifiedForDate != tomorrowKey) {
                    NotificationHelper.showBinReminder(
                        applicationContext,
                        dueTomorrow.map { it.type }
                    )
                    prefs.lastNotifiedForDate = tomorrowKey
                }
            }
            Result.success()
        } catch (e: Exception) {
            // Network hiccups / council site being briefly unavailable shouldn't be treated
            // as a permanent failure — let WorkManager retry on its normal schedule.
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "bin_check_periodic"

        /**
         * Schedules (or re-schedules) the recurring background check. Safe to call every
         * time the app starts or the user saves their UPRN — WorkManager will keep the
         * existing schedule rather than creating a duplicate.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BinCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
