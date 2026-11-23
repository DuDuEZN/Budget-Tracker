package com.duduezn.budgettracker.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duduezn.budgettracker.domain.analysis.DetectedSubscription
import com.duduezn.budgettracker.domain.analysis.SubscriptionDetector
import com.duduezn.budgettracker.domain.model.Category
import com.duduezn.budgettracker.domain.model.Transaction
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType
import com.duduezn.budgettracker.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AnalysisScale(val label: String) {
    MONTH("Mois par mois"),
    YEAR("Année par année")
}

/** Agrégat des dépenses confirmées sur une période (mois ou année). */
data class PeriodBucket(
    val label: String,
    val start: Long,
    val end: Long,
    val total: Double,
    val byCategory: Map<Long, Double>
)

data class CategoryComparison(
    val category: Category,
    val current: Double,
    val previous: Double
) {
    /** Null lorsque la période précédente était à zéro (nouvelle dépense). */
    val deltaPercent: Double?
        get() = if (previous > 0.0) (current - previous) / previous * 100.0 else null
}

data class AnalysisUiState(
    val scale: AnalysisScale = AnalysisScale.MONTH,
    val selectedCategoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val buckets: List<PeriodBucket> = emptyList(),
    val currentTotal: Double = 0.0,
    val previousTotal: Double = 0.0,
    val categoryComparisons: List<CategoryComparison> = emptyList(),
    val subscriptions: List<DetectedSubscription> = emptyList(),
    val loading: Boolean = true
) {
    val selectedCategoryName: String?
        get() = selectedCategoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }

    /** Valeurs de la courbe pour la catégorie sélectionnée (ou toutes catégories). */
    val series: List<Double>
        get() = buckets.map { bucket ->
            selectedCategoryId?.let { bucket.byCategory[it] ?: 0.0 } ?: bucket.total
        }

    val labels: List<String>
        get() = buckets.map { it.label }
}

class AnalysisViewModel(
    repository: BudgetRepository
) : ViewModel() {

    private val scale = MutableStateFlow(AnalysisScale.MONTH)
    private val selectedCategoryId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<AnalysisUiState> = combine(
        repository.observeTransactions(),
        repository.observeCategories(),
        scale,
        selectedCategoryId
    ) { transactions, categories, activeScale, activeCategory ->
        val buckets = buildBuckets(transactions, activeScale)
        val latest = buckets.lastOrNull()
        val previous = buckets.dropLast(1).lastOrNull()

        fun value(bucket: PeriodBucket?): Double {
            if (bucket == null) return 0.0
            return activeCategory?.let { bucket.byCategory[it] ?: 0.0 } ?: bucket.total
        }

        AnalysisUiState(
            scale = activeScale,
            selectedCategoryId = activeCategory,
            categories = categories,
            buckets = buckets,
            currentTotal = value(latest),
            previousTotal = value(previous),
            categoryComparisons = categories
                .map { category ->
                    CategoryComparison(
                        category = category,
                        current = latest?.byCategory?.get(category.id) ?: 0.0,
                        previous = previous?.byCategory?.get(category.id) ?: 0.0
                    )
                }
                .filter { it.current > 0.0 || it.previous > 0.0 }
                .sortedByDescending { it.current },
            subscriptions = SubscriptionDetector.detect(transactions),
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnalysisUiState()
    )

    fun setScale(value: AnalysisScale) {
        scale.value = value
    }

    fun setCategory(value: Long?) {
        selectedCategoryId.value = value
    }

    private fun buildBuckets(
        transactions: List<Transaction>,
        scale: AnalysisScale
    ): List<PeriodBucket> {
        val spending = transactions.filter {
            it.status == TransactionStatus.CONFIRMED && it.type != TransactionType.DEPOT
        }

        return when (scale) {
            AnalysisScale.MONTH -> monthlyBuckets(spending, count = 12)
            AnalysisScale.YEAR -> yearlyBuckets(spending, count = 5)
        }
    }

    private fun monthlyBuckets(
        spending: List<Transaction>,
        count: Int
    ): List<PeriodBucket> {
        val formatter = SimpleDateFormat("MMM yy", Locale.FRENCH)
        val cursor = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -(count - 1))
        }

        return buildList {
            repeat(count) {
                val start = cursor.timeInMillis
                val next = (cursor.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                val end = next.timeInMillis
                add(bucket(spending, start, end, formatter.format(Date(start))))
                cursor.add(Calendar.MONTH, 1)
            }
        }
    }

    private fun yearlyBuckets(
        spending: List<Transaction>,
        count: Int
    ): List<PeriodBucket> {
        val formatter = SimpleDateFormat("yyyy", Locale.FRENCH)
        val cursor = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.YEAR, -(count - 1))
        }

        return buildList {
            repeat(count) {
                val start = cursor.timeInMillis
                val next = (cursor.clone() as Calendar).apply { add(Calendar.YEAR, 1) }
                val end = next.timeInMillis
                add(bucket(spending, start, end, formatter.format(Date(start))))
                cursor.add(Calendar.YEAR, 1)
            }
        }
    }

    private fun bucket(
        spending: List<Transaction>,
        start: Long,
        end: Long,
        label: String
    ): PeriodBucket {
        val inPeriod = spending.filter { it.timestamp in start until end }
        val byCategory = inPeriod
            .groupBy { it.detectedCategory ?: UNCATEGORIZED_ID }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        return PeriodBucket(
            label = label.replaceFirstChar { it.titlecase(Locale.FRENCH) },
            start = start,
            end = end,
            total = inPeriod.sumOf { it.amount },
            byCategory = byCategory
        )
    }

    private companion object {
        const val UNCATEGORIZED_ID = -1L
    }
}
