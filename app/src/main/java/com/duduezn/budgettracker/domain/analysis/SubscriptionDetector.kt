package com.duduezn.budgettracker.domain.analysis

import com.duduezn.budgettracker.domain.model.Transaction
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType
import kotlin.math.abs
import kotlin.math.roundToInt

/** Abonnement ou paiement récurrent détecté localement à partir de l'historique. */
data class DetectedSubscription(
    val normalizedCounterparty: String,
    val displayName: String,
    val categoryId: Long?,
    val averageAmount: Double,
    val occurrences: Int,
    val intervalDays: Int,
    val lastPayment: Long,
    val nextExpected: Long,
    val active: Boolean
)

/**
 * Détecte les abonnements mensuels sans aucun appel réseau :
 * même marchand, montant stable et paiements espacés d'environ un mois.
 */
object SubscriptionDetector {

    private const val MIN_OCCURRENCES = 3
    private const val MIN_INTERVAL_DAYS = 25
    private const val MAX_INTERVAL_DAYS = 35
    private const val ACTIVE_WINDOW_DAYS = 45
    private const val AMOUNT_TOLERANCE = 0.20
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun detect(
        transactions: List<Transaction>,
        now: Long = System.currentTimeMillis()
    ): List<DetectedSubscription> {
        return transactions
            .asSequence()
            .filter { it.status == TransactionStatus.CONFIRMED }
            .filter { it.type != TransactionType.DEPOT }
            .filter { !it.counterparty.isNullOrBlank() }
            .groupBy { normalize(it.counterparty.orEmpty()) }
            .mapNotNull { (normalized, group) -> detectGroup(normalized, group, now) }
            .sortedByDescending { it.lastPayment }
            .toList()
    }

    private fun detectGroup(
        normalized: String,
        group: List<Transaction>,
        now: Long
    ): DetectedSubscription? {
        if (group.size < MIN_OCCURRENCES) return null

        val sorted = group.sortedBy { it.timestamp }
        val amounts = sorted.map { it.amount }
        val median = amounts.sorted()[amounts.size / 2]
        if (median <= 0.0) return null

        val stableAmounts = sorted.all { abs(it.amount - median) <= median * AMOUNT_TOLERANCE }
        if (!stableAmounts) return null

        val intervals = sorted.zipWithNext { first, second ->
            ((second.timestamp - first.timestamp).toDouble() / DAY_MS).roundToInt()
        }
        if (intervals.isEmpty()) return null

        val monthlyIntervals = intervals.count { it in MIN_INTERVAL_DAYS..MAX_INTERVAL_DAYS }
        if (monthlyIntervals < intervals.size * 0.75) return null

        val averageInterval = intervals.average()
        val last = sorted.last()
        val nextExpected = last.timestamp + (averageInterval * DAY_MS).toLong()
        val active = now - last.timestamp <= ACTIVE_WINDOW_DAYS * DAY_MS

        return DetectedSubscription(
            normalizedCounterparty = normalized,
            displayName = last.counterparty?.trim().orEmpty().ifBlank { normalized },
            categoryId = last.detectedCategory,
            averageAmount = amounts.average(),
            occurrences = sorted.size,
            intervalDays = averageInterval.roundToInt(),
            lastPayment = last.timestamp,
            nextExpected = nextExpected,
            active = active
        )
    }

    private fun normalize(value: String): String =
        value.trim()
            .replace(Regex("\\s+"), " ")
            .uppercase()
}
