package com.duduezn.budgettracker.data.parser

import com.duduezn.budgettracker.domain.model.IncomingMessage
import com.duduezn.budgettracker.domain.model.MessageSource
import com.duduezn.budgettracker.domain.model.Operator
import com.duduezn.budgettracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cas réels fournis par l'utilisateur (MTN MoMo, Moov, Celtiis, BIIC).
 * Le parseur doit extraire les faits et ignorer tout message non financier.
 */
class TransactionParserTest {

    private val parser = TransactionParser()

    private fun parse(body: String, sender: String = "MTN") = parser.parse(
        IncomingMessage(
            body = body,
            sender = sender,
            timestamp = 1_700_000_000_000L,
            source = MessageSource.SMS
        )
    )

    // ------------------------------------------------------------------
    // MTN MoMo
    // ------------------------------------------------------------------

    @Test
    fun `depot MTN depuis BIIC par PI-SPI`() {
        val parsed = requireNotNull(
            parse(
                """
                Transfert 10000F de VITAL KADOUKPE EZIN
                VITAL KADOUKPE EZIN 2026-11-21 16:30:04
                Ref:BAccount:BJ66BJ18501100***********
                9,BFI:BJB011,BName:VITAL KADOUKPE
                DIEUDONNEEZIN Solde: *****F ID:13429733003.
                """.trimIndent()
            )
        )

        assertEquals(Operator.MTN_MOMO, parsed.operator)
        assertEquals(TransactionType.DEPOT, parsed.type)
        assertEquals(10_000.0, parsed.amount, 0.01)
        assertEquals("VITAL KADOUKPE EZIN", parsed.counterparty)
        assertNull(parsed.rawReason)
        assertFalse(parsed.reasonIsExplicit)
    }

    @Test
    fun `paiement SBEE sans reference`() {
        val parsed = requireNotNull(
            parse(
                """
                1. Paiement 5000F a MFS DIRECT SBEE
                2026-11-21 16:33:09 Frais:50F Solde: **** F
                ID:13429757965 Ref :-. Nouvelle App MoMo
                https://onelink.to/va7cnt
                """.trimIndent()
            )
        )

        assertEquals(Operator.MTN_MOMO, parsed.operator)
        assertEquals(TransactionType.PAIEMENT, parsed.type)
        assertEquals(5_000.0, parsed.amount, 0.01)
        assertEquals("MFS DIRECT SBEE", parsed.counterparty)
        assertNull(parsed.rawReason)
    }

    @Test
    fun `transfert Gina avec motif explicite`() {
        val parsed = requireNotNull(
            parse(
                """
                1. Transfert 4000F a HOUEFA GINA MAHUSSE
                ABOUTA(2290152380480) 2026-11-21 20:28:50
                Frais: 50F Solde:912F Ref: Gina ID: 13432144364.Nouvelle App MoMo
                https://onelink.to/va7cnt
                """.trimIndent()
            )
        )

        assertEquals(TransactionType.RETRAIT, parsed.type)
        assertEquals(4_000.0, parsed.amount, 0.01)
        assertEquals("HOUEFA GINA MAHUSSE ABOUTA", parsed.counterparty)
        assertEquals("Gina", parsed.rawReason)
        assertTrue(parsed.reasonIsExplicit)
    }

    @Test
    fun `retrait agent sans motif`() {
        val parsed = requireNotNull(
            parse(
                """
                1. Retrait 10000F via LES MERVEILLES DE
                SEGNISSE (IFU) TOKAN
                AITCHEDJI(2290151771206 - RBCCM ABC 18A
                8534) 2026-11-20 17:54:55 Solde: ** F Frais:225F
                ID:13421350399.Nouvelle App MoMo
                https://onelink.to/va7cnt
                """.trimIndent()
            )
        )

        assertEquals(TransactionType.RETRAIT, parsed.type)
        assertEquals(10_000.0, parsed.amount, 0.01)
        assertEquals("LES MERVEILLES DE SEGNISSE", parsed.counterparty)
        assertNull(parsed.rawReason)
        assertFalse(parsed.reasonIsExplicit)
    }

