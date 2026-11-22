package com.duduezn.budgettracker.data.parser

import com.duduezn.budgettracker.domain.model.IncomingMessage
import com.duduezn.budgettracker.domain.model.MessageSource
import com.duduezn.budgettracker.domain.model.Operator
import com.duduezn.budgettracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileMoneyParserTest {

    private val parser = MobileMoneyParser()

    private fun parse(body: String, sender: String = "MTN") = parser.parse(
        IncomingMessage(
            body = body,
            sender = sender,
            timestamp = 1_700_000_000_000L,
            source = MessageSource.SMS
        )
    )

    @Test
    fun `depot MTN avec nom entre parentheses`() {
        val parsed = requireNotNull(
            parse(
                "Vous avez recu 25 000 FCFA de 0197000000 (KOUASSI JEAN). " +
                    "Votre nouveau solde est de 45 000 FCFA."
            )
        )

        assertEquals(Operator.MTN_MOMO, parsed.operator)
        assertEquals(TransactionType.DEPOT, parsed.type)
        assertEquals(25_000.0, parsed.amount, 0.01)
        assertEquals("0197000000", parsed.counterparty)
        assertEquals("Autres", parsed.categoryName)
    }

    @Test
    fun `retrait Moov classe en transferts`() {
        val parsed = requireNotNull(
            parse(
                "Retrait de 10 000 FCFA effectue. Frais: 100 FCFA. " +
                    "Nouveau solde: 5 000 FCFA.",
                sender = "Moov Money"
            )
        )

        assertEquals(Operator.MOOV_MONEY, parsed.operator)
        assertEquals(TransactionType.RETRAIT, parsed.type)
        assertEquals(10_000.0, parsed.amount, 0.01)
        assertEquals("Transferts/Retraits", parsed.categoryName)
    }

    @Test
    fun `paiement marchand restaurant detecte`() {
        val parsed = requireNotNull(
            parse(
                "Paiement marchand de 2 500 FCFA chez RESTAURANT LE BAOBAB. Merci.",
                sender = "MoMo"
            )
        )

        assertEquals(Operator.MTN_MOMO, parsed.operator)
        assertEquals(TransactionType.PAIEMENT, parsed.type)
        assertEquals(2_500.0, parsed.amount, 0.01)
        assertEquals("RESTAURANT LE BAOBAB", parsed.counterparty)
        assertEquals("Restauration", parsed.categoryName)
    }

    @Test
    fun `paiement boutique Celtiis detecte`() {
        val parsed = requireNotNull(
            parse(
                "Celtiis Money: vous avez effectue un paiement de 1 000 FCFA " +
                    "chez BOUTIQUE ALPHA.",
                sender = "Celtiis"
            )
        )

        assertEquals(Operator.CELTIIS, parsed.operator)
        assertEquals(TransactionType.PAIEMENT, parsed.type)
        assertEquals(1_000.0, parsed.amount, 0.01)
        assertEquals("Achats", parsed.categoryName)
    }

    @Test
    fun `transfert sortant avec numero beneficiaire`() {
        val parsed = requireNotNull(
            parse(
                "Transfert de 5 000 FCFA effectue au 0197000000. " +
                    "Nouveau solde 1 000 FCFA.",
                sender = "Moov"
            )
        )

        assertEquals(TransactionType.RETRAIT, parsed.type)
        assertEquals(5_000.0, parsed.amount, 0.01)
        assertEquals("0197000000", parsed.counterparty)
        assertEquals("Transferts/Retraits", parsed.categoryName)
    }

    @Test
    fun `code OTP ignore`() {
        assertNull(
            parse("Votre code de confirmation est 123456. Ne le partagez pas.")
        )
    }

    @Test
    fun `message sans montant ignore`() {
        assertNull(parse("Bienvenue sur MTN MoMo. Votre solde est disponible."))
    }
}
