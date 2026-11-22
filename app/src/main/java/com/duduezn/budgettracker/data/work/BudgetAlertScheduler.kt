package com.duduezn.budgettracker.data.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Planifie la vérification budgétaire quotidienne, sans serveur. */
object BudgetAlertScheduler {

    private const val WORK_NAME = "budget_alert_daily"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BudgetAlertWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
