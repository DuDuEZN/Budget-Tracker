package com.duduezn.budgettracker.domain.categorization

import com.duduezn.budgettracker.domain.model.Operator
import com.duduezn.budgettracker.domain.model.ParsedMessage
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizationEngineTest {

    private val engine = CategorizationEngine()

    private fun message(
        type: TransactionType,
        counterparty: String? = null,
        reason: String? = null,
        explicit: Boolean = false
    ) = ParsedMessage(
        rawMessage = "message",
        operator = Operator.MTN_MOMO,
        type = type,
        amount = 1_000.0,
        counterparty = counterparty,
        timestamp = 1_700_000_000_000L,
        rawReason = reason,
        reasonIsExplicit = explicit
    )

    @Test
    fun `etape 1 - regle apprise confirme immediatement`() {
        val result = engine.categorize(
            message(TransactionType.RETRAIT, counterparty = "HOUEFA GINA"),
            LearnedRule("HOUEFA GINA", "Tontine", "Épargne")
        )

        assertEquals("Épargne", result.categoryName)
        assertEquals("Tontine", result.finalReason)
        assertEquals(TransactionStatus.CONFIRMED, result.status)
        assertEquals(CategorizationSource.LEARNED_RULE, result.source)
    }

    @Test
    fun `etape 2 - motif explicite taxi confirme transport`() {
        val result = engine.categorize(
            message(TransactionType.RETRAIT, reason = "Taxi", explicit = true)
        )

        assertEquals("Transport", result.categoryName)
        assertEquals(TransactionStatus.CONFIRMED, result.status)
        assertEquals(CategorizationSource.REASON_KEYWORD, result.source)
    }

    @Test
    fun `etape 2 - marchand SBEE confirme factures`() {
        val result = engine.categorize(
            message(TransactionType.PAIEMENT, counterparty = "MFS DIRECT SBEE")
        )

        assertEquals("Factures & Services", result.categoryName)
        assertEquals(TransactionStatus.CONFIRMED, result.status)
        assertEquals(CategorizationSource.MERCHANT_KEYWORD, result.source)
    }

    @Test
    fun `etape 2 - BUY DATA confirme factures`() {
        val result = engine.categorize(
            message(TransactionType.PAIEMENT, counterparty = "BUY DATA")
        )

        assertEquals("Factures & Services", result.categoryName)
        assertEquals(TransactionStatus.CONFIRMED, result.status)
    }

    @Test
    fun `etape 3 - retrait cash sans motif reste en attente`() {
        val result = engine.categorize(
            message(TransactionType.RETRAIT, counterparty = "LES MERVEILLES DE SEGNISSE")
        )

        assertEquals("Transferts/Retraits", result.categoryName)
        assertEquals(TransactionStatus.PENDING_REASON, result.status)
    }

    @Test
    fun `retrait avec motif explicite Gina confirme transferts`() {
        val result = engine.categorize(
            message(TransactionType.RETRAIT, reason = "Gina", explicit = true)
        )

        assertEquals("Transferts/Retraits", result.categoryName)
        assertEquals(TransactionStatus.CONFIRMED, result.status)
    }

    @Test
    fun `description BIIC ACHAT POS suggere achats mais reste en attente`() {
        val result = engine.categorize(
            message(
                TransactionType.PAIEMENT,
                reason = "ACHAT POS/RESERVATION DE FONDS",
                explicit = false
            )
        )

        assertEquals("Achats", result.categoryName)
        assertEquals(TransactionStatus.PENDING_REASON, result.status)
    }

    @Test
    fun `description BIIC TRANSFRT suggere transferts mais reste en attente`() {
        val result = engine.categorize(
            message(
                TransactionType.RETRAIT,
                reason = "TRANSFRT NATION AUT BQE NORMAL",
                explicit = false
            )
        )

        assertEquals("Transferts/Retraits", result.categoryName)
        assertEquals(TransactionStatus.PENDING_REASON, result.status)
    }

    @Test
    fun `depot sans motif confirme autres sans relance`() {
        val result = engine.categorize(
            message(TransactionType.DEPOT, counterparty = "VITAL KADOUKPE EZIN")
        )

        assertEquals("Autres", result.categoryName)
        assertEquals(TransactionStatus.CONFIRMED, result.status)
    }
}
