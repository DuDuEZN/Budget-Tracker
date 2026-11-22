package com.duduezn.budgettracker.data.local

/**
 * Catégories livrées avec l'application.
 * Les plafonds sont des valeurs de départ, modifiables depuis l'écran Catégories.
 */
data class DefaultCategory(
    val name: String,
    val monthlyBudgetLimit: Double,
    val colorHex: String,
    val iconName: String
)

object DefaultCategories {

    val ALL: List<DefaultCategory> = listOf(
        DefaultCategory(
            name = "Restauration",
            monthlyBudgetLimit = 50_000.0,
            colorHex = "#EF6C00",
            iconName = "restaurant"
        ),
        DefaultCategory(
            name = "Transport",
            monthlyBudgetLimit = 30_000.0,
            colorHex = "#0288D1",
            iconName = "directions_bus"
        ),
        DefaultCategory(
            name = "Factures & Services",
            monthlyBudgetLimit = 40_000.0,
            colorHex = "#7B1FA2",
            iconName = "receipt_long"
        ),
        DefaultCategory(
            name = "Achats",
            monthlyBudgetLimit = 50_000.0,
            colorHex = "#F9A825",
            iconName = "shopping_bag"
        ),
        DefaultCategory(
            name = "Épargne",
            monthlyBudgetLimit = 25_000.0,
            colorHex = "#2E7D32",
            iconName = "savings"
        ),
        DefaultCategory(
            name = "Transferts/Retraits",
            monthlyBudgetLimit = 0.0,
            colorHex = "#6D4C41",
            iconName = "swap_horiz"
        ),
        DefaultCategory(
            name = "Autres",
            monthlyBudgetLimit = 0.0,
            colorHex = "#607D8B",
            iconName = "category"
        )
    )
}
