package com.duduezn.budgettracker.di

import android.content.Context
import com.duduezn.budgettracker.data.local.BudgetDatabase
import com.duduezn.budgettracker.data.parser.TransactionParser
import com.duduezn.budgettracker.data.repository.OfflineBudgetRepository
import com.duduezn.budgettracker.domain.parser.MessageParser
import com.duduezn.budgettracker.domain.repository.BudgetRepository
import com.duduezn.budgettracker.domain.usecase.ConfirmTransactionReasonUseCase
import com.duduezn.budgettracker.domain.usecase.IngestMessageUseCase
import com.duduezn.budgettracker.domain.usecase.UpdateCategoryBudgetUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Conteneur de dépendances manuel, sans framework d'injection. */
interface AppContainer {
    val repository: BudgetRepository
    val ingestMessage: IngestMessageUseCase
    val confirmReason: ConfirmTransactionReasonUseCase
    val updateCategoryBudget: UpdateCategoryBudgetUseCase
    val applicationScope: CoroutineScope

    fun initialize()
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    private val database by lazy { BudgetDatabase.getInstance(appContext) }

    private val parser: MessageParser = TransactionParser()

    override val repository: BudgetRepository by lazy {
        OfflineBudgetRepository(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            merchantRuleDao = database.merchantRuleDao()
        )
    }

    override val ingestMessage: IngestMessageUseCase by lazy {
        IngestMessageUseCase(repository, parser)
    }

    override val confirmReason: ConfirmTransactionReasonUseCase by lazy {
        ConfirmTransactionReasonUseCase(repository)
    }

    override val updateCategoryBudget: UpdateCategoryBudgetUseCase by lazy {
        UpdateCategoryBudgetUseCase(repository)
    }

    override val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun initialize() {
        applicationScope.launch {
            repository.ensureDefaultCategories()
        }
    }
}
