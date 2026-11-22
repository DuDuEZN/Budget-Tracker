package com.duduezn.budgettracker.data.parser

import com.duduezn.budgettracker.domain.model.IncomingMessage
import com.duduezn.budgettracker.domain.model.Operator
import com.duduezn.budgettracker.domain.model.ParsedMessage
import com.duduezn.budgettracker.domain.model.TransactionType
import com.duduezn.budgettracker.domain.parser.MessageParser
import com.duduezn.budgettracker.domain.util.TextNormalizer

/**
 * Parseur des SMS et notifications Mobile Money béninois.
 *
 * Chaque opérateur possède ses propres expressions régulières, regroupées
 * dans les objets `MtnPatterns`, `BiicPatterns`, etc. Pour ajouter un nouvel
 * opérateur, il suffit d'ajouter un bloc de patterns et une branche dans
 * [detectOperator] / [detectType].
 *
 * Le parseur ne décide jamais de la catégorie : il extrait uniquement les
 * faits présents dans le message. La catégorisation est déléguée à
 * `CategorizationEngine`.
 */
class TransactionParser : MessageParser {

    override fun parse(message: IncomingMessage): ParsedMessage? {
        val body = message.body.trim()
        if (body.length < 8) return null
        if (isNonTransactional(body)) return null

        val operator = detectOperator(message.sender, body)
        val type = detectType(body, operator) ?: return null
        val amount = extractAmount(body) ?: return null
        if (amount <= 0.0) return null

        val reason = extractReason(body)
        val counterparty = extractCounterparty(body, type, operator)

        return ParsedMessage(
            rawMessage = body,
            operator = operator,
            type = type,
            amount = amount,
            currency = "XOF",
            counterparty = counterparty,
            timestamp = message.timestamp,
            rawReason = reason?.value,
            reasonIsExplicit = reason?.explicit == true
        )
    }

    // ---------------------------------------------------------------------
    // Filtrage des messages non financiers
    // ---------------------------------------------------------------------

    private fun isNonTransactional(body: String): Boolean {
        val text = TextNormalizer.normalize(body)

        if (OTP_MARKERS.any { text.contains(it) }) return true

        val hasTransactionSignature = TRANSACTION_SIGNATURES.any { text.contains(it) }
        if (!hasTransactionSignature && PROMO_MARKERS.any { text.contains(it) }) return true

        // Un message uniquement promotionnel avec un lien et aucun verbe métier.
        if (!hasTransactionSignature && text.contains("http")) return true

        return false
    }

    // ---------------------------------------------------------------------
    // Opérateur
    // ---------------------------------------------------------------------

    private fun detectOperator(sender: String?, body: String): Operator {
        val text = TextNormalizer.normalize("${sender.orEmpty()} $body")
        return when {
            text.contains("biic") ||
                (text.contains("montant : xof") && text.contains("desc :")) -> Operator.BIIC

            text.contains("mtn") ||
                text.contains("momo") ||
                text.contains("mobile money") -> Operator.MTN_MOMO

            text.contains("moov") || text.contains("flooz") -> Operator.MOOV_MONEY
            text.contains("celtiis") || text.contains("celtis") -> Operator.CELTIIS
            else -> Operator.UNKNOWN
        }
    }

    // ---------------------------------------------------------------------
    // Type d'opération
    // ---------------------------------------------------------------------

