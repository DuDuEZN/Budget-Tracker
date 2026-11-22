package com.duduezn.budgettracker.ui.model

import com.duduezn.budgettracker.domain.model.Category

/** Budget d'une catégorie et montant consommé sur le mois courant. */
data class CategoryProgress(
    val category: Category,
    val spent: Double
) {
    val ratio: Float
        get() = if (category.monthlyBudgetLimit > 0.0) {
            (spent / category.monthlyBudgetLimit).toFloat()
        } else {
            0f
        }
}
