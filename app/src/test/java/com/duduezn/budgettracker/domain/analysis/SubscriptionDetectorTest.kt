package com.duduezn.budgettracker.domain.analysis

import com.duduezn.budgettracker.domain.model.Operator
import com.duduezn.budgettracker.domain.model.Transaction
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDetectorTest {

    private val day = 24 * 60 * 60 * 1000L
    private val base = 1_700_000_000_000L

    private fun payment(
        counterparty: String,
        amount: Double,
        dayOffset: Int,
        status: TransactionStatus = TransactionStatus.CONFIRMED
    ) = Transaction(
        id = dayOffset.toLong(),
        rawMessage = "Paiement",
        operator = Operator.MTN_MOMO,
        type = TransactionType.PAIEMENT,
        amount = amount,
        currency = "XOF",
        counterparty = counterparty,
        timestamp = base + dayOffset * day,
        rawReason = null,
        detectedCategory = 1L,
        finalReason = null,
        status = status
    )

    @Test
    fun `detecte un abonnement mensuel regulier`() {
        val transactions = listOf(
            payment("NETFLIX", 5_000.0, 0),
            payment("NETFLIX", 5_000.0, 30),
            payment("NETFLIX", 5_000.0, 60)
        )

        val result = SubscriptionDetector.detect(transactions, now = base + 65 * day)

        assertEquals(1, result.size)
        val subscription = result.single()
        assertEquals("NETFLIX", subscription.displayName)
        assertEquals(3, subscription.occurrences)
        assertEquals(30, subscription.intervalDays)
        assertTrue(subscription.active)
    }

    @Test
    fun `ignore des paiements trop espaces ou irreguliers`() {
        val transactions = listOf(
            payment("BOUTIQUE", 2_000.0, 0),
            payment("BOUTIQUE", 2_000.0, 5),
            payment("BOUTIQUE", 2_000.0, 40)
        )

        assertTrue(SubscriptionDetector.detect(transactions, now = base + 45 * day).isEmpty())
    }

    @Test
    fun `ignore les montants trop variables`() {
        val transactions = listOf(
            payment("MARCHAND", 5_000.0, 0),
            payment("MARCHAND", 12_000.0, 30),
            payment("MARCHAND", 5_000.0, 60)
        )

        assertTrue(SubscriptionDetector.detect(transactions, now = base + 65 * day).isEmpty())
    }

    @Test
    fun `ignore les transactions non confirmees`() {
        val transactions = listOf(
            payment("SPOTIFY", 3_000.0, 0, TransactionStatus.PENDING_REASON),
            payment("SPOTIFY", 3_000.0, 30, TransactionStatus.PENDING_REASON),
            payment("SPOTIFY", 3_000.0, 60, TransactionStatus.PENDING_REASON)
        )

        assertTrue(SubscriptionDetector.detect(transactions, now = base + 65 * day).isEmpty())
    }

    @Test
    fun `regroupe le meme marchand sans tenir compte de la casse`() {
        val transactions = listOf(
            payment("Netflix", 5_000.0, 0),
            payment("NETFLIX", 5_000.0, 30),
            payment(" netflix ", 5_000.0, 60)
        )

        val result = SubscriptionDetector.detect(transactions, now = base + 65 * day)

        assertEquals(1, result.size)
        assertEquals(3, result.single().occurrences)
    }
}
