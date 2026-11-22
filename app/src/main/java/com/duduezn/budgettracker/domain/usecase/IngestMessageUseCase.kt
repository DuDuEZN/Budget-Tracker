package com.duduezn.budgettracker.domain.usecase

import com.duduezn.budgettracker.domain.model.IncomingMessage
import com.duduezn.budgettracker.domain.model.IngestResult
import com.duduezn.budgettracker.domain.parser.MessageParser
import com.duduezn.budgettracker.domain.repository.BudgetRepository

/**
 * Transforme un SMS ou une notification en transaction locale.
 * Le parsing et l'écriture se font hors du thread principal.
 */
class IngestMessageUseCase(
    private val repository: BudgetRepository,
    private val parser: MessageParser
) {
    suspend operator fun invoke(message: IncomingMessage): IngestResult {
        if (message.body.isBlank()) return IngestResult.Ignored
        val parsed = parser.parse(message) ?: return IngestResult.Ignored
        return repository.ingest(parsed)
    }
}
