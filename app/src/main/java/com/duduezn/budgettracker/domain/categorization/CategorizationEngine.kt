package com.duduezn.budgettracker.domain.categorization

import com.duduezn.budgettracker.domain.model.ParsedMessage
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType
import com.duduezn.budgettracker.domain.util.TextNormalizer

/** Règle marchand déjà validée par l'utilisateur (étape 1). */
data class LearnedRule(
    val counterpartyIdentifier: String,
    val defaultReason: String,
    val categoryName: String
)

/** Origine de la décision, utile pour expliquer une catégorie dans l'UI. */
enum class CategorizationSource {
    LEARNED_RULE,
    REASON_KEYWORD,
    MERCHANT_KEYWORD,
    TYPE_DEFAULT,
    NONE
}

data class CategorizationResult(
    val categoryName: String?,
    val finalReason: String?,
    val status: TransactionStatus,
    val source: CategorizationSource,
    val confidence: Int
)

/**
 * Moteur hybride, 100 % local, exécuté dans l'ordre suivant :
 *
 * 1. règle marchand apprise (contrepartie déjà confirmée par l'utilisateur) ;
 * 2. dictionnaire de mots-clés pondérés sur le motif explicite puis sur le marchand ;
 * 3. relance utilisateur (`PENDING_REASON`) quand le motif reste inconnu.
 *
 * Aucune dépendance Android : la classe est testable en JUnit pur.
 */
