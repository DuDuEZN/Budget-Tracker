package com.duduezn.budgettracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.duduezn.budgettracker.BudgetTrackerApp
import com.duduezn.budgettracker.R
import com.duduezn.budgettracker.data.notification.TransactionActionReceiver
import com.duduezn.budgettracker.domain.model.Transaction
import com.duduezn.budgettracker.domain.model.TransactionStatus
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/**
 * Notifications locales pour les transactions détectées automatiquement.
 *
 * - Transaction confirmée : notification discrète avec le reste du budget.
 * - Transaction en attente : notification haute priorité avec réponse directe
 *   (RemoteInput) et actions rapides.
 */
object BudgetAlertNotifier {

    const val CHANNEL_CONFIRMED = "transaction_confirmed"
    const val CHANNEL_PENDING = "transaction_pending"

    const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
    const val EXTRA_CATEGORY_NAME = "extra_category_name"
    const val KEY_REASON = "key_reason"

    const val ACTION_REPLY_REASON = "com.duduezn.budgettracker.ACTION_REPLY_REASON"
    const val ACTION_QUICK_CATEGORY = "com.duduezn.budgettracker.ACTION_QUICK_CATEGORY"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CONFIRMED,
                "Transactions confirmées",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Confirmation des transactions enregistrées." }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PENDING,
                "Motif à préciser",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Relance pour préciser le motif d'une dépense." }
        )
    }

    suspend fun notifyConfirmed(context: Context, transaction: Transaction) {
        if (!canNotify(context)) return

        val app = context.applicationContext as BudgetTrackerApp
        val repository = app.container.repository

        val formatter = NumberFormat.getIntegerInstance(Locale.FRANCE)
        val title = "Transaction enregistrée"
        val text = buildString {
            append(formatter.format(transaction.amount.toLong())).append(" FCFA")
            val categoryId = transaction.detectedCategory
            if (categoryId != null) {
                val category = repository.findCategoryById(categoryId)
                if (category != null && category.monthlyBudgetLimit > 0.0) {
                    val (start, end) = currentMonthBounds()
                    val spent = repository.monthlySpentByCategory(category.id, start, end)
                    val remaining = category.monthlyBudgetLimit - spent
                    append(" • reste ").append(formatter.format(remaining.toLong()))
                        .append(" FCFA (").append(category.name).append(")")
                }
            }
        }

        val openIntent = Intent(context, com.duduezn.budgettracker.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            (transaction.id.toInt() + 5000),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CONFIRMED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .build()

        notify(context, transaction.id.toInt() + 2000, notification)
    }

    suspend fun notifyPending(context: Context, transaction: Transaction) {
        if (!canNotify(context)) return

        val formatter = NumberFormat.getIntegerInstance(Locale.FRANCE)
        val title = "Motif à préciser"
        val text = buildString {
            append("Dépense de ").append(formatter.format(transaction.amount.toLong())).append(" FCFA")
            transaction.counterparty?.takeIf { it.isNotBlank() }?.let { append(" chez ").append(it) }
        }

        val openIntent = Intent(context, com.duduezn.budgettracker.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TRANSACTION_ID, transaction.id)
        }
        val openPending = PendingIntent.getActivity(
            context,
            transaction.id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(KEY_REASON)
            .setLabel("Saisir le motif")
            .build()

        val replyIntent = Intent(context, TransactionActionReceiver::class.java).apply {
            action = ACTION_REPLY_REASON
            putExtra(EXTRA_TRANSACTION_ID, transaction.id)
        }
        val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val replyPending = PendingIntent.getBroadcast(
            context,
            transaction.id.toInt(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "Saisir le motif",
            replyPending
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val quickActions = listOf("Restauration", "Transport", "Divers")
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_PENDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .addAction(replyAction)

        quickActions.forEachIndexed { index, label ->
            val mapped = if (label == "Divers") "Autres" else label
            val quickIntent = Intent(context, TransactionActionReceiver::class.java).apply {
                action = ACTION_QUICK_CATEGORY
                putExtra(EXTRA_TRANSACTION_ID, transaction.id)
                putExtra(EXTRA_CATEGORY_NAME, mapped)
            }
            val quickPending = PendingIntent.getBroadcast(
                context,
                transaction.id.toInt() * 10 + index,
                quickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(R.drawable.ic_notification, label, quickPending)
        }

        val notification = notificationBuilder.build()
        notify(context, transaction.id.toInt() + 1000, notification)
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        return manager.areNotificationsEnabled()
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun currentMonthBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis
        return start to end
    }
}
