package com.duduezn.budgettracker.data.local

import android.content.Context
import com.duduezn.budgettracker.domain.util.MonthRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Réglages budgétaires de l'utilisateur : revenu du mois et début du cycle. */
data class BudgetSettingsState(
    val monthStartDay: Int = MonthRange.DEFAULT_START_DAY,
    val monthlyIncome: Double = 0.0
)

/**
 * Stocke localement le revenu mensuel et le jour de début du mois budgétaire.
 * Aucune donnée ne quitte l'appareil.
 */
class BudgetSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<BudgetSettingsState> = _state.asStateFlow()

    fun setMonthlyIncome(amount: Double) {
        val safe = amount.coerceAtLeast(0.0)
        prefs.edit().putLong(KEY_INCOME, safe.toBits()).apply()
        _state.value = _state.value.copy(monthlyIncome = safe)
    }

    fun setMonthStartDay(day: Int) {
        val safe = day.coerceIn(MonthRange.MIN_START_DAY, MonthRange.MAX_START_DAY)
        prefs.edit().putInt(KEY_START_DAY, safe).apply()
        _state.value = _state.value.copy(monthStartDay = safe)
    }

    private fun read(): BudgetSettingsState = BudgetSettingsState(
        monthStartDay = prefs.getInt(KEY_START_DAY, MonthRange.DEFAULT_START_DAY)
            .coerceIn(MonthRange.MIN_START_DAY, MonthRange.MAX_START_DAY),
        monthlyIncome = Double.fromBits(prefs.getLong(KEY_INCOME, 0.0.toBits()))
            .coerceAtLeast(0.0)
    )

    private companion object {
        const val PREFS_NAME = "budget_settings"
        const val KEY_INCOME = "monthly_income"
        const val KEY_START_DAY = "month_start_day"
    }
}