class CategorizationEngine(
    private val dictionary: Map<String, List<WeightedKeyword>> = DEFAULT_DICTIONARY
) {

    /** Suggestion de catégorie à partir d'un motif libre (réponse notification). */
    fun suggestCategory(text: String): String? = bestMatch(text)?.category

    fun categorize(parsed: ParsedMessage, learnedRule: LearnedRule? = null): CategorizationResult {
        // Étape 1 : une règle apprise prime toujours sur les heuristiques.
        if (learnedRule != null && learnedRule.categoryName.isNotBlank()) {
            return CategorizationResult(
                categoryName = learnedRule.categoryName,
                finalReason = learnedRule.defaultReason,
                status = TransactionStatus.CONFIRMED,
                source = CategorizationSource.LEARNED_RULE,
                confidence = 100
            )
        }

        val explicitReason = parsed.rawReason?.takeIf { parsed.reasonIsExplicit && it.isNotBlank() }
        val descriptiveReason = parsed.rawReason?.takeIf { !parsed.reasonIsExplicit && it.isNotBlank() }

        // Étape 2a : le motif saisi par l'utilisateur est la meilleure source.
        val reasonMatch = bestMatch(explicitReason.orEmpty())
        if (explicitReason != null && reasonMatch != null) {
            return confirmed(reasonMatch, explicitReason, CategorizationSource.REASON_KEYWORD)
        }

        // Étape 2b : le nom du marchand confirme un paiement (SBEE, BUY DATA, resto…).
        val merchantMatch = bestMatch(parsed.counterparty.orEmpty())
        if (merchantMatch != null && parsed.type == TransactionType.PAIEMENT) {
            return confirmed(
                merchantMatch,
                explicitReason ?: parsed.counterparty,
                CategorizationSource.MERCHANT_KEYWORD
            )
        }

        // Étape 2c : un motif explicite sans mot-clé reste néanmoins une information sûre.
        if (explicitReason != null) {
            return when (parsed.type) {
                TransactionType.RETRAIT -> CategorizationResult(
                    categoryName = CATEGORY_TRANSFER,
                    finalReason = explicitReason,
                    status = TransactionStatus.CONFIRMED,
                    source = CategorizationSource.REASON_KEYWORD,
                    confidence = 60
                )

                TransactionType.PAIEMENT, TransactionType.DEPOT -> CategorizationResult(
                    categoryName = CATEGORY_OTHER,
                    finalReason = explicitReason,
                    status = TransactionStatus.CONFIRMED,
                    source = CategorizationSource.REASON_KEYWORD,
                    confidence = 50
                )
            }
        }

        // Étape 2d : description bancaire (« ACHAT POS », « RET DAB ») = simple suggestion.
        val descriptiveMatch = bestMatch(descriptiveReason.orEmpty())
        if (descriptiveMatch != null) {
            return pending(descriptiveMatch, descriptiveReason)
        }

        // Étape 3 : décision de relance.
        return when (parsed.type) {
            TransactionType.RETRAIT -> pending(
                CategoryScore(CATEGORY_TRANSFER, 20),
                descriptiveReason
            )

            TransactionType.PAIEMENT -> pending(null, descriptiveReason)

            // Un dépôt n'est pas une dépense : on évite de polluer la file de relance.
            TransactionType.DEPOT -> CategorizationResult(
                categoryName = CATEGORY_OTHER,
                finalReason = "Dépôt automatique",
                status = TransactionStatus.CONFIRMED,
                source = CategorizationSource.TYPE_DEFAULT,
                confidence = 30
            )
        }
    }

    private fun confirmed(
        match: CategoryScore,
        reason: String?,
        source: CategorizationSource
    ): CategorizationResult = CategorizationResult(
        categoryName = match.category,
        finalReason = reason,
        status = TransactionStatus.CONFIRMED,
        source = source,
        confidence = match.score
    )

    private fun pending(match: CategoryScore?, reason: String?): CategorizationResult =
        CategorizationResult(
            categoryName = match?.category,
            finalReason = reason,
            status = TransactionStatus.PENDING_REASON,
            source = if (match == null) CategorizationSource.NONE else CategorizationSource.TYPE_DEFAULT,
            confidence = match?.score ?: 0
        )

    private fun bestMatch(text: String): CategoryScore? {
        if (text.isBlank()) return null
        val normalized = TextNormalizer.normalize(text)

        return dictionary.mapNotNull { (category, keywords) ->
            val matched = keywords.filter { TextNormalizer.containsWord(normalized, it.keyword) }
            if (matched.isEmpty()) {
                null
            } else {
                // Le meilleur mot-clé porte le score, les suivants ajoutent un petit bonus.
                CategoryScore(category, matched.maxOf { it.weight } + matched.size - 1)
            }
        }
            .filter { it.score >= MIN_SCORE }
            .maxByOrNull { it.score }
    }

    data class CategoryScore(val category: String, val score: Int)

    data class WeightedKeyword(val keyword: String, val weight: Int)

    companion object {
        const val CATEGORY_TRANSFER = "Transferts/Retraits"
        const val CATEGORY_OTHER = "Autres"
        private const val MIN_SCORE = 2

        /**
         * Dictionnaire volontairement simple à étendre : ajouter une entrée ici
         * suffit pour prendre en charge un nouveau marchand ou un nouveau motif.
         */
        val DEFAULT_DICTIONARY: Map<String, List<WeightedKeyword>> = linkedMapOf(
            "Restauration" to listOf(
                WeightedKeyword("restaurant", 4),
                WeightedKeyword("resto", 4),
                WeightedKeyword("maquis", 5),
                WeightedKeyword("cafe", 3),
                WeightedKeyword("fast food", 4),
                WeightedKeyword("pizza", 3),
                WeightedKeyword("burger", 3),
                WeightedKeyword("boulangerie", 3),
                WeightedKeyword("patisserie", 3),
                WeightedKeyword("snack", 3)
            ),
            "Transport" to listOf(
                WeightedKeyword("taxi", 5),
                WeightedKeyword("zem", 5),
                WeightedKeyword("zemi", 5),
                WeightedKeyword("moto", 4),
                WeightedKeyword("carburant", 5),
                WeightedKeyword("essence", 5),
                WeightedKeyword("gasoil", 5),
                WeightedKeyword("peage", 4),
                WeightedKeyword("gbaka", 5),
                WeightedKeyword("transport", 3)
            ),
            "Factures & Services" to listOf(
                WeightedKeyword("facture", 4),
                WeightedKeyword("sbee", 5),
                WeightedKeyword("soneb", 5),
                WeightedKeyword("mfs direct", 4),
                WeightedKeyword("canal", 3),
                WeightedKeyword("internet", 4),
                WeightedKeyword("abonnement", 3),
                WeightedKeyword("recharge", 3),
                WeightedKeyword("electricite", 4),
                WeightedKeyword("mairie", 3),
                WeightedKeyword("buy data", 5),
                WeightedKeyword("airtime", 4),
                WeightedKeyword("data", 3),
                WeightedKeyword("forfait", 3)
            ),
            "Achats" to listOf(
                WeightedKeyword("achat pos", 5),
                WeightedKeyword("achat", 4),
                WeightedKeyword("boutique", 4),
                WeightedKeyword("supermarche", 4),
                WeightedKeyword("pharmacie", 4),
                WeightedKeyword("quincaillerie", 4),
                WeightedKeyword("marche", 3),
                WeightedKeyword("shop", 3),
                WeightedKeyword("store", 3)
            ),
            "Épargne" to listOf(
                WeightedKeyword("epargne", 5),
                WeightedKeyword("tontine", 5),
                WeightedKeyword("saving", 3)
            ),
            CATEGORY_TRANSFER to listOf(
                WeightedKeyword("ret dab", 5),
                WeightedKeyword("dab", 4),
                WeightedKeyword("retrait", 4),
                WeightedKeyword("transfert", 4),
                WeightedKeyword("transfer", 4),
                WeightedKeyword("transfrt", 4),
                WeightedKeyword("envoi", 4),
                WeightedKeyword("reservation de fonds", 2)
            )
        )
    }
}
