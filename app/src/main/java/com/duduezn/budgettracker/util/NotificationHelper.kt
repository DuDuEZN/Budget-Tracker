package com.duduezn.budgettracker.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.duduezn.budgettracker.R
import com.duduezn.budgettracker.ui.MainActivity
import java.text.NumberFormat
import java.util.Locale

/** Notifications locales uniquement (aucun service push). */
object NotificationHelper {

    private const val CHANNEL_BUDGET = "budget_alerts"
    private const val NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_BUDGET,
                "Alertes budgétaires",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Prévient lorsque 80 % du budget mensuel est atteint."
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyBudgetAlert(context: Context, spent: Double, budget: Double) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formatter = NumberFormat.getIntegerInstance(Locale.FRANCE)
        val message = "Dépensé : ${formatter.format(spent)} FCFA sur " +
            "${formatter.format(budget)} FCFA ce mois-ci."

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Budget bientôt atteint")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
