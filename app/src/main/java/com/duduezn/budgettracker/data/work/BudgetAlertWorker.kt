package com.duduezn.budgettracker.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duduezn.budgettracker.BudgetTrackerApp
import com.duduezn.budgettracker.domain.util.MonthRange
import com.duduezn.budgettracker.util.NotificationHelper

/**
 * Vérifie une fois par jour l'état du budget mensuel et publie une alerte locale.
 * Aucun accès réseau : seules Room et les notifications système sont utilisées.
 */
class BudgetAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val container = (applicationContext as BudgetTrackerApp).container
        val totalBudget = container.repository.totalBudget()

        if (totalBudget <= 0.0) {
            Result.success()
        } else {
            val range = MonthRange.current()
            val spent = container.repository.monthlySpent(range.first, range.last + 1)
            if (spent >= totalBudget * ALERT_THRESHOLD) {
                NotificationHelper.notifyBudgetAlert(applicationContext, spent, totalBudget)
            }
            Result.success()
        }
    } catch (_: Throwable) {
        Result.retry()
    }

    private companion object {
        const val ALERT_THRESHOLD = 0.8
    }
}
