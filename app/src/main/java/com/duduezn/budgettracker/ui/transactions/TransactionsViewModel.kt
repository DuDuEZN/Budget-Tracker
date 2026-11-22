package com.duduezn.budgettracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duduezn.budgettracker.domain.model.Category
import com.duduezn.budgettracker.domain.model.IngestResult
import com.duduezn.budgettracker.domain.model.Operator
import com.duduezn.budgettracker.domain.model.ParsedMessage
import com.duduezn.budgettracker.domain.model.Transaction
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType
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
    val typeFilter: TransactionType? = null,
    val categoryFilter: Long? = null,
    val loading: Boolean = true
)

class TransactionsViewModel(
    private val repository: BudgetRepository,
    private val confirmReasonUseCase: ConfirmTransactionReasonUseCase
) : ViewModel() {

    private val filter = MutableStateFlow(TransactionFilter.ALL)
    private val typeFilter = MutableStateFlow<TransactionType?>(null)
    private val categoryFilter = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<TransactionsUiState> = combine(
        repository.observeTransactions(),
        repository.observeCategories(),
        filter,
        typeFilter,
        categoryFilter
    ) { transactions, categories, activeFilter, activeType, activeCategory ->
        val visibleTransactions = transactions.filter { transaction ->
            val statusOk = when (activeFilter) {
                TransactionFilter.ALL -> true
                TransactionFilter.PENDING -> transaction.status == TransactionStatus.PENDING_REASON
                TransactionFilter.CONFIRMED -> transaction.status == TransactionStatus.CONFIRMED
            }
            val typeOk = activeType == null || transaction.type == activeType
            val categoryOk = activeCategory == null || transaction.detectedCategory == activeCategory
            statusOk && typeOk && categoryOk
        }

        TransactionsUiState(
            transactions = visibleTransactions,
            categories = categories,
            categoriesById = categories.associateBy { it.id },
            pendingCount = transactions.count { it.status == TransactionStatus.PENDING_REASON },
            filter = activeFilter,
            typeFilter = activeType,
            categoryFilter = activeCategory,
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

    fun setTypeFilter(value: TransactionType?) {
        typeFilter.value = value
    }

    fun setCategoryFilter(value: Long?) {
        categoryFilter.value = value
    }

    fun confirmReason(
        transactionId: Long,
        categoryId: Long,
        reason: String,
        rememberMerchant: Boolean = true
    ) {
        viewModelScope.launch {
            confirmReasonUseCase(transactionId, categoryId, reason, rememberMerchant)
        }
    }

    fun addManualTransaction(
        type: TransactionType,
        amount: Double,
        counterparty: String?,
        categoryId: Long?,
        reason: String?,
        rememberMerchant: Boolean
    ) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            val cleanReason = reason?.trim()?.takeIf { it.isNotEmpty() }
            val parsed = ParsedMessage(
                rawMessage = "Saisie manuelle",
                operator = Operator.UNKNOWN,
                type = type,
                amount = amount,
                counterparty = counterparty?.trim()?.takeIf { it.isNotEmpty() },
                timestamp = System.currentTimeMillis(),
                rawReason = cleanReason,
                reasonIsExplicit = cleanReason != null
            )
            val result = repository.ingest(parsed)
            val inserted = result as? IngestResult.Inserted ?: return@launch
            if (categoryId != null) {
                confirmReasonUseCase(
                    inserted.transactionId,
                    categoryId,
                    cleanReason ?: "Dépense manuelle",
                    rememberMerchant
                )
            }
        }
    }
}
