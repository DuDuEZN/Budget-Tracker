package com.duduezn.budgettracker.data.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.duduezn.budgettracker.BudgetTrackerApp
import com.duduezn.budgettracker.domain.model.IncomingMessage
import com.duduezn.budgettracker.domain.model.MessageSource
import com.duduezn.budgettracker.util.BudgetAlertNotifier
import com.duduezn.budgettracker.util.CrashLogger
import kotlinx.coroutines.launch

/**
 * Capture les notifications des applications Mobile Money lorsque
 * l'utilisateur a accordé l'accès aux notifications (réglages système).
 *
 * Aucun contenu n'est transmis à l'extérieur : tout est analysé et stocké en local.
 */
class MomoNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (sbn.packageName == applicationContext.packageName) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        val mainText = bigText.ifBlank { text }
        val body = buildString {
            if (title.isNotBlank()) {
                append(title)
                if (mainText.isNotBlank()) append(". ")
            }
            append(mainText)
        }.trim()

        if (body.isBlank()) return

        val app = applicationContext as? BudgetTrackerApp ?: return
        app.container.applicationScope.launch {
            try {
                val result = app.container.ingestMessage(
                    IncomingMessage(
                        body = body,
                        sender = sbn.packageName,
                        timestamp = sbn.postTime,
                        source = MessageSource.NOTIFICATION
                    )
                )
                val inserted = result as? com.duduezn.budgettracker.domain.model.IngestResult.Inserted
                if (inserted != null) {
                    val transaction = app.container.repository.findTransaction(inserted.transactionId)
                    if (transaction != null) {
                        if (inserted.needsReason) {
                            BudgetAlertNotifier.notifyPending(applicationContext, transaction)
                        } else {
                            BudgetAlertNotifier.notifyConfirmed(applicationContext, transaction)
                        }
                    }
                }
            } catch (t: Throwable) {
                CrashLogger.log(applicationContext, t)
            }
        }
    }
}
