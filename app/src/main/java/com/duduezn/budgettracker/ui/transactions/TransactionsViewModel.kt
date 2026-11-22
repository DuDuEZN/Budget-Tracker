package com.duduezn.budgettracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duduezn.budgettracker.domain.model.Category
import com.duduezn.budgettracker.domain.model.Transaction
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.repository.BudgetRepository
import com.duduezn.budgettracker.domain.usecase.ConfirmTransactionReasonUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TransactionFilter(val label: String) {
    ALL("Toutes"),
    PENDING("À préciser"),
    CONFIRMED("Confirmées")
}

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val pendingCount: Int = 0,
    val filter: TransactionFilter = TransactionFilter.ALL,
    val loading: Boolean = true
)

class TransactionsViewModel(
    repository: BudgetRepository,
    private val confirmReasonUseCase: ConfirmTransactionReasonUseCase
) : ViewModel() {

    private val filter = MutableStateFlow(TransactionFilter.ALL)

    val uiState: StateFlow<TransactionsUiState> = combine(
        repository.observeTransactions(),
        repository.observeCategories(),
        filter
    ) { transactions, categories, activeFilter ->
        val visibleTransactions = when (activeFilter) {
            TransactionFilter.ALL -> transactions
            TransactionFilter.PENDING -> transactions.filter {
                it.status == TransactionStatus.PENDING_REASON
            }
            TransactionFilter.CONFIRMED -> transactions.filter {
                it.status == TransactionStatus.CONFIRMED
            }
        }

        TransactionsUiState(
            transactions = visibleTransactions,
            categories = categories,
            categoriesById = categories.associateBy { it.id },
            pendingCount = transactions.count { it.status == TransactionStatus.PENDING_REASON },
            filter = activeFilter,
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState()
    )

    fun setFilter(value: TransactionFilter) {
        filter.value = value
    }

    fun confirmReason(transactionId: Long, categoryId: Long, reason: String) {
        viewModelScope.launch {
            confirmReasonUseCase(transactionId, categoryId, reason)
        }
    }
}
