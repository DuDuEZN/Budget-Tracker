package com.duduezn.budgettracker.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType
import com.duduezn.budgettracker.domain.repository.BudgetRepository
import com.duduezn.budgettracker.domain.usecase.UpdateCategoryBudgetUseCase
import com.duduezn.budgettracker.domain.util.MonthRange
import com.duduezn.budgettracker.ui.model.CategoryProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val progress: List<CategoryProgress> = emptyList(),
    val totalBudget: Double = 0.0,
    val loading: Boolean = true
)

class CategoriesViewModel(
    repository: BudgetRepository,
    private val updateCategoryBudgetUseCase: UpdateCategoryBudgetUseCase
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = combine(
        repository.observeCategories(),
        repository.observeTransactions()
    ) { categories, transactions ->
        val range = MonthRange.current()
        val spentByCategory = transactions
            .filter {
                it.status == TransactionStatus.CONFIRMED &&
                    it.type != TransactionType.DEPOT &&
                    it.timestamp in range
            }
            .groupBy { it.detectedCategory }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        CategoriesUiState(
            progress = categories.map { category ->
                CategoryProgress(
                    category = category,
                    spent = spentByCategory[category.id] ?: 0.0
                )
            },
            totalBudget = categories.sumOf { it.monthlyBudgetLimit },
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoriesUiState()
    )

    fun saveBudget(categoryId: Long, monthlyLimit: Double) {
        viewModelScope.launch {
            updateCategoryBudgetUseCase(categoryId, monthlyLimit)
        }
    }
}
