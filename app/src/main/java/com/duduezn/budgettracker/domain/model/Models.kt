package com.duduezn.budgettracker.domain.model

/** Opérateurs Mobile Money pris en charge au Bénin. */
enum class Operator(val label: String) {
    MTN_MOMO("MTN MoMo"),
    MOOV_MONEY("Moov Money"),
    CELTIIS("Celtiis"),
    UNKNOWN("Inconnu")
}

/** Nature du mouvement financier. */
enum class TransactionType(val label: String) {
    DEPOT("Dépôt"),
    RETRAIT("Retrait"),
    PAIEMENT("Paiement")
}

/**
 * Cycle de vie d'une transaction :
 * - PENDING_REASON : le motif (et donc la catégorie) reste à préciser par l'utilisateur.
 * - CONFIRMED : le motif est reconnu (règle marchand, catégorie détectée ou saisie manuelle).
 */
enum class TransactionStatus(val label: String) {
    PENDING_REASON("À préciser"),
    CONFIRMED("Confirmé")
}

/** Origine de la détection : SMS ou notification système. */
enum class MessageSource {
    SMS,
    NOTIFICATION
}

data class Transaction(
    val id: Long,
    val rawMessage: String,
    val operator: Operator,
    val type: TransactionType,
    val amount: Double,
    val currency: String,
    val counterparty: String?,
    val timestamp: Long,
    val rawReason: String?,
    val detectedCategory: Long?,
    val finalReason: String?,
    val status: TransactionStatus
)

data class Category(
    val id: Long,
    val name: String,
    val monthlyBudgetLimit: Double,
    val colorHex: String,
    val iconName: String
)

data class MerchantRule(
    val id: Long,
    val counterpartyIdentifier: String,
    val defaultReason: String,
    val categoryId: Long,
    val usageCount: Int
)

/** Message brut reçu d'un SMS ou d'une notification. */
data class IncomingMessage(
    val body: String,
    val sender: String?,
    val timestamp: Long,
    val source: MessageSource
)

/** Résultat normalisé de l'analyse d'un message Mobile Money. */
data class ParsedMessage(
    val rawMessage: String,
    val operator: Operator,
    val type: TransactionType,
    val amount: Double,
    val currency: String = "XOF",
    val counterparty: String?,
    val timestamp: Long,
    val rawReason: String?,
    val categoryName: String?
)

/** Résultat de l'ingestion d'un message dans la base locale. */
sealed interface IngestResult {
    data class Inserted(
        val transactionId: Long,
        val needsReason: Boolean
    ) : IngestResult

    data object Duplicate : IngestResult

    data object Ignored : IngestResult
}
