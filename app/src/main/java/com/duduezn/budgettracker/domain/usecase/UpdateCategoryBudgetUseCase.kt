package com.duduezn.budgettracker.domain.usecase

import com.duduezn.budgettracker.domain.repository.BudgetRepository

class UpdateCategoryBudgetUseCase(
    private val repository: BudgetRepository
) {
    suspend operator fun invoke(categoryId: Long, monthlyLimit: Double) {
        require(monthlyLimit >= 0.0) { "Le plafond ne peut pas être négatif." }
        repository.updateCategoryBudget(categoryId, monthlyLimit)
    }
}
