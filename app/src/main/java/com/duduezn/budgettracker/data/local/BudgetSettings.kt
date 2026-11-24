package com.duduezn.budgettracker.data.local

import android.content.Context
import com.duduezn.budgettracker.domain.util.MonthRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Un revenu complémentaire déclaré par l'utilisateur (prime, freelance, etc.). */
data class ComplementaryIncome(
    val id: Long,
    val label: String,
    val amount: Double
)

/** Réglages budgétaires de l'utilisateur : revenu du mois, début du cycle, assistant terminé, revenus complémentaires. */
data class BudgetSettingsState(
    val monthStartDay: Int = MonthRange.DEFAULT_START_DAY,
    val monthlyIncome: Double = 0.0,
    val budgetSetupComplete: Boolean = false,
    val complementaryIncomes: List<ComplementaryIncome> = emptyList()
) {
    /** Somme des revenus complémentaires déclarés. */
    val totalComplementaryIncome: Double
        get() = complementaryIncomes.sumOf { it.amount }

    /** Revenu total prévu pour le mois (salaire + compléments). */
    val totalPlannedIncome: Double
        get() = monthlyIncome + totalComplementaryIncome
}

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

    fun setBudgetSetupComplete(complete: Boolean = true) {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETE, complete).apply()
        _state.value = _state.value.copy(budgetSetupComplete = complete)
    }

    fun addComplementaryIncome(label: String, amount: Double) {
        if (amount <= 0.0) return
        val entry = ComplementaryIncome(
            id = System.currentTimeMillis(),
            label = label.trim().ifBlank { "Revenu complémentaire" },
            amount = amount
        )
        val updated = _state.value.complementaryIncomes + entry
        saveComplementary(updated)
    }

    fun removeComplementaryIncome(id: Long) {
        val updated = _state.value.complementaryIncomes.filterNot { it.id == id }
        saveComplementary(updated)
    }

    private fun saveComplementary(list: List<ComplementaryIncome>) {
        val json = JSONArray()
        list.forEach { entry ->
            json.put(JSONObject().apply {
                put("id", entry.id)
                put("label", entry.label)
                put("amount", entry.amount)
            })
        }
        prefs.edit().putString(KEY_COMPLEMENTARY, json.toString()).apply()
        _state.value = _state.value.copy(complementaryIncomes = list)
    }

    private fun read(): BudgetSettingsState = BudgetSettingsState(
        monthStartDay = prefs.getInt(KEY_START_DAY, MonthRange.DEFAULT_START_DAY)
            .coerceIn(MonthRange.MIN_START_DAY, MonthRange.MAX_START_DAY),
        monthlyIncome = Double.fromBits(prefs.getLong(KEY_INCOME, 0.0.toBits()))
            .coerceAtLeast(0.0),
        budgetSetupComplete = prefs.getBoolean(KEY_SETUP_COMPLETE, false),
        complementaryIncomes = parseComplementary(prefs.getString(KEY_COMPLEMENTARY, null))
    )

    private fun parseComplementary(raw: String?): List<ComplementaryIncome> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ComplementaryIncome(
                    id = obj.optLong("id", i.toLong()),
                    label = obj.optString("label", "Revenu complémentaire"),
                    amount = obj.optDouble("amount", 0.0)
                )
            }.filter { it.amount > 0.0 }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS_NAME = "budget_settings"
        const val KEY_INCOME = "monthly_income"
        const val KEY_START_DAY = "month_start_day"
        const val KEY_SETUP_COMPLETE = "budget_setup_complete"
        const val KEY_COMPLEMENTARY = "complementary_incomes"
    }
}