    private fun detectType(body: String, operator: Operator): TransactionType? {
        val text = TextNormalizer.normalize(body)

        if (operator == Operator.BIIC) {
            return detectBiicType(body, text)
        }

        // Dépôt venant d'un compte bancaire (approvisionnement BIIC -> MoMo).
        if (text.contains("baccount") || text.contains("bname:") || text.contains("bfi:")) {
            return TransactionType.DEPOT
        }

        // « Transfert 10000F de X » = entrant ; « Transfert 4000F a Y » = sortant.
        if (INCOMING_TRANSFER.containsMatchIn(body)) return TransactionType.DEPOT
        if (TextNormalizer.containsWord(text, "recu") || TextNormalizer.containsWord(text, "reception") ||
            TextNormalizer.containsWord(text, "versement") || TextNormalizer.containsWord(text, "credite") ||
            TextNormalizer.containsWord(text, "depot") || TextNormalizer.containsWord(text, "receive")
        ) {
            return TransactionType.DEPOT
        }

        if (TextNormalizer.containsWord(text, "retrait") || TextNormalizer.containsWord(text, "retire") ||
            TextNormalizer.containsWord(text, "withdraw") || TextNormalizer.containsWord(text, "dab")
        ) {
            return TransactionType.RETRAIT
        }

        if (PAYMENT_KEYWORDS.any { TextNormalizer.containsWord(text, it) }) {
            return TransactionType.PAIEMENT
        }

        if (TextNormalizer.containsWord(text, "transfert") || TextNormalizer.containsWord(text, "transfer") ||
            TextNormalizer.containsWord(text, "envoi")
        ) {
            return TransactionType.RETRAIT
        }

        if (TextNormalizer.containsWord(text, "debit")) return TransactionType.PAIEMENT
        if (TextNormalizer.containsWord(text, "credit")) return TransactionType.DEPOT

        return null
    }

    private fun detectBiicType(body: String, text: String): TransactionType? {
        val description = extractDescription(body)?.let(TextNormalizer::normalize).orEmpty()

        return when {
            TextNormalizer.containsWord(description, "ret dab") || TextNormalizer.containsWord(description, "retrait") ||
                TextNormalizer.containsWord(description, "dab") -> TransactionType.RETRAIT

            TextNormalizer.containsWord(description, "achat") || TextNormalizer.containsWord(description, "pos") ||
                TextNormalizer.containsWord(description, "paiement") -> TransactionType.PAIEMENT

            TextNormalizer.containsWord(description, "transfrt") || TextNormalizer.containsWord(description, "transfert") ||
                TextNormalizer.containsWord(description, "virement") -> TransactionType.RETRAIT

            TextNormalizer.containsWord(text, "credit") -> TransactionType.DEPOT
            TextNormalizer.containsWord(text, "debit") -> TransactionType.PAIEMENT
            else -> null
        }
    }

    // ---------------------------------------------------------------------
    // Montant
    // ---------------------------------------------------------------------

    private fun extractAmount(body: String): Double? {
        val candidates = buildList {
            PREFIX_AMOUNT.findAll(body).forEach { match ->
                add(AmountCandidate(match.range.first, match.groupValues[1]))
            }
            SUFFIX_AMOUNT.findAll(body).forEach { match ->
                add(AmountCandidate(match.range.first, match.groupValues[1]))
            }
        }.sortedBy { it.index }

        for (candidate in candidates) {
            if (isExcludedAmountContext(body, candidate.index)) continue
            val value = parseAmount(candidate.raw) ?: continue
            if (value in 1.0..MAX_AMOUNT) return value
        }
        return null
    }

    private fun isExcludedAmountContext(body: String, index: Int): Boolean {
        val start = (index - 24).coerceAtLeast(0)
        val context = TextNormalizer.normalize(body.substring(start, index))
        return EXCLUDED_AMOUNT_CONTEXTS.any { context.contains(it) }
    }

