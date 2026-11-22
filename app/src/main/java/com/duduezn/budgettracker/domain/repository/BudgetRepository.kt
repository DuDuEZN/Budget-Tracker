package com.duduezn.budgettracker.domain.repository

import com.duduezn.budgettracker.domain.model.Category
import com.duduezn.budgettracker.domain.model.IngestResult
import com.duduezn.budgettracker.domain.model.MerchantRule
import com.duduezn.budgettracker.domain.model.ParsedMessage
import com.duduezn.budgettracker.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Point d'accès unique aux données budgétaires.
 * Aucune implémentation ne communique avec le réseau.
 */
interface BudgetRepository {

    fun observeTransactions(): Flow<List<Transaction>>

    fun observePendingTransactions(): Flow<List<Transaction>>

    fun observeCategories(): Flow<List<Category>>

    fun observeMerchantRules(): Flow<List<MerchantRule>>

    /** Enregistre une transaction analysée, en dédupliquant les messages proches. */
    suspend fun ingest(parsed: ParsedMessage): IngestResult

    /** Confirme le motif d'une transaction et alimente les règles marchand. */
    suspend fun confirmReason(transactionId: Long, categoryId: Long, reason: String)

    suspend fun updateCategoryBudget(categoryId: Long, monthlyLimit: Double)

    /** Insère les catégories par défaut si la base est vide. */
    suspend fun ensureDefaultCategories()

    suspend fun findCategoryByName(name: String): Category?

    suspend fun findCategoryById(id: Long): Category?

    suspend fun findTransaction(id: Long): Transaction?

    suspend fun findMerchantRule(counterparty: String): MerchantRule?

    suspend fun transactionCount(): Int

    suspend fun monthlySpent(startInclusive: Long, endExclusive: Long): Double

    suspend fun monthlySpentByCategory(categoryId: Long, startInclusive: Long, endExclusive: Long): Double

    suspend fun totalBudget(): Double
}
