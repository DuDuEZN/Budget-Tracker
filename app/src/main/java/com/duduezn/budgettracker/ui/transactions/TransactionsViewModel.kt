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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = true
)

class TransactionsViewModel(
    private val repository: BudgetRepository,
    private val confirmReasonUseCase: ConfirmTransactionReasonUseCase
) : ViewModel() {

    private data class QueryState(
        val filter: TransactionFilter = TransactionFilter.ALL,
        val type: TransactionType? = null,
        val categoryId: Long? = null,
        val limit: Int = PAGE_SIZE
    ) {
        fun statusFilter(): TransactionStatus? = when (filter) {
            TransactionFilter.ALL -> null
            TransactionFilter.PENDING -> TransactionStatus.PENDING_REASON
            TransactionFilter.CONFIRMED -> TransactionStatus.CONFIRMED
        }
    }

    private val query = MutableStateFlow(QueryState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TransactionsUiState> = combine(
        query,
        repository.observeCategories(),
        query.flatMapLatest { state ->
            repository.observeTransactionPage(
                status = state.statusFilter(),
                type = state.type,
                categoryId = state.categoryId,
                limit = state.limit,
                offset = 0
            )
        },
        query.flatMapLatest { state ->
            repository.observeTransactionCount(
                status = state.statusFilter(),
                type = state.type,
                categoryId = state.categoryId
            )
        },
        repository.observePendingTransactions()
    ) { state, categories, page, total, pending ->
        TransactionsUiState(
            transactions = page,
            categories = categories,
            categoriesById = categories.associateBy { it.id },
            pendingCount = pending.size,
            filter = state.filter,
            typeFilter = state.type,
            categoryFilter = state.categoryId,
            totalCount = total,
            hasMore = state.limit < total,
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState()
    )

    fun setFilter(value: TransactionFilter) {
        query.value = query.value.copy(filter = value, limit = PAGE_SIZE)
    }

    fun setTypeFilter(value: TransactionType?) {
        query.value = query.value.copy(type = value, limit = PAGE_SIZE)
    }

    fun setCategoryFilter(value: Long?) {
        query.value = query.value.copy(categoryId = value, limit = PAGE_SIZE)
    }

    fun loadMore() {
        val current = query.value
        query.value = current.copy(limit = current.limit + PAGE_SIZE)
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

    private companion object {
        const val PAGE_SIZE = 50
    }
}
