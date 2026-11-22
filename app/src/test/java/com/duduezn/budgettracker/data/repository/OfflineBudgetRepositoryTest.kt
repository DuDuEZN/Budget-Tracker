package com.duduezn.budgettracker.data.repository

import com.duduezn.budgettracker.data.local.DefaultCategories
import com.duduezn.budgettracker.data.local.dao.CategoryDao
import com.duduezn.budgettracker.data.local.dao.MerchantRuleDao
import com.duduezn.budgettracker.data.local.dao.TransactionDao
import com.duduezn.budgettracker.data.local.entity.CategoryEntity
import com.duduezn.budgettracker.data.local.entity.MerchantRuleEntity
import com.duduezn.budgettracker.data.local.entity.TransactionEntity
import com.duduezn.budgettracker.data.mapper.toEntity
import com.duduezn.budgettracker.domain.model.IngestResult
import com.duduezn.budgettracker.domain.model.Operator
import com.duduezn.budgettracker.domain.model.ParsedMessage
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineBudgetRepositoryTest {

    private val transactionDao = FakeTransactionDao()
    private val categoryDao = FakeCategoryDao()
    private val merchantRuleDao = FakeMerchantRuleDao()

    private fun repository(): OfflineBudgetRepository {
        val repository = OfflineBudgetRepository(transactionDao, categoryDao, merchantRuleDao)
        categoryDao.categories += DefaultCategories.ALL.mapIndexed { index, category ->
            category.toEntity().copy(id = (index + 1).toLong())
        }
        return repository
    }

    private fun parsed(
        counterparty: String?,
        categoryName: String?,
        amount: Double = 2_500.0,
        timestamp: Long = 1_700_000_000_000L
    ) = ParsedMessage(
        rawMessage = "Paiement de $amount FCFA chez $counterparty",
        operator = Operator.MTN_MOMO,
        type = TransactionType.PAIEMENT,
        amount = amount,
        counterparty = counterparty,
        timestamp = timestamp,
        rawReason = counterparty,
        categoryName = categoryName
    )

    @Test
    fun `transaction sans categorie reste en attente de motif`() = runTest {
        val repository = repository()

        val result = repository.ingest(
            parsed(counterparty = "MARCHAND INCONNU", categoryName = null)
        )

        assertTrue(result is IngestResult.Inserted)
        assertTrue((result as IngestResult.Inserted).needsReason)
        assertEquals(TransactionStatus.PENDING_REASON, transactionDao.transactions.single().status)
    }

    @Test
    fun `categorie detectee confirme la transaction`() = runTest {
        val repository = repository()

        val result = repository.ingest(
            parsed(counterparty = "RESTO LE BAOBAB", categoryName = "Restauration")
        )

        assertFalse((result as IngestResult.Inserted).needsReason)
        val stored = transactionDao.transactions.single()
        assertEquals(TransactionStatus.CONFIRMED, stored.status)
        assertEquals("Restauration", categoryDao.categories.first { it.id == stored.detectedCategory }.name)
    }

    @Test
    fun `confirmation cree une regle marchand reutilisee ensuite`() = runTest {
        val repository = repository()
        val first = repository.ingest(
            parsed(counterparty = "0197000000", categoryName = null)
        ) as IngestResult.Inserted

        val restaurationId = categoryDao.categories.first { it.name == "Restauration" }.id
        repository.confirmReason(first.transactionId, restaurationId, "Déjeuner")

        val rule = merchantRuleDao.rules.single()
        assertEquals("0197000000", rule.counterpartyIdentifier)
        assertEquals("Déjeuner", rule.defaultReason)
        assertEquals(1, rule.usageCount)

        val second = repository.ingest(
            parsed(
                counterparty = "0197000000",
                categoryName = null,
                amount = 3_000.0,
                timestamp = 1_700_000_600_000L
            )
        ) as IngestResult.Inserted

        assertFalse(second.needsReason)
        val stored = transactionDao.transactions.last()
        assertEquals(TransactionStatus.CONFIRMED, stored.status)
        assertEquals("Déjeuner", stored.finalReason)
        assertEquals(restaurationId, stored.detectedCategory)
        assertEquals(2, merchantRuleDao.rules.single().usageCount)
    }

    @Test
    fun `doublon SMS notification ignore`() = runTest {
        val repository = repository()
        val message = parsed(counterparty = "0197000000", categoryName = "Restauration")

        repository.ingest(message)
        val second = repository.ingest(message)

        assertEquals(IngestResult.Duplicate, second)
        assertEquals(1, transactionDao.transactions.size)
    }

    @Test
    fun `categories par defaut inserees une seule fois`() = runTest {
        val repository = repository()

        repository.ensureDefaultCategories()
        repository.ensureDefaultCategories()

        assertEquals(7, categoryDao.categories.size)
        assertNotNull(categoryDao.categories.firstOrNull { it.name == "Transferts/Retraits" })
    }
}

