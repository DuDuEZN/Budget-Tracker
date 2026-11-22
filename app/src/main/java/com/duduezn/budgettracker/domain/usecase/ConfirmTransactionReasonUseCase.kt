package com.duduezn.budgettracker.domain.usecase

import com.duduezn.budgettracker.domain.repository.BudgetRepository

/**
 * Confirme une transaction en attente : catégorie + motif.
 * La règle marchand correspondante est créée ou renforcée pour automatiser
 * les prochains paiements vers le même marchand.
 */
class ConfirmTransactionReasonUseCase(
    private val repository: BudgetRepository
) {
    suspend operator fun invoke(transactionId: Long, categoryId: Long, reason: String) {
        val cleanReason = reason.trim()
        require(cleanReason.isNotEmpty()) { "Le motif est obligatoire." }
        repository.confirmReason(transactionId, categoryId, cleanReason)
    }
}