    /**
     * Interprète les séparateurs :
     * - « 10.000 » et « 2.796 » -> séparateur de milliers (usage BIIC) ;
     * - « 2,5 » -> décimale ;
     * - « 10 000 » -> milliers.
     */
    private fun parseAmount(raw: String): Double? {
        var value = raw
            .replace(Regex("[\\s\\u00A0']"), "")
            .trim('.', ',', '-', ';', ':')

        if (value.isEmpty()) return null

        val hasDot = value.contains('.')
        val hasComma = value.contains(',')

        value = when {
            hasDot && hasComma -> {
                val lastDot = value.lastIndexOf('.')
                val lastComma = value.lastIndexOf(',')
                if (lastDot > lastComma) {
                    value.replace(",", "")
                } else {
                    value.replace(".", "").replace(',', '.')
                }
            }

            hasDot -> if (THOUSANDS_DOT.matches(value)) value.replace(".", "") else value
            hasComma -> if (THOUSANDS_COMMA.matches(value)) value.replace(",", "") else value.replace(',', '.')
            else -> value
        }

        return value.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    // ---------------------------------------------------------------------
    // Contrepartie
    // ---------------------------------------------------------------------

    private fun extractCounterparty(
        body: String,
        type: TransactionType,
        operator: Operator
    ): String? {
        if (operator == Operator.BIIC) return null

        val flat = body.replace(Regex("[\\s\\u00A0]+"), " ").trim()

        val directional = when (type) {
            TransactionType.DEPOT -> INCOMING_COUNTERPARTY.find(flat)
            TransactionType.PAIEMENT, TransactionType.RETRAIT -> OUTGOING_COUNTERPARTY.find(flat)
        }?.groupValues?.get(1)

        val merchant = MERCHANT_COUNTERPARTY.find(flat)?.groupValues?.get(1)

        val raw = directional ?: merchant ?: PHONE_PATTERN.find(flat)?.value
        return cleanCounterparty(raw)
    }

    private fun cleanCounterparty(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        var value = raw
            .substringBefore('\n')
            .substringBefore('(')
            .substringBefore('.')
            .replace(COUNTERPARTY_STOP, " ")
            .replace(Regex("[\\s\\u00A0]+"), " ")
            .trim(' ', '.', ',', ';', ':', '-', '/')

        value = dedupeRepeatedPhrase(value)

        if (value.length < 2) return null
        if (value.matches(Regex("[-.*\\s]+"))) return null

        val digits = value.filter(Char::isDigit)
        return if (digits.length >= 8 && value.all { it.isDigit() || it in " +-()." }) {
            digits
        } else {
            value
        }
    }

    /** « VITAL KADOUKPE EZIN VITAL KADOUKPE EZIN » -> « VITAL KADOUKPE EZIN ». */
    private fun dedupeRepeatedPhrase(value: String): String {
        val words = value.split(' ').filter { it.isNotBlank() }
        if (words.size >= 4 && words.size % 2 == 0) {
            val half = words.size / 2
            if (words.take(half) == words.drop(half)) {
                return words.take(half).joinToString(" ")
            }
        }
        return value
    }

    // ---------------------------------------------------------------------
    // Motif
    // ---------------------------------------------------------------------

    private fun extractReason(body: String): ReasonMatch? {
        for (pattern in REASON_PATTERNS) {
            val raw = pattern.regex.find(body)?.groupValues?.get(1) ?: continue
            val cleaned = cleanReason(raw) ?: continue
            return ReasonMatch(cleaned, pattern.explicit)
        }
        return null
    }

    private fun extractDescription(body: String): String? =
        DESCRIPTION_PATTERN.find(body)
            ?.groupValues?.get(1)
            ?.let(::cleanReason)

    private fun cleanReason(raw: String): String? {
        var value = raw
            .substringBefore('\n')
            .substringBefore('.')
            .replace(REASON_STOP, " ")
            .replace(Regex("[\\s\\u00A0]+"), " ")
            .trim(' ', '.', ',', ';', ':', '-', '/', '\\')

        if (value.length < 2) return null
        if (value.matches(Regex("[-.*\\s]+"))) return null
        if (value.matches(Regex("[0-9\\s*.,-]+"))) return null

        val normalized = TextNormalizer.normalize(value)
        if (TECHNICAL_REFERENCE.containsMatchIn(normalized)) return null

        return value
    }

    private data class AmountCandidate(val index: Int, val raw: String)

    private data class ReasonMatch(val value: String, val explicit: Boolean)

    private data class ReasonPattern(val regex: Regex, val explicit: Boolean)

    private companion object {
        const val MAX_AMOUNT = 100_000_000.0

        val OTP_MARKERS = listOf(
            "code de confirmation",
            "code secret",
            "mot de passe",
            "ne partagez pas",
            "verification code",
            "votre code est",
            "otp"
        )

        val PROMO_MARKERS = listOf(
            "gagnez",
            "felicitations",
            "promotion",
            "offre speciale",
            "tirage au sort",
            "jeu concours",
            "cliquez ici"
        )

        val TRANSACTION_SIGNATURES = listOf(
            "retrait", "paiement", "transfert", "depot", "debit", "credit",
            "montant", "solde", "recu", "versement"
        )

        val PAYMENT_KEYWORDS = listOf(
            "paiement", "paye", "achat", "facture", "marchand", "recharge",
            "abonnement", "buy data", "airtime"
        )

        /** « Transfert 10000F de X » : le « de » suit le montant. */
        val INCOMING_TRANSFER = Regex(
            "(?i)\\btransfert\\s+[0-9][0-9\\s\\u00A0.,]*\\s*(?:F\\s*CFA|FCFA|XOF|CFA|F)?\\s+de\\s+"
        )

        val PREFIX_AMOUNT = Regex(
            "(?i)(?:XOF|FCFA|CFA)\\s*([0-9][0-9\\s\\u00A0.,]*)"
        )

        val SUFFIX_AMOUNT = Regex(
            "(?i)([0-9][0-9\\s\\u00A0.,]*)\\s*(?:F\\s*CFA|FCFA|XOF|CFA|FRANCS?|F)\\b"
        )

        val EXCLUDED_AMOUNT_CONTEXTS = listOf(
            "frais", "solde", "balance", "compte", "id :", "id:", "reference", "ref :"
        )

        val THOUSANDS_DOT = Regex("\\d{1,3}(?:\\.\\d{3})+")
        val THOUSANDS_COMMA = Regex("\\d{1,3}(?:,\\d{3})+")

        val OUTGOING_COUNTERPARTY = Regex(
            "(?i)\\b(?:transfert|paiement|retrait|envoi|depot)\\b[^0-9]{0,30}" +
                "[0-9][0-9\\s\\u00A0.,]*\\s*(?:F\\s*CFA|FCFA|XOF|CFA|F)?" +
                "\\s+(?:a|au|chez|via)\\s+([^\\n]{3,90})"
        )

        val INCOMING_COUNTERPARTY = Regex(
            "(?i)\\b(?:transfert|depot|recu|reception|versement)\\b[^\\n]{0,70}?" +
                "\\bde\\s+([^\\n]{3,90})"
        )

        val MERCHANT_COUNTERPARTY = Regex(
            "(?i)\\bchez\\s+([^\\n]{3,70})"
        )

        val PHONE_PATTERN = Regex(
            "(?:(?:\\+?229|00229)[\\s.-]?)?(01[\\s.-]?\\d{2}(?:[\\s.-]?\\d{2}){3}|[4-9]\\d(?:[\\s.-]?\\d{2}){3})"
        )

        val COUNTERPARTY_STOP = Regex(
            "(?i)\\s+(?:(?:ref|solde|frais|id|date|compte|montant|desc|baccount|bname|bfi|" +
                "nouvelle app|http|tel|ifu)\\b|\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4}|\\d{2}:\\d{2}).*$"
        )

        val DESCRIPTION_PATTERN = Regex("(?i)\\bdesc(?:ription)?\\s*[:\\-]\\s*([^\\n]{2,90})")

        val REASON_PATTERNS = listOf(
            ReasonPattern(Regex("(?i)\\bmotif\\s*[:\\-]\\s*([^\\n]{2,90})"), explicit = true),
            ReasonPattern(Regex("(?i)\\braison\\s*[:\\-]\\s*([^\\n]{2,90})"), explicit = true),
            ReasonPattern(Regex("(?i)\\blibelle\\s*[:\\-]\\s*([^\\n]{2,90})"), explicit = true),
            ReasonPattern(Regex("(?i)\\bobjet\\s*[:\\-]\\s*([^\\n]{2,90})"), explicit = true),
            ReasonPattern(Regex("(?i)\\bref(?:erence)?\\s*[:\\-]\\s*([^\\n]{1,90})"), explicit = true),
            ReasonPattern(DESCRIPTION_PATTERN, explicit = false)
        )

        val REASON_STOP = Regex(
            "(?i)\\s+(?:id|solde|frais|date|compte|montant|nouvelle app|http|baccount|bname|bfi)\\b.*$"
        )

        /** Références purement techniques qui ne constituent pas un motif utilisateur. */
        val TECHNICAL_REFERENCE = Regex(
            "baccount|bname|bfi|iban|account|bic|nouvelle app|https?|montant|solde|frais|compte"
        )
    }
}