private class FakeTransactionDao : TransactionDao {

    val transactions = mutableListOf<TransactionEntity>()
    private val state = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<TransactionEntity>> = state

    override fun observeByStatus(status: TransactionStatus): Flow<List<TransactionEntity>> =
        state.map { list -> list.filter { it.status == status } }

    override fun observeBetween(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<TransactionEntity>> = state.map { list ->
        list.filter { it.timestamp in startInclusive until endExclusive }
    }

    override suspend fun getById(id: Long): TransactionEntity? =
        transactions.firstOrNull { it.id == id }

    override suspend fun count(): Int = transactions.size

    override suspend fun findDuplicate(
        amount: Double,
        operator: Operator,
        counterparty: String?,
        fromInclusive: Long,
        toInclusive: Long,
        referenceTimestamp: Long
    ): TransactionEntity? = transactions.firstOrNull {
        it.amount == amount &&
            it.operator == operator &&
            it.timestamp in fromInclusive..toInclusive &&
            (counterparty == null || it.counterparty == null || it.counterparty == counterparty)
    }

    override suspend fun insert(transaction: TransactionEntity): Long {
        val alreadyExists = transactions.any {
            it.rawMessage == transaction.rawMessage && it.timestamp == transaction.timestamp
        }
        if (alreadyExists) return -1L

        val stored = transaction.copy(id = nextId++)
        transactions += stored
        state.value = transactions.toList()
        return stored.id
    }

    override suspend fun confirmReason(transactionId: Long, categoryId: Long, reason: String) {
        val index = transactions.indexOfFirst { it.id == transactionId }
        if (index >= 0) {
            transactions[index] = transactions[index].copy(
                detectedCategory = categoryId,
                finalReason = reason,
                status = TransactionStatus.CONFIRMED
            )
            state.value = transactions.toList()
        }
    }

    override suspend fun spentBetween(startInclusive: Long, endExclusive: Long): Double =
        transactions.filter {
            it.type != TransactionType.DEPOT &&
                it.status == TransactionStatus.CONFIRMED &&
                it.timestamp in startInclusive until endExclusive
        }.sumOf { it.amount }
}

private class FakeCategoryDao : CategoryDao {

    val categories = mutableListOf<CategoryEntity>()
    private val state = MutableStateFlow<List<CategoryEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<CategoryEntity>> = state

    override suspend fun getAll(): List<CategoryEntity> = categories.toList()

    override suspend fun findByName(name: String): CategoryEntity? =
        categories.firstOrNull { it.name == name }

    override suspend fun count(): Int = categories.size

    override suspend fun totalBudget(): Double = categories.sumOf { it.monthlyBudgetLimit }

    override suspend fun insertAll(categories: List<CategoryEntity>): List<Long> {
        val ids = categories.map { category ->
            if (this.categories.any { it.name == category.name }) {
                -1L
            } else {
                val stored = category.copy(id = nextId++)
                this.categories += stored
                stored.id
            }
        }
        state.value = this.categories.toList()
        return ids
    }

    override suspend fun updateBudget(categoryId: Long, monthlyLimit: Double) {
        val index = categories.indexOfFirst { it.id == categoryId }
        if (index >= 0) {
            categories[index] = categories[index].copy(monthlyBudgetLimit = monthlyLimit)
            state.value = categories.toList()
        }
    }
}

private class FakeMerchantRuleDao : MerchantRuleDao {

    val rules = mutableListOf<MerchantRuleEntity>()
    private val state = MutableStateFlow<List<MerchantRuleEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<MerchantRuleEntity>> = state

    override suspend fun findByCounterparty(counterparty: String): MerchantRuleEntity? =
        rules.firstOrNull { it.counterpartyIdentifier == counterparty }

    override suspend fun insert(rule: MerchantRuleEntity): Long {
        val existingIndex = rules.indexOfFirst {
            it.counterpartyIdentifier == rule.counterpartyIdentifier
        }
        val stored = if (existingIndex >= 0) {
            rule.copy(id = rules[existingIndex].id)
        } else {
            rule.copy(id = nextId++)
        }
        if (existingIndex >= 0) {
            rules[existingIndex] = stored
        } else {
            rules += stored
        }
        state.value = rules.toList()
        return stored.id
    }

    override suspend fun updateUsage(counterparty: String, reason: String, categoryId: Long) {
        val index = rules.indexOfFirst { it.counterpartyIdentifier == counterparty }
        if (index >= 0) {
            rules[index] = rules[index].copy(
                defaultReason = reason,
                categoryId = categoryId,
                usageCount = rules[index].usageCount + 1
            )
            state.value = rules.toList()
        }
    }
}