    @Test
    fun `second retrait agent sans motif`() {
        val parsed = requireNotNull(
            parse(
                """
                2. Retrait 6000F via OFFICIEL BOKO (IFU)
                GEST1(2290169323693 - BOKO COMLAN
                VIANNEY PASCAL) 2026-11-18 19:00:07
                Solde:637F Frais:225F ID:13404914379.Nouvelle
                App MoMo https://onelink.to/va7cnt
                """.trimIndent()
            )
        )

        assertEquals(TransactionType.RETRAIT, parsed.type)
        assertEquals(6_000.0, parsed.amount, 0.01)
        assertEquals("OFFICIEL BOKO", parsed.counterparty)
        assertNull(parsed.rawReason)
    }

    @Test
    fun `achat de data BUY DATA 100F`() {
        val parsed = requireNotNull(
            parse(
                """
                1. Paiement 100F a BUY DATA 2026-11-17 21:57:37
                Frais:OF Solde: **** F ID:13398483244
                Ref :-. Nouvelle App MoMo
                https://onelink.to/va7cnt
                """.trimIndent()
            )
        )

        assertEquals(TransactionType.PAIEMENT, parsed.type)
        assertEquals(100.0, parsed.amount, 0.01)
        assertEquals("BUY DATA", parsed.counterparty)
        assertNull(parsed.rawReason)
    }

    @Test
    fun `achat de data BUY DATA 200F`() {
        val parsed = requireNotNull(
            parse(
                """
                2. Paiement 200F a BUY DATA 2026-11-17 13:06:17
                Frais:OF Solde: **** F ID:13393675058
                Ref :-. Nouvelle App MoMo
                https://onelink.to/va7cnt
                """.trimIndent()
            )
        )

        assertEquals(TransactionType.PAIEMENT, parsed.type)
        assertEquals(200.0, parsed.amount, 0.01)
        assertEquals("BUY DATA", parsed.counterparty)
    }

    @Test
    fun `depot recu avec nom entre parentheses`() {
        val parsed = requireNotNull(
            parse(
                "Vous avez recu 25 000 FCFA de 0197000000 (KOUASSI JEAN). " +
                    "Votre nouveau solde est de 45 000 FCFA."
            )
        )

        assertEquals(TransactionType.DEPOT, parsed.type)
        assertEquals(25_000.0, parsed.amount, 0.01)
        assertEquals("0197000000", parsed.counterparty)
    }

    @Test
    fun `transfert sortant vers un numero`() {
        val parsed = requireNotNull(
            parse(
                "Transfert de 5 000 FCFA effectue au 0197000000. " +
                    "Nouveau solde 1 000 FCFA.",
                sender = "Moov"
            )
        )

        assertEquals(Operator.MOOV_MONEY, parsed.operator)
        assertEquals(TransactionType.RETRAIT, parsed.type)
        assertEquals(5_000.0, parsed.amount, 0.01)
        assertEquals("0197000000", parsed.counterparty)
    }

    @Test
    fun `motif explicite prioritaire sur le marchand`() {
        val parsed = requireNotNull(
            parse(
                "Paiement de 3 000 FCFA. Motif: Facture SBEE. Merci.",
                sender = "MoMo"
            )
        )

        assertEquals(TransactionType.PAIEMENT, parsed.type)
        assertEquals(3_000.0, parsed.amount, 0.01)
        assertEquals("Facture SBEE", parsed.rawReason)
        assertTrue(parsed.reasonIsExplicit)
    }

    @Test
    fun `paiement marchand restaurant`() {
        val parsed = requireNotNull(
            parse(
                "Paiement marchand de 2 500 FCFA chez RESTAURANT LE BAOBAB. Merci.",
                sender = "MoMo"
            )
        )

        assertEquals(TransactionType.PAIEMENT, parsed.type)
        assertEquals(2_500.0, parsed.amount, 0.01)
        assertEquals("RESTAURANT LE BAOBAB", parsed.counterparty)
    }

    @Test
    fun `paiement Celtiis chez un commercant`() {
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
        assertEquals("BOUTIQUE ALPHA", parsed.counterparty)
    }

    @Test
    fun `retrait Moov avec motif taxi`() {
        val parsed = requireNotNull(
            parse(
                "Retrait de 10 000 FCFA effectue. Motif: Taxi. " +
                    "Nouveau solde: 5 000 FCFA.",
                sender = "Moov Money"
            )
        )

        assertEquals(Operator.MOOV_MONEY, parsed.operator)
        assertEquals(TransactionType.RETRAIT, parsed.type)
        assertEquals(10_000.0, parsed.amount, 0.01)
        assertEquals("Taxi", parsed.rawReason)
        assertTrue(parsed.reasonIsExplicit)
    }

