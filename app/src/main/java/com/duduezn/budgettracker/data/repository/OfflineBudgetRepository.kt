package com.duduezn.budgettracker.data.repository

import com.duduezn.budgettracker.data.local.DefaultCategories
import com.duduezn.budgettracker.data.local.dao.CategoryDao
import com.duduezn.budgettracker.data.local.dao.MerchantRuleDao
import com.duduezn.budgettracker.data.local.dao.TransactionDao
import com.duduezn.budgettracker.data.local.entity.MerchantRuleEntity
import com.duduezn.budgettracker.data.local.entity.TransactionEntity
import com.duduezn.budgettracker.data.mapper.toDomain
import com.duduezn.budgettracker.data.mapper.toEntity
import com.duduezn.budgettracker.domain.model.Category
import com.duduezn.budgettracker.domain.model.IngestResult
import com.duduezn.budgettracker.domain.model.MerchantRule
import com.duduezn.budgettracker.domain.model.ParsedMessage
import com.duduezn.budgettracker.domain.model.Transaction
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType
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
    private val merchantRuleDao: MerchantRuleDao
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

        val detectedCategory = parsed.categoryName?.let { categoryDao.findByName(it) }
        val merchantRule = if (parsed.type == TransactionType.PAIEMENT && counterparty != null) {
            merchantRuleDao.findByCounterparty(MerchantIdentifier.normalize(counterparty))
        } else {
            null
        }

        val categoryId = merchantRule?.categoryId ?: detectedCategory?.id
        val status = if (categoryId != null) {
            TransactionStatus.CONFIRMED
        } else {
            TransactionStatus.PENDING_REASON
        }
        val finalReason = merchantRule?.defaultReason
            ?: parsed.rawReason
            ?: detectedCategory?.name

        val entity = TransactionEntity(
            rawMessage = parsed.rawMessage,
            operator = parsed.operator,
            type = parsed.type,
            amount = parsed.amount,
            currency = parsed.currency,
            counterparty = counterparty,
            timestamp = parsed.timestamp,
            rawReason = parsed.rawReason,
            detectedCategory = categoryId,
            finalReason = finalReason,
            status = status
        )

        val insertedId = transactionDao.insert(entity)
        if (insertedId == -1L) return IngestResult.Duplicate

        if (merchantRule != null) {
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

    override suspend fun confirmReason(transactionId: Long, categoryId: Long, reason: String) {
        val transaction = transactionDao.getById(transactionId) ?: return
        transactionDao.confirmReason(transactionId, categoryId, reason)

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

    override suspend fun findMerchantRule(counterparty: String): MerchantRule? =
        merchantRuleDao.findByCounterparty(MerchantIdentifier.normalize(counterparty))?.toDomain()

    override suspend fun transactionCount(): Int = transactionDao.count()

    override suspend fun monthlySpent(startInclusive: Long, endExclusive: Long): Double =
        transactionDao.spentBetween(startInclusive, endExclusive)

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
