package com.duduezn.budgettracker.ui.util

import androidx.compose.ui.graphics.Color
import com.duduezn.budgettracker.domain.model.Transaction
import com.duduezn.budgettracker.domain.model.TransactionType
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val xofSymbols = DecimalFormatSymbols(Locale.FRANCE)

fun formatXof(amount: Double): String {
    val formatter = DecimalFormat("#,##0", xofSymbols)
    return "${formatter.format(amount)} FCFA"
}

fun formatSignedXof(transaction: Transaction): String {
    val sign = if (transaction.type == TransactionType.DEPOT) "+ " else "- "
    return sign + formatXof(transaction.amount)
}

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM • HH:mm", Locale.FRENCH).format(Date(timestamp))

fun hexToColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(Color(0xFF607D8B))
