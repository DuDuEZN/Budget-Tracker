package com.duduezn.budgettracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duduezn.budgettracker.di.AppContainer
import com.duduezn.budgettracker.ui.analysis.AnalysisViewModel
import com.duduezn.budgettracker.ui.categories.CategoriesViewModel
import com.duduezn.budgettracker.ui.dashboard.DashboardViewModel
import com.duduezn.budgettracker.ui.transactions.TransactionsViewModel

/** Fabrique unique : évite d'ajouter un framework d'injection pour trois ViewModels. */
class BudgetViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
            DashboardViewModel(container.repository, container.budgetSettings) as T

        modelClass.isAssignableFrom(TransactionsViewModel::class.java) ->
            TransactionsViewModel(container.repository, container.confirmReason) as T

        modelClass.isAssignableFrom(AnalysisViewModel::class.java) ->
            AnalysisViewModel(container.repository, container.budgetSettings) as T

        modelClass.isAssignableFrom(CategoriesViewModel::class.java) ->
            CategoriesViewModel(container.repository, container.updateCategoryBudget) as T

        else -> error("ViewModel non géré : ${modelClass.name}")
    }
}