    // ------------------------------------------------------------------
    // BIIC
    // ------------------------------------------------------------------

    @Test
    fun `BIIC retrait DAB montant masque ignore`() {
        assertNull(
            parse(
                """
                DEBIT
                Montant : XOF *****
                Compte : 007 ***** 501
                Desc : RET DAB /RESERVATION DE FONDS
                Date : 08/11/2026/22:17
                Solde : XOF ******
                """.trimIndent(),
                sender = "BIIC"
            )
        )
    }

    @Test
    fun `BIIC retrait DAB montant masque avec points ignore`() {
        assertNull(
            parse(
                """
                DEBIT
                Montant : XOF ***.***
                Compte : 007 ***** 501
                Desc : RET DAB /RESERVATION DE FONDS
                Date : 21/11/2026/18:29
                Solde : XOF ***.***
                """.trimIndent(),
                sender = "BIIC"
            )
        )
    }

    @Test
    fun `BIIC achat POS en ligne`() {
        val parsed = requireNotNull(
            parse(
                """
                DEBIT
                Montant : XOF2.796
                Compte : 007 ***** 501
                Desc : ACHAT POS/RESERVATION DE FONDS
                Date : 22/11/2026/12:25
                Solde : XOF ***.***
                """.trimIndent(),
                sender = "BIIC"
            )
        )

        assertEquals(Operator.BIIC, parsed.operator)
        assertEquals(TransactionType.PAIEMENT, parsed.type)
        assertEquals(2_796.0, parsed.amount, 0.01)
        assertNull(parsed.counterparty)
        assertEquals("ACHAT POS/RESERVATION DE FONDS", parsed.rawReason)
        assertFalse(parsed.reasonIsExplicit)
    }

    @Test
    fun `BIIC achat POS 866F`() {
        val parsed = requireNotNull(
            parse(
                """
                DEBIT
                Montant : XOF866
                Compte : 007 ***** 501
                Desc : ACHAT POS/RESERVATION DE FONDS
                Date : 14/11/2026/10:53
                Solde : XOF **.***
                """.trimIndent(),
                sender = "BIIC"
            )
        )

        assertEquals(TransactionType.PAIEMENT, parsed.type)
        assertEquals(866.0, parsed.amount, 0.01)
    }

    @Test
    fun `BIIC transfert national`() {
        val parsed = requireNotNull(
            parse(
                """
                DEBIT
                Montant : XOF10.000
                Compte : 007 ***** 501
                Desc : TRANSFRT NATION AUT BQE NORMAL
                Date : 21/11/2026/16:30
                Solde : XOF ***.***
                """.trimIndent(),
                sender = "BIIC"
            )
        )

        assertEquals(TransactionType.RETRAIT, parsed.type)
        assertEquals(10_000.0, parsed.amount, 0.01)
        assertEquals("TRANSFRT NATION AUT BQE NORMAL", parsed.rawReason)
        assertFalse(parsed.reasonIsExplicit)
    }

    @Test
    fun `BIIC second transfert national`() {
        val parsed = requireNotNull(
            parse(
                """
                DEBIT
                Montant : XOF10.000
                Compte : 007 ***** 501
                Desc : TRANSFRT NATION AUT BQE NORMAL
                Date : 19/11/2026/11:04
                Solde : XOF ***
                """.trimIndent(),
                sender = "BIIC"
            )
        )

        assertEquals(TransactionType.RETRAIT, parsed.type)
        assertEquals(10_000.0, parsed.amount, 0.01)
    }

    // ------------------------------------------------------------------
    // Messages non financiers
    // ------------------------------------------------------------------

    @Test
    fun `code OTP ignore`() {
        assertNull(
            parse("Votre code de confirmation est 123456. Ne le partagez pas.")
        )
    }

    @Test
    fun `promotion ignoree`() {
        assertNull(
            parse("Gagnez 50 000 FCFA avec MoMo. Cliquez ici pour participer.")
        )
    }

    @Test
    fun `message sans montant ignore`() {
        assertNull(parse("Bienvenue sur MTN MoMo. Votre solde est disponible."))
    }
}
