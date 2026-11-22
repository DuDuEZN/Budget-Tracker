package com.duduezn.budgettracker.data.repository

import com.duduezn.budgettracker.data.local.DefaultCategories
import com.duduezn.budgettracker.data.local.dao.CategoryDao
import com.duduezn.budgettracker.data.local.dao.MerchantRuleDao
import com.duduezn.budgettracker.data.local.dao.TransactionDao
import com.duduezn.budgettracker.data.local.entity.CategoryEntity
import com.duduezn.budgettracker.data.local.entity.MerchantRuleEntity
import com.duduezn.budgettracker.data.local.entity.TransactionEntity
import com.duduezn.budgettracker.data.mapper.toDomain
import com.duduezn.budgettracker.data.mapper.toEntity
import com.duduezn.budgettracker.domain.categorization.CategorizationEngine
import com.duduezn.budgettracker.domain.categorization.LearnedRule
import com.duduezn.budgettracker.domain.model.Category
import com.duduezn.budgettracker.domain.model.IngestResult
import com.duduezn.budgettracker.domain.model.MerchantRule
import com.duduezn.budgettracker.domain.model.ParsedMessage
import com.duduezn.budgettracker.domain.model.Transaction
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * Implémentation 100 % locale : Room + règles en mémoire.
 * Aucun appel réseau, aucun backend.
 */
class OfflineBudgetRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantRuleDao: MerchantRuleDao,
    private val categorizationEngine: CategorizationEngine = CategorizationEngine()
) : BudgetRepository {

    override fun observeTransactions(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observePendingTransactions(): Flow<List<Transaction>> =
        transactionDao.observeByStatus(TransactionStatus.PENDING_REASON)
            .map { entities -> entities.map { it.toDomain() } }

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeMerchantRules(): Flow<List<MerchantRule>> =
        merchantRuleDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun ingest(parsed: ParsedMessage): IngestResult {
        val counterparty = parsed.counterparty?.trim()?.takeIf { it.isNotEmpty() }

        val duplicate = transactionDao.findDuplicate(
            amount = parsed.amount,
            operator = parsed.operator,
            counterparty = counterparty,
            fromInclusive = parsed.timestamp - DEDUPE_WINDOW_MS,
            toInclusive = parsed.timestamp + DEDUPE_WINDOW_MS,
            referenceTimestamp = parsed.timestamp
        )
        if (duplicate != null) return IngestResult.Duplicate

        // Étape 1 : une règle apprise pour cette contrepartie, quel que soit le type.
        val merchantRule = counterparty?.let {
            merchantRuleDao.findByCounterparty(MerchantIdentifier.normalize(it))
        }
        val learnedRule = merchantRule?.let { rule ->
            categoryDao.getById(rule.categoryId)?.let { category ->
                LearnedRule(
                    counterpartyIdentifier = rule.counterpartyIdentifier,
                    defaultReason = rule.defaultReason,
                    categoryName = category.name
                )
            }
        }

        // Étapes 2 et 3 : mots-clés locaux puis décision de relance.
        val availableCategories = categoryDao.getAll().map { it.name }
        val categorization = categorizationEngine.categorize(parsed, learnedRule, availableCategories)
        val detectedCategory = categorization.categoryName?.let { categoryDao.findByName(it) }
        val status = if (detectedCategory != null && categorization.status == TransactionStatus.CONFIRMED) {
            TransactionStatus.CONFIRMED
        } else {
            TransactionStatus.PENDING_REASON
        }
        val finalReason = categorization.finalReason ?: parsed.rawReason

        val entity = TransactionEntity(
            rawMessage = parsed.rawMessage,
            operator = parsed.operator,
            type = parsed.type,
            amount = parsed.amount,
            currency = parsed.currency,
            counterparty = counterparty,
            timestamp = parsed.timestamp,
            rawReason = parsed.rawReason,
            detectedCategory = detectedCategory?.id,
            finalReason = finalReason,
            status = status
        )

        val insertedId = transactionDao.insert(entity)
        if (insertedId == -1L) return IngestResult.Duplicate

        if (merchantRule != null && learnedRule != null) {
            merchantRuleDao.updateUsage(
                counterparty = merchantRule.counterpartyIdentifier,
                reason = merchantRule.defaultReason,
                categoryId = merchantRule.categoryId
            )
        }

        return IngestResult.Inserted(
            transactionId = insertedId,
            needsReason = status == TransactionStatus.PENDING_REASON
        )
    }

    override suspend fun confirmReason(
        transactionId: Long,
        categoryId: Long,
        reason: String,
        rememberMerchant: Boolean
    ) {
        val transaction = transactionDao.getById(transactionId) ?: return
        transactionDao.confirmReason(transactionId, categoryId, reason)

        if (!rememberMerchant) return

        val counterparty = transaction.counterparty
            ?.takeIf { it.isNotBlank() }
            ?.let(MerchantIdentifier::normalize)
            ?: return

        val existingRule = merchantRuleDao.findByCounterparty(counterparty)
        if (existingRule == null) {
            merchantRuleDao.insert(
                MerchantRuleEntity(
                    counterpartyIdentifier = counterparty,
                    defaultReason = reason,
                    categoryId = categoryId,
                    usageCount = 1
                )
            )
        } else {
            merchantRuleDao.updateUsage(
                counterparty = counterparty,
                reason = reason,
                categoryId = categoryId
            )
        }
    }

    override suspend fun updateCategoryBudget(categoryId: Long, monthlyLimit: Double) {
        categoryDao.updateBudget(categoryId, monthlyLimit)
    }

    override suspend fun ensureDefaultCategories() {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(DefaultCategories.ALL.map { it.toEntity() })
        }
    }

    override suspend fun findCategoryByName(name: String): Category? =
        categoryDao.findByName(name)?.toDomain()

    override suspend fun findCategoryById(id: Long): Category? =
        categoryDao.getById(id)?.toDomain()

    override suspend fun createCategory(name: String, monthlyLimit: Double): Category? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (findCategoryByName(trimmed) != null) return null
        val entity = CategoryEntity(
            name = trimmed,
            monthlyBudgetLimit = monthlyLimit,
            colorHex = "#607D8B",
            iconName = "category"
        )
        val id = categoryDao.insert(entity)
        if (id <= 0L) return null
        return categoryDao.getById(id)?.toDomain()
    }

    override suspend fun renameCategory(id: Long, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val existing = findCategoryByName(trimmed)
        if (existing != null && existing.id != id) return false
        categoryDao.rename(id, trimmed)
        return true
    }

    override suspend fun deleteCategory(id: Long) {
        categoryDao.delete(id)
    }

    override suspend fun findTransaction(id: Long): Transaction? =
        transactionDao.getById(id)?.toDomain()

    override suspend fun findMerchantRule(counterparty: String): MerchantRule? =
        merchantRuleDao.findByCounterparty(MerchantIdentifier.normalize(counterparty))?.toDomain()

    override suspend fun transactionCount(): Int = transactionDao.count()

    override suspend fun monthlySpent(startInclusive: Long, endExclusive: Long): Double =
        transactionDao.spentBetween(startInclusive, endExclusive)

    override suspend fun monthlySpentByCategory(
        categoryId: Long,
        startInclusive: Long,
        endExclusive: Long
    ): Double = transactionDao.spentByCategory(categoryId, startInclusive, endExclusive)

    override suspend fun totalBudget(): Double = categoryDao.totalBudget()

    private companion object {
        const val DEDUPE_WINDOW_MS = 10 * 60 * 1000L
    }
}

/** Normalise un marchand pour que « Boutique  Alpha » et « boutique alpha » partagent la règle. */
object MerchantIdentifier {
    fun normalize(raw: String): String =
        raw.trim()
            .replace(Regex("\\s+"), " ")
            .uppercase(Locale.ROOT)
}
