package com.duduezn.budgettracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duduezn.budgettracker.data.local.entity.TransactionEntity
import com.duduezn.budgettracker.domain.model.Operator
import com.duduezn.budgettracker.domain.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY timestamp DESC")
    fun observeByStatus(status: TransactionStatus): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp >= :startInclusive AND timestamp < :endExclusive
        ORDER BY timestamp DESC
        """
    )
    fun observeBetween(startInclusive: Long, endExclusive: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /**
     * Recherche un doublon probable : même montant, même opérateur et fenêtre temporelle courte.
     * Cela évite de compter deux fois la même opération reçue par SMS puis par notification.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amount = :amount
          AND operator = :operator
          AND timestamp BETWEEN :fromInclusive AND :toInclusive
          AND (:counterparty IS NULL OR counterparty IS NULL OR counterparty = :counterparty)
        ORDER BY ABS(timestamp - :referenceTimestamp) ASC
        LIMIT 1
        """
    )
    suspend fun findDuplicate(
        amount: Double,
        operator: Operator,
        counterparty: String?,
        fromInclusive: Long,
        toInclusive: Long,
        referenceTimestamp: Long
    ): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Query(
        """
        UPDATE transactions
        SET detectedCategory = :categoryId,
            finalReason = :reason,
            status = 'CONFIRMED'
        WHERE id = :transactionId
        """
    )
    suspend fun confirmReason(transactionId: Long, categoryId: Long, reason: String)

    /** Somme des dépenses confirmées (paiements et retraits), hors dépôts. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions
        WHERE type IN ('PAIEMENT', 'RETRAIT')
          AND status = 'CONFIRMED'
          AND timestamp >= :startInclusive AND timestamp < :endExclusive
        """
    )
    suspend fun spentBetween(startInclusive: Long, endExclusive: Long): Double
}
