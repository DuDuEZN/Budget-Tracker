package com.duduezn.budgettracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duduezn.budgettracker.data.local.BudgetSettings
import com.duduezn.budgettracker.domain.model.Category
import com.duduezn.budgettracker.domain.model.Transaction
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType
import com.duduezn.budgettracker.domain.repository.BudgetRepository
import com.duduezn.budgettracker.domain.util.MonthRange
import com.duduezn.budgettracker.ui.model.CategoryProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val monthLabel: String = "",
    val monthStartDay: Int = MonthRange.DEFAULT_START_DAY,
    val totalSpent: Double = 0.0,
    val totalIncome: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val totalBudget: Double = 0.0,
    val pendingCount: Int = 0,
    val categoryProgress: List<CategoryProgress> = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val loading: Boolean = true
) {
    /** Reste à dépenser sur le revenu déclaré, ou null si aucun revenu n'est saisi. */
    val remainingIncome: Double?
        get() = if (monthlyIncome > 0.0) monthlyIncome - totalSpent else null
}

class DashboardViewModel(
    repository: BudgetRepository,
    settings: BudgetSettings
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeTransactions(),
        repository.observeCategories(),
        settings.state
    ) { transactions, categories, budget ->
        val range = MonthRange.current(startDay = budget.monthStartDay)
        val monthTransactions = transactions.filter { it.timestamp in range }
        val confirmedSpending = monthTransactions.filter {
            it.status == TransactionStatus.CONFIRMED && it.type != TransactionType.DEPOT
        }
        val confirmedIncome = monthTransactions.filter {
            it.status == TransactionStatus.CONFIRMED && it.type == TransactionType.DEPOT
        }
        val spentByCategory = confirmedSpending
            .groupBy { it.detectedCategory }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        DashboardUiState(
            monthLabel = MonthRange.label(startDay = budget.monthStartDay),
            monthStartDay = budget.monthStartDay,
            totalSpent = confirmedSpending.sumOf { it.amount },
            totalIncome = confirmedIncome.sumOf { it.amount },
            monthlyIncome = budget.totalPlannedIncome,
            totalBudget = categories.sumOf { it.monthlyBudgetLimit },
            pendingCount = transactions.count { it.status == TransactionStatus.PENDING_REASON },
            categoryProgress = categories.map { category ->
                CategoryProgress(
                    category = category,
                    spent = spentByCategory[category.id] ?: 0.0
                )
            },
            recentTransactions = transactions.take(5),
            categoriesById = categories.associateBy { it.id },
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )
}
