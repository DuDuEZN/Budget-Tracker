package com.duduezn.budgettracker.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.duduezn.budgettracker.BudgetTrackerApp
import com.duduezn.budgettracker.domain.model.IncomingMessage
import com.duduezn.budgettracker.domain.model.MessageSource
import kotlinx.coroutines.launch

/**
 * Reçoit les SMS Mobile Money et les enregistre immédiatement en local.
 * `goAsync()` laisse le temps à la coroutine d'écrire dans Room avant
 * que le système ne recycle le BroadcastReceiver.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val app = context.applicationContext as? BudgetTrackerApp ?: return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
        val sender = parts.firstOrNull()?.originatingAddress
        val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        val pendingResult = goAsync()
        app.container.applicationScope.launch {
            try {
                app.container.ingestMessage(
                    IncomingMessage(
                        body = body,
                        sender = sender,
                        timestamp = timestamp,
                        source = MessageSource.SMS
                    )
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
