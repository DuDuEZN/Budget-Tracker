package com.duduezn.budgettracker.data.parser

import com.duduezn.budgettracker.domain.model.IncomingMessage
import com.duduezn.budgettracker.domain.model.Operator
import com.duduezn.budgettracker.domain.model.ParsedMessage
import com.duduezn.budgettracker.domain.model.TransactionType
import com.duduezn.budgettracker.domain.parser.MessageParser
import java.text.Normalizer
import java.util.Locale

/**
 * Analyse les messages MTN MoMo, Moov Money et Celtiis (Bénin).
 *
 * Le parseur reste volontairement tolérant : les libellés des opérateurs changent
 * souvent. Il exige toutefois un montant en FCFA/XOF et un verbe transactionnel
 * pour éviter de transformer une publicité ou un code OTP en dépense.
 */
class MobileMoneyParser : MessageParser {

    override fun parse(message: IncomingMessage): ParsedMessage? {
        val body = message.body.trim()
        if (body.length < 8) return null

        val searchable = body.normalizeForSearch()
        if (isNonTransactional(searchable)) return null

        val type = detectType(searchable) ?: return null
        val amount = extractAmount(body) ?: return null
        if (amount <= 0.0) return null

        return ParsedMessage(
            rawMessage = body,
            operator = detectOperator("${message.sender.orEmpty()} $body"),
            type = type,
            amount = amount,
            currency = "XOF",
            counterparty = extractCounterparty(body, amount),
            timestamp = message.timestamp,
            rawReason = extractReason(body),
            categoryName = detectCategory(searchable, type)
        )
    }

    private fun isNonTransactional(text: String): Boolean {
        val otpMarkers = listOf(
            "code de confirmation",
            "code secret",
            "mot de passe",
            "ne partagez pas"
        )
        val looksLikeOtp = otpMarkers.any { text.contains(it) }
        val hasMoneyMarker = text.contains("fcfa") || text.contains("xof")
        return looksLikeOtp && !hasMoneyMarker
    }

    private fun detectType(text: String): TransactionType? = when {
        text.containsWord("retrait") ||
            text.containsWord("retire") ||
            text.containsWord("withdraw") -> TransactionType.RETRAIT

        PAYMENT_KEYWORDS.any { text.containsWord(it) } -> TransactionType.PAIEMENT

        DEPOSIT_KEYWORDS.any { text.containsWord(it) } -> TransactionType.DEPOT

        text.containsWord("transfert") ||
            text.containsWord("transfer") ||
            text.containsWord("envoi") -> TransactionType.RETRAIT

        else -> null
    }

    private fun detectOperator(text: String): Operator {
        val normalized = text.normalizeForSearch()
        return when {
            normalized.contains("mtn") || normalized.contains("momo") -> Operator.MTN_MOMO
            normalized.contains("moov") -> Operator.MOOV_MONEY
            normalized.contains("celtiis") || normalized.contains("celtis") -> Operator.CELTIIS
            else -> Operator.UNKNOWN
        }
    }

    private fun extractAmount(body: String): Double? =
        AMOUNT_PATTERN.findAll(body)
            .mapNotNull { match ->
                match.groupValues[1]
                    .replace(" ", "")
                    .replace("\u00A0", "")
                    .replace(".", "")
                    .replace(",", ".")
                    .toDoubleOrNull()
            }
            .firstOrNull { it in 1.0..MAX_AMOUNT }

    private fun extractCounterparty(body: String, amount: Double): String? {
        val amountDigits = amount.toLong().toString()
        val phone = PHONE_PATTERN.findAll(body)
            .map { match -> match.groupValues[1].filter(Char::isDigit) }
            .firstOrNull { digits -> digits.length in 8..12 && digits != amountDigits }
        if (phone != null) return phone

        val parenthesized = PARENTHESIZED_PATTERN.find(body)
            ?.groupValues?.get(1)?.trim()
        if (!parenthesized.isNullOrBlank()) return parenthesized

        return MERCHANT_PATTERN.find(body)
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractReason(body: String): String? =
        MERCHANT_PATTERN.find(body)
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun detectCategory(text: String, type: TransactionType): String? {
        if (type == TransactionType.RETRAIT) return CATEGORY_TRANSFER
        if (type == TransactionType.DEPOT) return CATEGORY_OTHER

        return CATEGORY_KEYWORDS.entries.firstOrNull { (_, keywords) ->
            keywords.any { text.containsWord(it) }
        }?.key
    }

    private fun String.normalizeForSearch(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.FRENCH)

    /** Vérifie la présence d'un mot entier : évite « marche » dans « marchand ». */
    private fun String.containsWord(word: String): Boolean =
        Regex("(?:^|[^a-z0-9])${Regex.escape(word)}(?:[^a-z0-9]|$)")
            .containsMatchIn(this)

    private companion object {
        const val MAX_AMOUNT = 100_000_000.0
        const val CATEGORY_TRANSFER = "Transferts/Retraits"
        const val CATEGORY_OTHER = "Autres"

        val DIACRITICS = Regex("\\p{M}+")

        val AMOUNT_PATTERN = Regex(
            """(?i)(\d{1,3}(?:[ \u00A0.]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)\s*(?:F\s*CFA|FCFA|XOF|CFA|FRANCS?|F\b)"""
        )

        /**
         * Numéros béninois : 10 chiffres commençant par 01 (format actuel),
         * ou 8 chiffres commençant par 4-9 (ancien format), avec indicatif optionnel.
         */
        val PHONE_PATTERN = Regex(
            """(?:(?:\+?229|00229)[\s.-]?)?(01[\s.-]?\d{2}(?:[\s.-]?\d{2}){3}|[4-9]\d(?:[\s.-]?\d{2}){3})"""
        )

        val PARENTHESIZED_PATTERN = Regex("""\(([^)]{3,50})\)""")

        val MERCHANT_PATTERN = Regex("""(?i)\bchez\s+([^.\n]{3,60})""")

        val PAYMENT_KEYWORDS = listOf(
            "paiement", "paye", "achat", "facture", "marchand", "recharge", "abonnement"
        )

        val DEPOSIT_KEYWORDS = listOf(
            "recu", "reception", "versement", "credite", "depot", "receive"
        )

        /** L'ordre compte : les catégories les plus spécifiques d'abord. */
        val CATEGORY_KEYWORDS: Map<String, List<String>> = linkedMapOf(
            "Restauration" to listOf(
                "restaurant", "resto", "cafe", "fast food", "pizza", "burger",
                "maquis", "boulangerie", "patisserie", "snack"
            ),
            "Transport" to listOf(
                "taxi", "zem", "moto", "transport", "carburant", "essence",
                "gasoil", "bus", "peage", "gbaka"
            ),
            "Factures & Services" to listOf(
                "facture", "sbeee", "edf", "soneb", "eau", "canal", "internet",
                "abonnement", "recharge", "electricite", "mairie"
            ),
            "Achats" to listOf(
                "achat", "boutique", "supermarche", "marche", "shop", "store",
                "pharmacie", "quincaillerie"
            ),
            "Épargne" to listOf("epargne", "tontine", "saving"),
            "Transferts/Retraits" to listOf("transfert", "transfer", "envoi")
        )
    }
}
