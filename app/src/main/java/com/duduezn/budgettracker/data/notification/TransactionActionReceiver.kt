package com.duduezn.budgettracker.data.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.duduezn.budgettracker.BudgetTrackerApp
import com.duduezn.budgettracker.domain.categorization.CategorizationEngine
import com.duduezn.budgettracker.util.BudgetAlertNotifier
import com.duduezn.budgettracker.util.CrashLogger
import kotlinx.coroutines.launch

/**
 * Traite les réponses directes (RemoteInput) et les actions rapides
 * des notifications de relance. Met à jour la transaction, enregistre la
 * règle marchand et bascule la notification en confirmation.
 */
class TransactionActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as? BudgetTrackerApp ?: run {
            pendingResult.finish()
            return
        }

        app.container.applicationScope.launch {
            try {
                val transactionId = intent.getLongExtra(
                    BudgetAlertNotifier.EXTRA_TRANSACTION_ID,
                    -1L
                )
                if (transactionId <= 0L) return@launch

                val repository = app.container.repository
                val transaction = repository.findTransaction(transactionId) ?: return@launch

                when (intent.action) {
                    BudgetAlertNotifier.ACTION_REPLY_REASON -> {
                        val results = RemoteInput.getResultsFromIntent(intent)
                        val reason = results
                            ?.getCharSequence(BudgetAlertNotifier.KEY_REASON)
                            ?.toString()
                            ?.trim()
                            .orEmpty()
                        if (reason.isBlank()) return@launch

                        val suggested = CategorizationEngine().suggestCategory(reason)
                            ?: "Autres"
                        val category = repository.findCategoryByName(suggested)
                            ?: repository.findCategoryByName("Autres")
                            ?: return@launch
                        repository.confirmReason(transactionId, category.id, reason)
                    }

                    BudgetAlertNotifier.ACTION_QUICK_CATEGORY -> {
                        val categoryName = intent.getStringExtra(
                            BudgetAlertNotifier.EXTRA_CATEGORY_NAME
                        ).orEmpty().ifBlank { "Autres" }
                        val category = repository.findCategoryByName(categoryName)
                            ?: repository.findCategoryByName("Autres")
                            ?: return@launch
                        val reason = if (categoryName == "Autres") "Divers" else categoryName
                        repository.confirmReason(transactionId, category.id, reason)
                    }

                    else -> return@launch
                }

                // Supprime la notification de relance et affiche la confirmation.
                NotificationManagerCompat.from(context)
                    .cancel(transactionId.toInt() + 1000)
                val updated = repository.findTransaction(transactionId)
                if (updated != null) {
                    BudgetAlertNotifier.notifyConfirmed(context, updated)
                }
            } catch (t: Throwable) {
                CrashLogger.log(context, t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
